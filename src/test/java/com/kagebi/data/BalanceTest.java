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
 * the design promises - a 25-to-40-minute descent, a first-timer who dies on
 * floor 2 or 3, a first death that buys exactly one upgrade - fails here with
 * the new numbers in the message.
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
        sb.append(String.format("descent %.0fs = %.1f min%n", total, total / 60));
        sb.append(String.format("first-timer dies on floor %d; competent player: %s%n",
            deathFloor(FIRST_TIMER_HITS, FIRST_TIMER_BOSS_HITS, BASE_HP),
            deathFloor(COMPETENT_HITS, COMPETENT_BOSS_HITS, BASE_HP) == 6 ? "wins"
                : "dies on " + deathFloor(COMPETENT_HITS, COMPETENT_BOSS_HITS, BASE_HP)));
        int death = deathFloor(FIRST_TIMER_HITS, FIRST_TIMER_BOSS_HITS, BASE_HP);
        sb.append(String.format("first death banks %.0f; floor-4 death banks %.0f; full clear banks %.0f;"
            + " level-1 costs %s%n", banked(death), banked(4), banked(6), Arrays.toString(levelOneCosts())));
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
    void theDescentTakesTwentyFiveToFortyMinutes() {
        double total = 0;
        for (FloorDef f : reg.allFloors()) {
            total += seconds(f);
        }
        assertTrue(total >= 25 * 60 && total <= 40 * 60, "descent is " + total / 60 + " minutes");
    }

    @Test
    void eachFloorTakesLongerThanTheOneAbove() {
        double previous = 0;
        for (FloorDef f : reg.allFloors()) {
            assertTrue(seconds(f) > previous, "floor " + f.number + " is shorter than the one above");
            previous = seconds(f);
        }
    }

    @Test
    void aFirstTimerDiesOnFloorTwoOrThree() {
        int floor = deathFloor(FIRST_TIMER_HITS, FIRST_TIMER_BOSS_HITS, BASE_HP);
        assertTrue(floor == 2 || floor == 3, "first-timer dies on floor " + floor);
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
     * The first death has to buy something or there is no second run, and it
     * should not buy everything or the shop has nothing left to say. "Exactly
     * one, with change" is the target; this pins it.
     */
    @Test
    void aFirstDeathBuysExactlyOneUpgrade() {
        double bank = banked(deathFloor(FIRST_TIMER_HITS, FIRST_TIMER_BOSS_HITS, BASE_HP));
        int[] costs = levelOneCosts();
        assertTrue(bank >= costs[0], "first death banks " + bank + ", cheapest upgrade is " + costs[0]);
        assertTrue(bank < costs[0] + costs[1], "first death banks " + bank + ", enough for two");
    }

    @Test
    void aFullClearPaysForACharacter() {
        int cheapest = Integer.MAX_VALUE;
        for (ShopCatalog.Unlock u : shop.unlocks()) {
            if (u.kind == ShopCatalog.UnlockKind.CHARACTER) {
                cheapest = Math.min(cheapest, u.cost);
            }
        }
        assertTrue(banked(6) >= cheapest, "a win banks " + banked(6) + ", cheapest character " + cheapest);
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
