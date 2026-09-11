package com.kagebi.gen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.XmlReader;
import com.kagebi.assets.Assets;

/**
 * Every room template on disk, read straight out of the .tmx XML.
 *
 * <p>Deliberately not via {@code TmxMapLoader}: that builds textures, so it
 * needs a GL context, and the generator has to be runnable in a plain JUnit
 * test over a thousand seeds. Reading the object layer as XML costs thirty
 * lines and keeps generation testable, which is the trade worth making.
 *
 * <p>The biome is the folder name, so adding a room is adding a file - there is
 * no index to keep in step, and no way for one to drift.
 */
public final class RoomCatalog {

    /** Object types in the spawns layer that are not enemies or chests. */
    private static final String LAYER = "spawns";

    public static Array<RoomTemplate> load() {
        return load(Gdx.files.internal(Assets.ROOMS_DIR));
    }

    public static Array<RoomTemplate> load(FileHandle roomsDir) {
        Array<RoomTemplate> out = new Array<>();
        if (!roomsDir.exists()) {
            return out;
        }
        for (FileHandle biomeDir : roomsDir.list()) {
            if (!biomeDir.isDirectory()) {
                continue;
            }
            for (FileHandle file : biomeDir.list(".tmx")) {
                out.add(read(biomeDir.name(), file));
            }
        }
        out.sort((a, b) -> a.id.compareTo(b.id));
        return out;
    }

    private static RoomTemplate read(String biome, FileHandle file) {
        XmlReader.Element map = new XmlReader().parse(file);
        int height = map.getIntAttribute("height", RoomTemplate.HEIGHT);
        int tileHeight = map.getIntAttribute("tileheight", 16);

        Array<SpawnPoint> spawns = new Array<>();
        for (XmlReader.Element group : map.getChildrenByName("objectgroup")) {
            if (!LAYER.equals(group.getAttribute("name", ""))) {
                continue;
            }
            for (XmlReader.Element object : group.getChildrenByName("object")) {
                SpawnPoint.Kind kind = kindOf(object.getAttribute("type", ""));
                if (kind == null) {
                    continue;
                }
                int x = Math.round(object.getFloatAttribute("x", 0f));
                // Tiled measures down from the top; everything downstream is y-up.
                int y = height * tileHeight - Math.round(object.getFloatAttribute("y", 0f));
                String tag = object.getAttribute("name", "");
                spawns.add(new SpawnPoint(kind, x, y, tag.isEmpty() ? null : tag));
            }
        }

        String id = biome + "/" + file.nameWithoutExtension();
        return new RoomTemplate(id, biome, kindsFor(file.nameWithoutExtension()),
                                Assets.ROOMS_DIR + biome + "/" + file.name(), spawns);
    }

    private static SpawnPoint.Kind kindOf(String type) {
        for (SpawnPoint.Kind k : SpawnPoint.Kind.values()) {
            if (k.name().equalsIgnoreCase(type)) {
                return k;
            }
        }
        return null;
    }

    /**
     * Which room kinds a template suits, taken from its file name prefix:
     * {@code boss_01.tmx} is a boss arena, {@code normal_03.tmx} is a fight.
     * Encoding it in the name rather than in a property keeps the whole catalog
     * legible from a directory listing.
     */
    private static Array<RoomKind> kindsFor(String name) {
        Array<RoomKind> kinds = new Array<>();
        String prefix = name.contains("_") ? name.substring(0, name.indexOf('_')) : name;
        for (RoomKind k : RoomKind.values()) {
            if (k.name().equalsIgnoreCase(prefix)) {
                kinds.add(k);
            }
        }
        if (kinds.isEmpty()) {
            // A room with no recognised prefix is a plain fight room, which is
            // the right default: it is what most of them are.
            kinds.add(RoomKind.NORMAL);
            kinds.add(RoomKind.START);
        }
        return kinds;
    }

    private RoomCatalog() {}
}
