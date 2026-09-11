package com.kagebi.combat;

import java.util.Random;

/**
 * The damage formula, split at the point where it changes hands.
 *
 * <p>{@link #outgoing} is everything the attacker controls - the weapon's
 * number, relic multipliers, the critical roll. {@link #incoming} is everything
 * the defender controls - flat armour and percentage resistance. Splitting them
 * is not tidiness: the crit has to be rolled once per swing rather than once
 * per target, or a wide hammer that crits on the left enemy and not the right
 * one reads as a bug, and that is only expressible if the two halves are
 * separate calls made at different moments.
 *
 * <p>Pure integer arithmetic with an injected {@link Random}, so every number
 * below is checkable in a unit test.
 */
public final class Damage {

    /**
     * A hit that connects always costs at least this much.
     *
     * <p>Armour high enough to zero a hit is the worst state a fight can be in:
     * the animation plays, the enemy flashes, and nothing happens. Players
     * conclude the hitbox is broken. One point is not a balance lever, it is a
     * promise that the feedback is telling the truth.
     */
    public static final int MIN = 1;

    /**
     * No defence may exceed this. Full immunity belongs to i-frames, which are
     * visible and temporary; an enemy that is permanently unhittable because a
     * designer typed 1.0 is indistinguishable from a bug.
     */
    public static final float MAX_RESIST = 0.9f;

    /** Weapon number scaled by the attacker's multipliers and its crit roll. */
    public static int outgoing(int base, float damageMult, boolean crit, float critMult) {
        float raw = base * damageMult;
        if (crit) {
            raw *= critMult;
        }
        return Math.max(MIN, Math.round(raw));
    }

    /**
     * Armour first, then resistance.
     *
     * <p>That order makes armour worth most against a flurry of small hits and
     * resistance worth most against one big one, which is a real choice. The
     * reverse order collapses the two stats into the same thing.
     */
    public static int incoming(int amount, int armour, float resist) {
        float after = amount - armour;
        after *= 1f - clampResist(resist);
        return Math.max(MIN, Math.round(after));
    }

    /** The whole formula end to end, for tests and for anything with both halves. */
    public static int compute(int base, float damageMult, boolean crit, float critMult,
                              int armour, float resist) {
        return incoming(outgoing(base, damageMult, crit, critMult), armour, resist);
    }

    /** One roll per swing. See the class comment for why not one per target. */
    public static boolean rollCrit(Random rng, float chance) {
        return chance > 0f && rng.nextFloat() < chance;
    }

    private static float clampResist(float resist) {
        if (resist <= 0f) {
            return 0f;
        }
        return Math.min(resist, MAX_RESIST);
    }

    private Damage() {}
}
