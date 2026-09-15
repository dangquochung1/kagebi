package com.kagebi.village;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectIntMap;
import com.kagebi.data.VillageCatalog;
import com.kagebi.data.def.LootTableDef;
import com.kagebi.loot.LootRoller;
import com.kagebi.save.VillageState;

/**
 * What each region's worker makes while the player is elsewhere.
 *
 * <p>A worker makes one unit every period until as many are waiting as their
 * workshop holds; then they stop, and start again from the moment someone
 * collects. So a workshop left alone for a day holds what it held after an hour,
 * and nothing is gained by leaving the game running overnight.
 *
 * <p>What a unit turns out to be - an egg or milk, stone or gold - is rolled with
 * {@link LootRoller} from the workshop's weights, seeded by the unit's number over
 * the workshop's life. A save collects the same goods however often it is
 * loaded, and a test can measure the split over ten thousand units rather than
 * trust it.
 *
 * <p>A workshop's record starts the first time anyone asks about it, so a worker
 * begins work when the village first looks at them, not at some moment before
 * the save knew they existed.
 */
public final class Workshops {

    /** The workshop's tool level, capped at the tool's top: a hand-edited level is not trusted. */
    public static int toolLevel(VillageCatalog cat, VillageState v, VillageCatalog.Workshop w) {
        VillageCatalog.Tool tool = cat.tool(w.tool);
        if (tool == null) {
            return 0;
        }
        return Math.max(0, Math.min(tool.maxLevel, v.tools.get(tool.id, 0)));
    }

    /** Seconds per unit at the workshop's tool level. */
    public static double period(VillageCatalog cat, VillageState v, VillageCatalog.Workshop w) {
        VillageCatalog.Tool tool = cat.tool(w.tool);
        double speed = tool == null ? 1 : Math.pow(tool.speedPerLevel, toolLevel(cat, v, w));
        return w.seconds / speed;
    }

    /** Units the worker makes before stopping to wait, at the tool's level. */
    public static int capacity(VillageCatalog cat, VillageState v, VillageCatalog.Workshop w) {
        VillageCatalog.Tool tool = cat.tool(w.tool);
        return w.capacity + (tool == null ? 0 : tool.capacityPerLevel * toolLevel(cat, v, w));
    }

    /** The workshop's record, started at the clock if nobody has asked before. */
    public static VillageState.Work work(VillageState v, VillageCatalog.Workshop w) {
        VillageState.Work work = v.workshops.get(w.id);
        if (work == null) {
            work = new VillageState.Work();
            work.since = v.clock;
            v.workshops.put(w.id, work);
        }
        return work;
    }

    /** Brings the workshop up to the clock, and returns how many units are waiting. */
    public static int ready(VillageCatalog cat, VillageState v, VillageCatalog.Workshop w) {
        VillageState.Work work = work(v, w);
        int cap = capacity(cat, v, w);
        if (work.since > v.clock) {
            work.since = v.clock;
        }
        if (work.held >= cap) {
            // Full, so nothing is being made and there is nothing to catch up on.
            work.held = cap;
            work.since = v.clock;
            return cap;
        }
        double period = period(cat, v, w);
        long made = (long) Math.floor((v.clock - work.since) / period);
        if (made >= cap - work.held) {
            work.held = cap;
            work.since = v.clock;
        } else {
            work.held += (int) made;
            work.since += made * period;
        }
        return work.held;
    }

    /** Seconds until the next unit is ready, or -1 while the workshop is full. */
    public static double untilNext(VillageCatalog cat, VillageState v, VillageCatalog.Workshop w) {
        if (ready(cat, v, w) >= capacity(cat, v, w)) {
            return -1;
        }
        VillageState.Work work = work(v, w);
        return Math.max(0, period(cat, v, w) - (v.clock - work.since));
    }

    /**
     * Takes everything waiting into the storehouse. Returns what it turned out to
     * be, by good, for the screen to show; empty if nothing was ready.
     */
    public static ObjectIntMap<String> collect(VillageCatalog cat, VillageState v, VillageCatalog.Workshop w) {
        ObjectIntMap<String> got = new ObjectIntMap<>();
        int held = ready(cat, v, w);
        if (held == 0) {
            return got;
        }
        VillageState.Work work = work(v, w);
        LootTableDef table = table(w);
        for (int i = 0; i < held; i++) {
            Array<LootRoller.Drop> drops = LootRoller.roll(table, seed(w, work.made + i));
            for (LootRoller.Drop d : drops) {
                got.getAndIncrement(d.itemId, 0, d.count);
                Counts.add(v.stock, d.itemId, d.count);
            }
        }
        work.made += held;
        work.held = 0;
        // If it was full, ready() has already restarted it from now.
        return got;
    }

    /** The workshop's goods as a loot table that always finds something, once. */
    static LootTableDef table(VillageCatalog.Workshop w) {
        LootTableDef.Entry[] entries = new LootTableDef.Entry[w.goods.length];
        for (int i = 0; i < entries.length; i++) {
            entries[i] = new LootTableDef.Entry(w.goods[i], w.weights[i], 1, 1);
        }
        return new LootTableDef("workshop_" + w.id, entries, 0, 1);
    }

    /**
     * One seed per unit, and a different run of them per workshop, so the
     * ranch and the mine never roll in step. LootRoller mixes the seed before
     * use, so consecutive units are independent.
     */
    static long seed(VillageCatalog.Workshop w, long unit) {
        return w.id.hashCode() * 0x9E3779B97F4A7C15L + unit;
    }

    private Workshops() {}
}
