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
     * Binds a key, clearing it from whatever held it before.
     *
     * @return the action the key was taken from, or null if it was unbound.
     */
    public GameAction bind(GameAction action, int keycode, boolean isSecondary) {
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
            primary[a.ordinal()] = prefs.getInteger("primary." + a.name(), a.defaultPrimary);
            secondary[a.ordinal()] = prefs.getInteger("secondary." + a.name(), a.defaultSecondary);
        }
        // A saved file that leaves something unreachable is worse than no saved
        // file, so fall back rather than boot into an unplayable state.
        if (!allActionsBound()) {
            Gdx.app.error("input", "saved bindings left an action unbound; using defaults");
            resetToDefaults();
        }
        rebuildReverse();
    }
}
