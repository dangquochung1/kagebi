package com.kagebi.gen;

/**
 * A marker read out of a room's Tiled object layer.
 *
 * <p>Positions are in pixels from the room's bottom-left corner, y-up, which is
 * the space libGDX renders in. Tiled stores objects y-down from the top, so the
 * reader flips them once, here at the edge, rather than leaving every consumer
 * to remember which way up it is.
 */
public final class SpawnPoint {

    public enum Kind {
        /** An enemy; {@link #tag} names one, or is null to roll from the pool. */
        ENEMY,
        CHEST,
        /** Where the player stands on entering, per door side. */
        ENTRY,
        /** The stairs to the next floor. */
        EXIT,
        SHOPKEEPER,
        /** Decorative but interactive: a torch, a breakable pot, a trap. */
        PROP
    }

    public final Kind kind;
    public final int x;
    public final int y;
    /** Which def to use, or null to let the generator choose. */
    public final String tag;

    public SpawnPoint(Kind kind, int x, int y, String tag) {
        this.kind = kind;
        this.x = x;
        this.y = y;
        this.tag = tag;
    }

    @Override
    public String toString() {
        return kind + "@" + x + "," + y + (tag == null ? "" : ":" + tag);
    }
}
