package com.kagebi.gen;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Array;
import com.kagebi.Dir;
import com.kagebi.data.def.FloorDef;

/**
 * A thousand seeds on every floor, because the failures that matter here are
 * the rare ones.
 *
 * <p>A floor with an unreachable boss does not crash. It generates, it
 * renders, and it strands whoever is playing it. That is invisible to hand
 * testing - one person, a handful of runs, and they already know the way - and
 * certain to turn up across a few thousand players. So every seed below is
 * checked against the invariant by a search written here, independently of
 * {@link FloorGenerator#check}; the generator's own check is exercised
 * separately, against floors built broken on purpose.
 *
 * <p>The floor definitions are built by hand. Floors normally come from
 * assets/data/floors.json, which is being written in parallel with this; the
 * numbers here are plausible, and the biome names are the room folders that
 * actually exist.
 */
class FloorGeneratorTest {

    private static final int SEEDS = 1000;

    private static FloorDef floor(int number, String biome, int min, int max,
                                  int treasure, int shops, String boss) {
        return new FloorDef(number, "floor." + number, biome, "music", null, null,
                            min, max, treasure, shops, new String[] {"slime"},
                            new int[] {1}, 2, 4, boss);
    }

    private static final FloorDef[] FLOORS = {
        floor(1, "ruins", 8, 11, 1, 1, "stone_golem"),
        floor(2, "ruins_green", 9, 12, 1, 1, "moss_knight"),
        floor(3, "ruins_orange", 10, 13, 2, 1, "ember_oni"),
        floor(4, "depths", 11, 14, 2, 1, "shade"),
        floor(5, "depths", 12, 16, 2, 1, "kagebi"),
    };

    private static Array<RoomTemplate> catalog;
    /** Every floor at every seed, generated once and shared by the read-only tests. */
    private static final List<FloorDef> defs = new ArrayList<>();
    private static final List<FloorLayout> layouts = new ArrayList<>();

    @BeforeAll
    static void generateEverything() {
        catalog = RoomCatalog.load(new FileHandle(new File("assets/maps/rooms")));
        assertTrue(catalog.size > 0, "no rooms on disk - run: python tools/make_maps.py");
        for (FloorDef f : FLOORS) {
            FloorGenerator gen = new FloorGenerator(catalog);
            for (long seed = 0; seed < SEEDS; seed++) {
                defs.add(f);
                layouts.add(gen.generate(f, seed));
            }
        }
    }

    // ---- the invariant, checked independently ----------------------------

    @Test
    void theBossIsReachableWithoutAKeyOrABomb() {
        for (int i = 0; i < layouts.size(); i++) {
            FloorLayout l = layouts.get(i);
            assertNotNull(l.boss(), where(i) + "no boss room");
            assertTrue(reach(l, false).contains(l.boss()),
                where(i) + "boss only reachable through a locked or secret door");
        }
    }

    @Test
    void theBossIsNeverNextDoorToTheStart() {
        for (int i = 0; i < layouts.size(); i++) {
            FloorLayout l = layouts.get(i);
            int gap = Math.abs(l.boss().gx - l.start().gx) + Math.abs(l.boss().gy - l.start().gy);
            assertTrue(gap > 1, where(i) + "boss is next door to the start");
            // Stronger than not-adjacent: it is at least two doors away by the
            // route the player actually walks.
            assertTrue(doorsBetween(l, l.start(), l.boss()) >= 2, where(i) + "boss one door away");
        }
    }

    @Test
    void roomCountStaysInsideTheFloorsRange() {
        for (int i = 0; i < layouts.size(); i++) {
            int n = layouts.get(i).rooms().size;
            FloorDef f = defs.get(i);
            assertTrue(n >= f.roomsMin && n <= f.roomsMax,
                where(i) + n + " rooms, wanted " + f.roomsMin + ".." + f.roomsMax);
        }
    }

    @Test
    void noRoomIsOrphaned() {
        for (int i = 0; i < layouts.size(); i++) {
            FloorLayout l = layouts.get(i);
            assertEquals(l.rooms().size, reach(l, true).size(), where(i) + "orphan rooms");
        }
    }

    @Test
    void everyDoorIsTwoWayAndMatchesTheGrid() {
        for (int i = 0; i < layouts.size(); i++) {
            FloorLayout l = layouts.get(i);
            for (Room r : l.rooms()) {
                assertEquals(r, l.roomAt(r.gx, r.gy), where(i) + r + " not where the grid says");
                for (Dir d : Dir.ALL) {
                    Room n = r.neighbour(d);
                    assertEquals(l.roomAt(r.gx + d.dx, r.gy + d.dy), n,
                        where(i) + r + " " + d + " disagrees with the grid");
                    assertEquals(n != null, r.hasDoor(d), where(i) + r + " door bit " + d);
                    if (n != null) {
                        assertEquals(r, n.neighbour(d.opposite()), where(i) + "one-way door at " + r);
                        assertTrue(n.hasDoor(d.opposite()), where(i) + "half a door at " + n);
                    }
                }
            }
        }
    }

    @Test
    void sideRoomsAndTheBossHaveExactlyOneDoor() {
        // One door is what keeps these off the critical path: a treasure room
        // or a locked room with two doors is a shortcut, and a boss room with
        // two is one the player can wander into from the wrong side.
        Set<RoomKind> oneDoor = Set.of(RoomKind.BOSS, RoomKind.TREASURE,
                                       RoomKind.SHOP, RoomKind.LOCKED);
        for (int i = 0; i < layouts.size(); i++) {
            for (Room r : layouts.get(i).rooms()) {
                if (oneDoor.contains(r.kind)) {
                    assertEquals(1, Integer.bitCount(r.doors()), where(i) + r + " has "
                        + Integer.bitCount(r.doors()) + " doors");
                }
            }
        }
    }

    @Test
    void nothingIsBehindALockedOrSecretRoomExceptItself() {
        for (int i = 0; i < layouts.size(); i++) {
            FloorLayout l = layouts.get(i);
            Set<Room> open = reach(l, false);
            for (Room r : l.rooms()) {
                if (!open.contains(r)) {
                    assertTrue(r.kind == RoomKind.LOCKED || r.kind == RoomKind.SECRET,
                        where(i) + r + " can only be reached through a locked or secret door");
                }
            }
        }
    }

    @Test
    void everyFloorGetsTheSideRoomsItAskedFor() {
        for (int i = 0; i < layouts.size(); i++) {
            FloorDef f = defs.get(i);
            Map<RoomKind, Integer> n = census(layouts.get(i));
            assertEquals(1, n.getOrDefault(RoomKind.START, 0), where(i) + "start rooms");
            assertEquals(1, n.getOrDefault(RoomKind.BOSS, 0), where(i) + "boss rooms");
            assertEquals(f.treasureRooms, n.getOrDefault(RoomKind.TREASURE, 0), where(i) + "treasure");
            assertEquals(f.shopRooms, n.getOrDefault(RoomKind.SHOP, 0), where(i) + "shops");
            assertEquals(1, n.getOrDefault(RoomKind.LOCKED, 0), where(i) + "locked rooms");
            assertEquals(1, n.getOrDefault(RoomKind.SECRET, 0), where(i) + "secret rooms");
        }
    }

    @Test
    void everyRoomGetsATemplateFromItsOwnBiomeThatSuitsIt() {
        for (int i = 0; i < layouts.size(); i++) {
            for (Room r : layouts.get(i).rooms()) {
                assertNotNull(r.template, where(i) + r + " has no template");
                assertEquals(defs.get(i).biome, r.template.biome, where(i) + r + " wrong biome");
                assertTrue(r.template.suits(r.kind), where(i) + r + " got " + r.template);
            }
        }
    }

    // ---- determinism and variety -----------------------------------------

    @Test
    void theSameSeedGivesTheSameFloorTwice() {
        // A fresh generator, so nothing can leak between runs through state
        // the first one kept.
        int i = 0;
        for (FloorDef f : FLOORS) {
            FloorGenerator again = new FloorGenerator(catalog);
            for (long seed = 0; seed < SEEDS; seed++, i++) {
                assertEquals(fingerprint(layouts.get(i)), fingerprint(again.generate(f, seed)),
                    where(i) + "not reproducible");
            }
        }
    }

    @Test
    void noOneFloorShapeComesUpOften() {
        // What a player notices is not the count of distinct floors but the
        // same one coming round again. Measured: the commonest shape turns up
        // at most four times in a thousand on any floor. A seed that is taken
        // and then ignored would put one shape at a thousand.
        //
        // Not "N distinct shapes" - an earlier version asserted more than 900
        // and passed only because of the seed correlation FloorGenerator.mix
        // exists to remove. Small floors genuinely share shapes: an 8-room
        // floor has a four-room spine, and there are only so many of those.
        for (int f = 0; f < FLOORS.length; f++) {
            Map<String, Integer> seen = new java.util.HashMap<>();
            for (int s = 0; s < SEEDS; s++) {
                seen.merge(shape(layouts.get(f * SEEDS + s)), 1, Integer::sum);
            }
            int commonest = Collections.max(seen.values());
            assertTrue(commonest <= SEEDS / 100, "floor " + FLOORS[f].number
                + ": one shape came up " + commonest + " times in " + SEEDS + " seeds");
        }
    }

    @Test
    void roomCountsSpreadAcrossTheWholeRange() {
        // The regression test for FloorGenerator.mix. Without it, seeds 0-999
        // gave an 8-11 room floor only 10 or 11 rooms and never 8 or 9, so
        // every other test here quietly skipped the smallest floors - which
        // are the cramped ones, where side rooms are hardest to fit.
        for (int f = 0; f < FLOORS.length; f++) {
            FloorDef def = FLOORS[f];
            int values = def.roomsMax - def.roomsMin + 1;
            int[] hits = new int[values];
            for (int s = 0; s < SEEDS; s++) {
                hits[layouts.get(f * SEEDS + s).rooms().size - def.roomsMin]++;
            }
            for (int v = 0; v < values; v++) {
                assertTrue(hits[v] >= SEEDS / values / 2, "floor " + def.number + ": "
                    + (def.roomsMin + v) + " rooms came up " + hits[v] + " times in " + SEEDS
                    + ", wanted about " + SEEDS / values);
            }
        }
    }

    // ---- awkward inputs ---------------------------------------------------

    @Test
    void aFloorWithNoBossEndsInAnExitRoom() {
        FloorDef f = floor(6, "depths", 8, 12, 1, 1, null);
        FloorGenerator gen = new FloorGenerator(catalog);
        for (long seed = 0; seed < SEEDS; seed++) {
            FloorLayout l = gen.generate(f, seed);
            assertNull(l.boss(), "seed " + seed);
            assertNotNull(l.exit(), "seed " + seed + ": no exit");
            assertEquals(RoomKind.EXIT, l.exit().kind);
            assertTrue(reach(l, false).contains(l.exit()), "seed " + seed + ": exit gated");
        }
    }

    @Test
    void crampedAndSprawlingFloorsStillHold() {
        FloorGenerator gen = new FloorGenerator(catalog);
        FloorDef[] odd = {
            // More side rooms asked for than the budget holds: some must be
            // dropped, and the count must still be exact.
            floor(7, "ruins", 5, 5, 3, 2, "b"),
            floor(8, "ruins", 4, 4, 0, 0, "b"),
            floor(9, "depths", 40, 60, 3, 2, "b"),
        };
        for (FloorDef f : odd) {
            for (long seed = 0; seed < SEEDS; seed++) {
                FloorLayout l = gen.generate(f, seed);
                assertTrue(l.rooms().size >= f.roomsMin && l.rooms().size <= f.roomsMax,
                    "floor " + f.number + " seed " + seed + ": " + l.rooms().size + " rooms");
                assertTrue(reach(l, false).contains(l.boss()), "floor " + f.number + " seed " + seed);
            }
        }
    }

    @Test
    void anEmptyCatalogStillMakesAFloor() {
        FloorLayout l = assertDoesNotThrow(
            () -> new FloorGenerator(new Array<>()).generate(FLOORS[0], 1));
        for (Room r : l.rooms()) {
            assertNull(r.template, "a room template appeared from nowhere");
        }
    }

    @Test
    void anUnknownBiomeBorrowsRoomsRatherThanThrowing() {
        FloorDef f = floor(1, "no_such_biome", 8, 11, 1, 1, "b");
        FloorLayout l = assertDoesNotThrow(() -> new FloorGenerator(catalog).generate(f, 7));
        for (Room r : l.rooms()) {
            assertNotNull(r.template, r + " should have borrowed a template");
            assertTrue(r.template.suits(r.kind), r + " borrowed " + r.template);
        }
    }

    @Test
    void theGridIsLargeEnoughThatTheWalkCannotStall() {
        // FloorGenerator.sideFor's argument: a tree can only run out of room to
        // grow if it spans the grid in both directions, which takes 2*side-1
        // rooms. Held for every size a floor could plausibly ask for.
        for (int rooms = 1; rooms <= 500; rooms++) {
            int side = FloorGenerator.sideFor(rooms);
            assertTrue(2 * side - 1 > rooms, rooms + " rooms on a " + side + " grid");
        }
    }

    // ---- the generator's own check, against floors broken on purpose -----

    private static final FloorDef SMALL = floor(1, "ruins", 1, 20, 0, 0, "b");

    private static Room room(FloorLayout l, int x, int y, RoomKind kind) {
        Room r = new Room(x, y, kind, null);
        l.place(r);
        return r;
    }

    @Test
    void checkRejectsABossBehindALockedDoor() {
        FloorLayout l = new FloorLayout(1, 0, 6, 3);
        Room s = room(l, 0, 1, RoomKind.START);
        Room a = room(l, 1, 1, RoomKind.NORMAL);
        Room k = room(l, 2, 1, RoomKind.LOCKED);
        Room b = room(l, 3, 1, RoomKind.BOSS);
        s.link(Dir.RIGHT, a);
        a.link(Dir.RIGHT, k);
        k.link(Dir.RIGHT, b);
        assertRejected(l, "locked or secret");
    }

    @Test
    void checkRejectsABossBehindASecretDoor() {
        FloorLayout l = new FloorLayout(1, 0, 6, 3);
        Room s = room(l, 0, 1, RoomKind.START);
        Room a = room(l, 1, 1, RoomKind.NORMAL);
        Room h = room(l, 2, 1, RoomKind.SECRET);
        Room b = room(l, 3, 1, RoomKind.BOSS);
        s.link(Dir.RIGHT, a);
        a.link(Dir.RIGHT, h);
        h.link(Dir.RIGHT, b);
        assertRejected(l, "locked or secret");
    }

    @Test
    void checkRejectsAnOrphanRoom() {
        FloorLayout l = new FloorLayout(1, 0, 6, 6);
        Room s = room(l, 0, 0, RoomKind.START);
        Room a = room(l, 1, 0, RoomKind.NORMAL);
        Room b = room(l, 2, 0, RoomKind.BOSS);
        room(l, 5, 5, RoomKind.NORMAL);
        s.link(Dir.RIGHT, a);
        a.link(Dir.RIGHT, b);
        assertRejected(l, "orphan");
    }

    @Test
    void checkRejectsABossNextDoorToTheStart() {
        FloorLayout l = new FloorLayout(1, 0, 4, 4);
        Room s = room(l, 0, 0, RoomKind.START);
        Room b = room(l, 1, 0, RoomKind.BOSS);
        Room a = room(l, 0, 1, RoomKind.NORMAL);
        Room c = room(l, 0, 2, RoomKind.NORMAL);
        s.link(Dir.RIGHT, b);
        s.link(Dir.UP, a);
        a.link(Dir.UP, c);
        assertRejected(l, "next door");
    }

    @Test
    void checkRejectsNeighboursWithNoDoorBetweenThem() {
        FloorLayout l = new FloorLayout(1, 0, 6, 3);
        Room s = room(l, 0, 0, RoomKind.START);
        Room a = room(l, 1, 0, RoomKind.NORMAL);
        Room b = room(l, 3, 0, RoomKind.BOSS);
        Room c = room(l, 2, 0, RoomKind.NORMAL);
        room(l, 1, 1, RoomKind.NORMAL);      // touches a on the grid, never linked
        s.link(Dir.RIGHT, a);
        a.link(Dir.RIGHT, c);
        c.link(Dir.RIGHT, b);
        assertRejected(l, "grid");
    }

    @Test
    void checkRejectsARoomCountOutsideTheRange() {
        FloorLayout l = new FloorLayout(1, 0, 6, 3);
        Room s = room(l, 0, 0, RoomKind.START);
        Room a = room(l, 1, 0, RoomKind.NORMAL);
        Room b = room(l, 2, 0, RoomKind.BOSS);
        s.link(Dir.RIGHT, a);
        a.link(Dir.RIGHT, b);
        IllegalStateException e = assertThrows(IllegalStateException.class,
            () -> FloorGenerator.check(l, floor(1, "ruins", 5, 9, 0, 0, "b")));
        assertTrue(e.getMessage().contains("rooms, wanted"), e.getMessage());
    }

    @Test
    void checkAcceptsASoundFloor() {
        // The control for the five above: the same shape, correctly built.
        FloorLayout l = new FloorLayout(1, 0, 6, 3);
        Room s = room(l, 0, 1, RoomKind.START);
        Room a = room(l, 1, 1, RoomKind.NORMAL);
        Room c = room(l, 2, 1, RoomKind.NORMAL);
        Room b = room(l, 3, 1, RoomKind.BOSS);
        Room k = room(l, 2, 2, RoomKind.LOCKED);
        s.link(Dir.RIGHT, a);
        a.link(Dir.RIGHT, c);
        c.link(Dir.RIGHT, b);
        c.link(Dir.UP, k);
        assertDoesNotThrow(() -> FloorGenerator.check(l, SMALL));
    }

    private static void assertRejected(FloorLayout l, String why) {
        IllegalStateException e = assertThrows(IllegalStateException.class,
            () -> FloorGenerator.check(l, SMALL));
        assertTrue(e.getMessage().contains(why), "wrong reason: " + e.getMessage());
    }

    // ---- helpers ----------------------------------------------------------

    private static String where(int i) {
        return "floor " + defs.get(i).number + " seed " + (i % SEEDS) + ": ";
    }

    /**
     * Rooms reachable from the start. With {@code throughAnything} false the
     * search stops at locked and secret rooms, which is the player with no key
     * and no bomb. Written here rather than borrowed from the generator, so a
     * mistake in one does not hide the same mistake in the other.
     */
    private static Set<Room> reach(FloorLayout l, boolean throughAnything) {
        Set<Room> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        Deque<Room> todo = new ArrayDeque<>();
        seen.add(l.start());
        todo.add(l.start());
        while (!todo.isEmpty()) {
            Room r = todo.poll();
            for (Dir d : Dir.ALL) {
                Room n = r.neighbour(d);
                if (n == null || seen.contains(n)) {
                    continue;
                }
                if (!throughAnything && (n.kind == RoomKind.LOCKED || n.kind == RoomKind.SECRET)) {
                    continue;
                }
                seen.add(n);
                todo.add(n);
            }
        }
        return seen;
    }

    private static int doorsBetween(FloorLayout l, Room from, Room to) {
        Map<Room, Integer> dist = new IdentityHashMap<>();
        Deque<Room> todo = new ArrayDeque<>();
        dist.put(from, 0);
        todo.add(from);
        while (!todo.isEmpty()) {
            Room r = todo.poll();
            if (r == to) {
                return dist.get(r);
            }
            for (Dir d : Dir.ALL) {
                Room n = r.neighbour(d);
                if (n != null && !dist.containsKey(n)) {
                    dist.put(n, dist.get(r) + 1);
                    todo.add(n);
                }
            }
        }
        return -1;
    }

    private static Map<RoomKind, Integer> census(FloorLayout l) {
        Map<RoomKind, Integer> n = new java.util.EnumMap<>(RoomKind.class);
        for (Room r : l.rooms()) {
            n.merge(r.kind, 1, Integer::sum);
        }
        return n;
    }

    /** Everything about a floor a player could notice, in placement order. */
    private static String fingerprint(FloorLayout l) {
        StringBuilder sb = new StringBuilder();
        sb.append(l.gridW()).append('x').append(l.gridH()).append(';');
        for (Room r : l.rooms()) {
            sb.append(r.kind).append('@').append(r.gx).append(',').append(r.gy)
              .append('/').append(r.doors()).append('/')
              .append(r.template == null ? "-" : r.template.id).append(';');
        }
        return sb.toString();
    }

    /** Just the shape: which kinds sit where, ignoring templates. */
    private static String shape(FloorLayout l) {
        List<String> cells = new ArrayList<>();
        for (Room r : l.rooms()) {
            cells.add(r.kind.ordinal() + "@" + (r.gx - l.start().gx) + "," + (r.gy - l.start().gy));
        }
        Collections.sort(cells);
        return String.join(";", cells);
    }

    @Test
    void fingerprintsSeeTheDifferenceBetweenTwoFloors() {
        // The determinism test is only as strong as the fingerprint; this is
        // its control.
        assertNotEquals(fingerprint(layouts.get(0)), fingerprint(layouts.get(1)));
    }
}
