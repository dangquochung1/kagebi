package com.kagebi.data.def;

/**
 * A weighted drop table, from {@code assets/data/loot_tables.json}.
 *
 * <p>Weights are integers and {@link #nothingWeight} is one of them, so "40% of
 * the time this drops nothing" is expressed in the same currency as everything
 * else. Tables that instead roll a separate drop chance first tend to grow two
 * places where the same decision is tuned.
 */
public final class LootTableDef {

    public static final class Entry {
        public final String itemId;
        public final int weight;
        public final int min;
        public final int max;

        public Entry(String itemId, int weight, int min, int max) {
            this.itemId = itemId;
            this.weight = weight;
            this.min = min;
            this.max = max;
        }
    }

    public final String id;
    public final Entry[] entries;
    /** Weight of rolling nothing at all, in the same pool as the entries. */
    public final int nothingWeight;
    /** How many independent rolls; a boss chest rolls more than a slime. */
    public final int rolls;

    public LootTableDef(String id, Entry[] entries, int nothingWeight, int rolls) {
        this.id = id;
        this.entries = entries;
        this.nothingWeight = nothingWeight;
        this.rolls = rolls;
    }

    public int totalWeight() {
        int total = nothingWeight;
        for (Entry e : entries) {
            total += e.weight;
        }
        return total;
    }

    @Override
    public String toString() {
        return "LootTableDef(" + id + ")";
    }
}
