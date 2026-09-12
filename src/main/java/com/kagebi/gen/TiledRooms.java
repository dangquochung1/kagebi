package com.kagebi.gen;

import com.badlogic.gdx.maps.MapLayer;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TiledMapTileLayer;

/**
 * Turns a loaded Tiled map into the plain grid collision works on.
 *
 * <p>The layer names are the contract between the map generator and the game:
 * {@code ground}, {@code dressing} and {@code decor} draw under the actors,
 * {@code walls} and {@code props} block them, {@code overhead} draws on top so
 * a character can walk behind a canopy or an archway. Anything in a blocking
 * layer is solid - there is no per-tile property to forget to set, which is the
 * failure mode this avoids.
 *
 * <p><b>Why two non-blocking layers under the actors.</b> {@code dressing} is
 * the generator's and {@code decor} is the human's, and they exist separately
 * because the generator has scenery to place that is drawn with transparency -
 * bones on a crypt floor - and nowhere honest to put it otherwise. Writing it
 * into {@code decor} would mean a regenerate cannot tell a person's work from
 * its own; making it blocking would mean a skull stops the player. Drawing
 * order follows ownership: {@code decor} is painted over {@code dressing}, so a
 * tile placed by hand always wins.
 */
public final class TiledRooms {

    public static final String GROUND = "ground";
    /** Generated, non-blocking overlay: scattered debris and the like. */
    public static final String DRESSING = "dressing";
    public static final String DECOR = "decor";
    public static final String WALLS = "walls";
    public static final String PROPS = "props";
    public static final String OVERHEAD = "overhead";

    /** Layers drawn below the actors, in order. */
    public static final String[] BELOW = {GROUND, DRESSING, DECOR, WALLS, PROPS};
    /** Layers drawn above them. */
    public static final String[] ABOVE = {OVERHEAD};

    private static final String[] BLOCKING = {WALLS, PROPS};

    /**
     * Whether a tile in this layer stops the player.
     *
     * <p>Public because it is the whole contract, and a second copy of the list
     * written down somewhere else is how a decorative layer quietly becomes a
     * wall: put {@code dressing} in here and every skull on the crypt floor
     * turns into an invisible obstacle, with nothing on screen to say so.
     */
    public static boolean blocks(String layer) {
        for (String name : BLOCKING) {
            if (name.equals(layer)) {
                return true;
            }
        }
        return false;
    }

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
