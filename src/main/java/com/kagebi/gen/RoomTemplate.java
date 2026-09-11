package com.kagebi.gen;

import com.badlogic.gdx.utils.Array;

/**
 * One hand-shaped room, stored as its own .tmx file.
 *
 * <p><b>Every template carries all four doorways.</b> The alternative - a
 * template per combination of open sides - needs fifteen masks times several
 * variants before a floor stops repeating itself, and the extra art buys
 * nothing a sealed-door sprite cannot do at runtime. So the generator picks any
 * template for any position and the screen covers the doors that lead nowhere.
 *
 * <p>Rooms are a fixed {@link #WIDTH} by {@link #HEIGHT} tiles so the camera can
 * snap to one room at a time with no scrolling, the way Binding of Isaac does.
 * That is what makes a 320x180 screen workable: a scrolling camera in a space
 * this small shows almost nothing of the room the player is fighting in.
 */
public final class RoomTemplate {

    /**
     * 20 x 11 tiles is 320 x 176 pixels. The four pixels left over against the
     * 180-pixel screen are letterbox, which the wall art covers; stretching the
     * grid to 12 rows instead would push 12 pixels off-screen.
     */
    public static final int WIDTH = 20;
    public static final int HEIGHT = 11;

    public static final int PIXEL_WIDTH = WIDTH * 16;
    public static final int PIXEL_HEIGHT = HEIGHT * 16;

    /**
     * Tiles of opening in the middle of every wall. The screen needs these to
     * know which tiles to cover when it seals a door that leads nowhere;
     * {@code RoomCatalogTest} holds them to what tools/make_maps.py actually
     * generated, so the two cannot quietly disagree.
     */
    public static final int DOOR_SPAN = 3;
    /** First tile column of the top and bottom openings: columns 8, 9, 10. */
    public static final int DOOR_X = (WIDTH - DOOR_SPAN) / 2;
    /**
     * First tile row of the left and right openings: rows 4, 5, 6. The same
     * counted from the top or the bottom, because 11 rows leave 4 either side,
     * so nobody has to ask which way up this is.
     */
    public static final int DOOR_Y = (HEIGHT - DOOR_SPAN) / 2;

    public final String id;
    /** Which floors may use it: the biome name from {@code FloorDef.biome}. */
    public final String biome;
    /** Kinds this layout suits. A boss arena is not a shop. */
    public final Array<RoomKind> kinds;
    /** Path under {@code assets/maps/rooms/}, e.g. {@code ruins/normal_03.tmx}. */
    public final String path;
    public final Array<SpawnPoint> spawns;

    public RoomTemplate(String id, String biome, Array<RoomKind> kinds,
                        String path, Array<SpawnPoint> spawns) {
        this.id = id;
        this.biome = biome;
        this.kinds = kinds;
        this.path = path;
        this.spawns = spawns;
    }

    public boolean suits(RoomKind kind) {
        return kinds.contains(kind, false);
    }

    @Override
    public String toString() {
        return "RoomTemplate(" + id + ")";
    }
}
