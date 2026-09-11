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

    public ItemDef(String id, String nameKey, String descKey, String sprite,
                   int icon, Kind kind, String effect, float magnitude,
                   int stackSize) {
        this.id = id;
        this.nameKey = nameKey;
        this.descKey = descKey;
        this.sprite = sprite;
        this.icon = icon;
        this.kind = kind;
        this.effect = effect;
        this.magnitude = magnitude;
        this.stackSize = stackSize;
    }

    @Override
    public String toString() {
        return "ItemDef(" + id + ")";
    }
}
