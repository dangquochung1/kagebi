package com.kagebi.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.kagebi.Dir;

class HitboxTest {

    private static final float REACH = 22f;    // katana
    private static final float HALF = 9f;

    private static Hitbox swing(Dir d) {
        return Hitbox.swing(100f, 50f, d, REACH, HALF, 7, 40f, Faction.PLAYER);
    }

    @Test
    void swingProjectsAlongEachFacing() {
        Hitbox r = swing(Dir.RIGHT);
        assertEquals(100f, r.x, 0f);
        assertEquals(41f, r.y, 0f);
        assertEquals(REACH, r.w, 0f);
        assertEquals(HALF * 2f, r.h, 0f);

        Hitbox l = swing(Dir.LEFT);
        assertEquals(100f - REACH, l.x, 0f);
        assertEquals(REACH, l.w, 0f);

        Hitbox u = swing(Dir.UP);
        assertEquals(91f, u.x, 0f);
        assertEquals(50f, u.y, 0f);
        assertEquals(HALF * 2f, u.w, 0f);
        assertEquals(REACH, u.h, 0f);

        Hitbox d = swing(Dir.DOWN);
        assertEquals(50f - REACH, d.y, 0f);
        assertEquals(REACH, d.h, 0f);
    }

    @Test
    void reachesTheTargetInFrontAndNotBehind() {
        Hitbox r = swing(Dir.RIGHT);
        assertTrue(r.overlapsCentred(115f, 50f, 12f, 12f), "an enemy a tile to the right");
        assertFalse(r.overlapsCentred(80f, 50f, 12f, 12f), "an enemy a tile behind");
        assertFalse(r.overlapsCentred(115f, 75f, 12f, 12f), "an enemy well above the swing");
    }

    @Test
    void pointBlankIsInside() {
        // Anchored at the centre, so an enemy standing on the player is hit. A
        // box that started at the edge of the body would leave a dead ring here.
        for (Dir d : Dir.ALL) {
            assertTrue(swing(d).overlapsCentred(100f, 50f, 12f, 12f), d.name());
        }
    }

    @Test
    void edgesAreOpenIntervals() {
        Hitbox r = swing(Dir.RIGHT);    // x spans [100, 122)
        // A 12-wide body centred at 128 spans [122, 134): touching, not inside.
        assertFalse(r.overlapsCentred(128f, 50f, 12f, 12f));
        assertTrue(r.overlapsCentred(127.9f, 50f, 12f, 12f));
    }

    @Test
    void bodyBoxIsCentred() {
        Hitbox b = Hitbox.body(40f, 30f, 12f, 10f, 5, 90f, Faction.ENEMY);
        assertEquals(34f, b.x, 0f);
        assertEquals(25f, b.y, 0f);
        assertEquals(40f, b.centreX(), 0f);
        assertEquals(30f, b.centreY(), 0f);
    }

    @Test
    void factionsOnlyHurtTheOtherSide() {
        assertFalse(Faction.PLAYER.hostileTo(Faction.PLAYER));
        assertFalse(Faction.ENEMY.hostileTo(Faction.ENEMY));
        assertTrue(Faction.PLAYER.hostileTo(Faction.ENEMY));
        assertTrue(Faction.ENEMY.hostileTo(Faction.PLAYER));
        assertTrue(Faction.HAZARD.hostileTo(Faction.PLAYER));
        assertTrue(Faction.HAZARD.hostileTo(Faction.ENEMY));
    }
}
