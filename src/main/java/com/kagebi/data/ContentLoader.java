package com.kagebi.data;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.JsonValue;
import com.kagebi.assets.Assets;
import com.kagebi.data.def.CraftDef;
import com.kagebi.data.def.EnemyDef;
import com.kagebi.data.def.FloorDef;
import com.kagebi.data.def.GearDef;
import com.kagebi.data.def.GemDef;
import com.kagebi.data.def.ItemDef;
import com.kagebi.data.def.LootTableDef;
import com.kagebi.data.def.QuestDef;
import com.kagebi.data.def.SkillDef;
import com.kagebi.data.def.RelicDef;
import com.kagebi.data.def.WeaponDef;

/**
 * Reads the JSON in {@link Assets#DATA_DIR} into a {@link ContentRegistry},
 * then refuses to return it if {@link ContentValidator} finds anything wrong.
 *
 * <p>Split in two on purpose. {@link #parse} turns text into defs and reports
 * only what makes a def impossible to build - a missing field, a string where a
 * number goes. Whether the defs make sense <em>together</em> - a floor naming
 * an enemy that does not exist, a relic whose effect combat has never heard of
 * - is the validator's question, because answering it needs the atlases and
 * the translations as well, and a test that builds three defs by hand should
 * be able to ask it without them.
 *
 * <p>Problems are collected, not thrown one at a time, so a single boot lists
 * everything that is wrong with the content.
 */
public final class ContentLoader {

    /**
     * What {@code Kagebi.create()} calls. Parses, validates against the real
     * atlases and translations, and throws with every problem listed.
     */
    public static ContentRegistry load() {
        ContentRegistry registry = load(Gdx.files::internal);
        Gdx.app.log("content", registry.allEnemies().size + " enemies, "
            + registry.allWeapons().size + " weapons, "
            + registry.allRelics().size + " relics, "
            + registry.allItems().size + " items, "
            + registry.allLootTables().size + " loot tables, "
            + registry.allFloors().size + " floors");
        return registry;
    }

    /**
     * Parse and validate, resolving every asset through {@code files}. The game
     * passes {@code Gdx.files::internal}; a test passes plain files, and so runs
     * exactly the checks the game runs at boot without a GL context.
     */
    public static ContentRegistry load(Function<String, FileHandle> files) {
        List<String> problems = new ArrayList<>();
        FileHandle dataDir = files.apply(Assets.DATA_DIR);
        ContentRegistry registry = parse(dataDir, problems);
        ShopCatalog shop = ShopCatalog.parse(dataDir, problems);
        VillageCatalog village = VillageCatalog.parse(dataDir, problems);
        if (problems.isEmpty()) {
            // Cross-checks on a half-built registry would only bury the real
            // parse errors under a pile of consequential ones.
            problems.addAll(ContentValidator.check(registry, shop, village,
                ContentValidator.AssetIndex.read(files)));
        }
        ContentValidator.throwIfAny(problems);
        return registry;
    }

    /** Parse only. Throws if any def could not be built. */
    public static ContentRegistry parse(FileHandle dataDir) {
        List<String> problems = new ArrayList<>();
        ContentRegistry registry = parse(dataDir, problems);
        ContentValidator.throwIfAny(problems);
        return registry;
    }

    static ContentRegistry parse(FileHandle dataDir, List<String> problems) {
        DataFiles data = DataFiles.read(dataDir, problems);
        ContentRegistry registry = new ContentRegistry();
        registry.putIcons(data.icons);

        Ids enemyIds = new Ids("enemy", problems);
        for (Fields f : data.objects(DataFiles.ENEMIES, "enemy")) {
            EnemyDef d = enemy(f);
            if (enemyIds.fresh(d.id, f)) {
                registry.put(d);
            }
        }
        Ids weaponIds = new Ids("weapon", problems);
        for (Fields f : data.objects(DataFiles.WEAPONS, "weapon")) {
            WeaponDef d = weapon(f, data);
            if (weaponIds.fresh(d.id, f)) {
                registry.put(d);
            }
        }
        Ids relicIds = new Ids("relic", problems);
        for (Fields f : data.objects(DataFiles.RELICS, "relic")) {
            RelicDef d = relic(f, data);
            if (relicIds.fresh(d.id, f)) {
                registry.put(d);
            }
        }
        Ids itemIds = new Ids("item", problems);
        for (Fields f : data.objects(DataFiles.ITEMS, "item")) {
            ItemDef d = item(f, data);
            if (itemIds.fresh(d.id, f)) {
                registry.put(d);
            }
        }
        Ids tableIds = new Ids("loot table", problems);
        for (Fields f : data.objects(DataFiles.TABLES, "loot table")) {
            LootTableDef d = table(f);
            if (tableIds.fresh(d.id, f)) {
                registry.put(d);
            }
        }
        Ids floorNumbers = new Ids("floor", problems);
        int highest = 0;
        for (Fields f : data.objects(DataFiles.FLOORS, "floor")) {
            FloorDef d = floor(f);
            if (floorNumbers.fresh(String.valueOf(d.number), f)) {
                registry.put(d);
                highest = Math.max(highest, d.number);
            }
        }
        // ContentRegistry.allFloors() walks 1, 2, 3... and stops at the first
        // gap, so a missing floor 3 would quietly end the dungeon at floor 2.
        if (registry.allFloors().size != highest) {
            problems.add("floors: numbered up to " + highest + " but floor "
                + (registry.allFloors().size + 1) + " is missing");
        }
        Ids gearIds = new Ids("gear", problems);
        for (Fields f : data.objects(DataFiles.GEAR, "gear")) {
            GearDef d = gear(f, data);
            if (gearIds.fresh(d.id, f)) {
                registry.put(d);
            }
        }
        Ids gemIds = new Ids("gem", problems);
        for (Fields f : data.objects(DataFiles.GEMS, "gem")) {
            GemDef d = gem(f, data);
            if (gemIds.fresh(d.id, f)) {
                registry.put(d);
            }
        }
        Ids craftIds = new Ids("craft", problems);
        for (Fields f : data.objects(DataFiles.CRAFTS, "craft")) {
            CraftDef d = craft(f);
            if (craftIds.fresh(d.id, f)) {
                registry.put(d);
            }
        }
        Ids questIds = new Ids("quest", problems);
        for (Fields f : data.objects(DataFiles.QUESTS, "quest")) {
            QuestDef d = quest(f, problems);
            if (questIds.fresh(d.id, f)) {
                registry.put(d);
            }
        }
        Ids skillIds = new Ids("skill", problems);
        for (Fields f : data.objects(DataFiles.SKILLS, "skill")) {
            SkillDef d = skill(f);
            if (skillIds.fresh(d.id, f)) {
                registry.put(d);
            }
        }
        return registry;
    }

    // ---- one def each -----------------------------------------------------

    private static EnemyDef enemy(Fields f) {
        String id = f.id();
        EnemyDef d = new EnemyDef(
            id, f.string("nameKey"), f.string("sprite"), f.integer("cell"),
            f.integer("maxHp"), f.integer("contactDamage"), f.integer("attackDamage"),
            f.number("moveSpeed"),
            f.string("brain"), f.number("aggroRange"), f.number("attackRange"),
            f.integer("windupSteps"), f.integer("activeSteps"), f.integer("recoverSteps"),
            f.integer("cooldownSteps"), f.number("knockbackResist"), f.integer("hurtInvulnSteps"),
            f.string("lootTable"), f.integer("goldMin"), f.integer("goldMax"),
            f.bool("boss", false), f.integerOr("phases", 1), f.bool("flying", false),
            f.stringOr("evolvesInto", null), f.stringsOr("summons"),
            f.numberOr("enrageAt", 0f), f.stringOr("projectile", null));
        f.done();
        return d;
    }

    private static WeaponDef weapon(Fields f, DataFiles data) {
        String id = f.id();
        WeaponDef d = new WeaponDef(
            id, f.string("nameKey"), f.string("descKey"), f.string("sprite"),
            f.icon("icon", data.icons),
            f.integer("damage"), f.number("reach"), f.number("width"),
            f.integer("windupSteps"), f.integer("activeSteps"), f.integer("recoverSteps"),
            f.number("knockback"), f.integer("rootSteps"),
            f.stringOr("projectile", null));
        f.done();
        return d;
    }

    private static GearDef gear(Fields f, DataFiles data) {
        String id = f.id();
        GearDef d = new GearDef(
            id, f.string("nameKey"), f.string("descKey"), f.icon("icon", data.icons),
            f.enumeration("slot", GearDef.Slot.class),
            f.integer("tier"), f.integerOr("sockets", 0),
            f.strings("effects"), f.numbers("magnitudes"), f.integerOr("price", 0));
        f.done();
        return d;
    }

    private static GemDef gem(Fields f, DataFiles data) {
        String id = f.id();
        GemDef d = new GemDef(
            id, f.string("nameKey"), f.icon("icon", data.icons),
            f.enumeration("colour", GemDef.Colour.class), f.integer("tier"),
            f.string("effect"), f.number("magnitude"), f.integerOr("price", 0));
        f.done();
        return d;
    }

    private static CraftDef craft(Fields f) {
        String id = f.id();
        CraftDef d = new CraftDef(
            id, f.enumeration("kind", CraftDef.Output.class), f.string("output"),
            f.strings("inputs"), f.integers("counts"), f.integerOr("goldCost", 0),
            f.stringOr("requirement", "none"), f.integerOr("requirementValue", 0));
        f.done();
        return d;
    }

    /**
     * A quest and its steps.
     *
     * <p>Steps are a nested array of objects, which {@link Fields} reads as a
     * raw child - the only shape in the content that needs it, and the reason
     * {@code Fields.child} exists. Each step is built by hand here rather than
     * through a second {@code Fields}, because a step has three keys and no
     * optional ones.
     */
    /**
     * A skill.
     *
     * <p>{@code cooldown} and {@code duration} are read as seconds and turned
     * into steps by {@link SkillDef}: the file is written by whoever is tuning
     * the game, and they think in seconds.
     */
    private static SkillDef skill(Fields f) {
        String id = f.id();
        SkillDef d = new SkillDef(
            id, f.string("nameKey"), f.string("descKey"), f.string("icon"),
            f.integer("slot"), f.enumeration("kind", SkillDef.Kind.class),
            f.number("cooldown"), f.numberOr("duration", 0f),
            f.numberOr("damageMult", 0f), f.numberOr("range", 0f),
            f.numberOr("knockback", 0f), f.string("vfx"),
            f.stringsOr("effects"), f.numbersOr("magnitudes"),
            f.numberOr("hpCost", 0f), f.numberOr("hpFloor", 0f));
        f.done();
        return d;
    }

    private static QuestDef quest(Fields f, List<String> problems) {
        String id = f.id();
        com.badlogic.gdx.utils.JsonValue raw = f.child("steps");
        List<QuestDef.Step> steps = new ArrayList<>();
        if (raw == null || !raw.isArray() || raw.size == 0) {
            problems.add("quest '" + id + "': has no steps");
        } else {
            for (com.badlogic.gdx.utils.JsonValue s = raw.child; s != null; s = s.next) {
                String kind = s.getString("kind", "");
                QuestDef.Kind parsed = null;
                for (QuestDef.Kind k : QuestDef.Kind.values()) {
                    if (k.name().equals(kind)) {
                        parsed = k;
                    }
                }
                if (parsed == null) {
                    problems.add("quest '" + id + "': step kind '" + kind + "' is not one of "
                        + java.util.Arrays.toString(QuestDef.Kind.values()));
                    continue;
                }
                steps.add(new QuestDef.Step(parsed, s.getString("target", ""),
                                            Math.max(1, s.getInt("count", 1))));
            }
        }
        QuestDef d = new QuestDef(id, f.string("nameKey"), f.string("descKey"),
            f.stringOr("giver", null), f.stringOr("requires", null),
            steps.toArray(new QuestDef.Step[0]), f.integerOr("rewardGold", 0),
            f.stringsOr("rewardItems"), f.stringOr("rewardGear", null),
            f.stringOr("rewardWeapon", null));
        f.done();
        return d;
    }

    private static RelicDef relic(Fields f, DataFiles data) {
        String id = f.id();
        RelicDef d = new RelicDef(
            id, f.string("nameKey"), f.string("descKey"), f.icon("icon", data.icons),
            f.enumeration("rarity", RelicDef.Rarity.class),
            f.string("effect"), f.number("magnitude"));
        f.done();
        return d;
    }

    private static ItemDef item(Fields f, DataFiles data) {
        String id = f.id();
        ItemDef d = new ItemDef(
            id, f.string("nameKey"), f.string("descKey"), f.stringOr("sprite", null),
            f.icon("icon", data.icons), f.enumeration("kind", ItemDef.Kind.class),
            f.string("effect"), f.number("magnitude"), f.integer("stackSize"),
            f.integerOr("price", 0));
        f.done();
        return d;
    }

    private static LootTableDef table(Fields f) {
        String id = f.id();
        List<LootTableDef.Entry> entries = new ArrayList<>();
        JsonValue list = f.child("entries");
        if (list != null && !list.isArray()) {
            f.problem("'entries' should be an array");
        } else if (list != null) {
            int i = 0;
            for (JsonValue e = list.child; e != null; e = e.next, i++) {
                Fields ef = new Fields(e, f.where() + " entry #" + i, f.problemsSink());
                entries.add(new LootTableDef.Entry(ef.string("itemId"), ef.integer("weight"),
                    ef.integerOr("min", 1), ef.integerOr("max", 1)));
                ef.done();
            }
        }
        LootTableDef d = new LootTableDef(id, entries.toArray(new LootTableDef.Entry[0]),
            f.integer("nothingWeight"), f.integerOr("rolls", 1));
        f.done();
        return d;
    }

    private static FloorDef floor(Fields f) {
        int number = f.integer("number");
        f.label(String.valueOf(number));
        FloorDef d = new FloorDef(
            number, f.string("nameKey"), f.string("descKey"), f.string("biome"),
            sound(f, "music", Assets.MUSIC_DIR, true),
            sound(f, "ambient", Assets.SFX_DIR, false),
            sound(f, "bossMusic", Assets.MUSIC_DIR, false),
            f.integer("roomsMin"), f.integer("roomsMax"),
            f.integer("treasureRooms"), f.integer("shopRooms"),
            f.strings("enemies"), f.integers("enemyWeights"),
            f.integer("packMin"), f.integer("packMax"),
            f.stringOr("boss", null), f.bool("side", false),
            f.numberOr("hpScale", 1f), f.numberOr("damageScale", 1f));
        f.done();
        return d;
    }

    // ---- sound names --------------------------------------------------------

    /**
     * Turns a sound name from JSON into the path {@code AudioService} wants.
     *
     * <p>A name matching a string constant on {@link Assets} - {@code
     * MUSIC_DUNGEON} - resolves to that constant, so the tracks the game
     * already names are referred to the same way everywhere. Anything else is a
     * name relative to {@code baseDir}: {@code 30_ruins.ogg} under the music
     * directory, {@code ambient/river.wav} under the sound-effect one. The
     * directory itself is only ever written in {@code Assets}.
     */
    static String sound(Fields f, String field, String baseDir, boolean required) {
        String name = required ? f.string(field) : f.stringOr(field, null);
        if (name == null) {
            return null;
        }
        String constant = assetsConstant(name);
        if (constant != null) {
            return constant;
        }
        if (name.startsWith("/") || name.startsWith("\\") || name.contains("..")
            || name.contains(":") || name.startsWith("assets")) {
            f.problem("'" + field + "' is '" + name + "': write a name relative to "
                + baseDir + " or an Assets constant, not a path");
            return null;
        }
        return baseDir + name;
    }

    private static String assetsConstant(String name) {
        try {
            Field field = Assets.class.getField(name);
            if (Modifier.isStatic(field.getModifiers()) && field.getType() == String.class) {
                return (String) field.get(null);
            }
        } catch (NoSuchFieldException | IllegalAccessException ignored) {
            // Not a constant; read as a relative name.
        }
        return null;
    }

    /** Duplicate ids overwrite silently in an ObjectMap; this is where that stops. */
    private static final class Ids {
        private final String kind;
        private final List<String> problems;
        private final Set<String> seen = new HashSet<>();

        Ids(String kind, List<String> problems) {
            this.kind = kind;
            this.problems = problems;
        }

        boolean fresh(String id, Fields f) {
            if (id == null) {
                return false;
            }
            if (!seen.add(id)) {
                problems.add(f.where() + ": " + kind + " id '" + id + "' is defined twice");
                return false;
            }
            return true;
        }
    }

    private ContentLoader() {}
}
