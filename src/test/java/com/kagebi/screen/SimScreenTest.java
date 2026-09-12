package com.kagebi.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.backends.headless.HeadlessApplication;
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration;
import com.kagebi.Cfg;
import com.kagebi.input.GameAction;
import com.kagebi.input.InputMap;
import com.kagebi.input.InputService;

/**
 * The fixed-timestep loop both playable screens run on.
 *
 * <p>Its failures never look like loop bugs. A press lost in a frame with no
 * step reads as "the controls are laggy on my monitor"; a carried backlog
 * reads as "the game froze after alt-tab". Both are cheap to pin down here and
 * miserable to find by playing.
 *
 * <p>A headless application is started only because {@link InputMap} reads the
 * player's saved bindings through {@code Gdx.app}; nothing here draws.
 */
class SimScreenTest {

    private static HeadlessApplication app;

    @BeforeAll
    static void boot() {
        app = new HeadlessApplication(new ApplicationAdapter() {},
                                      new HeadlessApplicationConfiguration());
    }

    @AfterAll
    static void exit() {
        app.exit();
    }

    /** Counts its steps, and the steps on which ATTACK arrived as a new press. */
    private static final class Counting extends SimScreen {
        int steps;
        int presses;

        Counting(InputService input) {
            super(input);
        }

        @Override
        protected void step() {
            steps++;
            if (input().justPressed(GameAction.ATTACK)) {
                presses++;
            }
        }

        @Override
        public void render(float delta) {}
    }

    private static InputService input() {
        return new InputService(new InputMap());
    }

    @Test
    void oneStepPerStepOfTimeAndTheRemainderCarries() {
        Counting s = new Counting(input());
        s.update(Cfg.STEP * 3.5f);
        assertEquals(3, s.steps);
        // Half a step left over from before, and six tenths now.
        s.update(Cfg.STEP * 0.6f);
        assertEquals(4, s.steps);
    }

    /**
     * At 144 Hz most frames run no step at all. A press that lands in one of
     * them has to survive to the next frame that does, and arrive once.
     */
    @Test
    void aPressInAFrameWithNoStepArrivesOnTheNextStep() {
        InputService in = input();
        Counting s = new Counting(in);
        in.keyDown(in.map().primary(GameAction.ATTACK));
        s.update(1f / 144f);
        assertEquals(0, s.steps, "a 144 Hz frame is shorter than a step");
        s.update(1f / 144f);
        s.update(1f / 144f);
        assertEquals(1, s.steps);
        assertEquals(1, s.presses);

        s.update(Cfg.STEP * 4f);
        assertEquals(1, s.presses, "a held key is one press, not one per step");
    }

    /**
     * A 1-second hitch must not be followed by 55 steps of catching up, each
     * taking longer than a frame - that is the spiral the cap exists to stop.
     */
    @Test
    void aLongFrameIsCappedAndItsBacklogDropped() {
        Counting s = new Counting(input());
        s.update(1f);
        assertEquals(Cfg.MAX_STEPS_PER_FRAME, s.steps);
        s.update(0f);
        assertEquals(Cfg.MAX_STEPS_PER_FRAME, s.steps, "the dropped backlog came back");
    }
}
