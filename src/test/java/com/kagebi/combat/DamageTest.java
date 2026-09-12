package com.kagebi.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.Test;

/**
 * The damage formula, number by number.
 *
 * <p>Every expected value here is worked by hand in the comment beside it, so a
 * failure says which rule moved rather than only that something did.
 */
class DamageTest {

    @Test
    void plainHitIsTheWeaponNumber() {
        assertEquals(7, Damage.compute(7, 1f, false, 2f, 0, 0f));
    }

    @Test
    void armourIsAFlatSoak() {
        // 10 - 3
        assertEquals(7, Damage.compute(10, 1f, false, 2f, 3, 0f));
    }

    @Test
    void critMultipliesBeforeArmourIsTaken() {
        // 10 x 2 = 20, then 20 - 3 = 17. Taking armour first would give 14,
        // which makes armour count double against a crit.
        assertEquals(17, Damage.compute(10, 1f, true, 2f, 3, 0f));
    }

    @Test
    void armourComesOffBeforeResistance() {
        // (20 - 4) x 0.5 = 8. The other order, 20 x 0.5 - 4, gives 6 - and
        // makes armour and resistance two names for one stat.
        assertEquals(8, Damage.compute(20, 1f, false, 2f, 4, 0.5f));
    }

    @Test
    void aConnectingHitAlwaysCostsAtLeastOne() {
        // 3 - 50 is negative; the floor is what keeps the flash honest.
        assertEquals(Damage.MIN, Damage.compute(3, 1f, false, 2f, 50, 0f));
    }

    @Test
    void resistanceIsCappedShortOfImmunity() {
        // Typed as 1.0, clamped to 0.9: 100 x 0.1 = 10, not 0.
        assertEquals(10, Damage.compute(100, 1f, false, 2f, 0, 1f));
        // Negative resistance is ignored rather than amplifying damage.
        assertEquals(10, Damage.compute(10, 1f, false, 2f, 0, -0.5f));
    }

    @Test
    void decimalMultipliersRoundTheWayTheyWereWritten() {
        // 1.15f is 1.14999998 in binary, so 10 x 1.15f is 11.4999998 and a
        // bare Math.round gives 11. A designer who typed "+15% on a 10" meant
        // 11.5, rounded to 12.
        assertEquals(12, Damage.outgoing(10, 1.15f, false, 2f));
        // And the nudge does not push an honest value over a boundary.
        assertEquals(11, Damage.outgoing(10, 1.14f, false, 2f));
    }

    @Test
    void critRollIsDeterministicForASeed() {
        Random a = new Random(42L);
        Random b = new Random(42L);
        for (int i = 0; i < 200; i++) {
            assertEquals(Damage.rollCrit(a, 0.3f), Damage.rollCrit(b, 0.3f));
        }
    }

    @Test
    void critChanceAtTheEndsIsAbsolute() {
        Random r = new Random(7L);
        for (int i = 0; i < 1000; i++) {
            assertFalse(Damage.rollCrit(r, 0f));
            assertTrue(Damage.rollCrit(r, 1f));
        }
    }

    // ---- the damage roll ------------------------------------------------------

    /** A null generator skips the roll, which is what keeps old tests exact. */
    @Test
    void withoutAGeneratorTheDamageIsStillTheOldFixedNumber() {
        assertEquals(Damage.outgoing(7, 1f, false, 2f),
                     Damage.outgoing(7, 1f, false, 2f, null));
    }

    /** The katana's 7 lands as 6, 7 or 8 - three values the player can see. */
    @Test
    void theRollStaysInsideItsSpreadAndUsesTheWholeOfIt() {
        Random r = new Random(11L);
        int low = Math.round(7 * (1f - Damage.SPREAD));
        int high = Math.round(7 * (1f + Damage.SPREAD));
        java.util.Set<Integer> seen = new java.util.TreeSet<>();
        for (int i = 0; i < 4000; i++) {
            int rolled = Damage.outgoing(7, 1f, false, 2f, r);
            assertTrue(rolled >= low && rolled <= high, "rolled " + rolled);
            seen.add(rolled);
        }
        assertEquals(java.util.Set.of(6, 7, 8), seen,
            "all three values should turn up, or the spread is invisible");
    }

    /**
     * The mean is unchanged, which is the whole reason this could be added
     * without retuning anything: {@code BalanceTest} models damage per second
     * from the flat number, and a symmetric roll leaves that model true.
     */
    @Test
    void theRollDoesNotMoveTheAverage() {
        Random r = new Random(3L);
        long total = 0;
        int rolls = 200_000;
        for (int i = 0; i < rolls; i++) {
            total += Damage.outgoing(20, 1f, false, 2f, r);
        }
        assertEquals(20.0, total / (double) rolls, 0.1);
    }

    /** A crit is scaled first and then rolled, so it is always the bigger band. */
    @Test
    void aCritOutrangesAnOrdinaryHit() {
        Random r = new Random(5L);
        int worstCrit = Integer.MAX_VALUE;
        int bestPlain = 0;
        for (int i = 0; i < 4000; i++) {
            worstCrit = Math.min(worstCrit, Damage.outgoing(7, 1f, true, 2f, r));
            bestPlain = Math.max(bestPlain, Damage.outgoing(7, 1f, false, 2f, r));
        }
        assertTrue(worstCrit > bestPlain,
            "the weakest crit (" + worstCrit + ") must beat the strongest normal hit ("
            + bestPlain + "), or the red number is sometimes smaller than the blue one");
    }
}
