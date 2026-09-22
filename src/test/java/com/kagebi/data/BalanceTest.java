package com.kagebi.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.kagebi.data.def.EnemyDef;
import com.kagebi.data.def.FloorDef;
import com.kagebi.data.def.ItemDef;
import com.kagebi.data.def.LootTableDef;
import com.kagebi.data.def.WeaponDef;
import com.kagebi.save.Profile;
import com.kagebi.village.Farm;
import com.kagebi.village.Kitchen;

/**
 * The balance arithmetic, run against the shipped JSON.
 *
 * <p>Numbers in a data file drift: someone bumps a skeleton's hit points, the
 * run grows by four minutes, and nobody finds out until a playtest. This test
 * is the pacing model written down as code, so a change that breaks one of
 * the design promises - five stages that together run 25 to 40 minutes and
 * none of which outstays a sitting on its own, a first-timer who stops at
 * stage 2 or 3, a first two stages that buy exactly one upgrade - fails here
 * with the new numbers in the message.
 *
 * <p><b>The economy is measured per stage now.</b> When this was one descent,
 * a run passed through every floor above the one it ended on and banked the
 * lot; {@link #banked} is still that arithmetic and is kept because the shop's
 * upper price ladder was set from its cumulative column. What the shop's entry
 * prices are measured against is {@link #stageBank}, which has one term.
 *
 * <p>It is a MODEL. Its constants are assumptions, stated below, and the
 * right response to a playtest that disagrees is to change a constant here and
 * then retune the JSON until this passes again.
 */
class BalanceTest {

    // ---- the assumptions --------------------------------------------------------------

    /** Fraction of a weapon's theoretical DPS that lands, after approach, dodging and i-frames. */
    static final double EFFECTIVE = 0.60;
    /** What a relic is worth to damage output, and how many a cleared floor hands out. */
    static final double PER_RELIC = 1.10;
    static final int RELICS_PER_FLOOR = 2;
    /** Wall-clock seconds per second of pure damage: moving, dodging, waiting on telegraphs. */
    static final double OVERHEAD_TRASH = 2.6;
    /** Lower for a boss, which stands still to be hit more than a pack does. */
    static final double OVERHEAD_BOSS = 2.2;
    static final double WALK_SECONDS_PER_ROOM = 7;
    /** Hits taken per enemy met. A first-timer has never seen any of them before. */
    static final double FIRST_TIMER_HITS = 0.50;
    static final double COMPETENT_HITS = 0.25;
    /** Hits taken per boss phase. */
    static final double FIRST_TIMER_BOSS_HITS = 5;
    static final double COMPETENT_BOSS_HITS = 3;
    /** Share of run gold spent in the dungeon's own shops rather than banked. */
    static final double IN_RUN_SPEND = 0.20;
    static final int BASE_HP = 100;

    static ContentRegistry reg;
    static ShopCatalog shop;
    static VillageCatalog village;

    @BeforeAll
    static void load() {
        reg = ContentLoaderTest.real();
        shop = ShopCatalog.parse(ContentLoaderTest.DISK.apply(com.kagebi.assets.Assets.DATA_DIR));
        village = VillageCatalog.parse(ContentLoaderTest.DISK.apply(com.kagebi.assets.Assets.DATA_DIR));
    }

    // ---- per-floor quantities -------------------------------------------------------------

    static double avgRooms(FloorDef f) {
        return (f.roomsMin + f.roomsMax) / 2.0;
    }

    /** Rooms with a pack in them: everything but start, exit, treasure, shop and boss. */
    static double kills(FloorDef f) {
        double combat = avgRooms(f) - 2 - f.treasureRooms - f.shopRooms - (f.hasBoss() ? 1 : 0);
        return combat * (f.packMin + f.packMax) / 2.0;
    }

    interface Stat {
        double of(EnemyDef e);
    }

    /** Spawn-weighted mean of a stat over a floor's roster. */
    static double weighted(FloorDef f, Stat stat) {
        double sum = 0;
        double weights = 0;
        for (int i = 0; i < f.enemies.length; i++) {
            sum += stat.of(reg.enemy(f.enemies[i])) * f.enemyWeights[i];
            weights += f.enemyWeights[i];
        }
        return sum / weights;
    }

    static double avgHp(FloorDef f) {
        // Scaled, because the floor's multiplier is applied where an enemy is
        // spawned and never written back into the def. Reading maxHp straight
        // off the def says a stage that reuses an easier stage's roster is as
        // easy as it, which is the opposite of what hpScale was added to say.
        return weighted(f, e -> e.maxHp) * f.hpScale;
    }

    /** A boss's health as the room will actually build it. */
    static double bossHp(FloorDef f) {
        return reg.enemy(f.boss).maxHp * f.hpScale;
    }

    /** A hit is the enemy's harder blow: most hits taken are from its attack, if it has one. */
    static double avgHit(FloorDef f) {
        return weighted(f, e -> Math.max(e.contactDamage, e.attackDamage)) * f.damageScale;
    }

    static double dps(int floor) {
        return theoretical(reg.weapon("katana")) * EFFECTIVE
            * Math.pow(PER_RELIC, RELICS_PER_FLOOR * (floor - 1));
    }

    /**
     * What a player actually swings for on stage N, now that stages are
     * separate outings.
     *
     * <p>{@link #dps} compounds relics across the floors below - which was
     * correct when reaching floor 5 meant surviving floors 1 to 4 in one
     * sitting, and is how the floor pacing above is still modelled, because a
     * stage's own trash is still met after that stage's own relics. It is
     * wrong for the one moment where the mistake is expensive: nobody arrives
     * at the stage-5 boss with stage 1-4's relics any more. They arrive with
     * whatever this stage has handed them, which is one floor's worth.
     *
     * <p>Deliberately ignores the village shop, which does close some of that
     * gap. A boss sized against a player who has bought upgrades is a boss that
     * is unbeatable for the player who has not, and the first time anyone meets
     * a boss is the run they have banked the least.
     */
    static double stageDps() {
        return theoretical(reg.weapon("katana")) * EFFECTIVE
            * Math.pow(PER_RELIC, RELICS_PER_FLOOR);
    }

    /** Wall-clock seconds of one boss fight, for a player who brought only a stage. */
    static double bossSeconds(FloorDef f) {
        return f.hasBoss() ? bossHp(f) / stageDps() * OVERHEAD_BOSS : 0;
    }

    static double theoretical(WeaponDef w) {
        return w.damage * 60.0 / (w.windupSteps + w.activeSteps + w.recoverSteps);
    }

    static double seconds(FloorDef f) {
        double s = kills(f) * avgHp(f) / dps(f.number) * OVERHEAD_TRASH
            + avgRooms(f) * WALK_SECONDS_PER_ROOM;
        if (f.hasBoss()) {
            s += bossHp(f) / dps(f.number) * OVERHEAD_BOSS;
        }
        return s;
    }

    /** Expected value, per roll set, of whatever an item effect adds up to. */
    interface Value {
        double of(ItemDef item, double count);
    }

    static double expected(LootTableDef t, Value v) {
        double total = 0;
        for (LootTableDef.Entry e : t.entries) {
            double p = e.weight / (double) t.totalWeight();
            total += p * v.of(reg.item(e.itemId), (e.min + e.max) / 2.0);
        }
        return total * t.rolls;
    }

    static final Value HEAL = (item, n) ->
        "heal".equals(item.effect) || "max_hp_add".equals(item.effect) ? item.magnitude * n : 0;
    static final Value GOLD = (item, n) ->
        item.kind == ItemDef.Kind.GOLD ? item.magnitude * n : 0;

    static double perKill(FloorDef f, Value v) {
        return weighted(f, e -> expected(reg.lootTable(e.lootTable), v));
    }

    static double healing(FloorDef f) {
        double h = kills(f) * perKill(f, HEAL)
            + f.treasureRooms * expected(reg.lootTable("chest_treasure"), HEAL);
        if (f.hasBoss()) {
            h += expected(reg.lootTable(reg.enemy(f.boss).lootTable), HEAL);
        }
        return h;
    }

    static double damage(FloorDef f, double hits, double bossHits) {
        double d = kills(f) * hits * avgHit(f);
        if (f.hasBoss()) {
            EnemyDef b = reg.enemy(f.boss);
            d += bossHits * b.attackDamage * b.phases;
        }
        return d;
    }

    static double gold(FloorDef f) {
        double g = kills(f) * (weighted(f, e -> (e.goldMin + e.goldMax) / 2.0) + perKill(f, GOLD))
            + f.treasureRooms * expected(reg.lootTable("chest_treasure"), GOLD);
        if (f.hasBoss()) {
            EnemyDef b = reg.enemy(f.boss);
            g += (b.goldMin + b.goldMax) / 2.0 + expected(reg.lootTable(b.lootTable), GOLD);
        }
        return g;
    }

    /**
     * The floor a player of this skill dies on, or one past the last if they
     * win.
     *
     * <p>Down the descent only. A run is one stage picked off the map, not a
     * march through every floor that exists, and the side stages are neither
     * survived on the way to the ending nor in the order their numbers
     * suggest - walking them here would say a player dies in the Drowned Cove
     * on the way to the Flame Core, which is not a journey anyone makes.
     */
    static int deathFloor(double hits, double bossHits, int maxHp) {
        double hp = maxHp;
        for (FloorDef f : descent()) {
            hp = hp - damage(f, hits, bossHits) + healing(f);
            if (hp <= 0) {
                return f.number;
            }
            hp = Math.min(hp, maxHp);
        }
        return descent().size() + 1;
    }

    /** Banked gold for a run that dies halfway through {@code floor}. */
    static double banked(int floor) {
        double g = 0;
        for (FloorDef f : reg.allFloors()) {
            if (f.number < floor) {
                g += gold(f);
            } else if (f.number == floor) {
                g += gold(f) / 2;
            }
        }
        return g * (1 - IN_RUN_SPEND);
    }

    /**
      * What one stage pays into the bank: its own gold, and none of the gold
      * of the stages above it.
      *
      * <p>{@link #banked} is the arithmetic of a descent, which is what this
      * game used to be - it sums every floor down to the one the player died
      * on, because a single run passed through all of them. A stage is its own
      * outing, so the sum has exactly one term.
      *
      * @param cleared false for a death partway in, which pays half
      */
    static double stageBank(int stage, boolean cleared) {
        for (FloorDef f : reg.allFloors()) {
            if (f.number == stage) {
                double g = gold(f) * (1 - IN_RUN_SPEND);
                return cleared ? g : g / 2;
            }
        }
        return 0;
    }

    // ---- the promises ------------------------------------------------------------------------

    @Test
    void printTheArithmetic() {
        StringBuilder sb = new StringBuilder("\nfloor  rooms  kills  avgHP  dps   sec   min  hit  heal/kill gold\n");
        double total = 0;
        for (FloorDef f : reg.allFloors()) {
            double s = seconds(f);
            total += s;
            sb.append(String.format("  %d   %5.1f  %5.1f  %5.1f %5.1f %5.0f %5.1f %4.1f  %5.2f  %6.0f%n",
                f.number, avgRooms(f), kills(f), avgHp(f), dps(f.number), s, s / 60,
                avgHit(f), perKill(f, HEAL), gold(f)));
        }
        sb.append(String.format("five stages %.0fs = %.1f min%n", total, total / 60));
        sb.append(String.format("first-timer dies on floor %d; competent player: %s%n",
            deathFloor(FIRST_TIMER_HITS, FIRST_TIMER_BOSS_HITS, BASE_HP),
            deathFloor(COMPETENT_HITS, COMPETENT_BOSS_HITS, BASE_HP) > descent().size() ? "wins"
                : "dies on " + deathFloor(COMPETENT_HITS, COMPETENT_BOSS_HITS, BASE_HP)));
        // What a stage pays, which is what the shop is now priced against. The
        // cumulative column is what the same table used to report, and is kept
        // because that is what the shop's level-4 prices were set from.
        double running = 0;
        sb.append(String.format("stage  cleared  died halfway  cumulative%n"));
        for (FloorDef f : reg.allFloors()) {
            running += stageBank(f.number, true);
            sb.append(String.format("  %d   %7.0f  %12.0f  %10.0f%n", f.number,
                stageBank(f.number, true), stageBank(f.number, false), running));
        }
        sb.append(String.format("level-1 costs %s%n", Arrays.toString(levelOneCosts())));
        sb.append(String.format("village an hour: %.0f with the first tools, %.0f with the best;"
            + " a cleared stage averages %.0f%n",
            villagePerHour(false), villagePerHour(true), averageStage()));
        sb.append(trajectory("first-timer", FIRST_TIMER_HITS, FIRST_TIMER_BOSS_HITS, BASE_HP));
        sb.append(trajectory("first-timer + vigor 1", FIRST_TIMER_HITS, FIRST_TIMER_BOSS_HITS,
            BASE_HP + (int) shop.upgrade("vigor").magnitudePerLevel));
        sb.append(trajectory("competent", COMPETENT_HITS, COMPETENT_BOSS_HITS, BASE_HP));
        System.out.println(sb);
    }

    /** Damage taken, healing found and hit points left, floor by floor. */
    static String trajectory(String who, double hits, double bossHits, int maxHp) {
        StringBuilder sb = new StringBuilder(who + ":");
        double hp = maxHp;
        for (FloorDef f : reg.allFloors()) {
            double dmg = damage(f, hits, bossHits);
            double heal = healing(f);
            hp = hp - dmg + heal;
            sb.append(String.format("  F%d -%.0f +%.0f = %.0f", f.number, dmg, heal, hp));
            if (hp <= 0) {
                sb.append(" DEAD");
                break;
            }
            hp = Math.min(hp, maxHp);
        }
        return sb.append('\n').toString();
    }

    /**
     * Every floor of the descent is tougher than the one above it.
     *
     * <p>Side stages are left out, and the reason is the whole point of them.
     * The five numbered floors are read in order, each assumed to be reached
     * with what the one above handed over; a side stage is entered cold at
     * whatever strength the player already had, so it is balanced against the
     * kit someone brings rather than against the floor before it in the file.
     * Asserting the curve over both would force the cove to be harder than
     * the Flame Core to satisfy arithmetic nobody plays.
     */
    @Test
    void hitPointsRiseEveryFloorOfTheDescent() {
        double previous = 0;
        for (FloorDef f : descent()) {
            assertTrue(avgHp(f) > previous, "floor " + f.number + " is no tougher than the one above");
            previous = avgHp(f);
        }
    }

    @Test
    void theFiveStagesTogetherTakeTwentyFiveToFortyMinutes() {
        double total = 0;
        for (FloorDef f : descent()) {
            total += seconds(f);
        }
        assertTrue(total >= 25 * 60 && total <= 40 * 60, "the five stages are "
            + total / 60 + " minutes");
    }

    /**
     * No single stage outstays a sitting.
     *
     * <p>The number that matters changed when the descent became five stages.
     * Half an hour was the length of the whole game and nobody had to find it
     * in one piece; now a stage is what a player sits down for, so ten minutes
     * is the ceiling. Stage five is already 9.8 at the shipped table, which is
     * why this is tight rather than generous - it is a ceiling that will
     * actually catch the next floor somebody lengthens.
     */
    @Test
    void noSingleStageOutstaysASitting() {
        for (FloorDef f : reg.allFloors()) {
            assertTrue(seconds(f) <= 10 * 60,
                "stage " + f.number + " is " + seconds(f) / 60 + " minutes on its own");
        }
    }

    @Test
    void eachFloorOfTheDescentTakesLongerThanTheOneAbove() {
        double previous = 0;
        for (FloorDef f : descent()) {
            assertTrue(seconds(f) > previous, "floor " + f.number + " is shorter than the one above");
            previous = seconds(f);
        }
    }

    /**
     * A side stage is still a stage: it must be worth playing and must not
     * outstay a sitting, even though it sits outside the curve above.
     */
    @Test
    void everySideStageIsAStageLong() {
        for (FloorDef f : reg.allFloors()) {
            if (!f.side) {
                continue;
            }
            assertTrue(seconds(f) >= 4 * 60,
                "side stage " + f.number + " is " + seconds(f) / 60 + " minutes, barely a detour");
        }
    }

    /** The five numbered floors of the descent, in order, side stages left out. */
    private static java.util.List<FloorDef> descent() {
        java.util.List<FloorDef> out = new java.util.ArrayList<>();
        for (FloorDef f : reg.allFloors()) {
            if (!f.side) {
                out.add(f);
            }
        }
        return out;
    }

    /**
     * A boss is a fight, not a chore.
     *
     * <p>The number this pins was found by a playtest saying "boss qua trau" -
     * the boss is too beefy - and the arithmetic agreed: 900 and 1,800 hit
     * points, sized for a descent, came to 117 and 234 seconds once the game
     * became five separate stages. Nothing here noticed, because every promise
     * in this file was about a whole floor, and a floor is mostly walking and
     * trash. A four-minute boss hides inside a ten-minute stage.
     *
     * <p>Two minutes is the ceiling, and it is a ceiling rather than a target:
     * the last boss should be allowed to be the longest fight in the game. The
     * shipped table puts the final one at about 105 seconds.
     */
    @Test
    void noBossFightOutstaysItsWelcome() {
        for (FloorDef f : reg.allFloors()) {
            if (!f.hasBoss()) {
                continue;
            }
            assertTrue(bossSeconds(f) <= 120, "the stage " + f.number + " boss ("
                + reg.enemy(f.boss).maxHp + " hp) is " + bossSeconds(f)
                + " seconds for a player who brought only this stage's relics");
        }
    }

    /**
     * Where a first-timer stops.
     *
     * <p>The same arithmetic and the same answer as when this was one descent;
     * only the name changed. A stage starts at full health, so "the floor they
     * die on" and "the first stage they cannot clear" are the same number as
     * long as the model starts each floor full - which {@link #deathFloor}
     * does not, so this is now the more forgiving of the two readings and the
     * real wall is no earlier than it says.
     */
    @Test
    void aFirstTimerCannotClearStageTwoOrThree() {
        int floor = deathFloor(FIRST_TIMER_HITS, FIRST_TIMER_BOSS_HITS, BASE_HP);
        assertTrue(floor == 2 || floor == 3, "first-timer stops at stage " + floor);
    }

    /** The other half of the promise: the wall is a skill wall, not a number wall. */
    @Test
    void aCompetentPlayerGetsPastTheWall() {
        assertTrue(deathFloor(COMPETENT_HITS, COMPETENT_BOSS_HITS, BASE_HP) > 3);
    }

    /** One level of vigor is meant to move a first-timer one floor deeper, not three. */
    @Test
    void theFirstUpgradeHelpsWithoutWinningTheGame() {
        int base = deathFloor(FIRST_TIMER_HITS, FIRST_TIMER_BOSS_HITS, BASE_HP);
        int withVigor = deathFloor(FIRST_TIMER_HITS, FIRST_TIMER_BOSS_HITS,
            BASE_HP + (int) shop.upgrade("vigor").magnitudePerLevel);
        assertTrue(withVigor <= base + 1, "vigor 1 moves the first-timer from " + base + " to " + withVigor);
    }

    static int[] levelOneCosts() {
        int[] costs = new int[shop.upgrades().size];
        for (int i = 0; i < costs.length; i++) {
            costs[i] = shop.upgrades().get(i).costs[0];
        }
        Arrays.sort(costs);
        return costs;
    }

    /**
     * The first two stages buy the first upgrade, and not the second.
     *
     * <p>This replaces a promise about a first death, and the promise had to
     * change with the game rather than the numbers. A descent that ended on
     * floor three banked everything down to it - about 635 - and the shop was
     * priced so that bought exactly one thing. A stage banks only its own, so
     * stage one pays 130 and the cheapest upgrade is 350: measured against a
     * single death the old assertion would now be off by a factor of five.
     *
     * <p>What survives is what it was protecting. There has to be a first
     * purchase early, or the shop is scenery; and it must not be every
     * purchase, or the shop has nothing left to say. Clearing stages one and
     * two is what pays for it now, which is the same beat one stage later.
     */
    @Test
    void theFirstTwoStagesBuyExactlyOneUpgrade() {
        double bank = stageBank(1, true) + stageBank(2, true);
        int[] costs = levelOneCosts();
        assertTrue(bank >= costs[0],
            "stages one and two bank " + bank + ", cheapest upgrade is " + costs[0]);
        assertTrue(bank < costs[0] + costs[1],
            "stages one and two bank " + bank + ", enough for two");
    }

    /**
     * And one stage on its own is not enough, which is what keeps the village
     * a place the player returns to rather than a shop they clear out on the
     * way past.
     */
    @Test
    void oneStageDoesNotBuyAnything() {
        assertTrue(stageBank(1, true) < levelOneCosts()[0],
            "stage one alone banks " + stageBank(1, true));
    }

    /**
     * A first full clear of the map pays for something on the unlock shelf.
     *
     * <p>This used to measure the cheapest character. The ninja recolours were
     * folded into skins, so the shelf's dearest tier is now a colour - priced
     * exactly as the characters were, because it is the same shelf with the
     * pretence removed. What is being asserted has not changed: a player who
     * works through the whole map can afford the next thing on it.
     */
    @Test
    void aFullClearPaysForTheCheapestPrize() {
        int cheapest = Integer.MAX_VALUE;
        for (ShopCatalog.Unlock u : shop.unlocks()) {
            if (u.kind != ShopCatalog.UnlockKind.WEAPON) {
                cheapest = Math.min(cheapest, u.cost);
            }
        }
        assertTrue(cheapest < Integer.MAX_VALUE, "the shelf has nothing but weapons on it");
        // Every stage cleared once, which is what a first full run through the
        // map is. banked(6) is the same sum for the descent this used to be.
        double all = 0;
        for (FloorDef f : reg.allFloors()) {
            all += stageBank(f.number, true);
        }
        assertTrue(all >= cheapest, "clearing every stage banks " + all
            + ", cheapest prize " + cheapest);
    }

    // ---- the village -----------------------------------------------------------------------------

    /** What a cleared stage banks, averaged over the five: what "a stage" means below. */
    static double averageStage() {
        double total = 0;
        int stages = 0;
        for (FloorDef f : reg.allFloors()) {
            total += stageBank(f.number, true);
            stages++;
        }
        return total / stages;
    }

    /**
     * Gold an hour from the farm: every plot of the whole field growing whichever
     * crop earns most, picked the moment it ripens and sold at once.
     */
    static double farmPerHour() {
        double best = 0;
        for (VillageCatalog.Crop c : village.crops()) {
            double profit = c.yield * village.good(c.good).price - c.seedPrice;
            best = Math.max(best, profit * 3600 / (c.stageSeconds * Farm.RIPE));
        }
        return best * Farm.allPlots(village);
    }

    /** Gold an hour from one worker, collected and sold as fast as they make it. */
    static double workshopPerHour(VillageCatalog.Workshop w, boolean bestTool) {
        VillageCatalog.Tool tool = village.tool(w.tool);
        double period = w.seconds / Math.pow(tool.speedPerLevel, bestTool ? tool.maxLevel : 0);
        double value = 0;
        double weights = 0;
        for (int i = 0; i < w.goods.length; i++) {
            value += w.weights[i] * village.good(w.goods[i]).price;
            weights += w.weights[i];
        }
        return 3600 / period * value / weights;
    }

    static double villagePerHour(boolean bestTools) {
        double total = farmPerHour();
        for (VillageCatalog.Workshop w : village.workshops()) {
            total += workshopPerHour(w, bestTools);
        }
        return total;
    }

    /**
     * An hour in the village, played perfectly with every tool bought, pays less
     * than one stage of the dungeon.
     *
     * <p>The promise the village economy was built on. A stage takes about six
     * minutes; if an hour on the island paid more, the dungeon would be what a
     * player does between harvests, and this game is the other way round. What
     * the village is for is food, which is priced in its ingredients below.
     */
    @Test
    void anHourInTheVillageAtItsBestPaysLessThanAStage() {
        assertTrue(villagePerHour(true) < averageStage(), "the village pays "
            + villagePerHour(true) + " an hour at its best; a cleared stage averages " + averageStage());
    }

    /**
     * No meal heals more for what its ingredients would sell for than a small
     * potion heals for its price. Otherwise buying a potion from the herbalist
     * is a mistake a player makes once.
     */
    @Test
    void noMealHealsMoreForItsIngredientsThanASmallPotionDoesForItsPrice() {
        ItemDef potion = reg.item("potion_small");
        for (VillageCatalog.Recipe r : village.recipes()) {
            ItemDef meal = reg.item(r.item);
            if (!"heal".equals(meal.effect)) {
                continue;
            }
            int cost = Kitchen.cost(village, r);
            assertTrue(meal.magnitude * potion.price <= potion.magnitude * cost, r.id + " heals "
                + meal.magnitude + " for " + cost + " gold of ingredients; a small potion heals "
                + potion.magnitude + " for " + potion.price);
        }
    }

    /** A meal that buffs costs at least the draught with the same buff, and gives no more of it. */
    @Test
    void aBuffingMealCostsAtLeastTheDraughtItStandsInFor() {
        for (VillageCatalog.Recipe r : village.recipes()) {
            ItemDef meal = reg.item(r.item);
            if ("heal".equals(meal.effect)) {
                continue;
            }
            ItemDef draught = null;
            for (ItemDef i : reg.allItems()) {
                if (i.forSale() && i.effect.equals(meal.effect)) {
                    draught = i;
                }
            }
            assertTrue(draught != null, r.id + " gives " + meal.effect + ", which nothing the trader sells gives");
            int cost = Kitchen.cost(village, r);
            assertTrue(cost >= draught.price, r.id + " costs " + cost + " in ingredients; "
                + draught.id + " costs " + draught.price);
            assertTrue(meal.magnitude <= draught.magnitude, r.id + " is stronger than " + draught.id);
        }
    }

    // ---- weapons ---------------------------------------------------------------------------------

    /**
     * Melee weapons trade speed for reach and width, not for damage per
     * second; within 25% of the katana's, or one of them is simply better.
     * The net is the exception by design and the hammer's number is paid for
     * in root steps.
     */
    @Test
    void meleeWeaponsDealComparableDamage() {
        double katana = theoretical(reg.weapon("katana"));
        for (String id : new String[] {"axe", "pickaxe"}) {
            double r = theoretical(reg.weapon(id)) / katana;
            assertTrue(r > 0.75 && r < 1.25, id + " is " + r + "x the katana");
        }
        WeaponDef hammer = reg.weapon("hammer");
        int cycle = hammer.windupSteps + hammer.activeSteps + hammer.recoverSteps;
        assertTrue(hammer.rootSteps * 2 > cycle, "the hammer must root for over half its swing");
    }

    @Test
    void rangeCostsDamage() {
        double katana = theoretical(reg.weapon("katana"));
        for (WeaponDef w : reg.allWeapons()) {
            if (w.thrown()) {
                assertTrue(theoretical(w) < katana, w.id + " outdamages the katana from range");
            }
        }
    }

    /**
     * One sword, and it has to be a weapon the content actually defines.
     *
     * <p>It was three: the katana plus two spells that came free with the fox
     * rather than being bought, because the validator wants everything in the
     * content to be reachable and "it comes with the character" was how they
     * were reached. The fox is gone and so are they.
     */
    @Test
    void theStarterKitIsWhatProfileSaysItIs() {
        Profile p = new Profile();
        assertEquals(1, p.unlockedWeapons.size);
        for (String id : p.unlockedWeapons) {
            assertTrue(reg.weapon(id) != null, id);
        }
    }
}
