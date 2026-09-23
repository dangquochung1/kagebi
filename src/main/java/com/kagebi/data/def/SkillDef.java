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
     * <p>The fourth was worth a branch. A {@link #LUNGE} is twelve steps along
     * one heading, fixed at the moment it starts; a {@link #CHARGE} runs for
     * three seconds, is steered the whole way, and takes the hands off the
     * weapons while it does. That is a different shape, not a lunge with
     * different numbers - which is the test this enum exists to apply.
     *
     * <p>{@link #PASSIVE} passes it differently: it is not a shape at all, it
     * is the absence of one. A character whose whole element is "everything I
     * do poisons" has something that is never cast, never on cooldown and
     * never on a key, and the only alternatives were a second perk field on
     * the shop row - which would have cost that character the perk they paid
     * for - or a branch per character, which is what notes/d.md section 3
     * records going wrong the last time.
     */
    public enum Kind {
        /** A thrust: move along the aim and hurt whatever is crossed. */
        LUNGE,
        /** A ring centred on the caster: hurt and shove everything inside it. */
        NOVA,
        /** A timed transformation: change the numbers, and what dashing does. */
        AVATAR,
        /** A run: fast and steered for a while, and it burns what it touches. */
        CHARGE,
        /** No key and no cast: effects a character simply has, for the whole run. */
        PASSIVE,
    }

    /**
     * The strips a skill draws besides its own {@link #vfx}, each null unless
     * the skill has one.
     *
     * <p>Together rather than as six fields on the skill, because they are one
     * idea: what this element looks like when it is doing something the game
     * already does. An ultimate that changes how a punch lands has to change
     * what the punch looks like, or the player is told about it in numbers
     * only - and an ultimate that changes none of it carries {@link #NONE} and
     * is unaffected by every line of this.
     *
     * <p>Six is as many as this shape should hold. A seventh should turn these
     * into a map keyed by role name with a vocabulary in
     * {@code ContentValidator}, the way effect names already work: six
     * nullable fields each read in one place is legible, and a dozen is a
     * lookup table someone has written out by hand.
     */
    public static final class Fx {

        /** Where the transformation begins, and what it shoves. */
        public final String cast;
        /** What the weapon leaves behind on a swing. */
        public final String melee;
        /** What appears on whatever the player's damage lands on. */
        public final String hit;
        /** What the off hand throws instead of its own art. */
        public final String thrown;
        /** What a burning enemy carries while it burns. */
        public final String burn;
        /**
         * The trail a dash leaves - and whether a dash is a weapon at all.
         *
         * <p>The one name here that gates a mechanic rather than replacing a
         * picture, and it does because the mechanic and the picture were the
         * same decision: while an ultimate is up, a dash used to leave a
         * lightning streak and arc to three enemies, for every ultimate, from
         * a hard-coded strip. The fire ultimate is meant to be a little speed
         * and nothing else, so the question "what does a dash look like now"
         * and the question "is a dash a weapon now" have one answer.
         */
        public final String dash;

        public static final Fx NONE = new Fx(null, null, null, null, null, null);

        public Fx(String cast, String melee, String hit,
                  String thrown, String burn, String dash) {
            this.cast = cast;
            this.melee = melee;
            this.hit = hit;
            this.thrown = thrown;
            this.burn = burn;
            this.dash = dash;
        }

        /** Every name set, for the check that they are all strips we ship. */
        public String[] named() {
            java.util.List<String> out = new java.util.ArrayList<>();
            for (String s : new String[] {cast, melee, hit, thrown, burn, dash}) {
                if (s != null) {
                    out.add(s);
                }
            }
            return out.toArray(new String[0]);
        }

        public boolean any() {
            return named().length > 0;
        }
    }

    public final String id;

    /**
     * The character this belongs to, or null for the set everyone else gets.
     *
     * <p>One field, read in one place - {@code ContentRegistry.skillsFor}.
     * The last rule in this game that gave one character something of their
     * own was enforced in four places and correct in two, and it went when she
     * did; see notes/d.md section 3. A slot is still a slot, so two skills may
     * share one as long as no character can reach both.
     */
    public final String character;

    public final String nameKey;
    public final String descKey;
    /** Region under {@code ui/skill/}; see {@code Assets.Ui.skillIcon}. */
    public final String icon;
    /**
     * 1, 2 or 3 - which key casts it, left to right on the bar. 0 for a
     * {@link Kind#PASSIVE}, which no key reaches.
     *
     * <p>{@code Player.setSkills} indexes {@code slot - 1} and ignores
     * anything outside the array, so a passive cannot reach the bar and cannot
     * be cast without a line of code being written to let it.
     */
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

    /**
     * The share of a blow's damage the transformation's own strike adds.
     *
     * <p>Zero for everything that is not an {@link Kind#AVATAR}, and zero for
     * an avatar that does not strike. It is a share rather than a number
     * because the strike is a follow-up: it has to stay the smaller half of
     * the exchange whatever weapon is swinging, and a flat number would be
     * most of a katana's hit and none of a hammer's.
     */
    public final float strikeMult;

    /** The strip under {@code fx/skill/} this skill draws. */
    public final String vfx;

    /** The strips it draws for the other things it changes; never null. */
    public final Fx fx;

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

    public SkillDef(String id, String character, String nameKey, String descKey,
                    String icon, int slot,
                    Kind kind, float cooldownSeconds, float durationSeconds,
                    float damageMult, float range, float knockback, float strikeMult,
                    String vfx, Fx fx,
                    String[] effects, float[] magnitudes, float hpCost, float hpFloor) {
        this.id = id;
        this.character = character;
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
        this.strikeMult = strikeMult;
        this.vfx = vfx;
        this.fx = fx == null ? Fx.NONE : fx;
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
        return "SkillDef(" + id + ", " + kind + " slot " + slot
            + (character == null ? "" : ", " + character) + ")";
    }
}
