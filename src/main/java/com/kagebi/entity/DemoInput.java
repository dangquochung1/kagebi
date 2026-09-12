package com.kagebi.entity;

import com.kagebi.input.GameAction;

/**
 * An {@link ActionSource} that swings on a timer, for screenshots.
 *
 * <p>Art direction is the one thing no test can check, and a swing lasts
 * eighteen steps out of every run - so the held weapon, the slash arc and the
 * hit flash were all unreviewable. A screenshot of an idle player says nothing
 * about them, and the only alternative was to press the key by hand while the
 * capture ran, which is both unrepeatable and the sort of thing that leaks into
 * whatever window has focus.
 *
 * <p>Deliberately not a bot. It presses one button on a fixed cadence and
 * cannot move, so it proves nothing about play and is useless as a test. Its
 * whole job is to make one frame of the game reachable by a camera.
 */
public final class DemoInput implements ActionSource {

    /** Steps between swings. Longer than any weapon's recovery, so none is cut. */
    public static final int PERIOD = 48;

    private final GameAction action;
    private int steps;

    public DemoInput(GameAction action) {
        this.action = action;
    }

    /** Advances the clock. Call once per fixed step, before stepping the world. */
    public void tick() {
        steps++;
    }

    /** Steps since the last press began, which is where in the swing we are. */
    public int sincePress() {
        return steps % PERIOD;
    }

    @Override
    public boolean isDown(GameAction a) {
        return false;
    }

    @Override
    public boolean buffered(GameAction a, int window) {
        return a == action && sincePress() < window;
    }

    @Override
    public void consume(GameAction a) {
    }
}
