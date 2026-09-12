package com.kagebi.integration;

import java.io.File;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.XmlReader;
import com.kagebi.gen.CollisionGrid;
import com.kagebi.gen.TiledRooms;

/**
 * A room's collision grid, read from the .tmx without a GL context.
 *
 * <p>The game gets this from {@code TiledRooms.collision} over a map loaded by
 * {@code TmxMapLoader}, which builds textures and therefore needs a window. A
 * headless test that wants the player to bump into the same walls has to read
 * the layers itself - the same way {@code RoomCatalog} already reads the object
 * layer, and for the same reason.
 *
 * <p>Which layers block is asked of {@link TiledRooms#blocks} rather than
 * written down again here. A second copy of that list is how a decorative layer
 * silently becomes a wall in one reader and not the other, and the bug would
 * show up as the bot getting stuck on a skull in a test while a person walks
 * straight over it in the game.
 */
final class RoomCollision {

    static CollisionGrid of(String tmxPath) {
        XmlReader.Element map = new XmlReader().parse(new FileHandle(new File(tmxPath)));
        int width = map.getIntAttribute("width");
        int height = map.getIntAttribute("height");
        CollisionGrid grid = new CollisionGrid(width, height);
        for (XmlReader.Element layer : map.getChildrenByName("layer")) {
            if (!TiledRooms.blocks(layer.getAttribute("name", ""))) {
                continue;
            }
            String csv = layer.getChildByName("data").getText().replace("\n", "");
            String[] cells = csv.split(",");
            for (int i = 0; i < cells.length && i < width * height; i++) {
                String cell = cells[i].trim();
                if (cell.isEmpty()) {
                    continue;
                }
                // The top four bits are Tiled's flip flags, not part of the id.
                if ((Long.parseLong(cell) & 0x0FFFFFFFL) != 0) {
                    // y-down in the file, y-up in the grid: the same flip
                    // RoomCatalog does for objects, for the same reason.
                    grid.set(i % width, height - 1 - i / width, true);
                }
            }
        }
        return grid;
    }

    private RoomCollision() {}
}
