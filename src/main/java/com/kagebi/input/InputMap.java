package com.kagebi.input;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.utils.IntMap;

/**
 * Which keys mean which action, in both directions.
 *
 * <p>Both directions matter: the game loop needs keycode to action sixty times
 * a second, and the controls screen needs action to keycode so it can draw the
 * right key icon.
 *
 * <p>A {@linkplain InputService#reserved reserved} key is never held here: not
 * by default, not through {@link #bind}, and not out of a saved file that
 * predates the rule. The last one matters most, because the key that became
 * reserved - Shift - was a shipped default, so every bindings file written
 * before then carries it.
 */
public final class InputMap {

    private static final String FILE = "kagebi-bindings";

    private final IntMap<GameAction> keyToAction = new IntMap<>();
    private final int[] primary = new int[GameAction.values().length];
    private final int[] secondary = new int[GameAction.values().length];

    public InputMap() {
        resetToDefaults();
        load();
    }

    private InputMap(boolean readSavedBindings) {
        resetToDefaults();
        if (readSavedBindings) {
            load();
        }
    }

    /**
     * The shipped bindings, without reading the ones the player has saved.
     *
     * <p>For tests. {@link #load} reads a real Preferences file out of the home
     * directory, so a test that says "space is roll" was in fact asserting
     * something about whoever happened to run it - and on this machine that
     * developer had rebound roll to L, so the test failed for a reason that had
     * nothing to do with the code under test.
     */
    public static InputMap defaults() {
        return new InputMap(false);
    }

    public void resetToDefaults() {
        for (GameAction a : GameAction.values()) {
            primary[a.ordinal()] = a.defaultPrimary;
            secondary[a.ordinal()] = a.defaultSecondary;
        }
        rebuildReverse();
    }

    private void rebuildReverse() {
        keyToAction.clear();
        for (GameAction a : GameAction.values()) {
            if (primary[a.ordinal()] >= 0) {
                keyToAction.put(primary[a.ordinal()], a);
            }
            if (secondary[a.ordinal()] >= 0) {
                keyToAction.put(secondary[a.ordinal()], a);
            }
        }
    }

    public GameAction actionFor(int keycode) {
        return keyToAction.get(keycode);
    }

    public int primary(GameAction action) {
        return primary[action.ordinal()];
    }

    public int secondary(GameAction action) {
        return secondary[action.ordinal()];
    }

    /**
     * Binds a key, clearing it from whatever held it before. A reserved key is
     * refused, and nothing changes.
     *
     * @return the action the key was taken from, or null if it was unbound or
     *         the binding was refused.
     */
    public GameAction bind(GameAction action, int keycode, boolean isSecondary) {
        if (InputService.reserved(keycode)) {
            return null;
        }
        GameAction previousOwner = keyToAction.get(keycode);
        if (previousOwner != null && previousOwner != action) {
            if (primary[previousOwner.ordinal()] == keycode) {
                primary[previousOwner.ordinal()] = -1;
            }
            if (secondary[previousOwner.ordinal()] == keycode) {
                secondary[previousOwner.ordinal()] = -1;
            }
        }
        if (isSecondary) {
            secondary[action.ordinal()] = keycode;
        } else {
            primary[action.ordinal()] = keycode;
        }
        rebuildReverse();
        return previousOwner == action ? null : previousOwner;
    }

    /**
     * True when every action still has at least one key. The controls screen
     * refuses to close otherwise: someone will unbind their only way to attack
     * and then wonder why the game is broken.
     */
    public boolean allActionsBound() {
        for (GameAction a : GameAction.values()) {
            if (primary[a.ordinal()] < 0 && secondary[a.ordinal()] < 0) {
                return false;
            }
        }
        return true;
    }

    public void save() {
        Preferences prefs = Gdx.app.getPreferences(FILE);
        for (GameAction a : GameAction.values()) {
            prefs.putInteger("primary." + a.name(), primary[a.ordinal()]);
            prefs.putInteger("secondary." + a.name(), secondary[a.ordinal()]);
        }
        prefs.flush();
    }

    public void load() {
        Preferences prefs = Gdx.app.getPreferences(FILE);
        for (GameAction a : GameAction.values()) {
            primary[a.ordinal()] =
                usable(prefs.getInteger("primary." + a.name(), a.defaultPrimary));
            secondary[a.ordinal()] =
                usable(prefs.getInteger("secondary." + a.name(), a.defaultSecondary));
        }
        // A saved file that leaves something unreachable is worse than no saved
        // file, so fall back rather than boot into an unplayable state.
        if (!allActionsBound()) {
            Gdx.app.error("input", "saved bindings left an action unbound; using defaults");
            resetToDefaults();
        }
        rebuildReverse();
    }

    /**
     * A saved key as this build will honour it: the key itself, or -1 for one
     * that may no longer be bound.
     *
     * <p>Unbound rather than put back to the default, so that whatever else the
     * player chose survives: a file that said roll is L and Shift becomes roll
     * is L. An action left with no key at all is caught by {@link #load}, which
     * falls back to the defaults.
     */
    static int usable(int keycode) {
        return InputService.reserved(keycode) ? -1 : keycode;
    }
}
