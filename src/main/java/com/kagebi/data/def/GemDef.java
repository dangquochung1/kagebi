package com.kagebi.data.def;

/**
 * A stone that goes in a socket and adds one number.
 *
 * <p>One effect each, deliberately. A stone with two would need a line of its
 * own on the forge shelf to explain itself; a stone with one is explained by
 * its colour, and the colours are a rule the player learns once - red is
 * damage, green is survival, blue is speed, yellow is the big hits, purple is
 * the strange ones. {@code ContentValidator.GEM_COLOURS} is where that rule
 * lives, and it fails the build if a stone breaks it.
 *
 * <p>Stones are also the forge's raw material, which is why they drop by the
 * handful: the same red shard is either set into a sword or melted into the
 * next tier of one.
 */
public final class GemDef {

    public enum Colour { RED, GREEN, BLUE, YELLOW, PURPLE }

    public final String id;
    public final String nameKey;
    public final int icon;
    public final Colour colour;
    /** 1 to 3. A tier-3 stone is worth nine tier-1 stones at the forge. */
    public final int tier;
    public final String effect;
    public final float magnitude;
    /** Gold, or 0 for one that is only ever found. */
    public final int price;

    public GemDef(String id, String nameKey, int icon, Colour colour, int tier,
                  String effect, float magnitude, int price) {
        this.id = id;
        this.nameKey = nameKey;
        this.icon = icon;
        this.colour = colour;
        this.tier = tier;
        this.effect = effect;
        this.magnitude = magnitude;
        this.price = price;
    }

    public boolean forSale() {
        return price > 0;
    }

    @Override
    public String toString() {
        return "GemDef(" + id + ", " + colour + " t" + tier + ")";
    }
}
