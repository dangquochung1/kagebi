package com.kagebi.save;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectIntMap;
import com.badlogic.gdx.utils.ObjectMap;

/**
 * The village between visits: its clock, the farm's plots, what each worker has
 * made, what is in the storehouse, and what is packed for the dungeon.
 *
 * <p>Part of the {@link Profile}, because it outlives runs the way gold does.
 * Only data: the rules that change it are in {@code com.kagebi.village}.
 *
 * <p><b>One clock.</b> Every time here is a reading of {@link #clock} - seconds
 * the game has been open since the village began - and never the wall clock. A
 * crop sown at 100 is ripe at 100 plus its growing time however long the game
 * was closed in between, which is what the player chose: the village grows
 * while they play, not while they are away.
 *
 * <p>Counts are kept without zeros. A good that runs out is a key that is gone,
 * so the save lists what there is rather than everything there ever was.
 */
public final class VillageState {

    /** Seconds of play since the village began. */
    public double clock;
    /** Crops harvested over the farm's whole life, which is what its level counts. */
    public int harvests;
    /** Whether the plots have been given the scene's own crops, which happens once. */
    public boolean planted;

    /** Goods in the storehouse, by good id. */
    public final ObjectIntMap<String> stock = new ObjectIntMap<>();
    /** Seeds bought and not yet sown, by crop id. */
    public final ObjectIntMap<String> seeds = new ObjectIntMap<>();
    /** Tool id to level. */
    public final ObjectIntMap<String> tools = new ObjectIntMap<>();
    /** Meals and potions packed for the next run, by item id. */
    public final ObjectIntMap<String> pantry = new ObjectIntMap<>();

    /** The field's plots in the map's order, grown as far as a plot has been used. */
    public final Array<Plot> plots = new Array<>();
    /** What each region's worker has made, by workshop id. */
    public final ObjectMap<String, Work> workshops = new ObjectMap<>();

    /** One plot of the field. */
    public static final class Plot {
        /** The crop growing, or null for bare soil. */
        public String crop;
        /** The clock when it was sown. */
        public double sown;
    }

    /** One worker's output. */
    public static final class Work {
        /** Units made and waiting to be collected. */
        public int held;
        /** The clock when the unit being made now was started. */
        public double since;
        /**
         * Units made over the workshop's whole life. What each unit turns out to
         * be is rolled from its number, so a save collects the same goods however
         * many times it is loaded.
         */
        public long made;
    }
}
