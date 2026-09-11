package com.kagebi.loot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Array;
import com.kagebi.assets.Assets;
import com.kagebi.data.ContentLoader;
import com.kagebi.data.ContentRegistry;
import com.kagebi.data.def.EnemyDef;
import com.kagebi.data.def.LootTableDef;

/**
 * Drop distributions, tested statistically rather than by eye.
 *
 * <p>Every check is a chi-square goodness-of-fit at p = 0.001 against the
 * weights the table declares. The seeds are fixed, so each test is
 * deterministic: it cannot flake, and if it fails it fails every time, which
 * is a real bias rather than a bad night. No Gdx application is created
 * anywhere in this file - that is the other half of what it proves.
 */
class LootRollerTest {

    private static final int N = 40_000;

    private static ContentRegistry content() {
        return ContentLoader.parse(new FileHandle(new File(Assets.DATA_DIR)));
    }

    /**
     * The chi-square value above which a fit is rejected at p = 0.001, by the
     * Wilson-Hilferty approximation. Against the exact tables it is within
     * 0.3 for every dof used here and always on the high side, so it never
     * fails a fit the exact value would pass.
     */
    static double critical(int dof) {
        double z = 3.0902;      // standard normal quantile for 0.999
        double a = 2.0 / (9.0 * dof);
        return dof * Math.pow(1 - a + z * Math.sqrt(a), 3);
    }

    static double chiSquare(long[] observed, double[] expected) {
        double x = 0;
        for (int i = 0; i < observed.length; i++) {
            double d = observed[i] - expected[i];
            x += d * d / expected[i];
        }
        return x;
    }

    // ---- determinism ------------------------------------------------------------

    @Test
    void theSameSeedAlwaysDropsTheSameThing() {
        LootTableDef t = content().lootTable("chest_locked");
        for (long seed = 0; seed < 2_000; seed++) {
            assertEquals(LootRoller.roll(t, seed).toString(), LootRoller.roll(t, seed).toString(),
                "seed " + seed);
        }
    }

    // ---- distributions -----------------------------------------------------------

    /**
     * Every shipped table, rolled forty thousand times, against its own
     * weights. This is the test that makes the percentages written in
     * loot_tables.json true rather than intended.
     */
    @Test
    void everyShippedTableDropsInProportionToItsWeights() {
        for (LootTableDef t : content().allLootTables()) {
            Map<String, Integer> index = new LinkedHashMap<>();
            Map<String, Double> weight = new LinkedHashMap<>();
            if (t.nothingWeight > 0) {
                index.put(null, 0);
                weight.put(null, (double) t.nothingWeight);
            }
            for (LootTableDef.Entry e : t.entries) {
                index.putIfAbsent(e.itemId, index.size());
                weight.merge(e.itemId, (double) e.weight, Double::sum);
            }
            long[] observed = new long[index.size()];
            long draws = (long) N * t.rolls;
            for (long seed = 0; seed < N; seed++) {
                Array<LootRoller.Drop> drops = LootRoller.roll(t, seed);
                for (LootRoller.Drop d : drops) {
                    observed[index.get(d.itemId)]++;
                }
                if (t.nothingWeight > 0) {
                    observed[0] += t.rolls - drops.size;
                }
            }
            double[] expected = new double[index.size()];
            int i = 0;
            for (double w : weight.values()) {
                expected[i++] = draws * w / t.totalWeight();
            }
            double x = chiSquare(observed, expected);
            int dof = observed.length - 1;
            assertTrue(x < critical(dof), t.id + ": chi-square " + x + " exceeds "
                + critical(dof) + " at " + dof + " dof");
        }
    }

    /** The number loot_tables.json's comments quote for the first floor. */
    @Test
    void aFloorOneKillDropsSomethingAQuarterOfTheTime() {
        LootTableDef t = content().lootTable("trash_f1");
        int n = 100_000;
        int dropped = 0;
        for (long seed = 0; seed < n; seed++) {
            dropped += LootRoller.roll(t, seed).size;
        }
        assertEquals(0.26, dropped / (double) n, 0.005);
    }

    @Test
    void countsAreUniformBetweenMinAndMaxInclusive() {
        LootTableDef t = new LootTableDef("t",
            new LootTableDef.Entry[] {new LootTableDef.Entry("coin", 1, 2, 5)}, 0, 1);
        long[] observed = new long[4];
        for (long seed = 0; seed < N; seed++) {
            int c = LootRoller.roll(t, seed).first().count;
            assertTrue(c >= 2 && c <= 5, "count " + c);
            observed[c - 2]++;
        }
        double[] expected = {N / 4.0, N / 4.0, N / 4.0, N / 4.0};
        assertTrue(chiSquare(observed, expected) < critical(3));
    }

    @Test
    void everyRollHappensAndNothingWeightZeroNeverMisses() {
        LootTableDef t = content().lootTable("boss_f5");
        assertEquals(0, t.nothingWeight);
        for (long seed = 0; seed < 5_000; seed++) {
            assertEquals(t.rolls, LootRoller.roll(t, seed).size);
        }
    }

    @Test
    void aTableWithNoWeightDropsNothingRatherThanThrowing() {
        LootTableDef t = new LootTableDef("t", new LootTableDef.Entry[0], 0, 3);
        assertEquals(0, LootRoller.roll(t, 1L).size);
    }

    // ---- luck ------------------------------------------------------------------------

    @Test
    void luckShrinksTheNothingWeightAndOnlyThat() {
        LootTableDef t = content().lootTable("trash_f1");
        assertEquals(74, LootRoller.nothingWeight(t, 0f));
        assertEquals(68, LootRoller.nothingWeight(t, 0.08f), "lucky_clover: rounded, not truncated");
        assertEquals(37, LootRoller.nothingWeight(t, 0.5f));
        assertEquals(0, LootRoller.nothingWeight(t, 1f));
        assertEquals(0, LootRoller.nothingWeight(t, 3f), "clamped, never negative");
        assertEquals(74, LootRoller.nothingWeight(t, -1f), "negative luck is no luck");

        // At luck 0.5 the pool is 37 + 26 = 63, so 26/63 of rolls drop something,
        // and among the drops the entries keep their own 12:6:4:4 proportions.
        long[] observed = new long[4];
        int dropped = 0;
        for (long seed = 0; seed < N; seed++) {
            for (LootRoller.Drop d : LootRoller.roll(t, seed, 0.5f)) {
                dropped++;
                for (int i = 0; i < t.entries.length; i++) {
                    if (t.entries[i].itemId.equals(d.itemId)) {
                        observed[i]++;
                    }
                }
            }
        }
        assertEquals(26.0 / 63.0, dropped / (double) N, 0.01);
        double[] expected = new double[4];
        for (int i = 0; i < 4; i++) {
            expected[i] = dropped * t.entries[i].weight / 26.0;
        }
        assertTrue(chiSquare(observed, expected) < critical(3), "luck changed what drops");
    }

    @Test
    void fullLuckMeansEveryRollFindsSomething() {
        LootTableDef t = content().lootTable("pot");
        for (long seed = 0; seed < 5_000; seed++) {
            assertEquals(1, LootRoller.roll(t, seed, 1f).size);
        }
    }

    // ---- seeding -----------------------------------------------------------------------

    /**
     * Callers will seed with runSeed + killIndex, so the outcome for seed n
     * must say nothing about the outcome for seed n+1. A 5x5 contingency table
     * of consecutive outcomes, tested for independence. java.util.Random fails
     * this by a factor of about five hundred; see the class comment on
     * LootRoller.
     */
    @Test
    void consecutiveSeedsGiveIndependentDrops() {
        LootTableDef t = content().lootTable("trash_f1");
        Map<String, Integer> index = new LinkedHashMap<>();
        index.put("-", 0);
        for (LootTableDef.Entry e : t.entries) {
            index.put(e.itemId, index.size());
        }
        int n = 200_000;
        long[][] pairs = new long[5][5];
        int prev = outcome(t, 0, index);
        for (long seed = 1; seed <= n; seed++) {
            int cur = outcome(t, seed, index);
            pairs[prev][cur]++;
            prev = cur;
        }
        long[] row = new long[5];
        long[] col = new long[5];
        for (int i = 0; i < 5; i++) {
            for (int j = 0; j < 5; j++) {
                row[i] += pairs[i][j];
                col[j] += pairs[i][j];
            }
        }
        double x = 0;
        for (int i = 0; i < 5; i++) {
            for (int j = 0; j < 5; j++) {
                double e = (double) row[i] * col[j] / n;
                x += (pairs[i][j] - e) * (pairs[i][j] - e) / e;
            }
        }
        assertTrue(x < critical(16), "consecutive seeds correlate: chi-square " + x);
    }

    private static int outcome(LootTableDef t, long seed, Map<String, Integer> index) {
        Array<LootRoller.Drop> d = LootRoller.roll(t, seed);
        return d.size == 0 ? 0 : index.get(d.first().itemId);
    }

    // ---- gold -----------------------------------------------------------------------------

    @Test
    void enemyGoldIsUniformOverItsRange() {
        EnemyDef slime = content().enemy("slime");
        int span = slime.goldMax - slime.goldMin + 1;
        long[] observed = new long[span];
        for (long seed = 0; seed < N; seed++) {
            int g = LootRoller.gold(slime, seed);
            assertTrue(g >= slime.goldMin && g <= slime.goldMax, "gold " + g);
            observed[g - slime.goldMin]++;
        }
        double[] expected = new double[span];
        java.util.Arrays.fill(expected, N / (double) span);
        assertTrue(chiSquare(observed, expected) < critical(span - 1));
    }

    @Test
    void theCriticalValuesAreTheTabulatedOnes() {
        // Exact p = 0.001 values from any chi-square table, for the dofs used above.
        double[][] exact = {{3, 16.266}, {4, 18.467}, {6, 22.458}, {16, 39.252}};
        for (double[] e : exact) {
            double approx = critical((int) e[0]);
            assertTrue(approx >= e[1] && approx - e[1] < 0.3, e[0] + " dof: " + approx);
        }
    }
}
