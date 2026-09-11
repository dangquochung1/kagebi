package com.kagebi.gen;

import com.kagebi.Dir;

/**
 * A room placed on a floor's grid.
 *
 * <p>The layout is generated once and then lived in, so the two things that
 * change during play - whether the player has been here, and whether the
 * enemies are dead - are mutable fields on the room rather than a parallel set
 * bolted on beside it. The minimap reads exactly these.
 */
public final class Room {

    /** Position on the floor grid, not in pixels. */
    public final int gx;
    public final int gy;
    public final RoomKind kind;
    public final RoomTemplate template;

    /** Bitmask of {@link Dir#bit()} for the sides that actually open. */
    private int doors;

    private final Room[] neighbours = new Room[4];

    public boolean visited;
    public boolean cleared;

    public Room(int gx, int gy, RoomKind kind, RoomTemplate template) {
        this.gx = gx;
        this.gy = gy;
        this.kind = kind;
        this.template = template;
    }

    public int doors() {
        return doors;
    }

    public boolean hasDoor(Dir d) {
        return (doors & d.bit()) != 0;
    }

    public Room neighbour(Dir d) {
        return neighbours[d.ordinal()];
    }

    /** Links both sides at once; a one-way door would be a generator bug. */
    public void link(Dir d, Room other) {
        neighbours[d.ordinal()] = other;
        doors |= d.bit();
        other.neighbours[d.opposite().ordinal()] = this;
        other.doors |= d.opposite().bit();
    }

    @Override
    public String toString() {
        return kind + "(" + gx + "," + gy + ")";
    }
}
