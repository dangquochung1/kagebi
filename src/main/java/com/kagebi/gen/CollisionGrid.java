package com.kagebi.gen;

/**
 * Which tiles of a room block movement.
 *
 * <p>Deliberately a bare boolean grid with no libGDX types in sight. Building
 * one from a TiledMap is a few lines in the screen that owns the map; keeping
 * that out of here is what lets collision be tested in plain JUnit, and it is
 * collision bugs - walking through a wall corner, sticking on a doorway - that
 * are most painful to reproduce by hand.
 *
 * <p>Positions are in the same y-up pixel space as everything else.
 */
public final class CollisionGrid {

    public static final int TILE = 16;

    private final int width;
    private final int height;
    private final boolean[] solid;

    public CollisionGrid(int width, int height) {
        this.width = width;
        this.height = height;
        this.solid = new boolean[width * height];
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public void set(int tx, int ty, boolean value) {
        if (tx < 0 || ty < 0 || tx >= width || ty >= height) {
            return;
        }
        solid[ty * width + tx] = value;
    }

    /** Outside the grid counts as solid: nothing should walk off a room. */
    public boolean solidTile(int tx, int ty) {
        if (tx < 0 || ty < 0 || tx >= width || ty >= height) {
            return true;
        }
        return solid[ty * width + tx];
    }

    public boolean solidAt(float x, float y) {
        return solidTile((int) Math.floor(x / TILE), (int) Math.floor(y / TILE));
    }

    /**
     * Whether an axis-aligned box overlaps anything solid.
     *
     * <p>The far edge is tested at one pixel less than the box's right and top.
     * A box whose right edge sits exactly on a tile boundary is touching that
     * tile, not inside it, and testing it as inside makes a character of
     * exactly tile width unable to fit down a corridor of exactly tile width.
     */
    public boolean overlaps(float x, float y, float w, float h) {
        int x0 = (int) Math.floor(x / TILE);
        int y0 = (int) Math.floor(y / TILE);
        int x1 = (int) Math.floor((x + w - 1) / TILE);
        int y1 = (int) Math.floor((y + h - 1) / TILE);
        for (int ty = y0; ty <= y1; ty++) {
            for (int tx = x0; tx <= x1; tx++) {
                if (solidTile(tx, ty)) {
                    return true;
                }
            }
        }
        return false;
    }
}
