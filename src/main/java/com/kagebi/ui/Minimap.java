package com.kagebi.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.kagebi.Dir;
import com.kagebi.assets.Assets;
import com.kagebi.gen.FloorLayout;
import com.kagebi.gen.Room;
import com.kagebi.gen.RoomKind;

/**
 * The floor, four pixels to a room.
 *
 * <p>Drawn from a single tinted 1x1 region rather than from art. At this size a
 * drawn tile would be three pixels of detail and one of outline, and the thing
 * the player actually reads off a minimap is <em>colour</em>: where I am, where
 * I have been, where the boss is.
 *
 * <p>It windows rather than scales. Fitting a whole floor into the corner of a
 * 320x180 screen means the cell size shrinks as the generator gets more
 * ambitious, and a two-pixel room is unreadable; scrolling a fixed-size window
 * around the current room stays legible for any floor shape. That also means
 * nothing here assumes the placeholder generator's single row of rooms.
 */
public final class Minimap {

    /** 4 pixels of room and 1 of gutter. Below this a room stops reading. */
    public static final int CELL = 5;
    /** Enlarged, for the full-screen map. */
    public static final int CELL_LARGE = 9;

    private static final Color CURRENT = new Color(0xffad55ff);
    private static final Color VISITED = new Color(0xeecf9bff);
    /** Seen through a door but not entered. */
    private static final Color KNOWN = new Color(0x9b513cff);
    private static final Color BOSS = new Color(0xd94a3aff);
    private static final Color MARK = new Color(0x131b1bff);
    private static final Color FRAME = new Color(0x46503cff);
    /** Translucent: the room under the corner is still part of the room. */
    private static final Color GROUND = new Color(0x0c0a10a8);

    private final TextureRegion pixel;

    public Minimap(Skin skin) {
        this.pixel = skin.getRegion(Assets.Ui.PIXEL);
    }

    /** How many cells of a grid fit in a box, never more than the grid has. */
    public static int visibleCells(int gridSize, int boxPixels, int cell) {
        return Math.max(1, Math.min(gridSize, boxPixels / cell));
    }

    /**
     * The first grid column or row to draw, so that the current room is as
     * central as the grid's edges allow. Clamped rather than wrapped: a map
     * that scrolls past its own edge reads as a bug.
     */
    public static int windowOrigin(int current, int gridSize, int visible) {
        int origin = current - visible / 2;
        return Math.max(0, Math.min(origin, gridSize - visible));
    }

    /**
     * Draws the floor into a box whose bottom-left corner is (x, y). The box is
     * filled and framed first, so the map reads over any tileset underneath.
     */
    public void draw(SpriteBatch batch, FloorLayout layout, Room current,
                     int x, int y, int boxW, int boxH, int cell) {
        fill(batch, GROUND, x, y, boxW, boxH);
        frame(batch, FRAME, x, y, boxW, boxH);
        if (layout == null) {
            return;
        }

        int cols = visibleCells(layout.gridW(), boxW - 4, cell);
        int rows = visibleCells(layout.gridH(), boxH - 4, cell);
        int gx0 = windowOrigin(current == null ? 0 : current.gx, layout.gridW(), cols);
        int gy0 = windowOrigin(current == null ? 0 : current.gy, layout.gridH(), rows);

        // Each cell is `cell` of pitch and one pixel narrower than that, so the
        // last gutter is not drawn. Centre the result on whole pixels: integer
        // division is deliberate, since an odd leftover halved is the one way a
        // HUD ends up on a half pixel.
        int usedW = cols * cell - 1;
        int usedH = rows * cell - 1;
        int left = x + 2 + (boxW - 4 - usedW) / 2;
        int bottom = y + 2 + (boxH - 4 - usedH) / 2;

        for (int gy = 0; gy < rows; gy++) {
            for (int gx = 0; gx < cols; gx++) {
                Room room = layout.roomAt(gx0 + gx, gy0 + gy);
                if (room == null) {
                    continue;
                }
                boolean known = room.visited || adjacentVisited(room);
                if (!known) {
                    continue;
                }
                int rx = left + gx * cell;
                int ry = bottom + gy * cell;
                int size = cell - 1;
                Color body = room == current ? CURRENT : room.visited ? VISITED : KNOWN;
                fill(batch, body, rx, ry, size, size);
                markKind(batch, room, rx, ry, size);
            }
        }
        batch.setColor(Color.WHITE);
    }

    /**
     * A room the player has not entered but can see the door to. Showing these
     * is the difference between a map that records where you went and one that
     * tells you where you can go.
     */
    private static boolean adjacentVisited(Room room) {
        for (Dir d : Dir.ALL) {
            Room n = room.neighbour(d);
            if (n != null && n.visited) {
                return true;
            }
        }
        return false;
    }

    /** A dot for the rooms worth finding, once they have been found. */
    private void markKind(SpriteBatch batch, Room room, int rx, int ry, int size) {
        if (room.kind != RoomKind.BOSS && room.kind != RoomKind.EXIT) {
            return;
        }
        int dot = Math.max(1, size / 2);
        int inset = (size - dot) / 2;
        fill(batch, room.kind == RoomKind.BOSS ? BOSS : MARK,
             rx + inset, ry + inset, dot, dot);
    }

    private void fill(SpriteBatch batch, Color color, int x, int y, int w, int h) {
        batch.setColor(color);
        batch.draw(pixel, x, y, w, h);
    }

    private void frame(SpriteBatch batch, Color color, int x, int y, int w, int h) {
        batch.setColor(color);
        batch.draw(pixel, x, y, w, 1);
        batch.draw(pixel, x, y + h - 1, w, 1);
        batch.draw(pixel, x, y, 1, h);
        batch.draw(pixel, x + w - 1, y, 1, h);
    }
}
