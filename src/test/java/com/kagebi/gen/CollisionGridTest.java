package com.kagebi.gen;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Collision finer than a tile, and a tile grid that has not noticed.
 *
 * <p>The second half matters as much as the first. Every dungeon room fills
 * whole tiles, and the grid under it changing from tiles to four-pixel cells
 * must not move a single wall there.
 */
class CollisionGridTest {

    private static final int T = CollisionGrid.TILE;

    /**
     * A wall three pixels thick down the left edge of a tile - the way the
     * house draws its partitions - blocks its own strip and not the rest of
     * the tile, so a player can walk up to it.
     */
    @Test
    void aThinWallBlocksOnlyTheStripItIsDrawnIn() {
        CollisionGrid g = new CollisionGrid(3, 3);
        g.fill(T, 0, 3, 3 * T);
        assertTrue(g.overlaps(T, T, 1, 1), "inside the strip");
        assertFalse(g.overlaps(T + 4, T, 12, 12), "a body standing against it");
        assertTrue(g.overlaps(T + 3, T, 12, 12), "and one pixel further is inside it");
        assertTrue(g.solidTile(1, 1), "the tile still counts as having a wall in it");
    }

    /** Two posts and the gap between them, the way the garden gate is drawn. */
    @Test
    void aBodyFitsThroughAGapWiderThanItself() {
        CollisionGrid g = new CollisionGrid(4, 3);
        g.fill(T, T, 8, T);
        g.fill(2 * T + 8, T, 8, T);
        assertFalse(g.overlaps(26, T + 2, 12, 12), "12px of body in a 16px gap");
        assertTrue(g.overlaps(20, T + 2, 12, 12), "but not over a post");
    }

    /** Whole tiles behave exactly as the tile grid did. */
    @Test
    void aWholeTileIsSolidToItsEdgeAndNoFurther() {
        CollisionGrid g = new CollisionGrid(3, 3);
        g.set(1, 1, true);
        assertTrue(g.solidTile(1, 1));
        assertFalse(g.solidTile(0, 1));
        assertTrue(g.overlaps(2 * T - 1, T, 1, 1), "the tile's last pixel");
        assertFalse(g.overlaps(2 * T, T, 1, 1), "the first pixel past it");
        assertTrue(g.solidAt(T, T));
        assertFalse(g.solidAt(T - 0.5f, T));
    }

    /** The rule the far-edge exclusion exists for, kept at cell resolution. */
    @Test
    void aBodyExactlyATileWideFitsACorridorExactlyATileWide() {
        CollisionGrid g = new CollisionGrid(3, 3);
        for (int y = 0; y < 3; y++) {
            g.set(0, y, true);
            g.set(2, y, true);
        }
        assertFalse(g.overlaps(T, 0, T, 3 * T));
    }

    @Test
    void outsideTheGridIsSolid() {
        CollisionGrid g = new CollisionGrid(2, 2);
        assertTrue(g.solidTile(-1, 0));
        assertTrue(g.overlaps(-1, 0, 4, 4));
        assertTrue(g.solidAt(0, 2 * T));
    }

    @Test
    void clearingATileClearsTheShapesInsideIt() {
        CollisionGrid g = new CollisionGrid(2, 2);
        g.fill(4, 4, 4, 4);
        assertTrue(g.solidTile(0, 0), "one solid cell makes the tile count");
        g.set(0, 0, false);
        assertFalse(g.solidTile(0, 0));
    }

    /** A rectangle a pixel off the cell lines still means the cells it covers. */
    @Test
    void aRectangleIsReadByTheCellCentresItCovers() {
        CollisionGrid g = new CollisionGrid(1, 1);
        g.fill(3, 0, 6, 4);
        assertFalse(g.solidAt(1, 1), "centre 2 is outside 3..9");
        assertTrue(g.solidAt(5, 1), "centre 6 is inside");
        assertFalse(g.solidAt(9, 1), "centre 10 is outside");
    }
}
