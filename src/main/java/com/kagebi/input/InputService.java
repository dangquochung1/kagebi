package com.kagebi.input;

import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.InputAdapter;

/**
 * Turns key events into per-action state that gameplay can poll.
 *
 * <p>Two details here matter more than they look.
 *
 * <p><b>Presses are promoted on the fixed step, not on render.</b> The event
 * callback only records that a key went down; {@link #beginStep()} turns that
 * into a just-pressed flag. Clearing flags in render instead means that at
 * 144Hz there are render frames with no simulation step, and presses inside
 * them vanish. Players describe that as "the controls feel laggy" and it is
 * close to impossible to find by reading code.
 *
 * <p><b>Presses are buffered.</b> {@link #buffered} reports a press made within
 * the last few steps, so hitting attack during the recovery frames of the
 * previous swing queues the next one instead of being swallowed. Without it the
 * game "eats inputs", and the player is right.
 *
 * <p><b>A press made while an operating-system modifier is held is not a
 * press.</b> {@code Win+Shift+S} is how Windows takes a screenshot, and this
 * game binds Shift to roll and S to walking down - so asking Windows for a
 * screenshot made the ninja roll downwards first. Neither binding can simply be
 * moved: S is the W-A-S-D key every player expects. What is actually wrong is
 * reading a chord aimed at the window manager as if it were aimed at the game,
 * and that is one rule rather than a search for safe keys.
 */
public final class InputService extends InputAdapter {

    /** How many fixed steps a press stays eligible: 6 steps is 100ms at 60Hz. */
    public static final int DEFAULT_BUFFER_STEPS = 6;

    /**
     * Keys that mean "this chord belongs to the desktop, not the game".
     *
     * <p>None of them is bindable, and every common Windows gesture built from
     * them - Win+Shift+S, Alt+Tab, Alt+F4, Ctrl+anything - would otherwise
     * arrive here as whatever the other keys in the chord happen to be bound to.
     */
    private static final int[] SYSTEM_MODIFIERS = {
        Keys.SYM, Keys.ALT_LEFT, Keys.ALT_RIGHT, Keys.CONTROL_LEFT, Keys.CONTROL_RIGHT,
    };

    private final InputMap map;
    private final int count = GameAction.values().length;

    private final boolean[] down = new boolean[count];
    private final boolean[] pendingPress = new boolean[count];
    private final boolean[] justPressed = new boolean[count];
    private final int[] lastPressStep = new int[count];
    private final int[] stepsHeld = new int[count];

    private int step;
    /** How many system modifiers are held. Zero means the game may listen. */
    private int modifiersHeld;

    public InputService(InputMap map) {
        this.map = map;
        java.util.Arrays.fill(lastPressStep, Integer.MIN_VALUE / 2);
    }

    public InputMap map() {
        return map;
    }

    /** Call once at the top of each fixed simulation step. */
    public void beginStep() {
        step++;
        for (int i = 0; i < count; i++) {
            justPressed[i] = pendingPress[i];
            if (pendingPress[i]) {
                lastPressStep[i] = step;
            }
            pendingPress[i] = false;
            stepsHeld[i] = down[i] ? stepsHeld[i] + 1 : 0;
        }
    }

    public boolean isDown(GameAction a) {
        return down[a.ordinal()];
    }

    public boolean justPressed(GameAction a) {
        return justPressed[a.ordinal()];
    }

    public int stepsHeld(GameAction a) {
        return stepsHeld[a.ordinal()];
    }

    /** True if the action was pressed within the last {@code steps} steps. */
    public boolean buffered(GameAction a, int steps) {
        return step - lastPressStep[a.ordinal()] <= steps;
    }

    public boolean buffered(GameAction a) {
        return buffered(a, DEFAULT_BUFFER_STEPS);
    }

    /** Spends a buffered press so it cannot trigger a second time. */
    public void consume(GameAction a) {
        lastPressStep[a.ordinal()] = Integer.MIN_VALUE / 2;
    }

    /** Movement on the x axis, -1, 0 or 1. */
    public int axisX() {
        return (isDown(GameAction.MOVE_RIGHT) ? 1 : 0) - (isDown(GameAction.MOVE_LEFT) ? 1 : 0);
    }

    public int axisY() {
        return (isDown(GameAction.MOVE_UP) ? 1 : 0) - (isDown(GameAction.MOVE_DOWN) ? 1 : 0);
    }

    /**
     * Drops all held state, for when a screen takes or loses focus.
     *
     * <p>The window losing focus is the important one and it is not optional:
     * the desktop stops delivering key events the moment focus moves, so a key
     * held as the player alt-tabs away is never reported released and the ninja
     * walks into a wall until they come back. It is also what stops a modifier
     * released outside the window from silencing the game for good.
     */
    public void clear() {
        java.util.Arrays.fill(down, false);
        java.util.Arrays.fill(pendingPress, false);
        java.util.Arrays.fill(justPressed, false);
        java.util.Arrays.fill(stepsHeld, 0);
        modifiersHeld = 0;
    }

    /** Whether a chord aimed at the window manager is in progress. */
    public boolean systemChord() {
        return modifiersHeld > 0;
    }

    private static boolean isSystemModifier(int keycode) {
        for (int key : SYSTEM_MODIFIERS) {
            if (key == keycode) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean keyDown(int keycode) {
        if (isSystemModifier(keycode)) {
            modifiersHeld++;
            return false;       // not ours; let anything else have it
        }
        GameAction a = map.actionFor(keycode);
        if (a == null || modifiersHeld > 0) {
            return false;
        }
        if (!down[a.ordinal()]) {
            pendingPress[a.ordinal()] = true;
        }
        down[a.ordinal()] = true;
        return true;
    }

    @Override
    public boolean keyUp(int keycode) {
        if (isSystemModifier(keycode)) {
            modifiersHeld = Math.max(0, modifiersHeld - 1);
            return false;
        }
        GameAction a = map.actionFor(keycode);
        if (a == null) {
            return false;
        }
        // Released even if the press was suppressed. A key that went down
        // during a chord and up after it must not be left stuck down.
        down[a.ordinal()] = false;
        return true;
    }
}
