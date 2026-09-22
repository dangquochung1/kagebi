package com.kagebi.data.def;

import com.kagebi.Cfg;

/**
 * One of the three things the skill keys do.
 *
 * <p>Everything a skill is made of is here rather than in code, because the
 * numbers are the part that gets changed: a cooldown is argued about for weeks
 * and a rebuild for each argument is how balance stops being tuned. What code
 * decides is the <em>shape</em> - a dash, a ring, a transformation - and that
 * is {@link Kind}.
 *
 * <p><b>Seconds in, steps out.</b> The file says "12" and means twelve seconds,
 * which is what a player counting under their breath means too. The simulation
 * counts fixed steps, so the conversion happens once, here, rather than at each
 * of the places that compare a timer.
 */
public final class SkillDef {

    /**
     * What a skill does, which is the one part of it that is code.
     *
     * <p>Three, because three were asked for. A fourth would be a new branch in
     * {@code Player}, and the point of everything else being data is that a
     * fourth <em>variant</em> of these three is not.
     */
    public enum Kind {
        /** A thrust: move along the aim and hurt whatever is crossed. */
        LUNGE,
        /** A ring centred on the caster: hurt and shove everything inside it. */
        NOVA,
        /** A timed transformation: change the numbers, and what dashing does. */
        AVATAR,
    }

    public final String id;
    public final String nameKey;
    public final String descKey;
    /** Region under {@code ui/skill/}; see {@code Assets.Ui.skillIcon}. */
    public final String icon;
    /** 1, 2 or 3 - which key casts it, left to right on the bar. */
    public final int slot;
    public final Kind kind;

    public final int cooldownSteps;
    /** How long an {@link Kind#AVATAR} lasts; zero for the instant ones. */
    public final int durationSteps;

    /** Multiplier on the player's usual hit, or zero for a skill that does none. */
    public final float damageMult;
    /** Virtual pixels: how far a nova reaches, or how far a lunge travels. */
    public final float range;
    /** Virtual pixels a second, away from the caster. */
    public final float knockback;

    /** The strip under {@code fx/skill/} this skill draws. */
    public final String vfx;

    /**
     * What an {@link Kind#AVATAR} adds while it is up, in the same effect names
     * relics and gear use, so it folds into the same {@code Modifiers}.
     */
    public final String[] effects;
    public final float[] magnitudes;

    /**
     * The share of current health an AVATAR costs to cast, and the share below
     * which it is free.
     *
     * <p>A cost that can kill is a cost nobody pays. The floor is what lets the
     * ultimate be the thing a losing fight is turned around with, which is what
     * an ultimate is for - without it the skill is unusable in exactly the
     * situation it exists for.
     */
    public final float hpCost;
    public final float hpFloor;

    public SkillDef(String id, String nameKey, String descKey, String icon, int slot,
                    Kind kind, float cooldownSeconds, float durationSeconds,
                    float damageMult, float range, float knockback, String vfx,
                    String[] effects, float[] magnitudes, float hpCost, float hpFloor) {
        this.id = id;
        this.nameKey = nameKey;
        this.descKey = descKey;
        this.icon = icon;
        this.slot = slot;
        this.kind = kind;
        this.cooldownSteps = steps(cooldownSeconds);
        this.durationSteps = steps(durationSeconds);
        this.damageMult = damageMult;
        this.range = range;
        this.knockback = knockback;
        this.vfx = vfx;
        this.effects = effects;
        this.magnitudes = magnitudes;
        this.hpCost = hpCost;
        this.hpFloor = hpFloor;
    }

    /** Seconds to fixed steps, rounded up so nothing becomes free. */
    public static int steps(float seconds) {
        return Math.max(0, (int) Math.ceil(seconds / Cfg.STEP));
    }

    public float cooldownSeconds() {
        return cooldownSteps * Cfg.STEP;
    }

    @Override
    public String toString() {
        return "SkillDef(" + id + ", " + kind + " slot " + slot + ")";
    }
}
