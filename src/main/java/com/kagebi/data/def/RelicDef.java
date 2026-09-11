package com.kagebi.data.def;

/**
 * A passive picked up during a run, from {@code assets/data/relics.json}.
 *
 * <p>Effects are named rather than scripted: a relic says
 * {@code effect="damage_mult", magnitude=1.15} and {@code combat} knows what
 * {@code damage_mult} means. That keeps balance edits in JSON, which is the
 * whole point of this being data. An unknown effect id must fail at load rather
 * than silently do nothing - a relic that does nothing is nearly impossible to
 * notice while playing.
 */
public final class RelicDef {

    public enum Rarity { COMMON, RARE, EPIC }

    public final String id;
    public final String nameKey;
    public final String descKey;
    /** Index into the 2,192-icon Raven grid; see {@code icons.json}. */
    public final int icon;
    public final Rarity rarity;

    public final String effect;
    public final float magnitude;

    public RelicDef(String id, String nameKey, String descKey, int icon,
                    Rarity rarity, String effect, float magnitude) {
        this.id = id;
        this.nameKey = nameKey;
        this.descKey = descKey;
        this.icon = icon;
        this.rarity = rarity;
        this.effect = effect;
        this.magnitude = magnitude;
    }

    @Override
    public String toString() {
        return "RelicDef(" + id + ")";
    }
}
