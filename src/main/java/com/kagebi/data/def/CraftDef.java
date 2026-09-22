package com.kagebi.data.def;

/**
 * One line on the forge's shelf: what it makes, and what it costs to make.
 *
 * <p>Separate from {@code VillageCatalog.Recipe}, which the cook uses, because
 * the two answer different questions. A recipe turns food into food and is
 * bought at a counter; this turns stones and salvage into a piece of gear or a
 * stone of the next tier up, and is the only way to get most of them. Folding
 * them together would mean one shelf listing both dinner and swords.
 *
 * <p>Inputs are ids of gear, stones or items, with counts alongside - the same
 * parallel-array shape the recipes and loot tables already use. What an id
 * refers to is resolved by the forge rather than declared here, because a
 * recipe that consumed "two red shards and a broken helm" should not have to
 * say which of those is which.
 */
public final class CraftDef {

    /** What comes out: a piece of gear, or a stone. */
    public enum Output { GEAR, GEM }

    public final String id;
    public final Output kind;
    /** The id of the {@link GearDef} or {@link GemDef} produced. */
    public final String output;
    public final String[] inputs;
    public final int[] counts;
    /** Gold on top of the materials, or 0. */
    public final int goldCost;
    /** One of ShopCatalog.REQUIREMENTS; what has to be true before it is offered. */
    public final String requirement;
    public final int requirementValue;

    public CraftDef(String id, Output kind, String output, String[] inputs, int[] counts,
                    int goldCost, String requirement, int requirementValue) {
        this.id = id;
        this.kind = kind;
        this.output = output;
        this.inputs = inputs;
        this.counts = counts;
        this.goldCost = goldCost;
        this.requirement = requirement;
        this.requirementValue = requirementValue;
    }

    @Override
    public String toString() {
        return "CraftDef(" + id + " -> " + kind + " " + output + ")";
    }
}
