package com.kagebi.combat;

import java.util.HashMap;
import java.util.Map;

/**
 * Everything the player's relics, village upgrades and character perk add up to.
 *
 * <p>Effects arrive as a name and a number - {@code damage_mult, 1.10} - and
 * are combined by one rule read off the name: <b>anything ending in
 * {@code _mult} compounds, everything else adds.</b> The same rule is already
 * how {@code ShopCatalog.upgradeValue} combines upgrade levels, so a relic and
 * an upgrade with the same effect name genuinely behave the same way, which is
 * what the content was written expecting.
 *
 * <p>Compounding rather than adding matters more than it looks. Two relics at
 * +10% are 1.21, not 1.20, and a player who stacks five of them gets 1.61
 * rather than 1.50. Adding them instead makes every later copy of a relic
 * weaker than the first, which is the opposite of how a roguelite build is
 * supposed to feel.
 *
 * <p><b>Pure.</b> No libGDX at all, so every number here is checkable in a
 * plain unit test - and a relic that silently does nothing is the single
 * hardest bug in this game to notice while playing.
 */
public final class Modifiers {

    /** Damage is multiplied by this while HP is under the fraction below. */
    public static final float LOW_HP_FRACTION = 0.35f;
    /** How long the timed buffs from items last. One duration, so one thing to learn. */
    public static final int BUFF_STEPS = 600;
    /** Slow and poison windows, from the content's own documentation. */
    public static final int SLOW_STEPS = 90;
    public static final int POISON_STEPS = 300;
    /** Damage-over-time and aura effects tick once a second. */
    public static final int TICK_STEPS = 60;
    /** How far a burn aura reaches, in pixels. */
    public static final float BURN_RANGE = 24f;

    private final Map<String, Float> values = new HashMap<>();
    private int reviveCharges;
    private float reviveFraction;

    /**
     * Multiplicative effects whose names do not end in {@code _mult}.
     *
     * <p>Both were named for what they do rather than for how they combine, and
     * the suffix rule alone gets them wrong in the worst possible direction: an
     * absent multiplicative effect has to default to 1, and defaulting to 0
     * multiplies everything it touches down to nothing. That is exactly what
     * happened - every hit the player took collapsed to the one-point minimum -
     * and it is the reason this list exists rather than the convention being
     * trusted to be complete.
     */
    private static final java.util.Set<String> ALSO_MULTIPLICATIVE =
        new java.util.HashSet<>(java.util.Arrays.asList(
            "glass_cannon", "damage_mult_low_hp"));

    /** True for an effect combined by multiplication rather than addition. */
    public static boolean multiplicative(String effect) {
        return effect != null
            && (effect.endsWith("_mult") || ALSO_MULTIPLICATIVE.contains(effect));
    }

    /**
     * Folds one effect in. Unknown names are accumulated like any other: a name
     * combat has never heard of is caught by {@code ContentValidator} at boot,
     * which is a better place to fail than here, three rooms into a run.
     */
    public void add(String effect, float magnitude) {
        if (effect == null || effect.isEmpty()) {
            return;
        }
        if ("revive_once".equals(effect)) {
            // Deliberately not the general rule. Two sources compounding would
            // give 0.5 x 0.5 = 0.25, so a second phoenix feather would revive
            // the player at a quarter health instead of a half - strictly worse
            // than owning one. Separate charges, each at the best fraction
            // offered, is what the name promises.
            reviveCharges++;
            reviveFraction = Math.max(reviveFraction, magnitude);
            return;
        }
        if (multiplicative(effect)) {
            values.merge(effect, magnitude, (a, b) -> a * b);
        } else {
            values.merge(effect, magnitude, Float::sum);
        }
    }

    /** The combined value, or the identity for its kind if nothing supplied it. */
    public float value(String effect) {
        Float v = values.get(effect);
        if (v != null) {
            return v;
        }
        return multiplicative(effect) ? 1f : 0f;
    }

    public int intValue(String effect) {
        return Math.round(value(effect));
    }

    // ---- damage -------------------------------------------------------------

    /**
     * Everything multiplying a hit the player lands.
     *
     * @param hpFraction current HP over max, for the effects that key off it
     */
    public float outgoingMult(float hpFraction) {
        float mult = value("damage_mult")
            * value("melee_damage_mult")
            * value("glass_cannon");
        if (hpFraction < LOW_HP_FRACTION) {
            mult *= value("damage_mult_low_hp");
        }
        return mult;
    }

    /**
     * Everything multiplying a hit the player takes. Glass cannon appears in
     * both directions on purpose: it is one number that cuts both ways, which
     * is the whole of its design.
     */
    public float incomingMult() {
        return value("damage_taken_mult") * value("glass_cannon");
    }

    /**
     * Flat damage soaked before percentages, from armour worn and stones set.
     *
     * <p>{@code Player.armour} and {@code Combatant.armour()} have existed
     * since combat was written and nothing ever wrote to them, so armour was
     * permanently zero and {@code Damage.incoming} subtracted nothing. This is
     * the value that finally fills it; the plumbing on the other side needed no
     * change at all.
     */
    public int armourAdd() {
        return Math.round(intValue("armour_add") * value("armour_mult"));
    }

    /**
     * The share of armour kept, for something that trades defence for offence.
     *
     * <p>Folded into {@link #armourAdd} rather than read separately, so no
     * caller has to remember that armour has two halves. Multiplicative by its
     * name, so two things halving it leave a quarter rather than nothing -
     * which is the rule everything ending in {@code _mult} already follows.
     */
    public float armourMult() {
        return value("armour_mult");
    }

    /**
     * Everything multiplying a thrown hit: the off hand's own damage number.
     *
     * <p>Separate from {@link #outgoingMult} because the two hands are separate
     * statistics on the profile screen and a stone that sharpens a sword should
     * not also sharpen a fireball. {@code damage_mult} is in both because it is
     * the one that means "damage", full stop.
     */
    public float throwMult() {
        return value("damage_mult") * value("throw_damage_mult");
    }

    public float critChanceAdd() {
        return value("crit_chance_add");
    }

    public float critDamageMult() {
        return value("crit_damage_mult");
    }

    public float lifesteal() {
        return value("lifesteal");
    }

    public float chainLightning() {
        return value("chain_lightning");
    }

    public float slowOnHit() {
        return value("slow_on_hit");
    }

    public int poisonOnHit() {
        return intValue("poison_on_hit");
    }

    public int burnAura() {
        return intValue("burn_aura");
    }

    // ---- the player ---------------------------------------------------------

    public float moveSpeedMult() {
        return value("move_speed_mult");
    }

    public float attackSpeedMult() {
        return value("attack_speed_mult");
    }

    public float reachAdd() {
        return value("reach_add");
    }

    public int maxHpAdd() {
        return intValue("max_hp_add");
    }

    public int invulnStepsAdd() {
        return intValue("invuln_steps_add");
    }

    public int rollInvulnAdd() {
        return intValue("roll_invuln_add");
    }

    public int healOnKill() {
        return intValue("heal_on_kill");
    }

    public int roomClearHeal() {
        return intValue("room_clear_heal");
    }

    public int throwExtra() {
        return intValue("throw_extra");
    }

    public int startKeysAdd() {
        return intValue("start_keys_add");
    }

    public int potionCapacityAdd() {
        return intValue("potion_capacity_add");
    }

    // ---- outside combat -----------------------------------------------------

    public float goldMult() {
        return value("gold_mult");
    }

    public float luckAdd() {
        return value("luck_add");
    }

    public int reviveCharges() {
        return reviveCharges;
    }

    public float reviveFraction() {
        return reviveFraction;
    }

    /** Spends one revive; false when there is none left. */
    public boolean spendRevive() {
        if (reviveCharges <= 0) {
            return false;
        }
        reviveCharges--;
        return true;
    }
}
