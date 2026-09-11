package com.kagebi.input;

import com.badlogic.gdx.Input.Keys;

/**
 * Everything the player can do, named by intent rather than by key.
 *
 * <p>Gameplay code asks whether {@code ATTACK} is down, never whether
 * {@code Keys.J} is. Without that separation, remappable controls stop being a
 * settings screen and become a refactor through every file that reads input.
 */
public enum GameAction {

    MOVE_UP("action.move_up", Keys.W, Keys.UP),
    MOVE_DOWN("action.move_down", Keys.S, Keys.DOWN),
    MOVE_LEFT("action.move_left", Keys.A, Keys.LEFT),
    MOVE_RIGHT("action.move_right", Keys.D, Keys.RIGHT),

    ATTACK("action.attack", Keys.J, Keys.Z),
    THROW("action.throw", Keys.K, Keys.X),
    ROLL("action.roll", Keys.SPACE, Keys.SHIFT_LEFT),
    INTERACT("action.interact", Keys.E, Keys.ENTER),
    USE_ITEM("action.use_item", Keys.Q, Keys.C),

    INVENTORY("action.inventory", Keys.TAB, Keys.I),
    MAP("action.map", Keys.M, -1),
    PAUSE("action.pause", Keys.ESCAPE, -1);

    /** Lookup key for the label shown in the controls screen. */
    public final String i18nKey;
    public final int defaultPrimary;
    /** Secondary default, or -1 for none. */
    public final int defaultSecondary;

    GameAction(String i18nKey, int defaultPrimary, int defaultSecondary) {
        this.i18nKey = i18nKey;
        this.defaultPrimary = defaultPrimary;
        this.defaultSecondary = defaultSecondary;
    }

    /** Actions the player may rebind. PAUSE is excluded on purpose: see InputMap. */
    public boolean rebindable() {
        return this != PAUSE;
    }
}
