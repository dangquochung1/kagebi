package com.kagebi.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import com.kagebi.assets.Assets;
import com.kagebi.entity.Player;
import com.kagebi.entity.Projectile;
import com.kagebi.gen.CollisionGrid;
import com.kagebi.gen.TiledRooms;
import com.kagebi.integration.RoomCollision;

/**
 * What {@code village.tmx} and {@code home.tmx} promise the screens that read
 * them, and the player who walks them.
 *
 * <p>Parsed as text rather than through libGDX, which would want a GL context
 * for eleven tileset images. What cannot be checked here is what the place
 * looks like, which is what {@code --screen hub} and {@code --screen home} are
 * for; what can be checked is the half of a map that never draws - its layer
 * names, its markers and what blocks - and that half fails silently every time.
 *
 * <p>The walking tests move a body the size of the player's over the collision
 * grid the game builds, two pixels at a time. Each of them is somewhere a
 * player once could not get to.
 */
class VillageLayoutTest {

    private static final Pattern LAYER = Pattern.compile("<layer[^>]*name=\"([^\"]+)\"");
    private static final Pattern OBJECT = Pattern.compile(
        "<object[^>]*name=\"([^\"]*)\"[^>]*x=\"([0-9.-]+)\"[^>]*y=\"([0-9.-]+)\"");
    private static final Pattern SIZE = Pattern.compile(
        "<map[^>]*\\bwidth=\"(\\d+)\"\\s+height=\"(\\d+)\"");
    private static final Pattern COLLISION_LAYER = Pattern.compile(
        "<objectgroup[^>]*name=\"" + TiledRooms.COLLISION + "\"[^>]*>(.*?)</objectgroup>",
        Pattern.DOTALL);

    /** How far apart the walking tests try positions, in pixels. */
    private static final int STEP = 2;
    private static final float HALF = Player.BODY / 2f;

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
     * Both maps say what blocks, in the one layer that can say it to the pixel.
     *
     * <p>An empty or missing one is a real possible outcome of the generator's
     * rules going wrong, and the symptom is a player who walks through the
     * furniture and out through the side of the house.
     */
    @Test
    void bothMapsCarryACollisionLayer() throws IOException {
        for (String path : new String[] {Assets.MAP_VILLAGE, Assets.MAP_HOME}) {
            Matcher layer = COLLISION_LAYER.matcher(read(path));
            assertTrue(layer.find(), path + " has no collision layer, so nothing in it blocks");
            int shapes = layer.group(1).split("<object ").length - 1;
            assertTrue(shapes > 20, path + " has only " + shapes + " collision rectangles");
        }
    }

    /**
     * The pack's art blocks by its pixels and never by whole tiles. Blocking
     * whole tiles is how two tiles with nothing drawn on them became a wall
     * across the front of the house, and how a gate drawn as two thin posts
     * became a fence with no gap. The villagers' houses are the exception:
     * they come from the old tileset and are solid rectangles anyway.
     */
    @Test
    void onlyTheOldHousesBlockByWholeTiles() throws IOException {
        List<String> village = new ArrayList<>();
        for (String name : layers(read(Assets.MAP_VILLAGE))) {
            if (TiledRooms.blocks(name)) {
                village.add(name);
            }
        }
        assertEquals(List.of("props_village"), village);
        for (String name : layers(read(Assets.MAP_HOME))) {
            assertFalse(TiledRooms.blocks(name), "home.tmx blocks by whole tiles in " + name);
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
            "entry", "gate", "door", "gateway", "villager1", "villager2", "villager3"));
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

    /** The home names the markers it needs: the way in and out, and the rug. */
    @Test
    void theHomeNamesItsDoorAndItsRug() throws IOException {
        Set<String> seen = markers(read(Assets.MAP_HOME)).keySet();
        assertTrue(seen.contains("door"), "home.tmx has no door, so it cannot be left");
        assertTrue(seen.contains("entry"), "home.tmx has no entry, so it cannot be arrived in");
        assertTrue(seen.contains("rug"), "home.tmx has no rug, so --screen home --page 2 has nowhere to stand");
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

    // ---- walking the village -------------------------------------------------

    /**
     * Out of the front door, through the gap in the garden fence, and down to
     * the torii. The gap is drawn as two posts, and while each post blocked its
     * whole tile there was no gap at all.
     */
    @Test
    void aNinjaWalksFromTheFrontDoorOutThroughTheGardenGateToTheTorii() throws IOException {
        Map<String, float[]> at = markers(read(Assets.MAP_VILLAGE));
        Set<Long> reach = walk(villageGrid(at), at.get("entry"));
        assertTrue(reaches(reach, at.get("gateway"), 3), "the garden gateway, the only way out");
        assertTrue(reaches(reach, at.get("gate"), HubScreen.GATE_RANGE), "the torii");
    }

    @Test
    void everyoneInTheVillageCanBeWalkedUpTo() throws IOException {
        Map<String, float[]> at = markers(read(Assets.MAP_VILLAGE));
        Set<Long> reach = walk(villageGrid(at), at.get("entry"));
        assertTrue(reaches(reach, at.get("door"), HubScreen.DOOR_RANGE), "the front door");
        for (int i = 1; i <= 3; i++) {
            float[] feet = at.get("villager" + i);
            // HubScreen measures the distance to a point just above the feet.
            float[] talk = {feet[0], feet[1] + 8};
            assertTrue(reaches(reach, talk, HubScreen.TALK_RANGE), "villager " + i);
        }
    }

    /**
     * The front of the house, left of the door. Two tiles there had nothing
     * drawn on them and blocked anyway, so a player could not walk that way and
     * a kunai thrown that way vanished a step from their hand.
     */
    @Test
    void theHouseFrontIsOpenLeftOfTheDoorToAPlayerAndToAKunai() throws IOException {
        Map<String, float[]> at = markers(read(Assets.MAP_VILLAGE));
        CollisionGrid grid = villageGrid(at);
        float[] door = at.get("door");
        float[] left = {door[0] - 3 * CollisionGrid.TILE, door[1]};
        assertTrue(reaches(walk(grid, at.get("entry")), left, 3), "walking left along the front");

        float half = Projectile.BODY / 2f;
        for (float x = door[0]; x >= left[0]; x -= 1f) {
            assertFalse(grid.overlaps(x - half, door[1] - half, Projectile.BODY, Projectile.BODY),
                "a kunai thrown left from the doormat stops " + (door[0] - x) + "px out");
        }
    }

    // ---- walking the house ---------------------------------------------------

    /**
     * From the door onto the rug, and across a hall that is actually a hall.
     *
     * <p>The first version of the house let a player reach 24 tiles of floor:
     * the rug blocked, the walls three pixels thick blocked sixteen, and the
     * corner by the red rug could not be walked into at all.
     */
    @Test
    void theHouseIsWalkedFromTheDoorOntoTheRugAndAcrossTheHall() throws IOException {
        Map<String, float[]> at = markers(read(Assets.MAP_HOME));
        Set<Long> reach = walk(RoomCollision.of(Assets.MAP_HOME), at.get("door"));
        assertTrue(reaches(reach, at.get("rug"), 3), "the rug beside the table");
        double floor = floorTiles(reach);
        assertTrue(floor >= 40, "only " + floor + " tiles of the house can be walked on");
    }

    /** Nothing indoors is tall enough to walk behind, so nothing draws over the player. */
    @Test
    void nothingDrawsOverTheNinjaIndoors() throws IOException {
        for (String name : layers(read(Assets.MAP_HOME))) {
            assertFalse(TiledRooms.plays(name, TiledRooms.OVERHEAD),
                name + " would be drawn over the player");
        }
    }

    /**
     * The table stands on the rug, so the rug is drawn first. The other way
     * round, the rug's upper rows covered half the table and its chairs.
     */
    @Test
    void theRugIsDrawnBeforeTheTableStandingOnIt() throws IOException {
        List<String> names = layers(read(Assets.MAP_HOME));
        int rug = names.indexOf("dressing_objects1");
        int table = names.indexOf("dressing_objects2");
        assertTrue(rug >= 0 && table >= 0, "home.tmx lost its furniture layers: " + names);
        assertTrue(rug < table, "the rug would be drawn over the table: " + names);
    }

    // ---- helpers -------------------------------------------------------------

    /** Named markers, turned y-up the way libGDX turns them for the screens. */
    private static Map<String, float[]> markers(String tmx) {
        Matcher size = SIZE.matcher(tmx);
        assertTrue(size.find(), "no map size");
        float height = Integer.parseInt(size.group(2)) * CollisionGrid.TILE;
        Map<String, float[]> at = new HashMap<>();
        Matcher m = OBJECT.matcher(tmx);
        while (m.find()) {
            at.put(m.group(1), new float[] {
                Float.parseFloat(m.group(2)), height - Float.parseFloat(m.group(3))});
        }
        return at;
    }

    /** The village's grid as HubScreen builds it: the map, and a villager on each of their tiles. */
    private static CollisionGrid villageGrid(Map<String, float[]> at) {
        CollisionGrid grid = RoomCollision.of(Assets.MAP_VILLAGE);
        for (int i = 1; i <= 3; i++) {
            float[] feet = at.get("villager" + i);
            grid.set(Math.round(feet[0]) / CollisionGrid.TILE,
                     Math.round(feet[1]) / CollisionGrid.TILE, true);
        }
        return grid;
    }

    /** Every point a player's body can walk to from a start, STEP pixels apart, y-up. */
    private static Set<Long> walk(CollisionGrid grid, float[] start) {
        int sx = Math.round(start[0]);
        int sy = Math.round(start[1]);
        assertTrue(fits(grid, sx, sy), "a player does not fit where they start, " + sx + "," + sy);
        Set<Long> seen = new HashSet<>();
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        seen.add(key(sx, sy));
        queue.add(new int[] {sx, sy});
        int[][] moves = {{STEP, 0}, {-STEP, 0}, {0, STEP}, {0, -STEP}};
        while (!queue.isEmpty()) {
            int[] p = queue.poll();
            for (int[] move : moves) {
                int x = p[0] + move[0];
                int y = p[1] + move[1];
                if (fits(grid, x, y) && seen.add(key(x, y))) {
                    queue.add(new int[] {x, y});
                }
            }
        }
        return seen;
    }

    /** The test Entity.moveBy makes before letting a player take a step. */
    private static boolean fits(CollisionGrid grid, int x, int y) {
        return !grid.overlaps(x - HALF, y - HALF, Player.BODY, Player.BODY);
    }

    private static long key(int x, int y) {
        return ((long) x << 32) | (y & 0xffffffffL);
    }

    private static boolean reaches(Set<Long> reach, float[] target, float radius) {
        for (long k : reach) {
            float dx = (int) (k >> 32) - target[0];
            float dy = (int) k - target[1];
            if (dx * dx + dy * dy < radius * radius) {
                return true;
            }
        }
        return false;
    }

    /** Tiles of floor a body covers, standing at every one of these points. */
    private static double floorTiles(Set<Long> reach) {
        int cell = CollisionGrid.CELL;
        int half = (int) HALF;
        Set<Long> covered = new HashSet<>();
        for (long k : reach) {
            int x = (int) (k >> 32);
            int y = (int) k;
            for (int cy = Math.floorDiv(y - half, cell); cy <= Math.floorDiv(y + half - 1, cell); cy++) {
                for (int cx = Math.floorDiv(x - half, cell); cx <= Math.floorDiv(x + half - 1, cell); cx++) {
                    covered.add(key(cx, cy));
                }
            }
        }
        int perTile = (CollisionGrid.TILE / cell) * (CollisionGrid.TILE / cell);
        return covered.size() / (double) perTile;
    }
}
