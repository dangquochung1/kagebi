package com.kagebi.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.kagebi.combat.Modifiers;
import com.kagebi.data.ContentRegistry;
import com.kagebi.data.def.WeaponDef;
import com.kagebi.run.RunState;

/**
 * The numbers the profile screen prints, against the ones the fight uses.
 *
 * <p>A profile screen that disagrees with the simulation is worse than none,
 * because it is believed. These are the assertions that say it does not.
 */
class StatSheetTest {

    private static final int BASE_HP = 100;

    private static ContentRegistry content() {
        ContentRegistry c = new ContentRegistry();
        c.put(new WeaponDef("katana", "n", "d", "s", 1, 10, 22, 9, 4, 3, 11, 40, 0, null));
        c.put(new WeaponDef("kunai", "n", "d", "s", 1, 6, 140, 5, 5, 2, 13, 30, 3, "kunai"));
        return c;
    }

    private static RunState run(String weapon, String offHand) {
        RunState r = new RunState(1L, "ninja", weapon, BASE_HP);
        r.throwWeaponId = offHand;
        return r;
    }

    @Test
    void anUnmodifiedSheetIsTheBareCharacter() {
        StatSheet s = StatSheet.of(run("katana", null), content(), new Modifiers(), BASE_HP);
        assertEquals(BASE_HP, s.maxHp);
        assertEquals(10, s.damage);
        assertEquals(Player.BASE_CRIT_CHANCE, s.crit, 1e-4);
        assertEquals(Player.BASE_CRIT_MULT, s.critDamage, 1e-4);
        assertEquals(0, s.throwDamage, "an empty off hand has no number");
        assertEquals(0, s.armour);
        assertEquals(1f, s.attackSpeed, 1e-4);
        assertEquals(Player.SPEED, s.moveSpeed, 1e-4);
    }

    @Test
    void everyModifierReachesItsOwnLine() {
        Modifiers m = new Modifiers();
        m.add("max_hp_add", 25f);
        m.add("damage_mult", 1.5f);
        m.add("crit_chance_add", 0.10f);
        m.add("crit_damage_mult", 1.25f);
        m.add("armour_add", 4f);
        m.add("attack_speed_mult", 1.20f);
        m.add("move_speed_mult", 1.10f);
        StatSheet s = StatSheet.of(run("katana", "kunai"), content(), m, BASE_HP);
        assertEquals(125, s.maxHp);
        assertEquals(15, s.damage);
        assertEquals(0.15f, s.crit, 1e-4);
        assertEquals(2.5f, s.critDamage, 1e-4);
        assertEquals(4, s.armour);
        assertEquals(1.20f, s.attackSpeed, 1e-4);
        assertEquals(Player.SPEED * 1.10f, s.moveSpeed, 1e-3);
    }

    /**
     * The off hand has its own multiplier, so a melee perk must not show up on
     * its line. This is the bug the split was made for.
     */
    @Test
    void aMeleePerkDoesNotSharpenTheOffHand() {
        Modifiers m = new Modifiers();
        m.add("melee_damage_mult", 2f);
        StatSheet s = StatSheet.of(run("katana", "kunai"), content(), m, BASE_HP);
        assertEquals(20, s.damage, "the sword feels it");
        assertEquals(6, s.throwDamage, "the kunai does not");
    }

    @Test
    void aThrowMultiplierReachesOnlyTheOffHand() {
        Modifiers m = new Modifiers();
        m.add("throw_damage_mult", 1.5f);
        StatSheet s = StatSheet.of(run("katana", "kunai"), content(), m, BASE_HP);
        assertEquals(10, s.damage);
        assertEquals(9, s.throwDamage);
    }

    /** damage_mult means damage, so it is the one that is in both. */
    @Test
    void thePlainDamageMultiplierIsInBothHands() {
        Modifiers m = new Modifiers();
        m.add("damage_mult", 2f);
        StatSheet s = StatSheet.of(run("katana", "kunai"), content(), m, BASE_HP);
        assertEquals(20, s.damage);
        assertEquals(12, s.throwDamage);
    }

    /**
     * Shown at full health. The low-hp multipliers are something that happens
     * later, not a statistic, and a damage figure that climbed as the player
     * bled would be unreadable.
     */
    @Test
    void theSheetIsReadAtFullHealth() {
        Modifiers m = new Modifiers();
        m.add("damage_mult_low_hp", 3f);
        StatSheet s = StatSheet.of(run("katana", null), content(), m, BASE_HP);
        assertEquals(10, s.damage);
    }

    @Test
    void aMissingRegistryIsNotACrash() {
        StatSheet s = StatSheet.of(run("katana", "kunai"), null, new Modifiers(), BASE_HP);
        assertEquals(0, s.damage);
        assertEquals(0, s.throwDamage);
        assertTrue(s.maxHp > 0);
    }
}
