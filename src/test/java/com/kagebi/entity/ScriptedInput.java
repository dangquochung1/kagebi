package com.kagebi.entity;

import java.util.Arrays;

import com.kagebi.input.GameAction;
import com.kagebi.input.InputService;

/**
 * An {@link ActionSource} a test can press buttons on.
 *
 * <p>It reproduces {@link InputService}'s buffer rules exactly - a press lands
 * as pending, is promoted on the next {@link #beginStep}, stays buffered for
 * {@code steps} after that, and is spent by {@link #consume} - so a test
 * written against this is a test of the player against the real rules.
 * {@code RealInputBufferTest} checks the two agree.
 *
 * <p>It also counts consumes, which is how "consumed exactly once" is observed.
 */
public final class ScriptedInput implements ActionSource {

    private static final int COUNT = GameAction.values().length;

    private final boolean[] down = new boolean[COUNT];
    private final boolean[] pending = new boolean[COUNT];
    private final int[] lastPress = new int[COUNT];
    private final int[] consumes = new int[COUNT];
    private int step;

    public ScriptedInput() {
        Arrays.fill(lastPress, Integer.MIN_VALUE / 2);
    }

    /** A key going down: pending until the next step, like a real event. */
    public void press(GameAction a) {
        if (!down[a.ordinal()]) {
            pending[a.ordinal()] = true;
        }
        down[a.ordinal()] = true;
    }

    public void release(GameAction a) {
        down[a.ordinal()] = false;
    }

    /** Press and immediately release, as a tap between two steps. */
    public void tap(GameAction a) {
        press(a);
        release(a);
    }

    public void beginStep() {
        step++;
        for (int i = 0; i < COUNT; i++) {
            if (pending[i]) {
                lastPress[i] = step;
                pending[i] = false;
            }
        }
    }

    public int consumes(GameAction a) {
        return consumes[a.ordinal()];
    }

    /** Whether shift is held: the key that reads a control instead of using it. */
    private boolean info;

    public void holdInfo(boolean held) {
        info = held;
    }

    @Override
    public boolean infoHeld() {
        return info;
    }

    @Override
    public boolean isDown(GameAction a) {
        return down[a.ordinal()];
    }

    @Override
    public boolean buffered(GameAction a, int steps) {
        return step - lastPress[a.ordinal()] <= steps;
    }

    @Override
    public void consume(GameAction a) {
        consumes[a.ordinal()]++;
        lastPress[a.ordinal()] = Integer.MIN_VALUE / 2;
    }

    /** One fixed step of the world, in the order the screen's loop runs it. */
    public void tick(EntityWorld world) {
        beginStep();
        world.stepWith(this);
    }

    public void ticks(EntityWorld world, int n) {
        for (int i = 0; i < n; i++) {
            tick(world);
        }
    }
}
