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

    /**
     * What you are carrying: the run's satchel below, the storehouse above.
     *
     * <p>F, which used to open the kit. Tab used to open this, and the two
     * swapped because the sheet is the screen a player opens twenty times a
     * run and the bag is the one they open to look something up - Tab is the
     * key every player tries first, so it should land on the busier screen.
     *
     * <p>Renamed from {@code INVENTORY} rather than rebound, which is not a
     * detail: {@code InputMap} reads saved bindings by action name, so a name
     * that survives a change of meaning keeps the old key and nothing appears
     * to happen. A new name misses the saved entry, takes the new default, and
     * leaves every key the player chose for something else alone.
     */
    BAG("action.bag", Keys.F, Keys.B),

    /**
     * The three skills, left to right on the bar.
     *
     * <p>Numbered rather than named after what they do, because what they do
     * is content: {@code assets/data/skills.json} says which skill sits in
     * which slot, and swapping two of them is an edit to that file rather than
     * a rebind. U, I and O are three unbound keys in a row under the fingers
     * that are not already holding a direction.
     */
    SKILL_1("action.skill_1", Keys.U, Keys.NUM_1),
    SKILL_2("action.skill_2", Keys.I, Keys.NUM_2),
    SKILL_3("action.skill_3", Keys.O, Keys.NUM_3),
    /**
     * The character sheet: who you are, what you wear, what you carry, what
     * you can make, and what you have met.
     *
     * <p>Tab, and P as it was before. There was a third badge in the village
     * that opened a separate screen for choosing a character and a weapon;
     * that screen is gone and its job is a panel on this one, so the village
     * has two doors instead of three and neither of them is a second copy of
     * the other.
     *
     * <p>Its own action rather than a spare gameplay key. The village runs a
     * real EntityWorld - the player walks, swings and drinks there like
     * anywhere else - so Q, K and J are all spoken for, and binding one of
     * them to a menu would mean the menu opening when someone meant to drink.
     *
     * <p>Renamed from {@code CHARACTER} for the reason given on {@link #BAG}.
     */
    SHEET("action.sheet", Keys.TAB, Keys.P),
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
