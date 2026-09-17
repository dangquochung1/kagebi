package com.kagebi.screen.island;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TiledMapTile;
import com.badlogic.gdx.maps.tiled.tiles.AnimatedTiledMapTile;
import com.badlogic.gdx.maps.tiled.tiles.StaticTiledMapTile;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.XmlReader;

/**
 * The sprites the island is dressed with: every person, animal, windmill,
 * barrel and wisp of smoke, as {@code tools/make_island.py} placed them.
 *
 * <p>They are Tiled tile objects in four object layers, and libGDX loads a
 * tile object but never draws one - {@code OrthogonalTiledMapRenderer} skips
 * object layers entirely. So they are drawn here. The tiles themselves come from
 * the loaded map, animations included; the placement is read straight off the
 * file, because the attributes Tiled writes (bottom-left corner, stretched size,
 * clockwise turn) are exactly what the build script wrote and checked its render
 * against, and a second interpretation of them in between is how a sprite ends
 * up a tile off.
 *
 * <p>Which layer a sprite is in says when it is drawn:
 * <ul>
 *   <li>{@code sprites_under} - behind the buildings and the forest;
 *   <li>{@code sprites_ground} - on the ground, under everyone: the shadows;
 *   <li>{@code sprites} - sorted with the player by where each stands;
 *   <li>{@code sprites_top} - over the roofs and canopies: birds on a ridge,
 *       chimney smoke, windmill sails.
 * </ul>
 */
public final class IslandProps {

    public static final String UNDER = "sprites_under";
    public static final String GROUND = "sprites_ground";
    public static final String SORTED = "sprites";
    public static final String TOP = "sprites_top";

    private static final long FLIP_H = 0x80000000L;
    private static final long FLIP_V = 0x40000000L;
    private static final long GID = 0x0FFFFFFFL;

    public final Array<Prop> under = new Array<>();
    public final Array<Prop> ground = new Array<>();
    public final Array<Prop> sorted = new Array<>();
    public final Array<Prop> top = new Array<>();

    /** The object layer a name belongs to, or null for one holding no sprites. */
    public Array<Prop> layer(String name) {
        switch (name) {
            case UNDER:
                return under;
            case GROUND:
                return ground;
            case SORTED:
                return sorted;
            case TOP:
                return top;
            default:
                return null;
        }
    }

    /** Every sprite on the island carrying a property, in file order. */
    public Array<Prop> with(String property) {
        Array<Prop> found = new Array<>();
        for (Array<Prop> layer : new Array[] {under, ground, sorted, top}) {
            for (Prop p : layer) {
                if (p.properties.containsKey(property)) {
                    found.add(p);
                }
            }
        }
        return found;
    }

    /**
     * The island's sprites, their tiles taken from {@code map} and their
     * placement from the file it was loaded from.
     */
    public static IslandProps load(TiledMap map, FileHandle tmx) {
        XmlReader.Element root = new XmlReader().parse(tmx);
        float mapHeight = root.getIntAttribute("height") * root.getIntAttribute("tileheight");
        IslandProps props = new IslandProps();
        for (XmlReader.Element group : root.getChildrenByName("objectgroup")) {
            Array<Prop> into = props.layer(group.getAttribute("name", ""));
            if (into == null) {
                continue;
            }
            for (XmlReader.Element object : group.getChildrenByName("object")) {
                Prop prop = read(map, object, mapHeight);
                if (prop != null) {
                    into.add(prop);
                }
            }
        }
        return props;
    }

    private static Prop read(TiledMap map, XmlReader.Element object, float mapHeight) {
        long raw = Long.parseLong(object.getAttribute("gid", "0"));
        TiledMapTile tile = map.getTileSets().getTile((int) (raw & GID));
        if (tile == null) {
            return null;
        }
        boolean flipH = (raw & FLIP_H) != 0;
        boolean flipV = (raw & FLIP_V) != 0;

        TextureRegion[] frames;
        int[] durations;
        if (tile instanceof AnimatedTiledMapTile) {
            AnimatedTiledMapTile animated = (AnimatedTiledMapTile) tile;
            StaticTiledMapTile[] tiles = animated.getFrameTiles();
            frames = new TextureRegion[tiles.length];
            for (int i = 0; i < tiles.length; i++) {
                frames[i] = tiles[i].getTextureRegion();
            }
            durations = animated.getAnimationIntervals().clone();
        } else {
            frames = new TextureRegion[] {tile.getTextureRegion()};
            durations = new int[] {0};
        }
        if (flipH || flipV) {
            for (int i = 0; i < frames.length; i++) {
                TextureRegion copy = new TextureRegion(frames[i]);
                copy.flip(flipH, flipV);
                frames[i] = copy;
            }
        }

        ObjectMap<String, String> properties = new ObjectMap<>();
        XmlReader.Element list = object.getChildByName("properties");
        if (list != null) {
            for (XmlReader.Element p : list.getChildrenByName("property")) {
                properties.put(p.getAttribute("name"), p.getAttribute("value", ""));
            }
        }

        TextureRegion first = frames[0];
        float width = object.getFloatAttribute("width", first.getRegionWidth());
        float height = object.getFloatAttribute("height", first.getRegionHeight());
        float x = object.getFloatAttribute("x", 0f);
        // Tiled's y is the bottom edge measured down from the top; ours is the
        // same edge measured up from the bottom.
        float y = mapHeight - object.getFloatAttribute("y", 0f);
        float rotation = object.getFloatAttribute("rotation", 0f);
        float foot = y + parseFloat(properties.get("foot"), 0f);
        int start = (int) parseFloat(properties.get("frame"), 0f);
        float speed = parseFloat(properties.get("speed"), 1f);
        return new Prop(object.getAttribute("name", ""), object.getAttribute("type", ""),
                        properties, frames, durations, start, speed,
                        x, y, width, height, rotation, foot, flipH);
    }

    private static float parseFloat(String value, float otherwise) {
        if (value == null || value.isEmpty()) {
            return otherwise;
        }
        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException e) {
            return otherwise;
        }
    }

    /** One placed sprite. */
    public static final class Prop {

        public final String name;
        /** The object's type in Tiled: {@code npc} for a person, empty for a thing. */
        public final String kind;
        public final ObjectMap<String, String> properties;

        private final TextureRegion[] frames;
        private final int[] durations;
        private final int loop;
        /** Milliseconds into the animation the sprite starts, from its {@code frame}. */
        private final int start;
        private final float speed;

        /** The bottom-left corner of the picture before it is turned, y up. */
        public final float x;
        public final float y;
        public final float width;
        public final float height;
        /** Clockwise degrees, as Tiled measures them, about (x, y). */
        public final float rotation;
        /** Where it stands, y up: the bottom row of its drawn pixels. */
        public final float foot;
        /**
         * Drawn mirrored. The pack's people face right as drawn, so a mirrored
         * one faces left - which is the side a fisher's line is in the water.
         */
        public final boolean flipX;

        Prop(String name, String kind, ObjectMap<String, String> properties,
             TextureRegion[] frames, int[] durations, int startFrame, float speed,
             float x, float y, float width, float height, float rotation, float foot,
             boolean flipX) {
            this.name = name;
            this.kind = kind;
            this.properties = properties;
            this.frames = frames;
            this.durations = durations;
            int total = 0;
            int start = 0;
            for (int i = 0; i < durations.length; i++) {
                if (i < startFrame % Math.max(1, durations.length)) {
                    start += durations[i];
                }
                total += durations[i];
            }
            this.loop = total;
            this.start = start;
            this.speed = speed;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.rotation = rotation;
            this.foot = foot;
            this.flipX = flipX;
        }

        /** The middle of the picture, across: where a person in a frame stands. */
        public float centreX() {
            return x + width / 2f;
        }

        /** The frame showing this many seconds into the island's clock. */
        public TextureRegion frame(float seconds) {
            if (frames.length == 1 || loop <= 0 || speed <= 0f) {
                return frames[0];
            }
            long ms = (long) (seconds * 1000f * speed) + start;
            int t = (int) (ms % loop);
            for (int i = 0; i < frames.length; i++) {
                t -= durations[i];
                if (t < 0) {
                    return frames[i];
                }
            }
            return frames[frames.length - 1];
        }

        public void draw(SpriteBatch batch, float seconds) {
            TextureRegion frame = frame(seconds);
            if (rotation == 0f) {
                batch.draw(frame, x, y, width, height);
            } else {
                // libGDX turns counter-clockwise, and about the origin given
                // relative to (x, y) - which here is the corner Tiled turns about.
                batch.draw(frame, x, y, 0f, 0f, width, height, 1f, 1f, -rotation);
            }
        }

        /** Whether any of it can be inside this rectangle, turned or not. */
        public boolean touches(float left, float bottom, float right, float top) {
            float reach = rotation == 0f ? 0f : Math.max(width, height);
            return x - reach < right && x + width + reach > left
                && y - reach < top && y + height + reach > bottom;
        }
    }
}
