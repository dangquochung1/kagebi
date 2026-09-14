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

    @BeforeAll
    static void load() {
        reg = ContentLoaderTest.real();
        shop = ShopCatalog.parse(ContentLoaderTest.DISK.apply(com.kagebi.assets.Assets.DATA_DIR));
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
        return weighted(f, e -> e.maxHp);
    }

    /** A hit is the enemy's harder blow: most hits taken are from its attack, if it has one. */
    static double avgHit(FloorDef f) {
        return weighted(f, e -> Math.max(e.contactDamage, e.attackDamage));
    }

    static double dps(int floor) {
        return theoretical(reg.weapon("katana")) * EFFECTIVE
            * Math.pow(PER_RELIC, RELICS_PER_FLOOR * (floor - 1));
    }

    static double theoretical(WeaponDef w) {
        return w.damage * 60.0 / (w.windupSteps + w.activeSteps + w.recoverSteps);
    }

    static double seconds(FloorDef f) {
        double s = kills(f) * avgHp(f) / dps(f.number) * OVERHEAD_TRASH
            + avgRooms(f) * WALK_SECONDS_PER_ROOM;
        if (f.hasBoss()) {
            s += reg.enemy(f.boss).maxHp / dps(f.number) * OVERHEAD_BOSS;
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

    /** The floor a player of this skill dies on, or 6 if they win. */
    static int deathFloor(double hits, double bossHits, int maxHp) {
        double hp = maxHp;
        for (FloorDef f : reg.allFloors()) {
            hp = hp - damage(f, hits, bossHits) + healing(f);
            if (hp <= 0) {
                return f.number;
            }
            hp = Math.min(hp, maxHp);
        }
        return 6;
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
            deathFloor(COMPETENT_HITS, COMPETENT_BOSS_HITS, BASE_HP) == 6 ? "wins"
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

    @Test
    void hitPointsRiseEveryFloor() {
        double previous = 0;
        for (FloorDef f : reg.allFloors()) {
            assertTrue(avgHp(f) > previous, "floor " + f.number + " is no tougher than the one above");
            previous = avgHp(f);
        }
    }

    @Test
    void theFiveStagesTogetherTakeTwentyFiveToFortyMinutes() {
        double total = 0;
        for (FloorDef f : reg.allFloors()) {
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
    void eachFloorTakesLongerThanTheOneAbove() {
        double previous = 0;
        for (FloorDef f : reg.allFloors()) {
            assertTrue(seconds(f) > previous, "floor " + f.number + " is shorter than the one above");
            previous = seconds(f);
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

    @Test
    void aFullClearPaysForACharacter() {
        int cheapest = Integer.MAX_VALUE;
        for (ShopCatalog.Unlock u : shop.unlocks()) {
            if (u.kind == ShopCatalog.UnlockKind.CHARACTER) {
                cheapest = Math.min(cheapest, u.cost);
            }
        }
        // Every stage cleared once, which is what a first full run through the
        // map is. banked(6) is the same sum for the descent this used to be.
        double all = 0;
        for (FloorDef f : reg.allFloors()) {
            all += stageBank(f.number, true);
        }
        assertTrue(all >= cheapest, "clearing every stage banks " + all
            + ", cheapest character " + cheapest);
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

    @Test
    void theStarterKitIsWhatProfileSaysItIs() {
        Profile p = new Profile();
        assertEquals(1, p.unlockedWeapons.size);
        assertTrue(reg.weapon(p.unlockedWeapons.first()) != null);
    }
}
