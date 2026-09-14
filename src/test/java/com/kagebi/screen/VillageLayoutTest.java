package com.kagebi.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import com.kagebi.assets.Assets;
import com.kagebi.gen.TiledRooms;

/**
 * What {@code village.tmx} and {@code home.tmx} promise the screens that read
 * them.
 *
 * <p>Parsed as text rather than through libGDX, which would want a GL context
 * for eleven tileset images. What cannot be checked here is what the place
 * looks like, which is what {@code --screen hub} and {@code --screen home} are
 * for; what can be checked is the half of a map that never draws - its layer
 * names and its markers - and that half fails silently every time.
 */
class VillageLayoutTest {

    private static final Pattern LAYER = Pattern.compile("<layer[^>]*name=\"([^\"]+)\"");
    private static final Pattern OBJECT = Pattern.compile(
        "<object[^>]*name=\"([^\"]*)\"[^>]*x=\"([0-9.-]+)\"[^>]*y=\"([0-9.-]+)\"");
    private static final Pattern SIZE = Pattern.compile(
        "<map[^>]*\\bwidth=\"(\\d+)\"\\s+height=\"(\\d+)\"");

    private static String read(String path) throws IOException {
        return new String(Files.readAllBytes(new File(path).toPath()), StandardCharsets.UTF_8);
    }

    private static List<String> layers(String tmx) {
        List<String> names = new ArrayList<>();
        Matcher m = LAYER.matcher(tmx);
        while (m.find()) {
            names.add(m.group(1));
        }
        return names;
    }

    /**
     * Every layer plays one of the six roles the renderer knows about.
     *
     * <p>The village is assembled from an art pack whose scene is twenty-two
     * layers with its own names - {@code Grass_details3}, {@code House_roof} -
     * and {@code tools/make_village.py} renames them all. One that slipped
     * through would be loaded, held in memory, and never drawn: the map would
     * simply be missing its roof, or its birds, with nothing anywhere to say
     * so. This is the only thing that would notice.
     */
    @Test
    void everyLayerInBothMapsPlaysARoleTheRendererDraws() throws IOException {
        List<String> roles = new ArrayList<>();
        roles.addAll(Arrays.asList(TiledRooms.BELOW));
        roles.addAll(Arrays.asList(TiledRooms.ABOVE));
        for (String path : new String[] {Assets.MAP_VILLAGE, Assets.MAP_HOME}) {
            List<String> orphans = new ArrayList<>();
            for (String name : layers(read(path))) {
                boolean played = false;
                for (String role : roles) {
                    played |= TiledRooms.plays(name, role);
                }
                if (!played) {
                    orphans.add(name);
                }
            }
            assertTrue(orphans.isEmpty(), path + " has layers nothing draws: " + orphans);
        }
    }

    /**
     * Both maps have something solid in them.
     *
     * <p>The interior's collision is derived rather than authored - the pack's
     * own "Walls" layer is a mask over everything that is not floor, and the
     * tiles are sorted into wall and floor by where they sit on the sheet - so
     * an empty walls layer is a real possible outcome of that rule going wrong,
     * and the symptom is a player who walks out through the side of the house.
     */
    @Test
    void bothMapsHaveWallsInThem() throws IOException {
        for (String path : new String[] {Assets.MAP_VILLAGE, Assets.MAP_HOME}) {
            boolean blocking = false;
            for (String name : layers(read(path))) {
                blocking |= TiledRooms.blocks(name);
            }
            assertTrue(blocking, path + " has no blocking layer; nothing would stop the player");
        }
    }

    /** The layer a person's hand-drawn decoration survives in. */
    @Test
    void bothMapsKeepADecorLayerForHandEditing() throws IOException {
        for (String path : new String[] {Assets.MAP_VILLAGE, Assets.MAP_HOME}) {
            assertTrue(layers(read(path)).contains(TiledRooms.DECOR),
                path + " lost its decor layer, which is where hand edits live");
        }
    }

    /**
     * The village names every marker {@link HubScreen} looks for, exactly once,
     * and each is on the map.
     *
     * <p>A missing one is not a crash - the screen falls back to a constant -
     * so nothing else would ever report it, and the village would quietly stop
     * being the thing that decides where its own people stand.
     */
    @Test
    void theVillageNamesEveryMarkerTheHubLooksFor() throws IOException {
        String tmx = read(Assets.MAP_VILLAGE);
        Matcher size = SIZE.matcher(tmx);
        assertTrue(size.find(), "village.tmx has no map size");
        int width = Integer.parseInt(size.group(1)) * 16;
        int height = Integer.parseInt(size.group(2)) * 16;

        Set<String> wanted = new HashSet<>(Arrays.asList(
            "entry", "gate", "door", "villager1", "villager2", "villager3"));
        Set<String> seen = new HashSet<>();
        Matcher m = OBJECT.matcher(tmx);
        while (m.find()) {
            String name = m.group(1);
            if (!wanted.contains(name)) {
                continue;
            }
            assertTrue(seen.add(name), name + " is named twice");
            float x = Float.parseFloat(m.group(2));
            float y = Float.parseFloat(m.group(3));
            assertTrue(x >= 0 && x <= width, name + " x is " + x + ", off a " + width + " map");
            assertTrue(y >= 0 && y <= height, name + " y is " + y + ", off a " + height + " map");
        }
        wanted.removeAll(seen);
        assertTrue(wanted.isEmpty(), "village.tmx never names " + wanted);
    }

    /** The home names the one marker it needs: the way back out. */
    @Test
    void theHomeNamesItsDoor() throws IOException {
        Set<String> seen = new HashSet<>();
        Matcher m = OBJECT.matcher(read(Assets.MAP_HOME));
        while (m.find()) {
            seen.add(m.group(1));
        }
        assertTrue(seen.contains("door"), "home.tmx has no door, so it cannot be left");
        assertTrue(seen.contains("entry"), "home.tmx has no entry, so it cannot be arrived in");
    }

    /**
     * Neither map is infinite.
     *
     * <p>The art pack ships both scenes as infinite maps, which libGDX cannot
     * read: {@code BaseTmxMapLoader} has no notion of a {@code <chunk>}, so it
     * parses the file, finds no tile data, and draws nothing at all. That is
     * the failure this whole conversion exists to avoid, and it is one
     * attribute away at any time.
     */
    @Test
    void neitherMapIsInfinite() throws IOException {
        for (String path : new String[] {Assets.MAP_VILLAGE, Assets.MAP_HOME}) {
            String tmx = read(path);
            assertFalse(tmx.contains("infinite=\"1\""),
                path + " is an infinite map; libGDX would load it as empty");
            assertFalse(tmx.contains("<chunk"), path + " still has chunked data");
        }
    }

    /** A role spans a family of layers now, but only across an underscore. */
    @Test
    void aRoleClaimsItsOwnNameAndItsUnderscoredFamily() {
        assertTrue(TiledRooms.plays("props", "props"));
        assertTrue(TiledRooms.plays("props_fence", "props"));
        assertFalse(TiledRooms.plays("propsfence", "props"),
            "a bare prefix would let any name starting with the right letters in");
        assertFalse(TiledRooms.plays("ground", "props"));
        assertEquals(false, TiledRooms.plays(null, "props"));
    }
}
