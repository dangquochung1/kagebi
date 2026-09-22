package com.kagebi.entity;

import com.kagebi.combat.Modifiers;
import com.kagebi.data.ContentRegistry;
import com.kagebi.data.def.WeaponDef;
import com.kagebi.run.RunState;

/**
 * The eight numbers the profile screen shows, worked out once.
 *
 * <p>Read from the same {@link Modifiers} the simulation reads, so the panel
 * cannot drift from the fight. That is the whole reason this exists rather
 * than the screen doing its own arithmetic: a profile screen that adds up
 * armour differently from {@code Damage.incoming} is worse than no profile
 * screen, because it is believed.
 *
 * <p>Damage is shown at its average roll. {@code Damage.outgoing} spreads every
 * hit by plus or minus twenty per cent and rounds, so no single number is "the"
 * damage; the mean is the one a player can plan with, and it is what changes
 * when they put a stone in.
 *
 * <p>Pure: no Gdx, no textures, no screen. See {@code StatSheetTest}.
 */
public final class StatSheet {

    /** Maximum health, including everything worn. */
    public final int maxHp;
    /** Average main-hand damage per swing, or 0 with no weapon. */
    public final int damage;
    /** Chance to crit, 0 to 1. */
    public final float crit;
    /** What a crit multiplies by. */
    public final float critDamage;
    /** Average off-hand damage per throw, or 0 with an empty off hand. */
    public final int throwDamage;
    /** Flat soak before percentages. */
    public final int armour;
    /** Attack speed as a multiplier of the weapon's own cadence. */
    public final float attackSpeed;
    /** Walking speed in pixels a second. */
    public final float moveSpeed;

    private StatSheet(int maxHp, int damage, float crit, float critDamage, int throwDamage,
                      int armour, float attackSpeed, float moveSpeed) {
        this.maxHp = maxHp;
        this.damage = damage;
        this.crit = crit;
        this.critDamage = critDamage;
        this.throwDamage = throwDamage;
        this.armour = armour;
        this.attackSpeed = attackSpeed;
        this.moveSpeed = moveSpeed;
    }

    /**
     * @param run     the run being played; its weapons are what the damage is of
     * @param content may be null, in which case both damage figures read zero
     * @param mods    everything worn, bought, found and perked, already folded
     * @param baseMaxHp what a run starts with before any of it
     */
    public static StatSheet of(RunState run, ContentRegistry content, Modifiers mods,
                               int baseMaxHp) {
        Modifiers m = mods == null ? new Modifiers() : mods;
        int hp = baseMaxHp + m.maxHpAdd();
        // At full health, which is the only honest place to show it: the low-hp
        // multipliers are a thing that happens later, not a statistic.
        float melee = m.outgoingMult(1f);
        return new StatSheet(
            hp,
            average(weapon(run == null ? null : run.weaponId, content), melee),
            Player.BASE_CRIT_CHANCE + m.critChanceAdd(),
            Player.BASE_CRIT_MULT * m.critDamageMult(),
            average(weapon(run == null ? null : run.throwWeaponId, content), m.throwMult()),
            m.armourAdd(),
            m.attackSpeedMult(),
            Player.SPEED * m.moveSpeedMult());
    }

    private static WeaponDef weapon(String id, ContentRegistry content) {
        if (id == null || content == null) {
            return null;
        }
        // An id with no def is content that failed to load, which the validator
        // would have refused to boot on. Null keeps a test from needing the
        // whole registry to show a profile.
        try {
            return content.weapon(id);
        } catch (IllegalArgumentException missing) {
            return null;
        }
    }

    private static int average(WeaponDef w, float mult) {
        return w == null ? 0 : Math.max(1, Math.round(w.damage * mult));
    }
}
