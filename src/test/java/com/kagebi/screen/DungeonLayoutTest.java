package com.kagebi.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntArray;
import com.kagebi.Dir;
import com.kagebi.gen.CollisionGrid;
import com.kagebi.gen.Room;
import com.kagebi.gen.RoomKind;
import com.kagebi.gen.RoomTemplate;
import com.kagebi.gen.SpawnPoint;
import com.kagebi.ui.Hud;
import com.kagebi.ui.Minimap;

/**
 * The arithmetic under the dungeon screen: sealed doors, the minimap window,
 * the hearts. All of it pure, so none of it needs a GL context.
 *
 * <p>Each of these failed first as something odd on screen - a doorway you
 * could walk out of into nothing, a minimap scrolled past its own edge - and
 * each is the kind of thing that only shows up on the one floor shape nobody
 * happened to try.
 */
class DungeonLayoutTest {

    private static final int W = RoomTemplate.WIDTH;
    private static final int H = RoomTemplate.HEIGHT;

    private static Room room() {
        Array<RoomKind> kinds = new Array<>();
        kinds.add(RoomKind.NORMAL);
        return new Room(0, 0, RoomKind.NORMAL,
            new RoomTemplate("t", "b", kinds, "t.tmx", new Array<SpawnPoint>()));
    }

    /** A wall ring with a doorway {@code width} tiles wide starting at {@code from}. */
    private static CollisionGrid ring(int from, int width) {
        CollisionGrid g = new CollisionGrid(W, H);
        for (int x = 0; x < W; x++) {
            g.set(x, 0, true);
            g.set(x, H - 1, true);
        }
        for (int y = 0; y < H; y++) {
            g.set(0, y, true);
            g.set(W - 1, y, true);
        }
        for (int i = from; i < from + width; i++) {
            g.set(i, 0, false);
            g.set(i, H - 1, false);
        }
        for (int i = 4; i < 7; i++) {
            g.set(0, i, false);
            g.set(W - 1, i, false);
        }
        return g;
    }

    @Test
    void sealsExactlyTheDoorwaysThatLeadNowhere() {
        Room here = room();
        here.link(Dir.RIGHT, room());
        CollisionGrid grid = ring(8, 3);
        IntArray sealed = new IntArray();

        DungeonScreen.seal(here, grid, sealed);

        assertEquals(9, sealed.size, "three doorways of three tiles each");
        for (int i = 8; i < 11; i++) {
            assertTrue(grid.solidTile(i, 0), "down doorway open at " + i);
            assertTrue(grid.solidTile(i, H - 1), "up doorway open at " + i);
        }
        for (int i = 4; i < 7; i++) {
            assertTrue(grid.solidTile(0, i), "left doorway open at " + i);
            assertFalse(grid.solidTile(W - 1, i), "right doorway leads somewhere, yet sealed");
        }
    }

    /**
     * The doorway is found, not assumed. A procgen template is free to put a
     * two-tile door anywhere along a wall, and a seal cut to the placeholder's
     * centred three tiles would leave a gap in it.
     */
    @Test
    void findsADoorwayOfAnyWidthAnywhere() {
        CollisionGrid grid = ring(2, 2);
        IntArray sealed = new IntArray();
        DungeonScreen.seal(room(), grid, sealed);
        assertTrue(grid.solidTile(2, 0) && grid.solidTile(3, 0));
        assertTrue(grid.solidTile(2, H - 1) && grid.solidTile(3, H - 1));
        assertEquals(2 + 2 + 3 + 3, sealed.size);
    }

    @Test
    void aRoomOpenOnEverySideIsLeftAlone() {
        Room here = room();
        for (Dir d : Dir.ALL) {
            here.link(d, room());
        }
        IntArray sealed = new IntArray();
        DungeonScreen.seal(here, ring(8, 3), sealed);
        assertEquals(0, sealed.size);
    }

    // ---- minimap -----------------------------------------------------------

    @Test
    void theMinimapShowsAWholeSmallFloor() {
        assertEquals(7, Minimap.visibleCells(7, 54, Minimap.CELL));
        assertEquals(0, Minimap.windowOrigin(3, 7, 7));
    }

    /** Clamped at both ends; a map scrolled past its own edge reads as a bug. */
    @Test
    void theMinimapWindowFollowsTheRoomButNeverLeavesTheFloor() {
        assertEquals(0, Minimap.windowOrigin(0, 20, 10));
        assertEquals(5, Minimap.windowOrigin(10, 20, 10));
        assertEquals(10, Minimap.windowOrigin(19, 20, 10));
    }

    @Test
    void theMinimapAlwaysShowsAtLeastTheCurrentRoom() {
        assertEquals(1, Minimap.visibleCells(9, 2, Minimap.CELL));
    }

    // ---- hearts ------------------------------------------------------------

    @Test
    void heartsFillInQuartersLeftToRight() {
        assertEquals(3, Hud.heartCount(12));
        assertEquals(4, Hud.heartCount(13), "a part-heart of capacity still gets drawn");
        int[] at5 = {Hud.quarters(5, 0), Hud.quarters(5, 1), Hud.quarters(5, 2)};
        assertEquals(4, at5[0]);
        assertEquals(1, at5[1]);
        assertEquals(0, at5[2]);
        assertEquals(0, Hud.quarters(-3, 0), "overkill never indexes below the empty frame");
        assertEquals(4, Hud.quarters(99, 0), "overheal never indexes past the full frame");
    }

    /** Every starting run draws whole hearts only; see Screens.DEFAULT_MAX_HP. */
    @Test
    void theStartingHealthIsWholeHearts() {
        assertEquals(0, Screens.DEFAULT_MAX_HP % com.kagebi.assets.Assets.Ui.HEART_STEPS);
    }
}
