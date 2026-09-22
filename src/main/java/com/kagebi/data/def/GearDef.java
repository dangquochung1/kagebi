package com.kagebi.data.def;

/**
 * A piece of armour: a slot, some numbers, and holes to put stones in.
 *
 * <p><b>Gear is not drawn on the character.</b> The pack has one body per
 * character and no layered equipment art, and inventing some would mean
 * redrawing every animation of every hero five times over. So a helmet changes
 * what the numbers say and nothing else, which is stated here rather than
 * discovered: there is no sprite field, and there is not meant to be one.
 *
 * <p>Effects are parallel arrays rather than a list of pairs, matching the way
 * {@code LootTableDef} and {@code FloorDef} already carry theirs. The validator
 * checks the two are the same length; nothing else has to.
 */
public final class GearDef {

    /** Where a piece is worn. Five, and one of each may be on at a time. */
    public enum Slot { HEAD, BODY, HANDS, FEET, TRINKET }

    public final String id;
    public final String nameKey;
    public final String descKey;
    /** One-based index into the icon grid; see IconSheet. */
    public final int icon;
    public final Slot slot;
    /**
     * 1 to 4. Not a multiplier on anything - the numbers are in the effects -
     * but the shelf sorts by it and the forge asks for the tier below.
     */
    public final int tier;
    /** How many stones this may hold, 0 to 3. */
    public final int sockets;
    public final String[] effects;
    public final float[] magnitudes;
    /** Gold, or 0 for something only the forge can make. */
    public final int price;

    public GearDef(String id, String nameKey, String descKey, int icon, Slot slot,
                   int tier, int sockets, String[] effects, float[] magnitudes, int price) {
        this.id = id;
        this.nameKey = nameKey;
        this.descKey = descKey;
        this.icon = icon;
        this.slot = slot;
        this.tier = tier;
        this.sockets = sockets;
        this.effects = effects;
        this.magnitudes = magnitudes;
        this.price = price;
    }

    public boolean forSale() {
        return price > 0;
    }

    @Override
    public String toString() {
        return "GearDef(" + id + ", " + slot + " t" + tier + ")";
    }
}
