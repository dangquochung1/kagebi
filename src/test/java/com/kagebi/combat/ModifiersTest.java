package com.kagebi.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The arithmetic behind every relic, checked against what the descriptions
 * promise rather than against what the code happens to do.
 */
class ModifiersTest {

    private static final float EPS = 1e-5f;

    @Test
    void anAbsentEffectIsTheIdentityForItsKind() {
        Modifiers m = new Modifiers();
        assertEquals(1f, m.value("damage_mult"), EPS, "a multiplier defaults to 1");
        assertEquals(0f, m.value("reach_add"), EPS, "an addend defaults to 0");
    }

    /**
     * The two effects whose names do not say how they combine. Getting this
     * wrong is not a small error: an absent multiplicative effect defaulting to
     * zero multiplies whatever it touches down to nothing, which is precisely
     * what happened - every hit the player took became one point of damage.
     */
    @Test
    void theMultipliersNotNamedMultAreStillMultipliers() {
        assertTrue(Modifiers.multiplicative("glass_cannon"));
        assertTrue(Modifiers.multiplicative("damage_mult_low_hp"));
        Modifiers m = new Modifiers();
        assertEquals(1f, m.incomingMult(), EPS,
            "a player with no relics must take exactly the damage dealt");
        assertEquals(1f, m.outgoingMult(1f), EPS);
    }

    /** Two copies of a relic compound. Adding them would make the second weaker. */
    @Test
    void twoCopiesOfAMultiplierCompound() {
        Modifiers m = new Modifiers();
        m.add("damage_mult", 1.10f);
        m.add("damage_mult", 1.10f);
        assertEquals(1.21f, m.outgoingMult(1f), EPS);
    }

    @Test
    void addendsSum() {
        Modifiers m = new Modifiers();
        m.add("reach_add", 4f);
        m.add("reach_add", 4f);
        assertEquals(8f, m.reachAdd(), EPS);
    }

    /** One number, both directions. That is the whole design of the relic. */
    @Test
    void glassCannonCutsBothWays() {
        Modifiers m = new Modifiers();
        m.add("glass_cannon", 1.75f);
        assertEquals(1.75f, m.outgoingMult(1f), EPS, "deals more");
        assertEquals(1.75f, m.incomingMult(), EPS, "takes more");
    }

    @Test
    void theLowHealthRelicOnlyAppliesBelowTheThreshold() {
        Modifiers m = new Modifiers();
        m.add("damage_mult_low_hp", 1.40f);
        assertEquals(1f, m.outgoingMult(0.9f), EPS, "not at full health");
        assertEquals(1f, m.outgoingMult(Modifiers.LOW_HP_FRACTION), EPS,
            "not exactly at the threshold");
        assertEquals(1.40f, m.outgoingMult(Modifiers.LOW_HP_FRACTION - 0.01f), EPS);
    }

    /**
     * The one effect that must not follow the general rule. Compounding two
     * would give 0.5 x 0.5 = 0.25, so a second phoenix feather would leave the
     * player worse off than one - the opposite of picking a relic up.
     */
    @Test
    void twoRevivesAreTwoChargesRatherThanAWorseOne() {
        Modifiers m = new Modifiers();
        m.add("revive_once", 0.50f);
        m.add("revive_once", 0.30f);

        assertEquals(2, m.reviveCharges());
        assertEquals(0.50f, m.reviveFraction(), EPS, "the better of the two, not the product");
        assertTrue(m.spendRevive());
        assertTrue(m.spendRevive());
        assertFalse(m.spendRevive(), "and only twice");
    }

    @Test
    void meleeUpgradesMultiplyWithRelicDamage() {
        Modifiers m = new Modifiers();
        m.add("damage_mult", 1.10f);          // a relic
        m.add("melee_damage_mult", 1.20f);    // a village upgrade
        assertEquals(1.32f, m.outgoingMult(1f), EPS);
    }

    /** Rounded to whole points, because hit points are whole numbers. */
    @Test
    void wholeNumberEffectsRound() {
        Modifiers m = new Modifiers();
        m.add("max_hp_add", 15f);
        m.add("max_hp_add", 10f);
        assertEquals(25, m.maxHpAdd());
    }

    @Test
    void anUnknownEffectIsCarriedRatherThanCrashing() {
        Modifiers m = new Modifiers();
        m.add("not_a_real_effect", 3f);
        assertEquals(3f, m.value("not_a_real_effect"), EPS);
    }
}
