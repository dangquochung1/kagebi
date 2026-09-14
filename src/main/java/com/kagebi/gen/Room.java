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

    /**
     * Which of this room's chests have been opened, as a bitmask over the
     * CHEST spawns in {@link #template}, in the order they appear there.
     *
     * <p>Here rather than on the chest because a chest does not survive
     * walking out of the room: {@code EntityWorld.enterRoom} clears every
     * marker and {@code spawn} builds fresh ones, so a flag on a marker is
     * forgotten the moment the player steps through a door. That is what let
     * one treasure chest be opened for its whole contents over and over, on an
     * RNG re-seeded to the same state each time - the same loot, every time,
     * about two seconds a cycle.
     *
     * <p>A mask rather than a boolean because a shop room holds three chests,
     * and opening one of them must not seal the other two.
     */
    public int chestsOpened;

    /**
     * Which of this room's shopkeeper's slots have been bought, as a bitmask
     * over the three the trader rolls.
     *
     * <p>Here for the same reason as {@link #chestsOpened}: the trader screen
     * is built fresh on every interaction, so the sold flags it kept were
     * forgotten the moment it closed, while the stock itself is rolled from a
     * fixed seed and came back identical. Closing and reopening restocked him,
     * which made a three-item shelf an unlimited one.
     */
    public int slotsBought;

    public Room(int gx, int gy, RoomKind kind, RoomTemplate template) {
        this.gx = gx;
        this.gy = gy;
        this.kind = kind;
        this.template = template;
    }

    public boolean chestOpened(int index) {
        return (chestsOpened & (1 << index)) != 0;
    }

    public void markChestOpened(int index) {
        chestsOpened |= 1 << index;
    }

    public boolean slotBought(int slot) {
        return (slotsBought & (1 << slot)) != 0;
    }

    public void markSlotBought(int slot) {
        slotsBought |= 1 << slot;
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
