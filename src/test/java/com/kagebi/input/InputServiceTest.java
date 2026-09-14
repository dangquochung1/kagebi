package com.kagebi.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.badlogic.gdx.Input.Keys;

/**
 * Presses aimed at the game, and chords aimed at the desktop.
 *
 * <p>The second is here because a player reported that asking Windows for a
 * screenshot made their ninja roll. {@code Win+Shift+S} is the gesture, S is
 * walking down, and Shift used to be roll - so the game did exactly what it was
 * told by keys that were never meant for it. The first fix only recognised the
 * chord when the Windows key went down first, and the player pressed Shift
 * first.
 */
class InputServiceTest {

    private static InputService service() {
        // Defaults, not the bindings on this machine: new InputMap() reads a
        // Preferences file out of the home directory, and these tests name the
        // shipped keys.
        return new InputService(InputMap.defaults());
    }

    /** A plain press still arrives, or nothing below means anything. */
    @Test
    void anOrdinaryPressIsReported() {
        InputService in = service();
        in.keyDown(Keys.SPACE);
        in.beginStep();
        assertTrue(in.justPressed(GameAction.ROLL));
        assertTrue(in.isDown(GameAction.ROLL));
    }

    @Test
    void theWindowsScreenshotChordNeitherRollsNorWalks() {
        InputService in = service();
        in.keyDown(Keys.SYM);           // the Windows key
        in.keyDown(Keys.SHIFT_LEFT);
        in.keyDown(Keys.S);             // bound to walking down
        in.beginStep();
        assertFalse(in.justPressed(GameAction.ROLL), "Win+Shift+S must not roll");
        assertFalse(in.isDown(GameAction.MOVE_DOWN), "Win+Shift+S must not walk");
        assertTrue(in.systemChord());
    }

    /**
     * The same chord with Shift first, which is how the player who reported it
     * pressed it. Nothing can see the Windows key coming, so the only answer is
     * that Shift on its own means nothing - including a whole step later.
     */
    @Test
    void shiftPressedBeforeTheWindowsKeyDoesNotRollEither() {
        InputService in = service();
        in.keyDown(Keys.SHIFT_LEFT);
        in.beginStep();
        assertFalse(in.justPressed(GameAction.ROLL), "Shift alone must not roll");
        in.keyDown(Keys.SYM);
        in.keyDown(Keys.S);
        in.beginStep();
        assertFalse(in.buffered(GameAction.ROLL), "and no roll is waiting in the buffer");
        assertFalse(in.isDown(GameAction.MOVE_DOWN), "nor does the S walk");
    }

    /** Holding Shift is not a chord, so it does not stop the next press counting. */
    @Test
    void holdingShiftDoesNotSilenceTheGame() {
        InputService in = service();
        in.keyDown(Keys.SHIFT_LEFT);
        in.keyDown(Keys.J);
        in.beginStep();
        assertTrue(in.justPressed(GameAction.ATTACK));
        assertFalse(in.systemChord());
    }

    @Test
    void noActionShipsWithAKeyTheDesktopOwns() {
        for (GameAction a : GameAction.values()) {
            assertFalse(InputService.reserved(a.defaultPrimary), a + " primary");
            assertFalse(InputService.reserved(a.defaultSecondary), a + " secondary");
        }
    }

    /** Taking Shift away left roll on one key; L is its second. */
    @Test
    void rollStillHasTwoKeysByDefault() {
        InputMap map = InputMap.defaults();
        assertEquals(GameAction.ROLL, map.actionFor(Keys.SPACE));
        assertEquals(GameAction.ROLL, map.actionFor(Keys.L));
        assertNull(map.actionFor(Keys.SHIFT_LEFT));
    }

    @Test
    void theControlsScreenCannotBindShift() {
        InputMap map = InputMap.defaults();
        assertNull(map.bind(GameAction.ROLL, Keys.SHIFT_LEFT, true));
        assertNull(map.actionFor(Keys.SHIFT_LEFT), "refused rather than bound");
        assertEquals(Keys.L, map.secondary(GameAction.ROLL), "and nothing else moved");
    }

    /**
     * A bindings file written before Shift was reserved still says roll is
     * Shift, and the player who reported the bug has exactly that file.
     */
    @Test
    void aSavedReservedKeyIsDroppedOnLoadAndEverythingElseIsKept() {
        assertEquals(-1, InputMap.usable(Keys.SHIFT_LEFT));
        assertEquals(-1, InputMap.usable(Keys.SHIFT_RIGHT));
        assertEquals(-1, InputMap.usable(Keys.SYM));
        assertEquals(Keys.L, InputMap.usable(Keys.L));
        assertEquals(-1, InputMap.usable(-1), "an unbound slot stays unbound");
    }

    /**
     * The chord's keys are often released in an order the game never sees in
     * full, and whatever it does see must leave nothing held.
     */
    @Test
    void lettingGoOfTheChordLeavesNothingHeld() {
        InputService in = service();
        in.keyDown(Keys.SYM);
        in.keyDown(Keys.S);
        in.keyUp(Keys.S);
        in.keyUp(Keys.SYM);
        in.beginStep();
        assertFalse(in.isDown(GameAction.MOVE_DOWN));
        assertFalse(in.systemChord(), "the modifier count must return to zero");

        // And the game listens again straight afterwards.
        in.keyDown(Keys.SPACE);
        in.beginStep();
        assertTrue(in.justPressed(GameAction.ROLL));
    }

    /**
     * A key held down when the chord starts keeps walking. Suppression is about
     * presses: taking a held key away mid-stride would make the player stop
     * dead every time they touched Ctrl.
     */
    @Test
    void aKeyAlreadyHeldIsNotTakenAwayByTheChord() {
        InputService in = service();
        in.keyDown(Keys.D);
        in.beginStep();
        assertTrue(in.isDown(GameAction.MOVE_RIGHT));
        in.keyDown(Keys.ALT_LEFT);
        in.beginStep();
        assertTrue(in.isDown(GameAction.MOVE_RIGHT), "a held key should stay held");
    }

    /**
     * Losing focus is the only moment the desktop tells us anything, and it
     * has to be enough: after it, nothing is held and nothing is suppressed.
     */
    @Test
    void losingFocusReleasesEverythingIncludingTheModifiers() {
        InputService in = service();
        in.keyDown(Keys.CONTROL_LEFT);
        in.keyDown(Keys.W);
        in.beginStep();
        in.clear();
        assertFalse(in.systemChord());
        assertFalse(in.isDown(GameAction.MOVE_UP));

        in.keyDown(Keys.W);
        in.beginStep();
        assertTrue(in.justPressed(GameAction.MOVE_UP), "input must work after a focus loss");
    }
}
