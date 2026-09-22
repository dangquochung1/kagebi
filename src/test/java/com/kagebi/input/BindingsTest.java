package com.kagebi.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.badlogic.gdx.Input.Keys;

/**
 * What the default keyboard is, and the rules it may not break.
 *
 * <p>Pinned because the skills took a key that was already spoken for: I was
 * the second way to open the inventory and is now the middle skill. Nothing
 * else notices a collision - the map hands the first action it finds to
 * whichever key is pressed - so a duplicate is a feature that silently stops
 * working rather than a build that fails.
 */
class BindingsTest {

    @Test
    void noTwoActionsShareADefaultKey() {
        Map<Integer, GameAction> seen = new HashMap<>();
        for (GameAction a : GameAction.values()) {
            for (int key : new int[] {a.defaultPrimary, a.defaultSecondary}) {
                if (key < 0) {
                    continue;
                }
                GameAction other = seen.put(key, a);
                assertEquals(null, other,
                    Keys.toString(key) + " is bound to both " + other + " and " + a);
            }
        }
    }

    @Test
    void theThreeSkillsAreOnTheThreeKeysUnderTheRightHand() {
        assertEquals(Keys.U, GameAction.SKILL_1.defaultPrimary);
        assertEquals(Keys.I, GameAction.SKILL_2.defaultPrimary);
        assertEquals(Keys.O, GameAction.SKILL_3.defaultPrimary);
    }

    /**
     * The two village doors, and the reason they were renamed rather than
     * rebound.
     *
     * <p>{@code InputMap} reads saved bindings by action name, so swapping two
     * defaults under their old names would have changed nothing for anybody
     * who had ever saved a binding - which is everybody, because the controls
     * screen writes the file on the way out. New names miss the saved entries
     * and take the new defaults; every other action keeps what the player
     * chose.
     */
    @Test
    void theSheetTookTabAndTheBagTookF() {
        assertEquals(Keys.TAB, GameAction.SHEET.defaultPrimary);
        assertEquals(Keys.P, GameAction.SHEET.defaultSecondary);
        assertEquals(Keys.F, GameAction.BAG.defaultPrimary);
        assertEquals(Keys.B, GameAction.BAG.defaultSecondary);
        for (GameAction a : GameAction.values()) {
            assertNotEquals("LOADOUT", a.name(), "the third village door is gone");
        }
    }

    @Test
    void everyActionHasAtLeastOneDefaultKey() {
        // InputMap.allActionsBound throws the whole saved file away when this
        // is false, so an action shipped with no default silently resets
        // everybody's controls on first launch.
        for (GameAction a : GameAction.values()) {
            assertTrue(a.defaultPrimary >= 0 || a.defaultSecondary >= 0, a + " has no key");
        }
    }

    @Test
    void noDefaultKeyBelongsToTheDesktop() {
        for (GameAction a : GameAction.values()) {
            assertFalse(InputService.reserved(a.defaultPrimary), a + " primary");
            assertFalse(InputService.reserved(a.defaultSecondary), a + " secondary");
        }
    }

    @Test
    void theDefaultsRoundTripThroughTheMap() {
        InputMap map = InputMap.defaults();
        for (GameAction a : GameAction.values()) {
            assertEquals(a.defaultPrimary, map.primary(a), a + " primary");
            assertEquals(a.defaultSecondary, map.secondary(a), a + " secondary");
        }
        assertTrue(map.allActionsBound());
        assertEquals(GameAction.SKILL_2, map.actionFor(Keys.I), "I still opens the bag");
        assertEquals(GameAction.SHEET, map.actionFor(Keys.TAB));
        assertEquals(GameAction.BAG, map.actionFor(Keys.F));
    }
}
