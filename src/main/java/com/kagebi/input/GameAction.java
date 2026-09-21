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
    // Not Shift, though Shift is where many games put a dodge: see
    // InputService.reserved. L sits beside attack and throw.
    ROLL("action.roll", Keys.SPACE, Keys.L),
    INTERACT("action.interact", Keys.E, Keys.ENTER),
    USE_ITEM("action.use_item", Keys.Q, Keys.C),

    INVENTORY("action.inventory", Keys.TAB, Keys.I),
    /**
     * The kit badge in the village, which used to be reachable only by mouse.
     *
     * <p>Its own action rather than a spare gameplay key. The village runs a
     * real EntityWorld - the player walks, swings and drinks there like
     * anywhere else - so Q, K and J are all already spoken for, and binding
     * one of them to a menu would mean the menu opening when someone meant to
     * use a potion. F is free, and being an action means it appears in the
     * controls screen and can be moved like everything else.
     */
    LOADOUT("action.loadout", Keys.F, Keys.B),
    MAP("action.map", Keys.M, -1),
    // The village only: the island is thirty screens of map, and the mouse
    // wheel does the same thing.
    ZOOM_IN("action.zoom_in", Keys.EQUALS, Keys.NUMPAD_ADD),
    ZOOM_OUT("action.zoom_out", Keys.MINUS, Keys.NUMPAD_SUBTRACT),
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
