package com.kagebi.data;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Array;
import com.kagebi.assets.Assets;

/**
 * The village economy: what the island's goods sell for, the farm's crops and
 * levels, what each region's worker makes, the tools that speed them up, and
 * the cook's recipes. From the {@code goods}, {@code crops}, {@code farm},
 * {@code workshops}, {@code tools} and {@code recipes} sections of the content
 * directory - {@code village.json} as shipped.
 *
 * <p>Beside {@link ContentRegistry} rather than in it, for the reason
 * {@link ShopCatalog} is: none of it is what a run is made of. Checked by the
 * same validator at boot. The rules that read it are in
 * {@code com.kagebi.village}, which has no Gdx in it.
 *
 * <p>Lookups return null for an id that is not here rather than throwing, so
 * the validator can ask about a misspelt reference and name it. Adding a def
 * whose id is already here replaces it, the way {@code ContentRegistry.put}
 * does; a file that defines an id twice is reported while it is parsed.
 */
public final class VillageCatalog {

    /**
     * The regions whose worker makes goods of their own accord. The farm is
     * worked by the player, and the kitchen cooks what the player asks for.
     */
    public static final Set<String> WORKSHOPS = Set.of("forest", "ranch", "fishing", "mine");

    /** Something the island produces, and what the herbalist pays for one. */
    public static final class Good {
        public final String id;
        public final String nameKey;
        public final int price;

        public Good(String id, String nameKey, int price) {
            this.id = id;
            this.nameKey = nameKey;
            this.price = price;
        }

        @Override
        public String toString() {
            return "Good(" + id + ")";
        }
    }

    /** A crop: the good it is harvested as, what its seed costs, and how long it takes. */
    public static final class Crop {
        public final String id;
        public final String good;
        public final int seedPrice;
        /** Goods one harvest gives. */
        public final int yield;
        /** Seconds of the village clock from one of its six pictures to the next. */
        public final float stageSeconds;
        /** The farm level at which the farmer sells its seed. */
        public final int farmLevel;

        public Crop(String id, String good, int seedPrice, int yield, float stageSeconds, int farmLevel) {
            this.id = id;
            this.good = good;
            this.seedPrice = seedPrice;
            this.yield = yield;
            this.stageSeconds = stageSeconds;
            this.farmLevel = farmLevel;
        }

        @Override
        public String toString() {
            return "Crop(" + id + ")";
        }
    }

    /** One level of the farm: the harvests it asks for, and how many plots it opens. */
    public static final class FarmLevel {
        public final int level;
        public final int harvests;
        public final int plots;

        public FarmLevel(int level, int harvests, int plots) {
            this.level = level;
            this.harvests = harvests;
            this.plots = plots;
        }

        @Override
        public String toString() {
            return "FarmLevel(" + level + ")";
        }
    }

    /** What one region's worker makes, and how fast. */
    public static final class Workshop {
        public final String id;
        public final String[] goods;
        /** How likely each of {@link #goods} is, relative to the others. */
        public final int[] weights;
        /** Seconds per unit, before any tool. */
        public final float seconds;
        /** Units made before the worker stops to wait for someone to collect. */
        public final int capacity;
        public final String tool;

        public Workshop(String id, String[] goods, int[] weights, float seconds, int capacity, String tool) {
            this.id = id;
            this.goods = goods;
            this.weights = weights;
            this.seconds = seconds;
            this.capacity = capacity;
            this.tool = tool;
        }

        @Override
        public String toString() {
            return "Workshop(" + id + ")";
        }
    }

    /** A tool that makes one workshop faster and lets it hold more. */
    public static final class Tool {
        public final String id;
        public final String nameKey;
        public final String descKey;
        public final int maxLevel;
        /** Price of level 1, 2, ... - one entry per level. */
        public final int[] costs;
        /** Each level divides the workshop's seconds per unit by this. */
        public final float speedPerLevel;
        /** And adds this to what it holds. */
        public final int capacityPerLevel;

        public Tool(String id, String nameKey, String descKey, int maxLevel, int[] costs,
                    float speedPerLevel, int capacityPerLevel) {
            this.id = id;
            this.nameKey = nameKey;
            this.descKey = descKey;
            this.maxLevel = maxLevel;
            this.costs = costs;
            this.speedPerLevel = speedPerLevel;
            this.capacityPerLevel = capacityPerLevel;
        }

        @Override
        public String toString() {
            return "Tool(" + id + ")";
        }
    }

    /** A meal the cook makes: the consumable it is, and the goods that go into it. */
    public static final class Recipe {
        public final String id;
        /** The item made, from the {@code items} section. */
        public final String item;
        public final String[] inputs;
        /** How many of each of {@link #inputs}. */
        public final int[] counts;

        public Recipe(String id, String item, String[] inputs, int[] counts) {
            this.id = id;
            this.item = item;
            this.inputs = inputs;
            this.counts = counts;
        }

        @Override
        public String toString() {
            return "Recipe(" + id + ")";
        }
    }

    private final Array<Good> goods = new Array<>();
    private final Array<Crop> crops = new Array<>();
    private final Array<FarmLevel> farmLevels = new Array<>();
    private final Array<Workshop> workshops = new Array<>();
    private final Array<Tool> tools = new Array<>();
    private final Array<Recipe> recipes = new Array<>();

    public VillageCatalog() {}

    // ---- loading -------------------------------------------------------------

    /** For the game. ContentLoader has already validated this at boot. */
    public static VillageCatalog load() {
        return parse(Gdx.files.internal(Assets.DATA_DIR));
    }

    public static VillageCatalog parse(FileHandle dataDir) {
        List<String> problems = new ArrayList<>();
        VillageCatalog out = parse(dataDir, problems);
        ContentValidator.throwIfAny(problems);
        return out;
    }

    /**
     * Reads the same directory ContentLoader does, so a broken file is reported
     * by both; {@link ContentValidator#throwIfAny} drops the repeats.
     */
    static VillageCatalog parse(FileHandle dataDir, List<String> problems) {
        DataFiles data = DataFiles.read(dataDir, problems);
        VillageCatalog out = new VillageCatalog();
        Set<String> seen = new HashSet<>();
        for (Fields f : data.objects(DataFiles.GOODS, "good")) {
            Good d = new Good(f.id(), f.string("nameKey"), f.integer("price"));
            f.done();
            if (fresh(seen, "good", d.id, f)) {
                out.add(d);
            }
        }
        for (Fields f : data.objects(DataFiles.CROPS, "crop")) {
            Crop d = new Crop(f.id(), f.string("good"), f.integer("seedPrice"), f.integer("yield"),
                f.number("stageSeconds"), f.integer("farmLevel"));
            f.done();
            if (fresh(seen, "crop", d.id, f)) {
                out.add(d);
            }
        }
        for (Fields f : data.objects(DataFiles.FARM, "farm level")) {
            int level = f.integer("level");
            f.label(String.valueOf(level));
            FarmLevel d = new FarmLevel(level, f.integer("harvests"), f.integer("plots"));
            f.done();
            if (fresh(seen, "farm level", String.valueOf(level), f)) {
                out.add(d);
            }
        }
        for (Fields f : data.objects(DataFiles.WORKSHOPS, "workshop")) {
            Workshop d = new Workshop(f.id(), f.strings("goods"), f.integers("weights"),
                f.number("seconds"), f.integer("capacity"), f.string("tool"));
            f.done();
            if (fresh(seen, "workshop", d.id, f)) {
                out.add(d);
            }
        }
        for (Fields f : data.objects(DataFiles.TOOLS, "tool")) {
            Tool d = new Tool(f.id(), f.string("nameKey"), f.string("descKey"), f.integer("maxLevel"),
                f.integers("costs"), f.number("speedPerLevel"), f.integer("capacityPerLevel"));
            f.done();
            if (fresh(seen, "tool", d.id, f)) {
                out.add(d);
            }
        }
        for (Fields f : data.objects(DataFiles.RECIPES, "recipe")) {
            Recipe d = new Recipe(f.id(), f.string("item"), f.strings("inputs"), f.integers("counts"));
            f.done();
            if (fresh(seen, "recipe", d.id, f)) {
                out.add(d);
            }
        }
        return out;
    }

    /** A second def with the same id is reported, not quietly put over the first. */
    private static boolean fresh(Set<String> seen, String kind, String id, Fields f) {
        if (id == null) {
            return false;
        }
        if (!seen.add(kind + ":" + id)) {
            f.problem(kind + " id '" + id + "' is defined twice");
            return false;
        }
        return true;
    }

    public void add(Good d) {
        put(goods, d, x -> x.id.equals(d.id));
    }

    public void add(Crop d) {
        put(crops, d, x -> x.id.equals(d.id));
    }

    public void add(FarmLevel d) {
        put(farmLevels, d, x -> x.level == d.level);
    }

    public void add(Workshop d) {
        put(workshops, d, x -> x.id.equals(d.id));
    }

    public void add(Tool d) {
        put(tools, d, x -> x.id.equals(d.id));
    }

    public void add(Recipe d) {
        put(recipes, d, x -> x.id.equals(d.id));
    }

    private static <T> void put(Array<T> list, T def, Predicate<T> same) {
        for (int i = 0; i < list.size; i++) {
            if (same.test(list.get(i))) {
                list.set(i, def);
                return;
            }
        }
        list.add(def);
    }

    // ---- reading ---------------------------------------------------------------

    public Array<Good> goods() {
        return goods;
    }

    public Array<Crop> crops() {
        return crops;
    }

    /** In the order the file lists them, which the validator holds to level order. */
    public Array<FarmLevel> farmLevels() {
        return farmLevels;
    }

    public Array<Workshop> workshops() {
        return workshops;
    }

    public Array<Tool> tools() {
        return tools;
    }

    public Array<Recipe> recipes() {
        return recipes;
    }

    // Index loops, not for-each: a libGDX Array hands out one iterator, and
    // these are called from inside loops over the catalog's other lists.

    public Good good(String id) {
        for (int i = 0; i < goods.size; i++) {
            if (goods.get(i).id.equals(id)) {
                return goods.get(i);
            }
        }
        return null;
    }

    public Crop crop(String id) {
        for (int i = 0; i < crops.size; i++) {
            if (crops.get(i).id.equals(id)) {
                return crops.get(i);
            }
        }
        return null;
    }

    public Workshop workshop(String id) {
        for (int i = 0; i < workshops.size; i++) {
            if (workshops.get(i).id.equals(id)) {
                return workshops.get(i);
            }
        }
        return null;
    }

    public Tool tool(String id) {
        for (int i = 0; i < tools.size; i++) {
            if (tools.get(i).id.equals(id)) {
                return tools.get(i);
            }
        }
        return null;
    }

    public Recipe recipe(String id) {
        for (int i = 0; i < recipes.size; i++) {
            if (recipes.get(i).id.equals(id)) {
                return recipes.get(i);
            }
        }
        return null;
    }
}
