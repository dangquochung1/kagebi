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
 * press.</b> {@code Win+Shift+S} is how Windows takes a screenshot, and S is
 * walking down - so asking Windows for a screenshot used to walk the ninja
 * downwards first. S cannot simply be moved: it is the W-A-S-D key every
 * player expects. What is wrong is reading a chord aimed at the window manager
 * as if it were aimed at the game, and that is one rule rather than a search
 * for safe keys.
 *
 * <p><b>Shift is not a game key at all.</b> The rule above needs the modifier
 * to arrive first, and Shift is the one key of that chord that is as often
 * pressed first as last. Shift used to roll, and a player who reached for
 * Shift, then Win, then S had rolled before Windows said a word. See
 * {@link #reserved}.
 */
public final class InputService extends InputAdapter {

    /** How many fixed steps a press stays eligible: 6 steps is 100ms at 60Hz. */
    public static final int DEFAULT_BUFFER_STEPS = 6;

    /**
     * Keys that mean "this chord belongs to the desktop, not the game".
     *
     * <p>Every common Windows gesture built from them - Win+Shift+S, Alt+Tab,
     * Alt+F4, Ctrl+anything - would otherwise arrive here as whatever the other
     * keys in the chord happen to be bound to.
     */
    private static final int[] SYSTEM_MODIFIERS = {
        Keys.SYM, Keys.ALT_LEFT, Keys.ALT_RIGHT, Keys.CONTROL_LEFT, Keys.CONTROL_RIGHT,
    };

    /**
     * Keys that may never be bound: the system modifiers, and Shift.
     *
     * <p>Shift is here but not above, and the difference is deliberate. It does
     * not silence the game while held - holding Shift and pressing attack still
     * attacks, and there is no reason it should not - but it cannot mean
     * anything on its own, because it is the key a desktop chord most often
     * starts with. A roll has to begin on the press, and by the time the
     * Windows key arrives to say "that was a screenshot", it already has.
     */
    private static final int[] RESERVED = {
        Keys.SYM, Keys.ALT_LEFT, Keys.ALT_RIGHT, Keys.CONTROL_LEFT, Keys.CONTROL_RIGHT,
        Keys.SHIFT_LEFT, Keys.SHIFT_RIGHT,
    };

    /** Whether a key belongs to the desktop, and so may never be bound to an action. */
    public static boolean reserved(int keycode) {
        for (int key : RESERVED) {
            if (key == keycode) {
                return true;
            }
        }
        return false;
    }

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

    /** Wheel travel since the last step, kept fractional for touchpads. */
    private float pendingScroll;
    /** Whole wheel notches this step, positive towards the player. */
    private int scroll;

    /**
     * Where the mouse is, in window pixels, and whether it was clicked.
     *
     * <p>Kept in window pixels rather than virtual ones because this class has
     * no viewport and must not acquire one: the window may be letterboxed, and
     * only the viewport that drew a screen knows where its black bars are. See
     * {@link com.kagebi.gfx.CameraController#toVirtual}, which every screen
     * already has an instance of.
     *
     * <p>The click is promoted on the fixed step exactly like a key press, for
     * the same reason: a click and release that both land between two steps
     * would otherwise be seen twice or not at all.
     */
    private int pointerX;
    private int pointerY;
    private boolean pointerDown;
    private boolean pendingClick;
    private boolean justClicked;

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
        // Promoted on the step like a press, and for the same reason: a notch
        // turned between two steps would otherwise be read twice or never.
        scroll = (int) pendingScroll;
        pendingScroll -= scroll;
        justClicked = pendingClick;
        pendingClick = false;
    }

    /**
     * Whole mouse-wheel notches turned since the last step: positive when the
     * wheel rolls towards the player, which libGDX reports as scrolling down.
     */
    public int scroll() {
        return scroll;
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
        // Without this, shift held as the player alt-tabs away is shift held
        // for ever, and every skill key reads instead of casting.
        shiftHeld = 0;
        pendingScroll = 0f;
        scroll = 0;
        // The pointer position survives: it is where the mouse is, which is
        // still true across a screen change. Only the click is dropped, so a
        // press that opened a screen cannot also press something on it.
        pointerDown = false;
        pendingClick = false;
        justClicked = false;
    }

    /** Whether a chord aimed at the window manager is in progress. */
    public boolean systemChord() {
        return modifiersHeld > 0;
    }

    /**
     * Whether shift is being held to read rather than to act.
     *
     * <p>Shift is in {@link #RESERVED} and always will be, so it cannot be a
     * {@code GameAction} and cannot be rebound - which is exactly what makes
     * it the right key for this. It is not a control; it is the modifier that
     * turns the controls into their own documentation, the way a
     * League-of-Legends player reads a spell by holding it over the key.
     *
     * <p>Tracked here rather than read off {@code Gdx.input} at the draw site,
     * so the rule that gameplay never names a keycode holds. It is deliberately
     * <em>not</em> a system modifier: those suppress every other key, and this
     * one has to let the skill keys through so it can tell which one is being
     * asked about.
     */
    public boolean infoHeld() {
        return shiftHeld > 0;
    }

    private int shiftHeld;

    private static boolean isShift(int keycode) {
        return keycode == Keys.SHIFT_LEFT || keycode == Keys.SHIFT_RIGHT;
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
        if (isShift(keycode)) {
            shiftHeld++;
            return false;       // never an action; see infoHeld()
        }
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
        if (isShift(keycode)) {
            shiftHeld = Math.max(0, shiftHeld - 1);
            return false;
        }
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

    /** Ctrl+wheel is the desktop's zoom, not the game's, so it is left alone. */
    @Override
    public boolean scrolled(float amountX, float amountY) {
        if (modifiersHeld > 0) {
            return false;
        }
        pendingScroll += amountY;
        return true;
    }

    // ---- mouse ---------------------------------------------------------------

    /** Where the pointer is, in window pixels with y running down. */
    public int pointerX() {
        return pointerX;
    }

    public int pointerY() {
        return pointerY;
    }

    /** Whether the left button is held. */
    public boolean pointerDown() {
        return pointerDown;
    }

    /**
     * Whether the left button went down during this step.
     *
     * <p>Spend it with {@link #consumeClick()} once a widget has acted on it,
     * so one press cannot also press whatever is underneath.
     */
    public boolean justClicked() {
        return justClicked;
    }

    public void consumeClick() {
        justClicked = false;
    }

    /**
     * Only the left button, and only when the desktop is not mid-chord.
     *
     * <p>Right and middle are left unclaimed rather than mapped to something:
     * this game has no use for them, and swallowing them would stop anything
     * else in the window from seeing them.
     */
    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        pointerX = screenX;
        pointerY = screenY;
        if (button != com.badlogic.gdx.Input.Buttons.LEFT || modifiersHeld > 0) {
            return false;
        }
        pointerDown = true;
        pendingClick = true;
        return true;
    }

    @Override
    public boolean touchUp(int screenX, int screenY, int pointer, int button) {
        pointerX = screenX;
        pointerY = screenY;
        if (button != com.badlogic.gdx.Input.Buttons.LEFT) {
            return false;
        }
        pointerDown = false;
        return true;
    }

    /**
     * Tracked but never claimed.
     *
     * <p>Returning false matters: a screen that puts a scene2d {@code Stage}
     * after this service in an {@link com.badlogic.gdx.InputMultiplexer} would
     * otherwise never see the mouse move, and its buttons would never light up
     * on hover.
     */
    @Override
    public boolean mouseMoved(int screenX, int screenY) {
        pointerX = screenX;
        pointerY = screenY;
        return false;
    }

    @Override
    public boolean touchDragged(int screenX, int screenY, int pointer) {
        pointerX = screenX;
        pointerY = screenY;
        return false;
    }
}
