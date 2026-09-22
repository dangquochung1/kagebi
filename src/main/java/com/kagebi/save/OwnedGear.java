package com.kagebi.save;

/**
 * One piece of armour the player actually owns, as opposed to the kind of
 * thing it is.
 *
 * <p>Gear needs an instance and relics do not, and the difference is the
 * sockets. Two Tempered Hoods are the same {@code GearDef} but one of them has
 * a red stone in it and the other does not, so "how many of this id do I have"
 * is not a question that can be answered with a count. Everything else the
 * player owns - upgrades, unlocks, stones, pantry goods - really is countable,
 * and is stored as a count.
 *
 * <p>The id is assigned by {@link Profile#nextGearId} and is never reused
 * inside a profile, so the equipped map can point at a piece rather than at a
 * position in a list that any sale or forge would shuffle.
 */
public final class OwnedGear {

    /** Unique within one profile, and stable across a save and load. */
    public final int instance;
    /** The {@code GearDef} this is one of. */
    public final String defId;
    /**
     * What is set in each socket, or null for an empty one.
     *
     * <p>Sized to the def's socket count when the piece is made. A stone put in
     * is spent: taking one out again is not offered, because a forge that
     * handed the stone back would make choosing where to put it free, and the
     * choice is the whole point of having sockets.
     */
    public final String[] sockets;

    public OwnedGear(int instance, String defId, int socketCount) {
        this.instance = instance;
        this.defId = defId;
        this.sockets = new String[Math.max(0, socketCount)];
    }

    public OwnedGear(int instance, String defId, String[] sockets) {
        this.instance = instance;
        this.defId = defId;
        this.sockets = sockets == null ? new String[0] : sockets;
    }

    /** The first empty socket, or -1 when the piece is full or has none. */
    public int freeSocket() {
        for (int i = 0; i < sockets.length; i++) {
            if (sockets[i] == null) {
                return i;
            }
        }
        return -1;
    }

    public int stonesSet() {
        int n = 0;
        for (String s : sockets) {
            if (s != null) {
                n++;
            }
        }
        return n;
    }

    @Override
    public String toString() {
        return "OwnedGear(#" + instance + " " + defId + ", " + stonesSet()
            + "/" + sockets.length + ")";
    }
}
