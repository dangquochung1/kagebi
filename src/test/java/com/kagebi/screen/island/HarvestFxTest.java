package com.kagebi.screen.island;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** How the island's small effects move, without anything to draw them on. */
class HarvestFxTest {

    /**
     * A good reaches the player however they walk while it is in the air, and
     * on a known step: the pop, then the flight, and not a step more.
     */
    @Test
    void aFlyingGoodReachesAPlayerWhoKeepsWalking() {
        HarvestFx fx = new HarvestFx(7);
        fx.fly(null, 100f, 100f);
        float px = 180f;
        float py = 60f;
        int steps = 0;
        int arrived = 0;
        while (arrived == 0 && steps < 200) {
            px += 1.5f;             // walking away as fast as the player walks
            py -= 0.5f;
            fx.step(px, py);
            arrived = fx.arrived();
            steps++;
        }
        assertEquals(1, arrived);
        assertEquals(HarvestFx.POP_STEPS + HarvestFx.FLY_STEPS, steps);
        assertTrue(fx.idle(), "an arrived good is gone");
    }

    @Test
    void aGoodIsThrownUpBeforeItFlies() {
        HarvestFx fx = new HarvestFx(3);
        fx.fly(null, 50f, 50f);
        float highest = 50f;
        for (int i = 0; i < HarvestFx.POP_STEPS; i++) {
            fx.step(500f, 0f);          // the player is far off, down and to the right
            highest = Math.max(highest, fx.flyers().first().y);
        }
        // The slowest throw, 2.2 px a step against 0.3 of gravity, still rises about 9px.
        assertTrue(highest > 57f, "it should rise first, however far away the player is: " + highest);
    }

    /** The fish clears the fisher's head and goes back in where it came out. */
    @Test
    void aFishLeapsOverTheHeadAndBackIntoTheSameWater() {
        HarvestFx fx = new HarvestFx(1);
        fx.leap(null, 40f, 10f, true);
        HarvestFx.Jump jump = fx.jumps().first();
        float highest = 0f;
        int splashes = 0;
        for (int i = 0; i < HarvestFx.JUMP_STEPS; i++) {
            fx.step(0f, 0f);
            highest = Math.max(highest, jump.y());
            splashes += fx.splashed();
        }
        assertEquals(10f + HarvestFx.JUMP_HEIGHT, highest, 0.5f);
        assertTrue(HarvestFx.JUMP_HEIGHT > 24f, "a fisher's head is 24px over the water line");
        assertEquals(10f, jump.y(), 1e-4f, "back in where it came out");
        assertEquals(1, splashes);
        assertTrue(fx.idle());
    }

    @Test
    void aLeapTurnsFromNoseUpToNoseDown() {
        HarvestFx fx = new HarvestFx(1);
        fx.leap(null, 0f, 0f, false);
        HarvestFx.Jump jump = fx.jumps().first();
        assertEquals(-90f, jump.degrees(), 1e-4f);
        jump.step = HarvestFx.JUMP_STEPS / 2;
        assertEquals(0f, jump.degrees(), 1e-4f);
    }

    @Test
    void aHopRisesFadesAndEnds() {
        HarvestFx fx = new HarvestFx(1);
        fx.hop(null, 0f, 30f);
        HarvestFx.Hop hop = fx.hops().first();
        for (int i = 0; i < HarvestFx.HOP_STEPS - 1; i++) {
            fx.step(0f, 0f);
        }
        assertTrue(hop.rise() > 9f);
        assertTrue(hop.alpha() < 0.2f);
        assertFalse(fx.idle());
        fx.step(0f, 0f);
        assertTrue(fx.idle());
    }
}
