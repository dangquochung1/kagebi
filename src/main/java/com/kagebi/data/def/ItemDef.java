package com.kagebi.data.def;

/** A pickup: a potion, a key, a coin. From {@code assets/data/items.json}. */
public final class ItemDef {

    public enum Kind {
        /** Carried in the inventory, spent when used. */
        CONSUMABLE,
        /** Opens one locked door or chest. */
        KEY,
        /** Adds to the run purse, and survives death as meta-currency. */
        GOLD,
        /**
         * The other purse. Rarer than gold by an order of magnitude, banked
         * the same way, and deliberately not multiplied by the fortune
         * upgrade - that track is priced against gold income.
         */
        DIAMOND,
        /** Takes effect the moment it is walked over. */
        INSTANT
    }

    public final String id;
    public final String nameKey;
    public final String descKey;
    /** Region in {@code ui.atlas} under {@code items/}, or null to use the icon. */
    public final String sprite;
    /** Index into the Raven icon grid, or -1. */
    public final int icon;

    public final Kind kind;
    public final String effect;
    public final float magnitude;
    /** How many the player may carry: 1 for a key, more for potions. */
    public final int stackSize;

    /**
     * What the dungeon's shopkeeper charges, or 0 for something never sold.
     *
     * <p>Zero rather than -1 because "not for sale" is the common case: gold
     * itself, the hearts enemies drop, and anything a chest hands over are all
     * pickups rather than goods. {@link #forSale} is the question every caller
     * actually asks.
     */
    public final int price;

    public ItemDef(String id, String nameKey, String descKey, String sprite,
                   int icon, Kind kind, String effect, float magnitude,
                   int stackSize) {
        this(id, nameKey, descKey, sprite, icon, kind, effect, magnitude, stackSize, 0);
    }

    public ItemDef(String id, String nameKey, String descKey, String sprite,
                   int icon, Kind kind, String effect, float magnitude,
                   int stackSize, int price) {
        this.id = id;
        this.nameKey = nameKey;
        this.descKey = descKey;
        this.sprite = sprite;
        this.icon = icon;
        this.kind = kind;
        this.effect = effect;
        this.magnitude = magnitude;
        this.stackSize = stackSize;
        this.price = price;
    }

    /** Whether the dungeon shopkeeper may stock it. */
    public boolean forSale() {
        return price > 0;
    }

    @Override
    public String toString() {
        return "ItemDef(" + id + ")";
    }
}
