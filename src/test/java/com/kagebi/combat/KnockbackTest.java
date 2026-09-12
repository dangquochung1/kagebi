package com.kagebi.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.kagebi.Cfg;
import com.kagebi.Dir;

class KnockbackTest {

    /** Sums the displacement a knockback produces, step by step, as an entity would. */
    private static float[] travel(Knockback k) {
        float x = 0f;
        float y = 0f;
        while (k.active()) {
            x += k.vx() * Cfg.STEP;
            y += k.vy() * Cfg.STEP;
            k.step();
        }
        return new float[] {x, y};
    }

    @Test
    void pushesDirectlyAwayFromTheSource() {
        Knockback k = new Knockback();
        k.apply(0f, 0f, 10f, 0f, 90f, 0f, Dir.DOWN);
        assertTrue(k.vx() > 0f);
        assertEquals(0f, k.vy(), 1e-6f);

        k.apply(5f, 5f, 5f, -20f, 90f, 0f, Dir.DOWN);
        assertTrue(k.vy() < 0f, "a target below the source is pushed down");
        assertEquals(0f, k.vx(), 1e-6f);
    }

    @Test
    void isDeterministic() {
        Knockback a = new Knockback();
        Knockback b = new Knockback();
        a.apply(3f, 4f, 20f, -7f, 110f, 0.2f, Dir.LEFT);
        b.apply(3f, 4f, 20f, -7f, 110f, 0.2f, Dir.LEFT);
        while (a.active() || b.active()) {
            assertEquals(a.vx(), b.vx(), 0f);
            assertEquals(a.vy(), b.vy(), 0f);
            a.step();
            b.step();
        }
    }

    @Test
    void travelMatchesTheDistanceTheCommentsQuote() {
        // Linear decay over N steps covers v0 * STEP * (N + 1) / 2. For the
        // default 12 steps that is 0.108 x the impulse: 90 px/s of contact
        // knockback is 9.75px, the "about 10" EntityWorld's comment claims.
        Knockback k = new Knockback();
        k.apply(0f, 0f, 1f, 0f, 90f, 0f, Dir.DOWN);
        float[] d = travel(k);
        assertEquals(90f * Cfg.STEP * (Knockback.DEFAULT_STEPS + 1) / 2f, d[0], 1e-3f);
        assertEquals(9.75f, d[0], 0.01f);
    }

    @Test
    void reachesExactlyZeroInsteadOfCreeping() {
        Knockback k = new Knockback();
        k.apply(0f, 0f, 1f, 0f, 200f, 0f, Dir.DOWN, 12);
        for (int i = 0; i < 12; i++) {
            assertTrue(k.active());
            k.step();
        }
        assertFalse(k.active());
        assertEquals(0f, k.vx(), 0f);
    }

    @Test
    void resistanceScalesAndFullResistanceCancels() {
        Knockback full = new Knockback();
        Knockback half = new Knockback();
        full.apply(0f, 0f, 1f, 0f, 100f, 0f, Dir.DOWN);
        half.apply(0f, 0f, 1f, 0f, 100f, 0.5f, Dir.DOWN);
        assertEquals(full.vx() / 2f, half.vx(), 1e-4f);

        // The bosses in enemies.json are 1.0: immovable, by design.
        Knockback boss = new Knockback();
        boss.apply(0f, 0f, 1f, 0f, 190f, 1f, Dir.DOWN);
        assertFalse(boss.active());
    }

    @Test
    void coincidentPositionsFallBackToAFacingInsteadOfNaN() {
        // The normal state of a melee fight, and a divide by zero without the
        // fallback. NaN here would poison the position and delete the entity.
        Knockback k = new Knockback();
        k.apply(8f, 8f, 8f, 8f, 90f, 0f, Dir.LEFT);
        assertFalse(Float.isNaN(k.vx()));
        assertFalse(Float.isNaN(k.vy()));
        assertTrue(k.vx() < 0f, "falls back to the given direction, LEFT");
    }
}
