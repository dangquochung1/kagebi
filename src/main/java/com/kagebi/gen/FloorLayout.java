package com.kagebi.gen;

import com.badlogic.gdx.utils.Array;

/**
 * The rooms of one floor and how they connect.
 *
 * <p>Produced by {@code FloorGenerator} from a seed, and then read by everything
 * else. Holding the grid sparsely - a flat array of slots, most of them null -
 * keeps neighbour lookups to arithmetic, which the minimap does every frame.
 *
 * <p>The one invariant worth stating out loud: <b>the boss room is always
 * reachable from the start room without passing through a locked or secret
 * door.</b> A floor that fails it is unplayable in a way no amount of testing
 * during a run will reliably catch, so the generator asserts it before
 * returning.
 */
public final class FloorLayout {

    private final int gridW;
    private final int gridH;
    private final Room[] slots;
    private final Array<Room> rooms = new Array<>();
    private final long seed;
    private final int floor;

    private Room start;
    private Room boss;
    private Room exit;

    public FloorLayout(int floor, long seed, int gridW, int gridH) {
        this.floor = floor;
        this.seed = seed;
        this.gridW = gridW;
        this.gridH = gridH;
        this.slots = new Room[gridW * gridH];
    }

    public int floor() {
        return floor;
    }

    public long seed() {
        return seed;
    }

    public int gridW() {
        return gridW;
    }

    public int gridH() {
        return gridH;
    }

    public Array<Room> rooms() {
        return rooms;
    }

    public Room roomAt(int gx, int gy) {
        if (gx < 0 || gy < 0 || gx >= gridW || gy >= gridH) {
            return null;
        }
        return slots[gy * gridW + gx];
    }

    public void place(Room room) {
        if (roomAt(room.gx, room.gy) != null) {
            throw new IllegalStateException("two rooms at " + room.gx + "," + room.gy);
        }
        slots[room.gy * gridW + room.gx] = room;
        rooms.add(room);
        switch (room.kind) {
            case START: start = room; break;
            case BOSS: boss = room; break;
            case EXIT: exit = room; break;
            default: break;
        }
    }

    public Room start() {
        return start;
    }

    public Room boss() {
        return boss;
    }

    /** The stairs down, which may be the boss room itself on a boss floor. */
    public Room exit() {
        return exit != null ? exit : boss;
    }

    @Override
    public String toString() {
        return "FloorLayout(floor=" + floor + ", rooms=" + rooms.size
            + ", seed=" + seed + ")";
    }
}
