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
import com.kagebi.combat.Modifiers;
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
        "heal_on_kill", "crit_chance_add", "crit_damage_mult",
        "chain_lightning", "slow_on_hit", "poison_on_hit", "revive_once",
        "throw_extra", "roll_invuln_add", "damage_mult_low_hp", "burn_aura",
        "glass_cannon", "room_clear_heal");

    /**
     * Every effect a piece of gear or a stone set into it may carry.
     *
     * <p>Its own vocabulary rather than a corner of {@link #RELIC_EFFECTS},
     * because the two are found in different ways and mean different things to
     * a player. A relic is a run-long surprise pulled out of a chest and may do
     * something strange - chain lightning, a burning aura, glass cannon. Gear
     * is bought, forged and worn between runs, and is deliberately dull: nine
     * numbers that go up. Nothing here needs new combat code, which is the
     * point - {@code Loadout} folds gear into the same {@link
     * com.kagebi.combat.Modifiers} everything else already writes to.
     *
     * <p>{@code armour_add} and {@code throw_damage_mult} are the two that had
     * no source before this: armour was a field nothing wrote, and the off hand
     * shared the main hand's multiplier.
     */
    /** Four tiers of gear and three of stone; what the forge and the shelf draw. */
    public static final int MAX_GEAR_TIER = 4;
    public static final int MAX_GEM_TIER = 3;
    /** Holes in one piece. Three fit across the panel; a fourth would not. */
    public static final int MAX_SOCKETS = 3;

    /** Keys on the skill bar. Three, because three were asked for and three fit. */
    public static final int SKILL_SLOTS = 3;

    public static final Set<String> GEAR_EFFECTS = Set.of(
        "max_hp_add", "armour_add", "damage_mult", "crit_chance_add",
        "crit_damage_mult", "throw_damage_mult", "attack_speed_mult",
        "move_speed_mult", "gold_mult");

    /**
     * Every effect an ultimate may put on the player while it is up.
     *
     * <p>Its own vocabulary for the same reason gear has one: these are
     * temporary and self-inflicted, which no relic or stone is. The set is
     * deliberately tiny - a transformation that lasts eight seconds should be
     * legible in one line of text, and eight numbers going up at once is not.
     *
     * <p>{@code armour_mult} is the only name here that is new to the game, and
     * it exists because an ultimate is a trade: the strike is harder and the
     * skin is thinner. Nothing else in the game reduces a statistic, so nothing
     * else needed it.
     *
     * <p>Three of these are the fire set's, and two of the three point outward
     * rather than at the caster - the first skill effects that do. {@code
     * burn_aura} is borrowed whole from the relics, which is the point of
     * these being names rather than code: the burning aura was already written
     * and already tested, and an ultimate that burns what stands near it
     * needed a magnitude, not a mechanism. {@code damage_taken_mult} is
     * borrowed the same way and needed nothing at all.
     *
     * <p>{@code heal_on_hurt} is the odd one and is the first effect in the
     * game that pays out for being hit. It exists because a ward that only
     * reduced damage would be a number the player cannot see working; healing
     * is the same number said out loud.
     */
    public static final Set<String> SKILL_EFFECTS = Set.of(
        "damage_mult", "crit_chance_add", "crit_damage_mult", "throw_damage_mult",
        "attack_speed_mult", "move_speed_mult", "armour_mult", "damage_taken_mult",
        "burn_aura", "burn_on_hit", "throw_reach_mult",
        "venom_on_hit", "heal_on_hurt");

    /**
     * The strips the three {@code tools/make_*fx.py} write, and the only ones
     * a skill may name.
     */
    public static final Set<String> SKILL_VFX = Set.of(
        "bolt", "trail", "strike", "shock", "nova", "aura",
        "firerun", "fireburst", "fireblast", "firering", "firestar",
        "firebloom", "firehit", "flamelash", "sunburn", "brightfire",
        "venomfall", "venombolt", "venomdash", "venommark",
        "venomdrain", "venomburst", "starfall", "starcomet", "starward");

    /** Icons those tools write beside them. */
    public static final Set<String> SKILL_ICONS = Set.of(
        "bolt", "nova", "shock", "fireblast", "firering", "firestar",
        "starfall", "starcomet", "starward", "venommark");

    /**
     * What colour of stone may carry what, which is the whole of the socket
     * system's design: a player learns "red is damage" once and then knows what
     * every red stone in the game is for without reading it.
     */
    public static final java.util.Map<String, Set<String>> GEM_COLOURS =
        java.util.Map.of(
            "RED", Set.of("damage_mult", "crit_chance_add"),
            "GREEN", Set.of("max_hp_add", "armour_add"),
            "BLUE", Set.of("attack_speed_mult", "move_speed_mult"),
            "YELLOW", Set.of("crit_damage_mult", "throw_damage_mult"),
            "PURPLE", Set.of("gold_mult"));

    /**
     * The villagers a quest may be given by or sent to.
     *
     * <p>Written here rather than read from {@code HubScreen.VILLAGERS},
     * because this class must stay free of anything that needs a screen or a
     * texture - it is run by tests with no GL context. The two lists agreeing
     * is what {@code HubMarksTest} is for.
     */
    public static final Set<String> VILLAGERS = Set.of("elder", "master", "herbalist");

    /** Every pickup effect the inventory and pickup code must interpret. */
    public static final Set<String> ITEM_EFFECTS = Set.of(
        "heal", "cure_poison", "max_hp_add", "gold", "diamond", "key",
        "speed_buff", "damage_buff", "shield_buff", "drop_aggro", "reveal_map");

    /** Every village-upgrade and character-perk effect run start must apply. */
    public static final Set<String> UPGRADE_EFFECTS = Set.of(
        "max_hp_add", "melee_damage_mult", "move_speed_mult", "gold_mult",
        "potion_capacity_add", "start_keys_add", "revive_once");

    /** Every brain the ai package must provide. See notes/d.md section 2. */
    public static final Set<String> BRAINS = Set.of(
        "chaser", "hopper", "wanderer", "flyer", "shooter", "charger",
        "ambusher", "orbiter", "splitter", "caster", "burster", "bomber",
        "boss_frog", "boss_tengu",
        "boss_pirateleader", "boss_orb", "boss_orb_tide",
        "boss_piratezombie", "boss_squidman", "boss_squidlord");

    /**
     * Folder names under {@code assets/maps/rooms/}. Procgen generated the
     * ruins interior once per colourway, so there are three ruins biomes, and
     * a floor naming any other would find no rooms at all.
     */
    public static final Set<String> BIOMES = Set.of(
        "ruins", "ruins_green", "ruins_orange", "depths", "cove", "cove_deep");

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

    /** Everything but the village economy, for callers that have no catalog of it. */
    public static List<String> check(ContentRegistry reg, ShopCatalog shop, AssetIndex assets) {
        return check(reg, shop, null, assets);
    }

    /** Every check, the village's included unless {@code village} is null. */
    public static List<String> check(ContentRegistry reg, ShopCatalog shop, VillageCatalog village,
                                     AssetIndex assets) {
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
        for (GearDef g : reg.allGear()) {
            gear(c, g);
        }
        for (GemDef g : reg.allGems()) {
            gem(c, g);
        }
        for (CraftDef cf : reg.allCrafts()) {
            craft(c, reg, cf);
        }
        // One set of slots per character, plus one for the shared set: two
        // skills may share slot 2 as long as no character can reach both.
        java.util.Map<String, Set<Integer>> slots = new java.util.HashMap<>();
        for (SkillDef s : reg.allSkills()) {
            skill(c, s, slots.computeIfAbsent(String.valueOf(s.character),
                                              k -> new java.util.HashSet<>()));
        }
        skillSets(c, reg);
        for (QuestDef q : reg.allQuests()) {
            quest(c, reg, q, VILLAGERS);
        }
        orphans(c, reg);
        shop(c, reg, shop);
        if (village != null) {
            village(c, reg, village);
        }
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
        if (e.evolvesInto != null) {
            if (!reg.hasEnemy(e.evolvesInto)) {
                c.fail(w, "evolvesInto '" + e.evolvesInto + "', which does not exist");
            } else if (!reg.enemy(e.evolvesInto).boss) {
                // Otherwise the successor is also in some floor's enemies
                // array, and the generator scatters copies of the boss's next
                // body through the ordinary rooms of the floor.
                c.fail(w, "evolvesInto '" + e.evolvesInto + "', which is not a boss");
            } else if (!e.boss) {
                c.fail(w, "evolvesInto '" + e.evolvesInto + "' but is not a boss itself");
            }
        }
        for (String add : e.summons) {
            if (!reg.hasEnemy(add)) {
                c.fail(w, "summons '" + add + "', which does not exist");
            } else if (reg.enemy(add).boss) {
                c.fail(w, "summons '" + add + "', which is a boss");
            }
        }
        if (e.enrageAt < 0f || e.enrageAt >= 1f) {
            c.fail(w, "enrageAt " + e.enrageAt + " - a fraction of maximum health, under 1");
        }
        if (e.enrageAt > 0f && !e.boss) {
            c.fail(w, "enrageAt " + e.enrageAt + " - only a boss enrages");
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
        c.key(w, "descKey", f.descKey);
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

    private static void gear(Check c, GearDef g) {
        String w = "gear '" + g.id + "'";
        c.key(w, "nameKey", g.nameKey);
        c.key(w, "descKey", g.descKey);
        c.icon(w, g.icon, true);
        c.atLeast(w, "tier", g.tier, 1);
        if (g.tier > MAX_GEAR_TIER) {
            c.fail(w, "tier " + g.tier + " is above the " + MAX_GEAR_TIER + " the forge knows");
        }
        c.atLeast(w, "sockets", g.sockets, 0);
        if (g.sockets > MAX_SOCKETS) {
            c.fail(w, "has " + g.sockets + " sockets; the panel draws " + MAX_SOCKETS);
        }
        c.atLeast(w, "price", g.price, 0);
        effects(c, w, g.effects, g.magnitudes);
    }

    private static void gem(Check c, GemDef g) {
        String w = "gem '" + g.id + "'";
        c.key(w, "nameKey", g.nameKey);
        c.icon(w, g.icon, true);
        c.atLeast(w, "tier", g.tier, 1);
        if (g.tier > MAX_GEM_TIER) {
            c.fail(w, "tier " + g.tier + " is above the " + MAX_GEM_TIER + " the forge knows");
        }
        c.atLeast(w, "price", g.price, 0);
        if (!GEAR_EFFECTS.contains(g.effect)) {
            c.fail(w, "effect '" + g.effect + "' is not one of "
                + new java.util.TreeSet<>(GEAR_EFFECTS));
            return;
        }
        // The colour is the label on the tin. A red stone that made the player
        // faster would teach them that the colours mean nothing, which is worse
        // than having no colours at all.
        Set<String> allowed = GEM_COLOURS.get(g.colour.name());
        if (allowed != null && !allowed.contains(g.effect)) {
            c.fail(w, "is " + g.colour + " but carries '" + g.effect + "'; "
                + g.colour + " is " + new java.util.TreeSet<>(allowed));
        }
        if (Modifiers.multiplicative(g.effect)) {
            c.positive(w, "magnitude", Math.round(g.magnitude * 1000));
        }
    }

    private static void craft(Check c, ContentRegistry reg, CraftDef cf) {
        String w = "craft '" + cf.id + "'";
        if (cf.inputs.length == 0) {
            c.fail(w, "has no inputs; a forge line that costs nothing is a free item");
        }
        if (cf.inputs.length != cf.counts.length) {
            c.fail(w, "has " + cf.inputs.length + " inputs and "
                + cf.counts.length + " counts");
        }
        for (int i = 0; i < cf.inputs.length; i++) {
            String in = cf.inputs[i];
            if (!reg.hasGem(in) && !reg.hasGear(in) && !reg.hasItem(in)) {
                c.fail(w, "input '" + in + "' is not a gem, a piece of gear or an item");
            }
            if (i < cf.counts.length) {
                c.positive(w, "counts[" + i + "]", cf.counts[i]);
            }
        }
        boolean made = cf.kind == CraftDef.Output.GEAR
            ? reg.hasGear(cf.output) : reg.hasGem(cf.output);
        if (!made) {
            c.fail(w, "makes '" + cf.output + "', which is not a " + cf.kind);
        }
        c.atLeast(w, "goldCost", cf.goldCost, 0);
        if (!ShopCatalog.REQUIREMENTS.contains(cf.requirement)) {
            c.fail(w, "requirement '" + cf.requirement + "' is not one of "
                + new java.util.TreeSet<>(ShopCatalog.REQUIREMENTS));
        }
        c.atLeast(w, "requirementValue", cf.requirementValue, 0);
    }

    /** The effects-and-magnitudes pair that gear carries, checked as one thing. */
    private static void effects(Check c, String where, String[] names, float[] magnitudes) {
        if (names.length == 0) {
            c.fail(where, "carries no effects; it would be an item with no reason to wear it");
        }
        if (names.length != magnitudes.length) {
            c.fail(where, "has " + names.length + " effects and "
                + magnitudes.length + " magnitudes");
        }
        for (int i = 0; i < names.length; i++) {
            if (!GEAR_EFFECTS.contains(names[i])) {
                c.fail(where, "effect '" + names[i] + "' is not one of "
                    + new java.util.TreeSet<>(GEAR_EFFECTS));
            }
        }
    }

    /**
     * A skill: its slot, its shape and the numbers that shape needs.
     *
     * <p>The checks that matter are the ones a player would otherwise discover
     * by pressing a key and having nothing happen: two skills on one key, a
     * lunge with no distance, a nova with no radius, an ultimate with no
     * duration. Every one of those loads and runs and simply does nothing.
     */
    /**
     * Every character's set is whole, and every named set belongs to somebody.
     *
     * <p>{@code skillsFor} takes a character's own skills if it has any and
     * the shared set otherwise, so a character given one fire skill would
     * silently lose the other two keys rather than mixing the sets. That is
     * the failure this catches: a half-written set validates perfectly
     * skill by skill.
     *
     * <p>A passive does not count towards the three. It is optional, at most
     * one, and a character that has one still owes three keys.
     */
    private static void skillSets(Check c, ContentRegistry reg) {
        java.util.Map<String, Integer> keyed = new java.util.TreeMap<>();
        java.util.Map<String, Integer> passives = new java.util.TreeMap<>();
        for (SkillDef s : reg.allSkills()) {
            if (s.character == null) {
                continue;
            }
            // Counted apart, because a passive is not one of the three keys and
            // a set of three keys plus a passive is whole, not one too many.
            if (s.kind == SkillDef.Kind.PASSIVE) {
                passives.merge(s.character, 1, Integer::sum);
            } else {
                keyed.merge(s.character, 1, Integer::sum);
            }
        }
        java.util.Set<String> all = new java.util.TreeSet<>(keyed.keySet());
        all.addAll(passives.keySet());
        for (String id : all) {
            String w = "character '" + id + "'";
            if (!java.util.Arrays.asList(Assets.Actor.CHARACTERS).contains(id)) {
                c.fail(w, "has skills but is not in Assets.Actor.CHARACTERS");
            }
            int keys = keyed.getOrDefault(id, 0);
            if (keys != SKILL_SLOTS) {
                c.fail(w, "has " + keys + " keyed skills of its own, not "
                    + SKILL_SLOTS + "; a part-written set loses the other keys");
            }
            if (passives.getOrDefault(id, 0) > 1) {
                // ContentRegistry.passiveFor returns the first it finds, so a
                // second one is a row nothing will ever read.
                c.fail(w, "has " + passives.get(id)
                    + " passives; only the first would ever be applied");
            }
        }
    }

    private static void skill(Check c, SkillDef s, Set<Integer> slots) {
        String w = "skill '" + s.id + "'";
        c.key(w, "nameKey", s.nameKey);
        c.key(w, "descKey", s.descKey);
        if (!SKILL_ICONS.contains(s.icon)) {
            c.fail(w, "icon '" + s.icon + "' is not one of "
                + new java.util.TreeSet<>(SKILL_ICONS));
        }
        if (!SKILL_VFX.contains(s.vfx)) {
            c.fail(w, "vfx '" + s.vfx + "' is not one of "
                + new java.util.TreeSet<>(SKILL_VFX));
        }
        for (String name : s.fx.named()) {
            if (!SKILL_VFX.contains(name)) {
                c.fail(w, "fx '" + name + "' is not one of "
                    + new java.util.TreeSet<>(SKILL_VFX));
            }
        }
        boolean passive = s.kind == SkillDef.Kind.PASSIVE;
        if (s.fx.any() && s.kind != SkillDef.Kind.AVATAR && s.kind != SkillDef.Kind.CHARGE) {
            c.fail(w, "carries extra fx but is a " + s.kind
                + ", which has nothing to draw them on");
        }
        if (passive) {
            // Not "slot 0 is allowed" but "slot 0 is required": a passive on a
            // real slot would take a key away from the set and then not answer
            // it, because Player.setSkills is the only thing that binds one.
            if (s.slot != 0) {
                c.fail(w, "is a PASSIVE on slot " + s.slot + "; a passive has no key");
            }
        } else if (s.slot < 1 || s.slot > SKILL_SLOTS) {
            c.fail(w, "slot " + s.slot + " is not 1 to " + SKILL_SLOTS);
        } else if (!slots.add(s.slot)) {
            // Both would be bound to the same key and one of them would never
            // be reachable, silently.
            c.fail(w, "is the second skill on slot " + s.slot);
        }
        if (!passive && s.cooldownSteps <= 0) {
            c.fail(w, "has no cooldown; it would fire every step the key is held");
        }
        switch (s.kind) {
            case LUNGE:
                c.atLeast(w, "range", (int) s.range, 1);
                break;
            case NOVA:
                c.atLeast(w, "range", (int) s.range, 1);
                break;
            case CHARGE:
                c.atLeast(w, "range", (int) s.range, 1);
                if (s.durationSteps <= 0) {
                    c.fail(w, "is a CHARGE with no duration; it would end the step it began");
                }
                if (s.durationSteps >= s.cooldownSteps) {
                    c.fail(w, "is a CHARGE that lasts as long as its cooldown, so it never ends");
                }
                break;
            case AVATAR:
                if (s.durationSteps <= 0) {
                    c.fail(w, "is an AVATAR with no duration; it would end the step it began");
                }
                if (s.effects.length == 0) {
                    c.fail(w, "is an AVATAR that changes nothing");
                }
                if (s.hpCost < 0f || s.hpCost >= 1f) {
                    c.fail(w, "hpCost " + s.hpCost + " is not a share of health below 1");
                }
                break;
            case PASSIVE:
                // The only thing a passive is. Without effects it is a row in
                // the file that costs a character one of its three keys - the
                // set check counts it - and does nothing at all.
                if (s.effects.length == 0) {
                    c.fail(w, "is a PASSIVE that changes nothing");
                }
                if (s.durationSteps > 0) {
                    c.fail(w, "is a PASSIVE with a duration; a passive is the whole run");
                }
                break;
            default:
                break;
        }
        if (s.strikeMult < 0f || s.strikeMult >= 1f) {
            // At 1 the follow-up equals the blow that caused it, which is not a
            // follow-up; above it the ultimate's decoration is its main weapon.
            c.fail(w, "strikeMult " + s.strikeMult + " is not a share below 1");
        }
        if (s.effects.length != s.magnitudes.length) {
            c.fail(w, "has " + s.effects.length + " effects and "
                + s.magnitudes.length + " magnitudes");
        }
        for (String name : s.effects) {
            if (!SKILL_EFFECTS.contains(name)) {
                c.fail(w, "effect '" + name + "' is not one of "
                    + new java.util.TreeSet<>(SKILL_EFFECTS));
            }
        }
    }

    /**
     * A quest, its steps and its pay.
     *
     * <p>Every target is checked against the thing it names, because a step
     * aimed at a monster that does not exist is a quest that can never be
     * finished - and the player has no way to find that out except by trying
     * for an hour.
     */
    private static void quest(Check c, ContentRegistry reg, QuestDef q, Set<String> givers) {
        String w = "quest '" + q.id + "'";
        c.key(w, "nameKey", q.nameKey);
        c.key(w, "descKey", q.descKey);
        c.atLeast(w, "rewardGold", q.rewardGold, 0);
        if (q.giver != null && !givers.contains(q.giver)) {
            c.fail(w, "giver '" + q.giver + "' is not a villager in HubScreen.VILLAGERS");
        }
        if (q.requires != null && !reg.hasQuest(q.requires)) {
            c.fail(w, "requires '" + q.requires + "', which is not a quest");
        }
        if (q.requires != null && q.requires.equals(q.id)) {
            c.fail(w, "requires itself");
        }
        if (q.steps.length == 0) {
            c.fail(w, "has no steps");
        }
        for (QuestDef.Step step : q.steps) {
            c.positive(w, "step count", step.count);
            switch (step.kind) {
                case KILL:
                    if (!reg.hasEnemy(step.target)) {
                        c.fail(w, "kills '" + step.target + "', which is not an enemy");
                    }
                    break;
                case COLLECT:
                    if (!reg.hasItem(step.target)) {
                        c.fail(w, "collects '" + step.target + "', which is not an item");
                    }
                    break;
                case TALK:
                    if (!givers.contains(step.target)) {
                        c.fail(w, "talks to '" + step.target + "', who is not a villager");
                    }
                    break;
                case REACH:
                    int floor = -1;
                    try {
                        floor = Integer.parseInt(step.target);
                    } catch (NumberFormatException notANumber) {
                        c.fail(w, "reaches '" + step.target + "', which is not a floor number");
                    }
                    if (floor > 0 && floor > reg.allFloors().size) {
                        c.fail(w, "reaches floor " + floor + ", and there are only "
                            + reg.allFloors().size);
                    }
                    break;
                default:
                    c.fail(w, "has a step of no kind");
            }
        }
        for (String item : q.rewardItems) {
            if (!reg.hasItem(item)) {
                c.fail(w, "pays '" + item + "', which is not an item");
            }
        }
        if (q.rewardGear != null && !reg.hasGear(q.rewardGear)) {
            c.fail(w, "pays '" + q.rewardGear + "', which is not a piece of gear");
        }
        if (q.rewardWeapon != null && !hasWeapon(reg, q.rewardWeapon)) {
            c.fail(w, "pays '" + q.rewardWeapon + "', which is not a weapon");
        }
        boolean pays = q.rewardGold > 0 || q.rewardItems.length > 0
            || q.rewardGear != null || q.rewardWeapon != null;
        if (!pays) {
            c.fail(w, "pays nothing at all");
        }
    }

    private static boolean hasWeapon(ContentRegistry reg, String id) {
        for (WeaponDef w : reg.allWeapons()) {
            if (w.id.equals(id)) {
                return true;
            }
        }
        return false;
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
        // Currencies and keys are routed by kind, not by effect: a GOLD item
        // with a "heal" effect would add to the purse and never heal.
        boolean goldKind = i.kind == ItemDef.Kind.GOLD;
        boolean gemKind = i.kind == ItemDef.Kind.DIAMOND;
        boolean keyKind = i.kind == ItemDef.Kind.KEY;
        if (goldKind != "gold".equals(i.effect)) {
            c.fail(w, "kind " + i.kind + " and effect '" + i.effect + "' disagree about gold");
        }
        if (gemKind != "diamond".equals(i.effect)) {
            c.fail(w, "kind " + i.kind + " and effect '" + i.effect + "' disagree about gems");
        }
        if (keyKind != "key".equals(i.effect)) {
            c.fail(w, "kind " + i.kind + " and effect '" + i.effect + "' disagree about keys");
        }
        // A gem is not for sale. The trader takes gold, and an item the player
        // could buy with gold and bank as a gem would be an exchange rate
        // nobody designed.
        if (gemKind && i.price > 0) {
            c.fail(w, "is a gem and has a price; the trader deals in gold only");
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
        java.util.Deque<String> todo = new java.util.ArrayDeque<>();
        for (FloorDef f : reg.allFloors()) {
            for (String id : f.enemies) {
                if (placed.add(id)) {
                    todo.add(id);
                }
            }
            if (f.boss != null && placed.add(f.boss)) {
                todo.add(f.boss);
            }
        }
        // Then everything those can put in the room themselves, and so on. A
        // boss that turns into another body, and a body that calls for help,
        // are both on the floor as far as the player is concerned; they are
        // simply reached through something else rather than placed by the
        // generator. Walking that graph here is what lets stage 6's chain of
        // five bodies live in enemies.json instead of in five Java brains.
        while (!todo.isEmpty()) {
            String id = todo.poll();
            if (!reg.hasEnemy(id)) {
                continue;               // already reported against its namer
            }
            EnemyDef e = reg.enemy(id);
            if (e.evolvesInto != null && placed.add(e.evolvesInto)) {
                todo.add(e.evolvesInto);
            }
            for (String add : e.summons) {
                if (placed.add(add)) {
                    todo.add(add);
                }
            }
        }
        for (EnemyDef e : reg.allEnemies()) {
            if (!placed.contains(e.id)) {
                c.fail("enemy '" + e.id + "'", "appears on no floor");
            }
        }
        chains(c, reg);
    }

    /**
     * No chain of bodies runs forever.
     *
     * <p>A boss that evolves back into something earlier in its own chain is a
     * fight with no end: each body summons the next on death, so a loop is a
     * room that can never be cleared and a stage that can never be left. It
     * costs four lines to refuse it here and it would cost a playtest to find
     * it otherwise.
     */
    private static void chains(Check c, ContentRegistry reg) {
        for (EnemyDef start : reg.allEnemies()) {
            Set<String> seen = new HashSet<>();
            String at = start.id;
            while (at != null && reg.hasEnemy(at)) {
                if (!seen.add(at)) {
                    c.fail("enemy '" + start.id + "'", "evolves in a loop, through '" + at + "'");
                    break;
                }
                at = reg.enemy(at).evolvesInto;
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
                // The starter has none, which is the exception that proves it:
                // it is free, so it has no row here to be checked.
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

    /**
     * The village economy. Everything its rules look up by id is looked up here
     * first: a misspelt good in a recipe is a meal nobody can ever cook, and
     * nothing on screen would say why.
     */
    private static void village(Check c, ContentRegistry reg, VillageCatalog v) {
        for (VillageCatalog.Good g : v.goods()) {
            String w = "good '" + g.id + "'";
            c.key(w, "nameKey", g.nameKey);
            c.positive(w, "price", g.price);
        }

        int levels = v.farmLevels().size;
        if (levels == 0) {
            c.fail("farm", "has no levels, so no plot is ever open");
        }
        for (int i = 0; i < levels; i++) {
            VillageCatalog.FarmLevel l = v.farmLevels().get(i);
            VillageCatalog.FarmLevel below = i == 0 ? null : v.farmLevels().get(i - 1);
            String w = "farm level " + l.level;
            if (l.level != i + 1) {
                c.fail(w, "is listed in place " + (i + 1) + "; levels run 1, 2, 3 in order");
            }
            if (below == null && l.harvests != 0) {
                c.fail(w, "asks for " + l.harvests + " harvests, but a new farm is level 1 with none");
            }
            if (below != null && l.harvests <= below.harvests) {
                c.fail(w, "asks for no more harvests than level " + below.level);
            }
            if (l.plots <= (below == null ? 0 : below.plots)) {
                c.fail(w, "opens " + l.plots + " plots, no more than the level below");
            }
        }

        for (VillageCatalog.Crop crop : v.crops()) {
            String w = "crop '" + crop.id + "'";
            VillageCatalog.Good good = v.good(crop.good);
            if (good == null) {
                c.fail(w, "good '" + crop.good + "' does not exist");
            }
            c.positive(w, "seedPrice", crop.seedPrice);
            c.positive(w, "yield", crop.yield);
            c.positive(w, "stageSeconds", crop.stageSeconds);
            if (crop.farmLevel < 1 || crop.farmLevel > levels) {
                c.fail(w, "farmLevel " + crop.farmLevel + " is not a level the farm has");
            }
            // A crop that sells for less than its seed is a field of loss that
            // looks, in the ground, exactly like a field of profit.
            if (good != null && crop.yield * good.price <= crop.seedPrice) {
                c.fail(w, "sells for " + crop.yield * good.price + " and its seed costs " + crop.seedPrice);
            }
        }

        Set<String> regions = new HashSet<>();
        Set<String> toolsUsed = new HashSet<>();
        for (VillageCatalog.Workshop ws : v.workshops()) {
            String w = "workshop '" + ws.id + "'";
            regions.add(ws.id);
            if (!VillageCatalog.WORKSHOPS.contains(ws.id)) {
                c.fail(w, "is not a region with a worker: "
                    + new java.util.TreeSet<>(VillageCatalog.WORKSHOPS));
            }
            if (ws.goods.length == 0) {
                c.fail(w, "makes nothing");
            }
            if (ws.goods.length != ws.weights.length) {
                c.fail(w, ws.goods.length + " goods but " + ws.weights.length + " weights");
            }
            for (int i = 0; i < ws.goods.length; i++) {
                if (v.good(ws.goods[i]) == null) {
                    c.fail(w, "good '" + ws.goods[i] + "' does not exist");
                }
                if (i < ws.weights.length && ws.weights[i] <= 0) {
                    c.fail(w, "weight for '" + ws.goods[i] + "' is " + ws.weights[i] + ", must be positive");
                }
            }
            c.positive(w, "seconds", ws.seconds);
            c.positive(w, "capacity", ws.capacity);
            if (v.tool(ws.tool) == null) {
                c.fail(w, "tool '" + ws.tool + "' does not exist");
            } else {
                toolsUsed.add(ws.tool);
            }
        }
        for (String region : VillageCatalog.WORKSHOPS) {
            if (!regions.contains(region)) {
                c.fail("region '" + region + "'", "has a worker and no workshop, so they would make nothing");
            }
        }

        for (VillageCatalog.Tool t : v.tools()) {
            String w = "tool '" + t.id + "'";
            c.key(w, "nameKey", t.nameKey);
            c.key(w, "descKey", t.descKey);
            c.positive(w, "maxLevel", t.maxLevel);
            if (t.costs.length != t.maxLevel) {
                c.fail(w, t.maxLevel + " levels but " + t.costs.length + " costs");
            }
            for (int i = 0; i < t.costs.length; i++) {
                if (t.costs[i] <= 0 || (i > 0 && t.costs[i] <= t.costs[i - 1])) {
                    c.fail(w, "costs must be positive and rising, found " + java.util.Arrays.toString(t.costs));
                    break;
                }
            }
            c.atLeast(w, "speedPerLevel", t.speedPerLevel, 1);
            c.atLeast(w, "capacityPerLevel", t.capacityPerLevel, 0);
            if (!toolsUsed.contains(t.id)) {
                c.fail(w, "belongs to no workshop, so buying it would do nothing");
            }
        }

        Set<String> meals = new HashSet<>();
        for (VillageCatalog.Recipe r : v.recipes()) {
            String w = "recipe '" + r.id + "'";
            if (!reg.hasItem(r.item)) {
                c.fail(w, "item '" + r.item + "' does not exist");
            } else {
                ItemDef item = reg.item(r.item);
                if (item.kind != ItemDef.Kind.CONSUMABLE) {
                    c.fail(w, "makes '" + r.item + "', which is not a consumable");
                }
                // The kitchen is the only way to a meal. One with a price is one
                // the dungeon's trader would stock beside the potions.
                if (item.forSale()) {
                    c.fail(w, "makes '" + r.item + "', which has a price; the trader would sell it");
                }
                if (!meals.add(r.item)) {
                    c.fail(w, "makes '" + r.item + "', which another recipe makes too");
                }
            }
            if (r.inputs.length == 0) {
                c.fail(w, "needs no ingredients");
            }
            if (r.inputs.length != r.counts.length) {
                c.fail(w, r.inputs.length + " ingredients but " + r.counts.length + " counts");
            }
            for (int i = 0; i < r.inputs.length; i++) {
                if (v.good(r.inputs[i]) == null) {
                    c.fail(w, "ingredient '" + r.inputs[i] + "' is not a good");
                }
                if (i < r.counts.length && r.counts[i] <= 0) {
                    c.fail(w, "count for '" + r.inputs[i] + "' is " + r.counts[i] + ", must be positive");
                }
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
