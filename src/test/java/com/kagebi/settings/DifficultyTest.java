package com.kagebi.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The three settings, and the promises their numbers make. */
class DifficultyTest {

    /**
     * NORMAL must be the content exactly as authored, or the balance work
     * already done stops being the specification and becomes one setting among
     * three. {@code BalanceTest} models this row and no other.
     */
    @Test
    void normalChangesNothingAtAll() {
        assertEquals(1f, Difficulty.NORMAL.damageTaken);
        assertEquals(1f, Difficulty.NORMAL.enemyHp);
        assertEquals(1f, Difficulty.NORMAL.bossHp);
        assertEquals(Difficulty.NORMAL, Difficulty.DEFAULT);
        for (int hp : new int[] {1, 22, 1800}) {
            assertEquals(hp, Difficulty.NORMAL.scaleHp(hp, false));
            assertEquals(hp, Difficulty.NORMAL.scaleHp(hp, true));
        }
    }

    /** The three are ordered, and the order is the same in both directions. */
    @Test
    void harderMeansMoreDamageTakenAndMoreEnemyHealth() {
        assertTrue(Difficulty.HARD.damageTaken > Difficulty.NORMAL.damageTaken);
        assertTrue(Difficulty.NORMAL.damageTaken > Difficulty.WEAK.damageTaken);
        assertTrue(Difficulty.HARD.enemyHp > Difficulty.NORMAL.enemyHp);
        assertTrue(Difficulty.NORMAL.enemyHp > Difficulty.WEAK.enemyHp);
    }

    /**
     * The final boss is 1,800 hit points, which is eighty-nine measured seconds
     * of unbroken swinging with the starting katana. At the ordinary enemy
     * factor HARD would push that past a hundred and five, which is not harder,
     * only longer - so a boss must always scale more gently than its minions.
     */
    @Test
    void aBossScalesMoreGentlyThanAnOrdinaryEnemy() {
        for (Difficulty d : Difficulty.values()) {
            assertTrue(Math.abs(d.bossHp - 1f) <= Math.abs(d.enemyHp - 1f),
                d + " moves a boss further from normal than an ordinary enemy");
        }
        // The measured floor, in seconds of swinging, must stay under 100.
        float secondsAtNormal = 89f;
        assertTrue(secondsAtNormal * Difficulty.HARD.bossHp < 100f,
            "the final boss becomes a slog on hard");
    }

    /** An enemy can never be scaled out of existence, however weak the setting. */
    @Test
    void nothingIsEverScaledBelowOneHitPoint() {
        for (Difficulty d : Difficulty.values()) {
            assertTrue(d.scaleHp(1, false) >= 1, d + " scaled a 1 hp enemy to nothing");
            assertTrue(d.scaleHp(1, true) >= 1);
        }
    }

    /** A saved name that no longer exists must fall back, not fail to boot. */
    @Test
    void anUnknownSavedNameFallsBackToNormal() {
        assertEquals(Difficulty.HARD, Difficulty.fromName("HARD"));
        assertEquals(Difficulty.DEFAULT, Difficulty.fromName("BRUTAL"));
        assertEquals(Difficulty.DEFAULT, Difficulty.fromName(null));
        assertEquals(Difficulty.DEFAULT, Difficulty.fromName(""));
    }

    /**
     * The two numbers must move together. A setting that made enemies tougher
     * while making the player safer would be neither harder nor easier, just
     * longer, and is the shape of mistake this catches.
     */
    @Test
    void theTwoDialsNeverPullAgainstEachOther() {
        for (Difficulty d : Difficulty.values()) {
            boolean harderToSurvive = d.damageTaken >= 1f;
            boolean tougherEnemies = d.enemyHp >= 1f;
            assertEquals(harderToSurvive, tougherEnemies,
                d + " scales damage and enemy health in opposite directions");
        }
    }
}
