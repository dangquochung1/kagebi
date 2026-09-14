package com.kagebi.gen;

import com.badlogic.gdx.maps.MapLayer;
import com.badlogic.gdx.maps.MapObject;
import com.badlogic.gdx.maps.objects.RectangleMapObject;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TiledMapTileLayer;
import com.badlogic.gdx.math.Rectangle;

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
 * <p><b>A role may be spread over several layers.</b> A layer counts as
 * {@code props} if it is called {@code props} or {@code props_something}, and
 * the same for every other name here. The generated rooms use one layer per
 * role and always will; the village does not, because it is assembled from an
 * art pack whose scene is twenty-two layers of grass over grass over road, and
 * flattening those into one would have thrown away 256 tiles of the ground
 * detail that is the whole look of it. Within a role the map's own order wins,
 * so {@code props_fence} drawn after {@code props_trees} in Tiled is drawn
 * after it here.
 *
 * <p>The underscore is required rather than a bare prefix: {@code groundwork}
 * should not quietly join the ground pass because it starts with the right six
 * letters.
 *
 * <p><b>Why two non-blocking layers under the actors.</b> {@code dressing} is
 * the generator's and {@code decor} is the human's, and they exist separately
 * because the generator has scenery to place that is drawn with transparency -
 * bones on a crypt floor - and nowhere honest to put it otherwise. Writing it
 * into {@code decor} would mean a regenerate cannot tell a person's work from
 * its own; making it blocking would mean a skull stops the player. Drawing
 * order follows ownership: {@code decor} is painted over {@code dressing}, so a
 * tile placed by hand always wins.
 *
 * <p><b>Or solid where the art is.</b> A map may also carry an object layer
 * named {@link #COLLISION}, whose rectangles are solid to the pixel wherever
 * they are - finer than any tile. The village and the house block this way and
 * almost no other: their art pack draws a partition three pixels thick along
 * the edge of a tile, and puts a rug and the table standing on it in the same
 * layer, so what stops the player cannot be read off tiles or layer names at
 * all. {@code tools/make_village.py} writes the rectangles from the art itself.
 */
public final class TiledRooms {

    public static final String GROUND = "ground";
    /** Generated, non-blocking overlay: scattered debris and the like. */
    public static final String DRESSING = "dressing";
    public static final String DECOR = "decor";
    public static final String WALLS = "walls";
    public static final String PROPS = "props";
    public static final String OVERHEAD = "overhead";

    /** The object layer whose rectangles are solid. See the class comment. */
    public static final String COLLISION = "collision";

    /** Layers drawn below the actors, in order. */
    public static final String[] BELOW = {GROUND, DRESSING, DECOR, WALLS, PROPS};
    /** Layers drawn above them. */
    public static final String[] ABOVE = {OVERHEAD};

    private static final String[] BLOCKING = {WALLS, PROPS};

    /** Whether a layer plays a role: named for it, or {@code role_something}. */
    public static boolean plays(String layer, String role) {
        return layer != null
            && (layer.equals(role) || layer.startsWith(role + "_"));
    }

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
            if (plays(layer, name)) {
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
        for (MapLayer layer : map.getLayers()) {
            if (!(layer instanceof TiledMapTileLayer) || !blocks(layer.getName())) {
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
        MapLayer shapes = map.getLayers().get(COLLISION);
        if (shapes != null) {
            for (MapObject object : shapes.getObjects()) {
                if (object instanceof RectangleMapObject) {
                    // Already y-up and measured from the bottom edge: the
                    // loader flips object coordinates as it reads them.
                    Rectangle r = ((RectangleMapObject) object).getRectangle();
                    grid.fill(r.x, r.y, r.width, r.height);
                }
            }
        }
        return grid;
    }

    /**
     * Indices of the layers playing these roles, for a two-pass render.
     *
     * <p>Roles in the order given, and within one role the order the map puts
     * them in - which is the order a person stacked them in Tiled, and the only
     * one that reproduces what they saw there.
     */
    public static int[] layerIndices(TiledMap map, String[] roles) {
        com.badlogic.gdx.utils.IntArray found = new com.badlogic.gdx.utils.IntArray();
        for (String role : roles) {
            for (int i = 0; i < map.getLayers().size(); i++) {
                if (plays(map.getLayers().get(i).getName(), role)) {
                    found.add(i);
                }
            }
        }
        return found.toArray();
    }

    private TiledRooms() {}
}
