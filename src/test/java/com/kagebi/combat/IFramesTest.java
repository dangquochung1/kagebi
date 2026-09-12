package com.kagebi.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class IFramesTest {

    @Test
    void windowLastsExactlyTheGrantedSteps() {
        IFrames f = new IFrames();
        f.grant(10);
        for (int i = 0; i < 10; i++) {
            assertTrue(f.invulnerable(), "should still be invulnerable after " + i + " steps");
            f.step();
        }
        assertFalse(f.invulnerable(), "a 10-step window must close on step 10, not 11");
    }

    @Test
    void aShorterGrantNeverCutsALongerWindowShort() {
        IFrames f = new IFrames();
        f.grant(30);        // a roll
        for (int i = 0; i < 5; i++) {
            f.step();
        }
        f.grant(10);        // clipped mid-roll
        assertEquals(25, f.remaining(), "the roll's remaining 25 steps must survive");
    }

    @Test
    void aLongerGrantExtends() {
        IFrames f = new IFrames();
        f.grant(5);
        f.grant(40);
        assertEquals(40, f.remaining());
    }

    @Test
    void steppingAnEmptyWindowStaysAtZero() {
        IFrames f = new IFrames();
        for (int i = 0; i < 3; i++) {
            f.step();
        }
        assertEquals(0, f.remaining());
    }
}
