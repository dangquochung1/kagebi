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
}
