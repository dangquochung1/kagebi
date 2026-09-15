package com.kagebi.village;

import com.kagebi.data.VillageCatalog;
import com.kagebi.save.VillageState;

/**
 * The farm: the field's plots, which the player sows and harvests themselves.
 *
 * <p>A crop has six pictures - just sown, four stages of growing, ripe - and
 * moves to the next every {@code stageSeconds} of the village clock. A ripe crop
 * waits to be picked; nothing rots, because a player who spent a stage in the
 * dungeon should come back to a harvest, not to a penalty.
 *
 * <p>The farm's level is counted in harvests. Each level opens more of the field
 * and puts that level's seeds on the farmer's shelf.
 */
public final class Farm {

    /** The stage a crop is ripe at: its sixth picture. */
    public static final int RIPE = 5;

    /** The farm's level: the highest one whose harvests have been reached. */
    public static int level(VillageCatalog cat, VillageState v) {
        int level = 1;
        for (int i = 0; i < cat.farmLevels().size; i++) {
            VillageCatalog.FarmLevel l = cat.farmLevels().get(i);
            if (v.harvests >= l.harvests) {
                level = Math.max(level, l.level);
            }
        }
        return level;
    }

    /** How many of the field's plots the farm's level has opened for sowing. */
    public static int openPlots(VillageCatalog cat, VillageState v) {
        int level = level(cat, v);
        int open = 0;
        for (int i = 0; i < cat.farmLevels().size; i++) {
            VillageCatalog.FarmLevel l = cat.farmLevels().get(i);
            if (l.level <= level) {
                open = Math.max(open, l.plots);
            }
        }
        return open;
    }

    /** The plots in the whole field: what the top level opens. */
    public static int allPlots(VillageCatalog cat) {
        int all = 0;
        for (int i = 0; i < cat.farmLevels().size; i++) {
            all = Math.max(all, cat.farmLevels().get(i).plots);
        }
        return all;
    }

    /** The plot at this index, bare if it has never been used. */
    public static VillageState.Plot plot(VillageState v, int index) {
        while (v.plots.size <= index) {
            v.plots.add(new VillageState.Plot());
        }
        return v.plots.get(index);
    }

    /** The picture a plot shows: 0 just sown, up to {@link #RIPE}, or -1 for bare soil. */
    public static int stage(VillageCatalog cat, VillageState v, int index) {
        VillageCatalog.Crop crop = crop(cat, v, index);
        if (crop == null) {
            return -1;
        }
        double grown = Math.max(0, v.clock - v.plots.get(index).sown);
        return (int) Math.min(RIPE, Math.floor(grown / crop.stageSeconds));
    }

    public static boolean ripe(VillageCatalog cat, VillageState v, int index) {
        return stage(cat, v, index) == RIPE;
    }

    /** Seconds until a plot's crop is ripe: 0 once it is, -1 for bare soil. */
    public static double untilRipe(VillageCatalog cat, VillageState v, int index) {
        VillageCatalog.Crop crop = crop(cat, v, index);
        if (crop == null) {
            return -1;
        }
        double ripeAt = v.plots.get(index).sown + (double) crop.stageSeconds * RIPE;
        return Math.max(0, ripeAt - v.clock);
    }

    /** The crop growing on a plot, or null for bare soil or a crop the catalog no longer has. */
    public static VillageCatalog.Crop crop(VillageCatalog cat, VillageState v, int index) {
        if (index < 0 || index >= v.plots.size) {
            return null;
        }
        String id = v.plots.get(index).crop;
        return id == null ? null : cat.crop(id);
    }

    /** Whether this crop can go in this plot now: a seed in hand, at the farm's level, in open bare soil. */
    public static boolean canSow(VillageCatalog cat, VillageState v, int index, VillageCatalog.Crop crop) {
        return crop != null
            && index >= 0 && index < openPlots(cat, v)
            && stage(cat, v, index) < 0
            && crop.farmLevel <= level(cat, v)
            && v.seeds.get(crop.id, 0) > 0;
    }

    /** Sows one seed, or changes nothing and says so. */
    public static boolean sow(VillageCatalog cat, VillageState v, int index, VillageCatalog.Crop crop) {
        if (!canSow(cat, v, index, crop)) {
            return false;
        }
        Counts.add(v.seeds, crop.id, -1);
        VillageState.Plot plot = plot(v, index);
        plot.crop = crop.id;
        plot.sown = v.clock;
        return true;
    }

    /**
     * Picks a ripe crop into the storehouse and counts the harvest toward the
     * farm's level. Returns how many goods it gave: 0 if nothing there was ripe.
     */
    public static int harvest(VillageCatalog cat, VillageState v, int index) {
        if (!ripe(cat, v, index)) {
            return 0;
        }
        VillageCatalog.Crop crop = crop(cat, v, index);
        Counts.add(v.stock, crop.good, crop.yield);
        v.harvests++;
        VillageState.Plot plot = v.plots.get(index);
        plot.crop = null;
        plot.sown = 0;
        return crop.yield;
    }

    /**
     * The first visit: the field as the scene painted it.
     *
     * <p>Each plot gets the crop the scene shows on it, already grown to the
     * stage it shows, so the farm a player walks into for the first time is the
     * farm in the picture rather than bare soil where the picture had pumpkins.
     * It happens once. A crop the catalog does not know leaves its plot bare, and
     * a plot the farm has not opened keeps its crop until it is picked.
     */
    public static void plantScene(VillageCatalog cat, VillageState v, String[] crops, int[] stages) {
        if (v.planted) {
            return;
        }
        v.planted = true;
        for (int i = 0; i < crops.length; i++) {
            VillageCatalog.Crop crop = crops[i] == null ? null : cat.crop(crops[i]);
            if (crop == null) {
                continue;
            }
            int stage = Math.max(0, Math.min(RIPE, i < stages.length ? stages[i] : 0));
            VillageState.Plot plot = plot(v, i);
            plot.crop = crop.id;
            plot.sown = v.clock - (double) crop.stageSeconds * stage;
        }
    }

    private Farm() {}
}
