package com.kagebi.screen;

import com.kagebi.Cfg;
import com.kagebi.input.InputService;

/**
 * A screen that runs a fixed-timestep simulation under a free-running renderer.
 *
 * <p>Both playable screens need the same loop, and it is short enough that
 * copying it into each looks harmless. It is not: the two copies drift, and the
 * symptom - one screen feeling heavier than the other on a 144Hz display - is
 * never traced back to the loop.
 *
 * <p>Three details earn their place.
 *
 * <p><b>{@link InputService#beginStep()} is called inside the loop</b>, not in
 * render. Presses are promoted to just-pressed on the simulation step, so a
 * render frame that runs no step must not clear them; clearing per frame loses
 * every press made inside the gaps, which players report as laggy controls.
 *
 * <p><b>The backlog is dropped, not carried, when the cap is hit.</b> Keeping it
 * means a frame that took 200ms is followed by frames trying to catch up, each
 * of which takes longer than a frame, which is the spiral {@link
 * Cfg#MAX_STEPS_PER_FRAME} exists to stop. A dropped backlog is a moment of slow
 * motion; a carried one is a hang.
 *
 * <p><b>{@link #alpha()} is exposed but nothing has to use it.</b> Actors are
 * drawn on whole pixels, so interpolating a 60Hz position for a 144Hz frame
 * usually rounds back to where it started; the room slide is the one place the
 * sub-step remainder is worth spending.
 */
public abstract class SimScreen extends GameScreen {

    private final InputService input;

    /** Real seconds not yet turned into steps. Always less than one step. */
    private float accumulator;
    private int steps;

    protected SimScreen(InputService input) {
        this.input = input;
    }

    protected InputService input() {
        return input;
    }

    /** Steps run since this screen was created. Animation clocks read it. */
    protected int steps() {
        return steps;
    }

    /** How far into the next step the renderer is, 0 to 1. */
    protected float alpha() {
        return accumulator / Cfg.STEP;
    }

    @Override
    public final void update(float delta) {
        accumulator += delta;
        int ran = 0;
        while (accumulator >= Cfg.STEP) {
            if (ran == Cfg.MAX_STEPS_PER_FRAME) {
                accumulator = 0f;
                break;
            }
            accumulator -= Cfg.STEP;
            steps++;
            ran++;
            input.beginStep();
            step();
        }
    }

    /** One fixed step. Poll {@link #input()} here and nowhere else. */
    protected abstract void step();
}
