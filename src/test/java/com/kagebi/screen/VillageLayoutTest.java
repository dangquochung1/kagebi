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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.XmlReader;
import com.kagebi.assets.Assets;
import com.kagebi.entity.Player;
import com.kagebi.entity.Projectile;
import com.kagebi.gen.CollisionGrid;
import com.kagebi.gen.TiledRooms;
import com.kagebi.integration.RoomCollision;
import com.kagebi.screen.island.IslandProps;

/**
 * What {@code village.tmx} and {@code home.tmx} promise the screens that read
 * them, and the player who walks them.
 *
 * <p>Read as text and XML rather than through libGDX, which would want a GL
 * context for a hundred tileset images. What cannot be checked here is what
 * the island looks like, which is what {@code --screen hub --page} is for; what
 * can be checked is the half of a map that never draws - its layer names, its
 * markers, who stands where and what blocks - and that half fails silently.
 *
 * <p>The walking tests move a body the size of the player's over the collision
 * grid the game builds, two pixels at a time. Each of them is somewhere a
 * player needs to get to.
 */
class VillageLayoutTest {

    private static final Pattern LAYER = Pattern.compile("<layer[^>]*name=\"([^\"]+)\"");
    private static final Pattern COLLISION_LAYER = Pattern.compile(
        "<objectgroup[^>]*name=\"" + TiledRooms.COLLISION + "\"[^>]*>(.*?)</objectgroup>",
        Pattern.DOTALL);

    /** How far apart the walking tests try positions, in pixels. */
    private static final int STEP = 2;
    private static final float HALF = Player.BODY / 2f;

    private static String read(String path) throws IOException {
        return new String(Files.readAllBytes(new File(path).toPath()), StandardCharsets.UTF_8);
    }

    private static XmlReader.Element xml(String path) {
        return new XmlReader().parse(new FileHandle(new File(path)));
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
     * Every tile layer plays one of the roles the renderer knows about.
     *
     * <p>One that slipped through would be loaded, held in memory, and never
     * drawn - the island would simply be missing its roofs, with nothing
     * anywhere to say so. This is the only thing that would notice.
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
     * <p>An empty or missing one is a real possible outcome of a generator's
     * rules going wrong, and the symptom is a player who walks on the sea.
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
     * Neither map blocks by whole tiles. Both packs draw walls, posts and
     * fences thinner than a tile, and blocking whole tiles is how two tiles
     * with nothing drawn on them once became a wall across the front of a house.
     */
    @Test
    void neitherMapBlocksByWholeTiles() throws IOException {
        for (String path : new String[] {Assets.MAP_VILLAGE, Assets.MAP_HOME}) {
            for (String name : layers(read(path))) {
                assertFalse(TiledRooms.blocks(name), path + " blocks by whole tiles in " + name);
            }
        }
    }

    /** The layer a person's hand-drawn decoration survives a regeneration in. */
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
     * <p>A missing one is not a crash - the screen leaves the villager out, or
     * the door shut - so nothing else would ever report it.
     */
    @Test
    void theVillageNamesEveryMarkerTheHubLooksFor() {
        XmlReader.Element map = xml(Assets.MAP_VILLAGE);
        float width = map.getIntAttribute("width") * CollisionGrid.TILE;
        float height = map.getIntAttribute("height") * CollisionGrid.TILE;

        Set<String> wanted = new HashSet<>(Arrays.asList("entry", "gate", "door", "shop"));
        for (String[] villager : HubScreen.VILLAGERS) {
            wanted.add(villager[0]);
        }
        for (String region : HubScreen.REGIONS) {
            wanted.add("stand_" + region);
        }
        Set<String> seen = new HashSet<>();
        for (XmlReader.Element o : objects(map, HubScreen.SPAWNS)) {
            String name = o.getAttribute("name", "");
            if (!wanted.contains(name)) {
                continue;
            }
            assertTrue(seen.add(name), name + " is named twice");
            float x = o.getFloatAttribute("x");
            float y = o.getFloatAttribute("y");
            assertTrue(x >= 0 && x <= width, name + " x is " + x + ", off a " + width + " map");
            assertTrue(y >= 0 && y <= height, name + " y is " + y + ", off a " + height + " map");
        }
        wanted.removeAll(seen);
        assertTrue(wanted.isEmpty(), "village.tmx never names " + wanted);
    }

    /** Each region has exactly one person the player deals with. */
    @Test
    void everyRegionHasOneWorker() {
        Map<String, Integer> count = new HashMap<>();
        XmlReader.Element map = xml(Assets.MAP_VILLAGE);
        for (XmlReader.Element group : map.getChildrenByName("objectgroup")) {
            for (XmlReader.Element o : group.getChildrenByName("object")) {
                String role = property(o, "role");
                if (role != null) {
                    count.merge(role, 1, Integer::sum);
                }
            }
        }
        for (String region : HubScreen.REGIONS) {
            assertEquals(Integer.valueOf(1), count.get(region),
                "the " + region + " region should have exactly one worker");
        }
        assertEquals(HubScreen.REGIONS.length, count.size(), "workers with no region: " + count);
    }

    /**
     * Every sprite on the island is in one of the four layers the hub draws
     * sprites from. A tile object in any other layer loads, and never appears.
     */
    @Test
    void everySpriteIsInALayerTheHubDraws() {
        IslandProps names = new IslandProps();
        XmlReader.Element map = xml(Assets.MAP_VILLAGE);
        int sprites = 0;
        for (XmlReader.Element group : map.getChildrenByName("objectgroup")) {
            String name = group.getAttribute("name", "");
            for (XmlReader.Element o : group.getChildrenByName("object")) {
                if (o.getAttribute("gid", null) != null) {
                    assertTrue(names.layer(name) != null,
                        "a sprite is in object layer '" + name + "', which nothing draws");
                    sprites++;
                }
            }
        }
        assertTrue(sprites > 400, "the island has only " + sprites + " sprites on it");
    }

    /** The home names the markers it needs: the way in and out, and the rug. */
    @Test
    void theHomeNamesItsDoorAndItsRug() {
        Set<String> seen = markers(Assets.MAP_HOME).keySet();
        assertTrue(seen.contains("door"), "home.tmx has no door, so it cannot be left");
        assertTrue(seen.contains("entry"), "home.tmx has no entry, so it cannot be arrived in");
        assertTrue(seen.contains("rug"), "home.tmx has no rug, so --screen home --page 2 has nowhere to stand");
    }

    /**
     * Neither map is infinite.
     *
     * <p>Both art packs ship their scenes as infinite maps or rooms, which libGDX
     * cannot read: {@code BaseTmxMapLoader} has no notion of a {@code <chunk>},
     * so it parses the file, finds no tile data, and draws nothing at all.
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

    /** A role spans a family of layers, but only across an underscore. */
    @Test
    void aRoleClaimsItsOwnNameAndItsUnderscoredFamily() {
        assertTrue(TiledRooms.plays("props", "props"));
        assertTrue(TiledRooms.plays("props_fence", "props"));
        assertFalse(TiledRooms.plays("propsfence", "props"),
            "a bare prefix would let any name starting with the right letters in");
        assertFalse(TiledRooms.plays("ground", "props"));
        assertEquals(false, TiledRooms.plays(null, "props"));
    }

    // ---- walking the island ----------------------------------------------------

    /**
     * From the front door to everything the village is for: back down through
     * the torii, into the shop, and to every villager and every region's worker.
     * The island was a picture before it was a map, and nothing in the picture
     * had to be walked to.
     */
    @Test
    void fromTheFrontDoorEveryoneAndEverythingCanBeReached() {
        Map<String, float[]> at = markers(Assets.MAP_VILLAGE);
        Reach reach = walk(villageGrid(at), at.get("entry"));
        assertTrue(reach.near(at.get("door"), HubScreen.DOOR_RANGE), "the front door");
        assertTrue(reach.near(at.get("gate"), HubScreen.GATE_RANGE), "the torii");
        assertTrue(reach.near(at.get("shop"), HubScreen.DOOR_RANGE), "the shop");
        for (String[] villager : HubScreen.VILLAGERS) {
            float[] feet = at.get(villager[0]);
            // HubScreen measures the distance to a point just above the feet.
            assertTrue(reach.near(new float[] {feet[0], feet[1] + 8}, HubScreen.TALK_RANGE),
                villager[1]);
        }
        Map<String, float[]> workers = workers(Assets.MAP_VILLAGE);
        for (String region : HubScreen.REGIONS) {
            float[] feet = workers.get(region);
            assertTrue(reach.near(new float[] {feet[0], feet[1] + 8}, HubScreen.WORK_RANGE),
                "the " + region + " worker");
            assertTrue(reach.near(at.get("stand_" + region), 3),
                "where --screen hub puts a player in front of the " + region + " worker");
        }
    }

    /**
     * Every place a player is put down in the village has its prompt up on
     * arrival: the front door from the step outside it, the torii, Bà lang from
     * the shop, and each region's worker from in front of them.
     *
     * <p>Being in range of something and being offered it are not the same:
     * the hub offers the nearest person in range before the torii or the door,
     * and a shop marker a pixel too far from the herbalist is a shop with no
     * way to open it that {@code --screen hub --page 3} shows as a quiet street.
     */
    @Test
    void everyArrivalPointHasItsPromptUp() {
        Map<String, float[]> at = markers(Assets.MAP_VILLAGE);
        Map<String, float[]> workers = workers(Assets.MAP_VILLAGE);
        assertEquals("door", offered(at, workers, at.get("entry")), "arriving at the front door");
        assertEquals("gate", offered(at, workers, at.get("gate")), "arriving at the torii");
        assertEquals(Assets.Npc.HERBALIST, offered(at, workers, at.get("shop")), "arriving at the shop");
        for (String region : HubScreen.REGIONS) {
            assertEquals("worker_" + region, offered(at, workers, at.get("stand_" + region)),
                "arriving in front of the " + region + " worker");
        }
    }

    /**
     * The front of the house, left of the door, is open to a player and to a
     * kunai: two tiles once blocked there with nothing drawn on them, and a
     * kunai thrown that way vanished a step from the player's hand.
     */
    @Test
    void theHouseFrontIsOpenLeftOfTheDoorToAPlayerAndToAKunai() {
        Map<String, float[]> at = markers(Assets.MAP_VILLAGE);
        CollisionGrid grid = villageGrid(at);
        float[] door = at.get("door");
        float[] left = {door[0] - 3 * CollisionGrid.TILE, door[1]};
        assertTrue(walk(grid, at.get("entry")).near(left, 3), "walking left along the front");

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
    void theHouseIsWalkedFromTheDoorOntoTheRugAndAcrossTheHall() {
        Map<String, float[]> at = markers(Assets.MAP_HOME);
        Reach reach = walk(RoomCollision.of(Assets.MAP_HOME), at.get("door"));
        assertTrue(reach.near(at.get("rug"), 3), "the rug beside the table");
        double floor = reach.floorTiles();
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

    private static List<XmlReader.Element> objects(XmlReader.Element map, String group) {
        List<XmlReader.Element> found = new ArrayList<>();
        for (XmlReader.Element g : map.getChildrenByName("objectgroup")) {
            if (group.equals(g.getAttribute("name", ""))) {
                for (XmlReader.Element o : g.getChildrenByName("object")) {
                    found.add(o);
                }
            }
        }
        return found;
    }

    private static String property(XmlReader.Element object, String name) {
        XmlReader.Element list = object.getChildByName("properties");
        if (list == null) {
            return null;
        }
        for (XmlReader.Element p : list.getChildrenByName("property")) {
            if (name.equals(p.getAttribute("name", ""))) {
                return p.getAttribute("value", "");
            }
        }
        return null;
    }

    /**
     * What {@code HubScreen.findInteraction} offers a player standing here: the
     * nearest villager or worker in range, else the torii, else the door, else
     * nothing.
     */
    private static String offered(Map<String, float[]> at, Map<String, float[]> workers,
                                  float[] player) {
        String best = null;
        double nearest = Double.MAX_VALUE;
        for (String[] villager : HubScreen.VILLAGERS) {
            float[] feet = at.get(villager[0]);
            double d = Math.hypot(player[0] - feet[0], player[1] - (feet[1] + 8));
            if (d < HubScreen.TALK_RANGE && d < nearest) {
                nearest = d;
                best = villager[1];
            }
        }
        for (Map.Entry<String, float[]> worker : workers.entrySet()) {
            float[] feet = worker.getValue();
            double d = Math.hypot(player[0] - feet[0], player[1] - (feet[1] + 8));
            if (d < HubScreen.WORK_RANGE && d < nearest) {
                nearest = d;
                best = "worker_" + worker.getKey();
            }
        }
        if (best != null) {
            return best;
        }
        float[] gate = at.get("gate");
        if (Math.hypot(player[0] - gate[0], player[1] - gate[1]) < HubScreen.GATE_RANGE) {
            return "gate";
        }
        float[] door = at.get("door");
        if (Math.hypot(player[0] - door[0], player[1] - door[1]) < HubScreen.DOOR_RANGE) {
            return "door";
        }
        return null;
    }

    /** Named markers, turned y-up the way libGDX turns them for the screens. */
    private static Map<String, float[]> markers(String path) {
        XmlReader.Element map = xml(path);
        float height = map.getIntAttribute("height") * CollisionGrid.TILE;
        Map<String, float[]> at = new HashMap<>();
        for (XmlReader.Element o : objects(map, HubScreen.SPAWNS)) {
            at.put(o.getAttribute("name", ""),
                   new float[] {o.getFloatAttribute("x"), height - o.getFloatAttribute("y")});
        }
        return at;
    }

    /**
     * Where each region's worker stands, y-up, as {@link IslandProps} reads it:
     * the middle of their picture across, and their {@code foot} above its
     * bottom edge.
     */
    private static Map<String, float[]> workers(String path) {
        XmlReader.Element map = xml(path);
        float height = map.getIntAttribute("height") * CollisionGrid.TILE;
        Map<String, float[]> at = new HashMap<>();
        for (XmlReader.Element group : map.getChildrenByName("objectgroup")) {
            for (XmlReader.Element o : group.getChildrenByName("object")) {
                String role = property(o, "role");
                if (role == null) {
                    continue;
                }
                String foot = property(o, "foot");
                float x = o.getFloatAttribute("x") + o.getFloatAttribute("width") / 2f;
                float y = height - o.getFloatAttribute("y") + (foot == null ? 0f : Float.parseFloat(foot));
                at.put(role, new float[] {x, y});
            }
        }
        return at;
    }

    /** The village's grid as HubScreen builds it: the map, and each villager's feet. */
    private static CollisionGrid villageGrid(Map<String, float[]> at) {
        CollisionGrid grid = RoomCollision.of(Assets.MAP_VILLAGE);
        for (String[] villager : HubScreen.VILLAGERS) {
            float[] feet = at.get(villager[0]);
            if (feet != null) {
                grid.fill(Math.round(feet[0]) - 5f, Math.round(feet[1]), 10f, 6f);
            }
        }
        return grid;
    }

    /** Every point a player's body can walk to from a start, STEP pixels apart, y-up. */
    private static Reach walk(CollisionGrid grid, float[] start) {
        int width = grid.width() * CollisionGrid.TILE / STEP + 1;
        int height = grid.height() * CollisionGrid.TILE / STEP + 1;
        int sx = Math.round(start[0] / STEP);
        int sy = Math.round(start[1] / STEP);
        assertTrue(fits(grid, sx * STEP, sy * STEP),
            "a player does not fit where they start, " + sx * STEP + "," + sy * STEP);
        boolean[] seen = new boolean[width * height];
        int[] queue = new int[width * height];
        int head = 0;
        int tail = 0;
        seen[sy * width + sx] = true;
        queue[tail++] = sy * width + sx;
        int[][] moves = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (head < tail) {
            int i = queue[head++];
            int x = i % width;
            int y = i / width;
            for (int[] move : moves) {
                int nx = x + move[0];
                int ny = y + move[1];
                if (nx < 0 || ny < 0 || nx >= width || ny >= height) {
                    continue;
                }
                int n = ny * width + nx;
                if (!seen[n] && fits(grid, nx * STEP, ny * STEP)) {
                    seen[n] = true;
                    queue[tail++] = n;
                }
            }
        }
        return new Reach(seen, width, height);
    }

    /** The test Entity.moveBy makes before letting a player take a step. */
    private static boolean fits(CollisionGrid grid, int x, int y) {
        return !grid.overlaps(x - HALF, y - HALF, Player.BODY, Player.BODY);
    }

    /** The points a body walked to, on a lattice STEP pixels apart. */
    private static final class Reach {
        final boolean[] seen;
        final int width;
        final int height;

        Reach(boolean[] seen, int width, int height) {
            this.seen = seen;
            this.width = width;
            this.height = height;
        }

        boolean near(float[] target, float radius) {
            int x0 = Math.max(0, (int) Math.floor((target[0] - radius) / STEP));
            int x1 = Math.min(width - 1, (int) Math.ceil((target[0] + radius) / STEP));
            int y0 = Math.max(0, (int) Math.floor((target[1] - radius) / STEP));
            int y1 = Math.min(height - 1, (int) Math.ceil((target[1] + radius) / STEP));
            for (int y = y0; y <= y1; y++) {
                for (int x = x0; x <= x1; x++) {
                    float dx = x * STEP - target[0];
                    float dy = y * STEP - target[1];
                    if (seen[y * width + x] && dx * dx + dy * dy < radius * radius) {
                        return true;
                    }
                }
            }
            return false;
        }

        /** Tiles of floor a body covers, standing at every point it reached. */
        double floorTiles() {
            int cell = CollisionGrid.CELL;
            int half = (int) HALF;
            Set<Long> covered = new HashSet<>();
            for (int i = 0; i < seen.length; i++) {
                if (!seen[i]) {
                    continue;
                }
                int x = (i % width) * STEP;
                int y = (i / width) * STEP;
                for (int cy = Math.floorDiv(y - half, cell); cy <= Math.floorDiv(y + half - 1, cell); cy++) {
                    for (int cx = Math.floorDiv(x - half, cell); cx <= Math.floorDiv(x + half - 1, cell); cx++) {
                        covered.add(((long) cx << 32) | (cy & 0xffffffffL));
                    }
                }
            }
            int perTile = (CollisionGrid.TILE / cell) * (CollisionGrid.TILE / cell);
            return covered.size() / (double) perTile;
        }
    }
}
