package com.kagebi.combat;

/**
 * A countdown of invulnerable simulation steps.
 *
 * <p>Counted in steps rather than seconds so that a roll dodges exactly the
 * same swing at 60Hz and at 144Hz. A roguelite whose dodge window is measured
 * in wall-clock time is a different game on a different monitor.
 */
public final class IFrames {

    private int remaining;

    /**
     * Extends the window, never shortens it.
     *
     * <p>The max is the whole point. A roll grants a long window; being clipped
     * mid-roll then grants a shorter one, and a naive assignment would cut the
     * dodge off at the moment the player most needs it - which is precisely the
     * frame they will remember.
     */
    public void grant(int steps) {
        if (steps > remaining) {
            remaining = steps;
        }
    }

    public void step() {
        if (remaining > 0) {
            remaining--;
        }
    }

    public boolean invulnerable() {
        return remaining > 0;
    }

    public int remaining() {
        return remaining;
    }

    public void clear() {
        remaining = 0;
    }
}
