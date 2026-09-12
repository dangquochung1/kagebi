package com.kagebi.combat;

import com.kagebi.Dir;

/**
 * The shove a hit puts on whatever it landed on.
 *
 * <p>Velocity in virtual pixels per second, decaying linearly to zero over a
 * fixed number of steps. Linear rather than exponential on purpose: an
 * exponential tail never quite reaches zero, so an enemy nudged once keeps
 * creeping for a second afterwards and its AI spends that whole time fighting a
 * drift it did not ask for.
 *
 * <p>Deterministic - same inputs, same sequence of velocities - which is what
 * makes it testable at all.
 */
public final class Knockback {

    /** 0.2s. Long enough to read as a shove, short enough not to eat the turn. */
    public static final int DEFAULT_STEPS = 12;

    private float vx0;
    private float vy0;
    private int total;
    private int remaining;

    /**
     * Shoves the target directly away from where the hit came from.
     *
     * @param fallback the direction to use when the two points coincide. An
     *                 enemy standing exactly on the player is not a corner case
     *                 here, it is the normal state of a melee fight, and
     *                 normalising a zero vector gives NaN - which then poisons
     *                 the position and the entity disappears off the map. This
     *                 argument exists because that bug is easy to write and
     *                 nearly impossible to read back out of a stack trace.
     */
    public void apply(float fromX, float fromY, float toX, float toY,
                      float strength, float resist, Dir fallback, int steps) {
        float effective = strength * (1f - clamp01(resist));
        if (effective <= 0f || steps <= 0) {
            return;
        }
        float dx = toX - fromX;
        float dy = toY - fromY;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 0.0001f) {
            Dir d = fallback == null ? Dir.DOWN : fallback;
            dx = d.dx;
            dy = d.dy;
            len = 1f;
        }
        vx0 = dx / len * effective;
        vy0 = dy / len * effective;
        total = steps;
        remaining = steps;
    }

    public void apply(float fromX, float fromY, float toX, float toY,
                      float strength, float resist, Dir fallback) {
        apply(fromX, fromY, toX, toY, strength, resist, fallback, DEFAULT_STEPS);
    }

    /** Current velocity on x, in virtual pixels per second. */
    public float vx() {
        return remaining <= 0 ? 0f : vx0 * remaining / total;
    }

    public float vy() {
        return remaining <= 0 ? 0f : vy0 * remaining / total;
    }

    public void step() {
        if (remaining > 0) {
            remaining--;
        }
    }

    public boolean active() {
        return remaining > 0;
    }

    public int remaining() {
        return remaining;
    }

    /** Cancels the shove outright, for a phase change or a teleport. */
    public void clear() {
        remaining = 0;
        vx0 = 0f;
        vy0 = 0f;
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }
}
