package com.kagebi.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.backends.headless.HeadlessApplication;
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration;
import com.kagebi.data.ContentRegistry;
import com.kagebi.gen.RoomKind;
import com.kagebi.input.GameAction;
import com.kagebi.input.InputMap;
import com.kagebi.input.InputService;

/**
 * The buffering contract against the real {@link InputService}, not a copy.
 *
 * <p>Everything else in these tests drives the player through
 * {@link ScriptedInput}, which reimplements the service's rules. This is the
 * one place those rules are checked against the original, so the rest can be
 * trusted. {@code InputMap} reads {@code Preferences} in its constructor, so a
 * headless application is booted - with a negative update rate, which skips its
 * render loop entirely, so no thread is left running.
 *
 * <p>The key is asked of the map rather than written as a keycode: a developer
 * who has rebound attack on this machine still gets a passing test.
 */
class RealInputBufferTest {

    private static HeadlessApplication app;

    @BeforeAll
    static void boot() {
        HeadlessApplicationConfiguration config = new HeadlessApplicationConfiguration();
        config.updatesPerSecond = -1;
        app = new HeadlessApplication(new ApplicationAdapter() {}, config);
    }

    @AfterAll
    static void shutdown() {
        if (app != null) {
            app.exit();
        }
    }

    private static int keyFor(InputService input, GameAction a) {
        int primary = input.map().primary(a);
        return primary >= 0 ? primary : input.map().secondary(a);
    }

    private static void tap(InputService input, GameAction a) {
        int key = keyFor(input, a);
        input.keyDown(key);
        input.keyUp(key);
    }

    /** One fixed step in the order the screen's accumulator loop runs it. */
    private static void tick(InputService input, EntityWorld world) {
        input.beginStep();
        world.step(input);
    }

    private static EntityWorld world() {
        EntityWorld w = new EntityWorld(null, new ContentRegistry(), TestDefs.run(), null);
        w.enterRoom(TestDefs.room(RoomKind.NORMAL), TestDefs.walled(), null);
        return w;
    }

    @Test
    void anAttackTappedDuringRecoveryQueuesOnTheRealService() {
        InputService input = new InputService(InputMap.defaults());
        EntityWorld w = world();
        Player p = w.player();

        tap(input, GameAction.ATTACK);
        int starts = 0;
        for (int t = 1; t <= 60; t++) {
            if (t == 15) {
                tap(input, GameAction.ATTACK);  // three steps before recovery ends
            }
            tick(input, w);
            if (p.swing().busy() && p.swing().elapsed() == 1) {
                starts++;
            }
        }
        assertEquals(2, starts, "the second press was held, not eaten");
    }

    @Test
    void oneTapIsOneSwingOnTheRealService() {
        InputService input = new InputService(InputMap.defaults());
        EntityWorld w = world();
        Player p = w.player();

        tap(input, GameAction.ATTACK);
        int starts = 0;
        for (int t = 0; t < 120; t++) {
            tick(input, w);
            if (p.swing().busy() && p.swing().elapsed() == 1) {
                starts++;
            }
        }
        assertEquals(1, starts);
        assertTrue(!input.buffered(GameAction.ATTACK), "and the press is spent");
    }

    @Test
    void aTapBetweenStepsIsNotLost() {
        // Pressed and released before the step that reads it: at 144Hz this is
        // the normal case, and a service that cleared presses on render would
        // drop it.
        InputService input = new InputService(InputMap.defaults());
        EntityWorld w = world();
        tap(input, GameAction.ROLL);
        tick(input, w);
        assertTrue(w.player().rolling());
    }
}
