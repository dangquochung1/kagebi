package com.kagebi.input;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.badlogic.gdx.Input.Keys;

/**
 * Presses aimed at the game, and chords aimed at the desktop.
 *
 * <p>The second is here because a player reported that asking Windows for a
 * screenshot made their ninja roll. {@code Win+Shift+S} is the gesture, this
 * game binds Shift to roll and S to walking down, and the window had focus - so
 * the game did exactly what it was told by keys that were never meant for it.
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
        in.keyDown(Keys.SHIFT_LEFT);    // bound to roll
        in.keyDown(Keys.S);             // bound to walking down
        in.beginStep();
        assertFalse(in.justPressed(GameAction.ROLL), "Win+Shift+S must not roll");
        assertFalse(in.isDown(GameAction.MOVE_DOWN), "Win+Shift+S must not walk");
        assertTrue(in.systemChord());
    }

    /**
     * The chord's keys are often released in an order the game never sees in
     * full, and whatever it does see must leave nothing held.
     */
    @Test
    void lettingGoOfTheChordLeavesNothingHeld() {
        InputService in = service();
        in.keyDown(Keys.SYM);
        in.keyDown(Keys.SHIFT_LEFT);
        in.keyUp(Keys.SHIFT_LEFT);
        in.keyUp(Keys.SYM);
        in.beginStep();
        assertFalse(in.isDown(GameAction.ROLL));
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
