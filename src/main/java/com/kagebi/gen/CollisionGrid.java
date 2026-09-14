package com.kagebi.gen;

/**
 * Which parts of a room block movement.
 *
 * <p>Deliberately a bare boolean grid with no libGDX types in sight. Building
 * one from a TiledMap is a few lines in the screen that owns the map; keeping
 * that out of here is what lets collision be tested in plain JUnit, and it is
 * collision bugs - walking through a wall corner, sticking on a doorway - that
 * are most painful to reproduce by hand.
 *
 * <p><b>Finer than a tile.</b> The grid is kept in {@link #CELL}-pixel cells,
 * four to a tile edge. Dungeon rooms only ever fill whole tiles, and for a grid
 * of whole tiles every query here answers exactly what the old tile grid did.
 * The village and the house are different: they come from an art pack that
 * draws a partition as a three-pixel line along one edge of a tile and a gate
 * as two posts at the edges of two tiles, and a grid of whole tiles can only
 * turn those into a wall a tile thick - a gap the player can never close to
 * the wall, and a gate nobody fits through.
 *
 * <p>Positions are in the same y-up pixel space as everything else.
 */
public final class CollisionGrid {

    public static final int TILE = 16;
    /** One collision cell, in pixels. Divides {@link #TILE}. */
    public static final int CELL = 4;
    private static final int PER_TILE = TILE / CELL;

    private final int width;
    private final int height;
    private final int cellsWide;
    private final int cellsHigh;
    private final boolean[] solid;

    /** A grid {@code width} by {@code height} tiles, all of it clear. */
    public CollisionGrid(int width, int height) {
        this.width = width;
        this.height = height;
        this.cellsWide = width * PER_TILE;
        this.cellsHigh = height * PER_TILE;
        this.solid = new boolean[cellsWide * cellsHigh];
    }

    /** Width in tiles. */
    public int width() {
        return width;
    }

    /** Height in tiles. */
    public int height() {
        return height;
    }

    /** Makes a whole tile solid or clear, whatever finer shapes were in it. */
    public void set(int tx, int ty, boolean value) {
        if (tx < 0 || ty < 0 || tx >= width || ty >= height) {
            return;
        }
        for (int cy = ty * PER_TILE; cy < (ty + 1) * PER_TILE; cy++) {
            for (int cx = tx * PER_TILE; cx < (tx + 1) * PER_TILE; cx++) {
                solid[cy * cellsWide + cx] = value;
            }
        }
    }

    /**
     * Makes solid every cell whose centre lies inside a rectangle.
     *
     * <p>By centre, so that a rectangle nudged a pixel off the cell lines in
     * Tiled still means the cells it visibly covers, rather than gaining or
     * losing a whole cell depending on which way it missed.
     *
     * @param x left edge, in pixels
     * @param y bottom edge, in pixels, y-up
     */
    public void fill(float x, float y, float w, float h) {
        int x0 = Math.max(0, (int) Math.ceil(x / CELL - 0.5f));
        int y0 = Math.max(0, (int) Math.ceil(y / CELL - 0.5f));
        int x1 = Math.min(cellsWide - 1, (int) Math.ceil((x + w) / CELL - 0.5f) - 1);
        int y1 = Math.min(cellsHigh - 1, (int) Math.ceil((y + h) / CELL - 0.5f) - 1);
        for (int cy = y0; cy <= y1; cy++) {
            for (int cx = x0; cx <= x1; cx++) {
                solid[cy * cellsWide + cx] = true;
            }
        }
    }

    /**
     * Whether any part of a tile is solid. Outside the grid counts as solid:
     * nothing should walk off a room.
     */
    public boolean solidTile(int tx, int ty) {
        if (tx < 0 || ty < 0 || tx >= width || ty >= height) {
            return true;
        }
        for (int cy = ty * PER_TILE; cy < (ty + 1) * PER_TILE; cy++) {
            for (int cx = tx * PER_TILE; cx < (tx + 1) * PER_TILE; cx++) {
                if (solid[cy * cellsWide + cx]) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean solidAt(float x, float y) {
        return solidCell((int) Math.floor(x / CELL), (int) Math.floor(y / CELL));
    }

    private boolean solidCell(int cx, int cy) {
        if (cx < 0 || cy < 0 || cx >= cellsWide || cy >= cellsHigh) {
            return true;
        }
        return solid[cy * cellsWide + cx];
    }

    /**
     * Whether an axis-aligned box overlaps anything solid.
     *
     * <p>The far edge is tested at one pixel less than the box's right and top.
     * A box whose right edge sits exactly on a cell boundary is touching that
     * cell, not inside it, and testing it as inside makes a character of
     * exactly tile width unable to fit down a corridor of exactly tile width.
     */
    public boolean overlaps(float x, float y, float w, float h) {
        int x0 = (int) Math.floor(x / CELL);
        int y0 = (int) Math.floor(y / CELL);
        int x1 = (int) Math.floor((x + w - 1) / CELL);
        int y1 = (int) Math.floor((y + h - 1) / CELL);
        for (int cy = y0; cy <= y1; cy++) {
            for (int cx = x0; cx <= x1; cx++) {
                if (solidCell(cx, cy)) {
                    return true;
                }
            }
        }
        return false;
    }
}
