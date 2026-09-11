package com.kagebi.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.Predicate;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.kagebi.assets.Assets;
import com.kagebi.data.def.EnemyDef;
import com.kagebi.data.def.FloorDef;
import com.kagebi.data.def.ItemDef;
import com.kagebi.data.def.LootTableDef;
import com.kagebi.data.def.RelicDef;
import com.kagebi.data.def.WeaponDef;
import com.kagebi.save.Profile;

/**
 * Checks that the content makes sense as a whole, and fails the boot if not.
 *
 * <p>Everything here is a mistake that playing would not reliably reveal. A
 * relic with a misspelt effect shows its icon, reads its tooltip and does
 * nothing at all. An enemy whose loot table does not exist drops nothing,
 * which looks like bad luck. A floor that names a missing enemy spawns a gap.
 * Each of those takes an hour of play to suspect and minutes to find once
 * suspected; here they take one boot and are listed by id.
 *
 * <p>The vocabularies below - effect names, brains, biomes, projectiles - are
 * the contract with the combat, ai and procgen packages. They are written here
 * rather than discovered from those packages because those packages are being
 * written at the same time as this one; {@code notes/d.md} is the prose copy.
 * Adding a name means adding it here and implementing it there.
 */
public final class ContentValidator {

    /** Every relic effect combat must interpret. See notes/d.md section 1. */
    public static final Set<String> RELIC_EFFECTS = Set.of(
        "damage_mult", "attack_speed_mult", "damage_taken_mult", "move_speed_mult",
        "reach_add", "max_hp_add", "gold_mult", "luck_add", "invuln_steps_add",
        "heal_on_kill", "crit_chance_add", "crit_damage_mult", "lifesteal",
        "chain_lightning", "slow_on_hit", "poison_on_hit", "revive_once",
        "throw_extra", "roll_invuln_add", "damage_mult_low_hp", "burn_aura",
        "glass_cannon", "room_clear_heal");

    /** Every pickup effect the inventory and pickup code must interpret. */
    public static final Set<String> ITEM_EFFECTS = Set.of(
        "heal", "cure_poison", "max_hp_add", "gold", "key",
        "speed_buff", "damage_buff", "shield_buff", "drop_aggro", "reveal_map");

    /** Every village-upgrade and character-perk effect run start must apply. */
    public static final Set<String> UPGRADE_EFFECTS = Set.of(
        "max_hp_add", "melee_damage_mult", "move_speed_mult", "gold_mult",
        "potion_capacity_add", "start_keys_add", "revive_once", "darkness_resist");

    /** Every brain the ai package must provide. See notes/d.md section 2. */
    public static final Set<String> BRAINS = Set.of(
        "chaser", "hopper", "wanderer", "flyer", "shooter", "charger",
        "ambusher", "orbiter", "splitter", "caster", "boss_frog", "boss_tengu");

    /**
     * Folder names under {@code assets/maps/rooms/}. Procgen generated the
     * ruins interior once per colourway, so there are three ruins biomes, and
     * a floor naming any other would find no rooms at all.
     */
    public static final Set<String> BIOMES = Set.of(
        "ruins", "ruins_green", "ruins_orange", "depths");

    /**
     * Projectile ids a thrown weapon may name. Enemies fire others too, but
     * EnemyDef has no field to name them, so those live in notes/d.md section 3
     * and nothing here can check them.
     */
    public static final Set<String> PROJECTILES = Set.of("kunai", "shuriken");

    /**
     * Icons in the Raven grid: 16 columns by 137 rows, measured from the
     * 256x2192 sheet. Indices are 1-based; -1 means none.
     */
    public static final int ICON_COUNT = 16 * 137;

    // ---- the assets the content is checked against ------------------------------

    /**
     * Region names per atlas, translation keys per language, and a way to ask
     * whether a file exists. Built from disk by {@link #read}, or by hand in a
     * test that wants to check three defs against three regions.
     */
    public static final class AssetIndex {
        final Map<String, Set<String>> regionsByAtlas;
        final Map<String, Set<String>> keysByLanguage;
        final Predicate<String> fileExists;

        public AssetIndex(Map<String, Set<String>> regionsByAtlas,
                          Map<String, Set<String>> keysByLanguage,
                          Predicate<String> fileExists) {
            this.regionsByAtlas = regionsByAtlas;
            this.keysByLanguage = keysByLanguage;
            this.fileExists = fileExists;
        }

        public static AssetIndex read(Function<String, FileHandle> files) {
            Map<String, Set<String>> regions = new HashMap<>();
            for (String atlas : new String[] {
                    Assets.ATLAS_UI, Assets.ATLAS_ACTORS, Assets.ATLAS_FX, Assets.ATLAS_NPC}) {
                regions.put(atlas, atlasRegions(files.apply(atlas)));
            }
            return new AssetIndex(regions, translationKeys(files.apply(Assets.I18N_DIR)),
                path -> files.apply(path).exists());
        }

        /**
         * Parses the atlas text with libGDX's own reader, which builds no
         * texture and so needs no GL context.
         */
        static Set<String> atlasRegions(FileHandle atlas) {
            Set<String> out = new HashSet<>();
            if (!atlas.exists()) {
                return out;
            }
            TextureAtlasData data = new TextureAtlasData(atlas, atlas.parent(), false);
            for (TextureAtlasData.Region r : data.getRegions()) {
                out.add(r.name);
            }
            return out;
        }

        /**
         * Keys per language, grouped by the code before {@code .json}: both
         * {@code vi.json} and {@code content.vi.json} are "vi". Listing rather
         * than naming the files keeps the naming scheme in one place, I18n.
         */
        static Map<String, Set<String>> translationKeys(FileHandle dir) {
            Map<String, Set<String>> out = new TreeMap<>();
            if (!dir.isDirectory()) {
                return out;
            }
            for (FileHandle file : dir.list(".json")) {
                String stem = file.nameWithoutExtension();
                String lang = stem.substring(stem.lastIndexOf('.') + 1);
                Set<String> keys = out.computeIfAbsent(lang, k -> new HashSet<>());
                JsonValue root = new JsonReader().parse(file);
                for (JsonValue e = root.child; e != null; e = e.next) {
                    keys.add(e.name);
                }
            }
            return out;
        }

        Set<String> regions(String atlas) {
            return regionsByAtlas.getOrDefault(atlas, Set.of());
        }
    }

    // ---- the checks --------------------------------------------------------------

    public static List<String> check(ContentRegistry reg, ShopCatalog shop, AssetIndex assets) {
        Check c = new Check(assets);

        if (reg.allFloors().size == 0) {
            c.problems.add("no floors defined - is the content directory empty?");
        }
        for (EnemyDef e : reg.allEnemies()) {
            enemy(c, reg, e);
        }
        for (FloorDef f : reg.allFloors()) {
            floor(c, reg, f);
        }
        for (WeaponDef w : reg.allWeapons()) {
            weapon(c, w);
        }
        for (RelicDef r : reg.allRelics()) {
            relic(c, r);
        }
        for (ItemDef i : reg.allItems()) {
            item(c, i);
        }
        for (LootTableDef t : reg.allLootTables()) {
            table(c, reg, t);
        }
        orphans(c, reg);
        shop(c, reg, shop);
        return c.problems;
    }

    private static void enemy(Check c, ContentRegistry reg, EnemyDef e) {
        String w = "enemy '" + e.id + "'";
        c.key(w, "nameKey", e.nameKey);
        c.region(w, e.sprite, Assets.ATLAS_ACTORS);
        c.positive(w, "cell", e.cell);
        c.positive(w, "maxHp", e.maxHp);
        c.atLeast(w, "contactDamage", e.contactDamage, 0);
        c.atLeast(w, "attackDamage", e.attackDamage, 0);
        if (e.contactDamage == 0 && e.attackDamage == 0) {
            c.fail(w, "can deal no damage at all");
        }
        c.atLeast(w, "moveSpeed", e.moveSpeed, 0);
        if (!BRAINS.contains(e.brain)) {
            c.fail(w, "brain '" + e.brain + "' is not one of " + new java.util.TreeSet<>(BRAINS));
        }
        c.positive(w, "aggroRange", e.aggroRange);
        c.atLeast(w, "attackRange", e.attackRange, 0);
        c.atLeast(w, "windupSteps", e.windupSteps, 0);
        c.positive(w, "activeSteps", e.activeSteps);
        c.atLeast(w, "recoverSteps", e.recoverSteps, 0);
        c.atLeast(w, "cooldownSteps", e.cooldownSteps, 0);
        c.atLeast(w, "hurtInvulnSteps", e.hurtInvulnSteps, 0);
        if (e.knockbackResist < 0 || e.knockbackResist > 1) {
            c.fail(w, "knockbackResist " + e.knockbackResist + " is outside 0..1");
        }
        if (!reg.hasLootTable(e.lootTable)) {
            c.fail(w, "lootTable '" + e.lootTable + "' does not exist");
        }
        if (e.goldMin < 0 || e.goldMin > e.goldMax) {
            c.fail(w, "gold range " + e.goldMin + ".." + e.goldMax + " is empty or negative");
        }
        // A boss brain on a trash mob, or a trash brain on a boss, is a copy-
        // paste mistake that produces a fight nobody designed.
        if (e.boss != e.brain.startsWith("boss_")) {
            c.fail(w, e.boss ? "is a boss but its brain '" + e.brain + "' is not a boss brain"
                             : "is not a boss but uses boss brain '" + e.brain + "'");
        }
        if (e.phases < 1 || (!e.boss && e.phases != 1)) {
            c.fail(w, "phases " + e.phases + " - only a boss may have more than one");
        }
        if (e.phases > 1) {
            // The phase change plays the transformation strip; without it the
            // boss would snap from one form to the next with nothing between.
            String trans = e.sprite.substring(0, e.sprite.lastIndexOf('/') + 1) + "trans";
            c.region(w + " phase change", trans, Assets.ATLAS_ACTORS);
        }
    }

    private static void floor(Check c, ContentRegistry reg, FloorDef f) {
        String w = "floor " + f.number;
        c.key(w, "nameKey", f.nameKey);
        if (!BIOMES.contains(f.biome)) {
            c.fail(w, "biome '" + f.biome + "' is not one of " + new java.util.TreeSet<>(BIOMES));
        }
        c.file(w, "music", f.music, true);
        c.file(w, "ambient", f.ambient, false);
        c.file(w, "bossMusic", f.bossMusic, false);
        if (f.enemies.length == 0) {
            c.fail(w, "has no enemies");
        }
        if (f.enemies.length != f.enemyWeights.length) {
            c.fail(w, f.enemies.length + " enemies but " + f.enemyWeights.length + " weights");
        }
        for (int i = 0; i < f.enemies.length; i++) {
            String id = f.enemies[i];
            if (!reg.hasEnemy(id)) {
                c.fail(w, "enemy '" + id + "' does not exist");
            } else if (reg.enemy(id).boss) {
                c.fail(w, "enemy '" + id + "' is a boss and would spawn in ordinary rooms");
            }
            if (i < f.enemyWeights.length && f.enemyWeights[i] <= 0) {
                c.fail(w, "weight for '" + id + "' is " + f.enemyWeights[i] + ", must be positive");
            }
        }
        if (f.boss != null) {
            if (!reg.hasEnemy(f.boss)) {
                c.fail(w, "boss '" + f.boss + "' does not exist");
            } else if (!reg.enemy(f.boss).boss) {
                c.fail(w, "boss '" + f.boss + "' is not marked boss: true");
            }
        }
        c.atLeast(w, "treasureRooms", f.treasureRooms, 0);
        c.atLeast(w, "shopRooms", f.shopRooms, 0);
        // Start and exit always exist, plus every special room asked for; a
        // floor that cannot fit them would be generated short of them.
        int needed = 2 + f.treasureRooms + f.shopRooms + (f.hasBoss() ? 1 : 0);
        if (f.roomsMin < needed) {
            c.fail(w, "roomsMin " + f.roomsMin + " cannot fit start, exit and "
                + (needed - 2) + " special rooms");
        }
        if (f.roomsMin > f.roomsMax) {
            c.fail(w, "roomsMin " + f.roomsMin + " > roomsMax " + f.roomsMax);
        }
        c.positive(w, "packMin", f.packMin);
        if (f.packMin > f.packMax) {
            c.fail(w, "packMin " + f.packMin + " > packMax " + f.packMax);
        }
    }

    private static void weapon(Check c, WeaponDef wd) {
        String w = "weapon '" + wd.id + "'";
        c.key(w, "nameKey", wd.nameKey);
        c.key(w, "descKey", wd.descKey);
        // Held weapons are drawn over the player from the actor atlas; thrown
        // ones only exist as projectiles, which live in the effects atlas.
        c.region(w, wd.sprite, wd.thrown() ? Assets.ATLAS_FX : Assets.ATLAS_ACTORS);
        c.icon(w, wd.icon, false);
        c.positive(w, "damage", wd.damage);
        c.positive(w, "reach", wd.reach);
        c.positive(w, "width", wd.width);
        c.atLeast(w, "windupSteps", wd.windupSteps, 0);
        c.positive(w, "activeSteps", wd.activeSteps);
        c.atLeast(w, "recoverSteps", wd.recoverSteps, 0);
        c.atLeast(w, "knockback", wd.knockback, 0);
        c.atLeast(w, "rootSteps", wd.rootSteps, 0);
        if (wd.projectile != null && !PROJECTILES.contains(wd.projectile)) {
            c.fail(w, "projectile '" + wd.projectile + "' is not one of "
                + new java.util.TreeSet<>(PROJECTILES));
        }
    }

    private static void relic(Check c, RelicDef r) {
        String w = "relic '" + r.id + "'";
        c.key(w, "nameKey", r.nameKey);
        c.key(w, "descKey", r.descKey);
        // Relics have no pack art; the icon is all there is to draw.
        c.icon(w, r.icon, true);
        if (r.rarity == null) {
            c.fail(w, "has no rarity");
        }
        if (!RELIC_EFFECTS.contains(r.effect)) {
            c.fail(w, "effect '" + r.effect + "' is not one combat implements - it would do nothing."
                + " Known: " + new java.util.TreeSet<>(RELIC_EFFECTS));
        }
        if (!(r.magnitude > 0) || Float.isInfinite(r.magnitude)) {
            c.fail(w, "magnitude " + r.magnitude + " must be a positive number");
        }
    }

    private static void item(Check c, ItemDef i) {
        String w = "item '" + i.id + "'";
        c.key(w, "nameKey", i.nameKey);
        c.key(w, "descKey", i.descKey);
        if (i.sprite != null) {
            c.region(w, i.sprite, Assets.ATLAS_UI);
        }
        c.icon(w, i.icon, i.sprite == null);
        if (i.kind == null) {
            c.fail(w, "has no kind");
        }
        if (!ITEM_EFFECTS.contains(i.effect)) {
            c.fail(w, "effect '" + i.effect + "' is not one the pickup code implements."
                + " Known: " + new java.util.TreeSet<>(ITEM_EFFECTS));
        }
        // Gold and keys are routed by kind, not by effect: a GOLD item with a
        // "heal" effect would add to the purse and never heal.
        boolean goldKind = i.kind == ItemDef.Kind.GOLD;
        boolean keyKind = i.kind == ItemDef.Kind.KEY;
        if (goldKind != "gold".equals(i.effect)) {
            c.fail(w, "kind " + i.kind + " and effect '" + i.effect + "' disagree about gold");
        }
        if (keyKind != "key".equals(i.effect)) {
            c.fail(w, "kind " + i.kind + " and effect '" + i.effect + "' disagree about keys");
        }
        c.positive(w, "stackSize", i.stackSize);
        c.atLeast(w, "magnitude", i.magnitude, 0);
    }

    private static void table(Check c, ContentRegistry reg, LootTableDef t) {
        String w = "loot table '" + t.id + "'";
        c.positive(w, "rolls", t.rolls);
        c.atLeast(w, "nothingWeight", t.nothingWeight, 0);
        if (t.entries.length == 0) {
            c.fail(w, "has no entries");
        }
        for (LootTableDef.Entry e : t.entries) {
            String ew = w + " entry '" + e.itemId + "'";
            if (!reg.hasItem(e.itemId)) {
                c.fail(ew, "item does not exist");
            }
            c.positive(ew, "weight", e.weight);
            c.positive(ew, "min", e.min);
            if (e.min > e.max) {
                c.fail(ew, "min " + e.min + " > max " + e.max);
            }
        }
        if (t.totalWeight() <= 0) {
            c.fail(w, "total weight is " + t.totalWeight());
        }
    }

    /**
     * Content nobody can meet is almost always content somebody forgot to add
     * to a floor, and it is invisible until someone wonders where it went.
     */
    private static void orphans(Check c, ContentRegistry reg) {
        Set<String> placed = new HashSet<>();
        for (FloorDef f : reg.allFloors()) {
            placed.addAll(java.util.Arrays.asList(f.enemies));
            if (f.boss != null) {
                placed.add(f.boss);
            }
        }
        for (EnemyDef e : reg.allEnemies()) {
            if (!placed.contains(e.id)) {
                c.fail("enemy '" + e.id + "'", "appears on no floor");
            }
        }
    }

    private static void shop(Check c, ContentRegistry reg, ShopCatalog shop) {
        Set<String> seen = new HashSet<>();
        for (ShopCatalog.Upgrade u : shop.upgrades()) {
            String w = "upgrade '" + u.id + "'";
            if (!seen.add(u.id)) {
                c.fail(w, "is defined twice");
            }
            c.key(w, "nameKey", u.nameKey);
            c.key(w, "descKey", u.descKey);
            c.icon(w, u.icon, true);
            if (!UPGRADE_EFFECTS.contains(u.effect)) {
                c.fail(w, "effect '" + u.effect + "' is not one run start applies."
                    + " Known: " + new java.util.TreeSet<>(UPGRADE_EFFECTS));
            }
            c.positive(w, "maxLevel", u.maxLevel);
            if (u.costs.length != u.maxLevel) {
                c.fail(w, u.maxLevel + " levels but " + u.costs.length + " costs");
            }
            for (int i = 0; i < u.costs.length; i++) {
                if (u.costs[i] <= 0 || (i > 0 && u.costs[i] <= u.costs[i - 1])) {
                    c.fail(w, "costs must be positive and rising, found " + java.util.Arrays.toString(u.costs));
                    break;
                }
            }
        }

        Profile fresh = new Profile();
        // ContentRegistry has no hasWeapon(), and is not this package's to extend.
        Set<String> weapons = new HashSet<>();
        for (WeaponDef wd : reg.allWeapons()) {
            weapons.add(wd.id);
        }
        Set<String> unlockable = new HashSet<>();
        for (ShopCatalog.Unlock u : shop.unlocks()) {
            String w = "unlock '" + u.id + "'";
            if (!seen.add(u.id)) {
                c.fail(w, "is defined twice");
            }
            c.key(w, "nameKey", u.nameKey);
            c.key(w, "descKey", u.descKey);
            c.icon(w, u.icon, true);
            c.positive(w, "cost", u.cost);
            c.atLeast(w, "requirementValue", u.requirementValue, 0);
            if (!ShopCatalog.REQUIREMENTS.contains(u.requirement)) {
                c.fail(w, "requirement '" + u.requirement + "' is not one of "
                    + new java.util.TreeSet<>(ShopCatalog.REQUIREMENTS));
            }
            if (u.kind == ShopCatalog.UnlockKind.CHARACTER) {
                if (!java.util.Arrays.asList(Assets.Actor.CHARACTERS).contains(u.id)) {
                    c.fail(w, "is not a character in Assets.Actor.CHARACTERS");
                }
                // A perk is what makes a character a choice rather than a palette.
                if (u.effect == null || !UPGRADE_EFFECTS.contains(u.effect)) {
                    c.fail(w, "character perk '" + u.effect + "' is not an upgrade effect");
                }
            } else if (u.kind == ShopCatalog.UnlockKind.WEAPON) {
                if (!weapons.contains(u.id)) {
                    c.fail(w, "is not a weapon");
                }
                if (u.effect != null) {
                    c.fail(w, "a weapon unlock's effect would be silently ignored");
                }
            } else {
                c.fail(w, "has no kind");
            }
            unlockable.add(u.id);
        }
        // Everything not free from the start has to be buyable somewhere, or it
        // is content that exists and can never be used.
        for (String ch : Assets.Actor.CHARACTERS) {
            if (!fresh.unlockedCharacters.contains(ch) && !unlockable.contains(ch)) {
                c.fail("character '" + ch + "'", "is locked and nothing unlocks it");
            }
        }
        for (WeaponDef wd : reg.allWeapons()) {
            if (!fresh.unlockedWeapons.contains(wd.id) && !unlockable.contains(wd.id)) {
                c.fail("weapon '" + wd.id + "'", "is locked and nothing unlocks it");
            }
        }
        for (String starter : fresh.unlockedWeapons) {
            if (!weapons.contains(starter)) {
                c.fail("Profile", "starts with weapon '" + starter + "', which does not exist");
            }
        }
    }

    // ---- reporting ---------------------------------------------------------------

    /** Throws one exception listing every problem, each once, in the order found. */
    public static void throwIfAny(List<String> problems) {
        if (problems.isEmpty()) {
            return;
        }
        Set<String> distinct = new LinkedHashSet<>(problems);
        StringBuilder sb = new StringBuilder()
            .append(distinct.size()).append(" content problem")
            .append(distinct.size() == 1 ? "" : "s").append(" in ").append(Assets.DATA_DIR).append(':');
        for (String p : distinct) {
            sb.append("\n  - ").append(p);
        }
        throw new IllegalStateException(sb.toString());
    }

    /** Accumulates problems; one method per kind of check keeps the messages uniform. */
    private static final class Check {
        final List<String> problems = new ArrayList<>();
        final AssetIndex assets;

        Check(AssetIndex assets) {
            this.assets = assets;
        }

        void fail(String where, String message) {
            problems.add(where + ": " + message);
        }

        void key(String where, String field, String key) {
            if (key == null) {
                fail(where, field + " is missing");
                return;
            }
            for (Map.Entry<String, Set<String>> lang : assets.keysByLanguage.entrySet()) {
                if (!lang.getValue().contains(key)) {
                    fail(where, field + " '" + key + "' is not translated in '" + lang.getKey() + "'");
                }
            }
        }

        void region(String where, String region, String atlas) {
            if (region == null || !assets.regions(atlas).contains(region)) {
                fail(where, "sprite '" + region + "' is not a region in " + atlas);
            }
        }

        void icon(String where, int icon, boolean required) {
            if (icon == -1) {
                if (required) {
                    fail(where, "has nothing to draw: no sprite and no icon");
                }
            } else if (icon < 1 || icon > ICON_COUNT) {
                fail(where, "icon " + icon + " is outside the grid's 1.." + ICON_COUNT);
            }
        }

        void file(String where, String field, String path, boolean required) {
            if (path == null) {
                if (required) {
                    fail(where, field + " is missing");
                }
                return;
            }
            if (!assets.fileExists.test(path)) {
                fail(where, field + " '" + path + "' does not exist");
            }
        }

        void positive(String where, String field, float value) {
            if (!(value > 0)) {
                fail(where, field + " is " + value + ", must be positive");
            }
        }

        void atLeast(String where, String field, float value, float min) {
            if (!(value >= min)) {
                fail(where, field + " is " + value + ", must be at least " + min);
            }
        }
    }

    private ContentValidator() {}
}
