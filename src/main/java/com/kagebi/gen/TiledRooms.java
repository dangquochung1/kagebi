package com.kagebi.gen;

import com.badlogic.gdx.maps.MapLayer;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TiledMapTileLayer;

/**
 * Turns a loaded Tiled map into the plain grid collision works on.
 *
 * <p>The layer names are the contract between the map generator and the game:
 * {@code ground} and {@code decor} draw under the actors, {@code walls} and
 * {@code props} block them, {@code overhead} draws on top so a character can
 * walk behind a canopy or an archway. Anything in a blocking layer is solid -
 * there is no per-tile property to forget to set, which is the failure mode
 * this avoids.
 */
public final class TiledRooms {

    public static final String GROUND = "ground";
    public static final String DECOR = "decor";
    public static final String WALLS = "walls";
    public static final String PROPS = "props";
    public static final String OVERHEAD = "overhead";

    /** Layers drawn below the actors, in order. */
    public static final String[] BELOW = {GROUND, DECOR, WALLS, PROPS};
    /** Layers drawn above them. */
    public static final String[] ABOVE = {OVERHEAD};

    private static final String[] BLOCKING = {WALLS, PROPS};

    public static CollisionGrid collision(TiledMap map) {
        int width = RoomTemplate.WIDTH;
        int height = RoomTemplate.HEIGHT;
        for (MapLayer layer : map.getLayers()) {
            if (layer instanceof TiledMapTileLayer) {
                width = ((TiledMapTileLayer) layer).getWidth();
                height = ((TiledMapTileLayer) layer).getHeight();
                break;
            }
        }
        CollisionGrid grid = new CollisionGrid(width, height);
        for (String name : BLOCKING) {
            MapLayer layer = map.getLayers().get(name);
            if (!(layer instanceof TiledMapTileLayer)) {
                continue;
            }
            TiledMapTileLayer tiles = (TiledMapTileLayer) layer;
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    if (tiles.getCell(x, y) != null) {
                        grid.set(x, y, true);
                    }
                }
            }
        }
        return grid;
    }

    /** Indices of the named layers that exist, for a two-pass render. */
    public static int[] layerIndices(TiledMap map, String[] names) {
        int[] found = new int[names.length];
        int n = 0;
        for (String name : names) {
            int index = map.getLayers().getIndex(name);
            if (index >= 0) {
                found[n++] = index;
            }
        }
        int[] out = new int[n];
        System.arraycopy(found, 0, out, 0, n);
        return out;
    }

    private TiledRooms() {}
}
