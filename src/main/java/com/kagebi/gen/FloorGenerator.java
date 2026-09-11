package com.kagebi.gen;

import com.badlogic.gdx.utils.Array;
import com.kagebi.Dir;
import com.kagebi.data.def.FloorDef;

/**
 * PLACEHOLDER. Lays the rooms of a floor out in a straight line.
 *
 * <p>Enough for the dungeon screen to have a floor to walk through while the
 * real generator is written beside it. Expected to be replaced whole; what must
 * survive is {@link #generate}, and the invariant it already upholds - the boss
 * room is reachable from the start without a locked or secret door, which a
 * line trivially satisfies and a graph does not.
 *
 * <p>Pure by design: no {@code Gdx.} and no graphics anywhere below, so a real
 * generator can be hammered with a thousand seeds in a plain JUnit test. That
 * matters more here than anywhere else in the game, because an unreachable boss
 * room is a bug that only shows up as a player wandering a floor they cannot
 * finish.
 */
public final class FloorGenerator {

    private final Array<RoomTemplate> templates;

    public FloorGenerator(Array<RoomTemplate> templates) {
        this.templates = templates;
    }

    public FloorLayout generate(FloorDef floor, long seed) {
        java.util.Random rng = new java.util.Random(seed);
        int count = Math.max(3, floor.roomsMin
            + (floor.roomsMax > floor.roomsMin
               ? rng.nextInt(floor.roomsMax - floor.roomsMin + 1) : 0));

        FloorLayout layout = new FloorLayout(floor.number, seed, count, 1);
        Room previous = null;
        for (int i = 0; i < count; i++) {
            RoomKind kind = i == 0 ? RoomKind.START
                : i == count - 1 ? (floor.hasBoss() ? RoomKind.BOSS : RoomKind.EXIT)
                : RoomKind.NORMAL;
            Room room = new Room(i, 0, kind, pick(floor.biome, kind, rng));
            layout.place(room);
            if (previous != null) {
                previous.link(Dir.RIGHT, room);
            }
            previous = room;
        }
        return layout;
    }

    private RoomTemplate pick(String biome, RoomKind kind, java.util.Random rng) {
        Array<RoomTemplate> matching = new Array<>();
        for (RoomTemplate t : templates) {
            if (t.biome.equals(biome) && t.suits(kind)) {
                matching.add(t);
            }
        }
        if (matching.isEmpty()) {
            // Falling back to any room at all beats refusing to make a floor;
            // a mismatched room is visible, an exception three screens in is a
            // crash report.
            matching = templates;
        }
        if (matching.isEmpty()) {
            throw new IllegalStateException(
                "no room templates at all - run: python tools/make_maps.py");
        }
        return matching.get(rng.nextInt(matching.size));
    }
}
