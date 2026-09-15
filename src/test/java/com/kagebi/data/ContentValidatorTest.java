package com.kagebi.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.badlogic.gdx.files.FileHandle;
import com.kagebi.assets.Assets;
import com.kagebi.data.def.EnemyDef;
import com.kagebi.data.def.FloorDef;
import com.kagebi.data.def.ItemDef;
import com.kagebi.data.def.LootTableDef;
import com.kagebi.data.def.RelicDef;
import com.kagebi.data.def.WeaponDef;

/**
 * Proves the validator notices things, one broken def at a time.
 *
 * <p>Each test starts from the shipped content - which must itself be clean -
 * and swaps exactly one def for a broken copy ({@code ContentRegistry.put}
 * replaces by id). A validator that passes the real content proves nothing on
 * its own; one that passes it and then fails each of these, naming the def,
 * proves it is looking.
 */
class ContentValidatorTest {

    private ContentRegistry reg;
    private ShopCatalog shop;
    private VillageCatalog village;
    private ContentValidator.AssetIndex assets;

    @BeforeEach
    void loadRealContent() {
        FileHandle data = ContentLoaderTest.DISK.apply(Assets.DATA_DIR);
        reg = ContentLoader.parse(data);
        shop = ShopCatalog.parse(data);
        village = VillageCatalog.parse(data);
        assets = ContentValidator.AssetIndex.read(ContentLoaderTest.DISK);
    }

    private List<String> problems() {
        return ContentValidator.check(reg, shop, village, assets);
    }

    /** Asserts exactly one problem, and that it says what it should. */
    private void expectOne(String... fragments) {
        List<String> found = problems();
        assertEquals(1, found.size(), "expected exactly one problem, got " + found);
        for (String f : fragments) {
            assertTrue(found.get(0).contains(f), "'" + f + "' not in: " + found.get(0));
        }
    }

    @Test
    void theShippedContentIsClean() {
        assertEquals(List.of(), problems());
    }

    // ---- the village -----------------------------------------------------------------

    /** A crop whose harvest is a good nobody defined: a field that grows nothing to sell. */
    @Test
    void aCropOfAGoodThatDoesNotExistIsCaught() {
        VillageCatalog.Crop carrot = village.crop("carrot");
        village.add(new VillageCatalog.Crop(carrot.id, "carot", carrot.seedPrice, carrot.yield,
            carrot.stageSeconds, carrot.farmLevel));
        expectOne("crop 'carrot'", "carot");
    }

    /** A meal with a price is a meal the dungeon's trader would stock beside the potions. */
    @Test
    void aMealTheTraderWouldSellIsCaught() {
        ItemDef sushi = reg.item("food_sushi");
        reg.put(new ItemDef(sushi.id, sushi.nameKey, sushi.descKey, sushi.sprite, sushi.icon,
            sushi.kind, sushi.effect, sushi.magnitude, sushi.stackSize, 90));
        expectOne("recipe 'sushi'", "has a price");
    }

    @Test
    void aWorkshopGoodWeighedAtNothingIsCaught() {
        VillageCatalog.Workshop forest = village.workshop("forest");
        village.add(new VillageCatalog.Workshop(forest.id, forest.goods, new int[] {0},
            forest.seconds, forest.capacity, forest.tool));
        expectOne("workshop 'forest'", "weight");
    }

    // ---- the headline failure ------------------------------------------------------

    /** A relic that loads, draws, reads well, and does nothing. */
    @Test
    void aMisspeltRelicEffectIsCaught() {
        RelicDef r = reg.relic("flame_ember");
        reg.put(new RelicDef(r.id, r.nameKey, r.descKey, r.icon, r.rarity, "damage_mul", r.magnitude));
        expectOne("relic 'flame_ember'", "damage_mul", "would do nothing");
    }

    // ---- enemies ---------------------------------------------------------------------

    @Test
    void anEnemyWithAMissingLootTableIsCaught() {
        reg.put(enemy(reg.enemy("slime"), e -> e.lootTable = "trash_f9"));
        expectOne("enemy 'slime'", "trash_f9");
    }

    @Test
    void anUnknownBrainIsCaught() {
        reg.put(enemy(reg.enemy("mouse"), e -> e.brain = "wander"));
        expectOne("enemy 'mouse'", "brain 'wander'");
    }

    @Test
    void aSpriteMissingFromTheAtlasIsCaught() {
        reg.put(enemy(reg.enemy("bluebat"), e -> e.sprite = "monsters/bluebatt/spritesheet"));
        expectOne("enemy 'bluebat'", "bluebatt", Assets.ATLAS_ACTORS);
    }

    @Test
    void aBossBrainOnATrashMobIsCaught() {
        reg.put(enemy(reg.enemy("larva"), e -> e.brain = "boss_frog"));
        expectOne("enemy 'larva'", "boss brain");
    }

    /** Only tengured ships a transformation strip; a second phase needs one. */
    @Test
    void aSecondPhaseWithoutATransformationStripIsCaught() {
        reg.put(enemy(reg.enemy("giantfrog2"), e -> e.phases = 2));
        expectOne("giantfrog2", "bosses/giantfrog2/trans");
    }

    @Test
    void anEnemyOnNoFloorIsCaught() {
        EnemyDef slime = reg.enemy("slime");
        reg.put(enemy(slime, e -> e.id = "slime_forgotten"));
        expectOne("enemy 'slime_forgotten'", "appears on no floor");
    }

    // ---- floors -----------------------------------------------------------------------

    @Test
    void aFloorNamingAMissingEnemyIsCaught() {
        reg.put(floor(reg.floor(2), f -> f.enemies = new String[] {
            "axolot", "kappagreen", "kappared", "octopos", "mollusc"}));
        // octopus is now on no floor as well - two true problems, both named.
        List<String> found = problems();
        assertTrue(found.stream().anyMatch(p -> p.contains("floor 2") && p.contains("octopos")), found.toString());
    }

    @Test
    void aBossInAnOrdinaryRosterIsCaught() {
        reg.put(floor(reg.floor(1), f -> {
            f.enemies = new String[] {"slime", "mouse", "bluebat", "larva", "giantfrog2"};
            f.enemyWeights = new int[] {30, 28, 22, 20, 1};
        }));
        expectOne("floor 1", "giantfrog2", "is a boss");
    }

    @Test
    void aNonPositiveSpawnWeightIsCaught() {
        reg.put(floor(reg.floor(1), f -> f.enemyWeights = new int[] {30, 0, 22, 20}));
        expectOne("floor 1", "weight for 'mouse'");
    }

    @Test
    void anUnknownBiomeIsCaught() {
        reg.put(floor(reg.floor(2), f -> f.biome = "ruins_blue"));
        expectOne("floor 2", "ruins_blue");
    }

    @Test
    void aMissingMusicFileIsCaught() {
        assets = new ContentValidator.AssetIndex(assets.regionsByAtlas, assets.keysByLanguage,
            path -> !path.endsWith("12_temple.ogg"));
        expectOne("floor 3", "12_temple.ogg", "does not exist");
    }

    // ---- items, weapons, loot ----------------------------------------------------------

    /** Gold is routed by kind, so a GOLD item that "heals" banks and never heals. */
    @Test
    void anItemWhoseKindAndEffectDisagreeIsCaught() {
        ItemDef i = reg.item("gold_coin");
        reg.put(new ItemDef(i.id, i.nameKey, i.descKey, i.sprite, i.icon, i.kind, "heal",
            i.magnitude, i.stackSize));
        expectOne("item 'gold_coin'", "disagree about gold");
    }

    @Test
    void anIconOffTheEndOfTheGridIsCaught() {
        ItemDef i = reg.item("antidote");
        reg.put(new ItemDef(i.id, i.nameKey, i.descKey, i.sprite, 2193, i.kind, i.effect,
            i.magnitude, i.stackSize));
        expectOne("item 'antidote'", "2193");
    }

    /** A thrown weapon has no in-hand art; its sprite lives in the effects atlas. */
    @Test
    void aThrownWeaponDrawnFromTheWrongAtlasIsCaught() {
        WeaponDef k = reg.weapon("kunai");
        reg.put(new WeaponDef(k.id, k.nameKey, k.descKey, "player/weapons/katana", k.icon,
            k.damage, k.reach, k.width, k.windupSteps, k.activeSteps, k.recoverSteps,
            k.knockback, k.rootSteps, k.projectile));
        expectOne("weapon 'kunai'", Assets.ATLAS_FX);
    }

    @Test
    void aLootEntryForAMissingItemIsCaught() {
        LootTableDef t = reg.lootTable("pot");
        LootTableDef.Entry[] entries = t.entries.clone();
        entries[0] = new LootTableDef.Entry("heart_smal", 12, 1, 1);
        reg.put(new LootTableDef(t.id, entries, t.nothingWeight, t.rolls));
        expectOne("loot table 'pot'", "heart_smal");
    }

    @Test
    void aZeroLootWeightIsCaught() {
        LootTableDef t = reg.lootTable("pot");
        LootTableDef.Entry[] entries = t.entries.clone();
        entries[1] = new LootTableDef.Entry(entries[1].itemId, 0, 1, 1);
        reg.put(new LootTableDef(t.id, entries, t.nothingWeight, t.rolls));
        expectOne("loot table 'pot'", "weight");
    }

    // ---- translations -------------------------------------------------------------------

    @Test
    void aNameMissingFromOneLanguageIsCaught() {
        Map<String, Set<String>> keys = new HashMap<>();
        for (Map.Entry<String, Set<String>> e : assets.keysByLanguage.entrySet()) {
            keys.put(e.getKey(), new HashSet<>(e.getValue()));
        }
        keys.get("en").remove("relic.bloodthorn.desc");
        assets = new ContentValidator.AssetIndex(assets.regionsByAtlas, keys, assets.fileExists);
        expectOne("relic 'bloodthorn'", "relic.bloodthorn.desc", "'en'");
    }

    // ---- the shop -------------------------------------------------------------------------

    @Test
    void aLockedWeaponNothingUnlocksIsCaught() {
        ShopCatalog trimmed = new ShopCatalog();
        for (ShopCatalog.Upgrade u : shop.upgrades()) {
            trimmed.add(u);
        }
        for (ShopCatalog.Unlock u : shop.unlocks()) {
            if (!u.id.equals("hammer")) {
                trimmed.add(u);
            }
        }
        shop = trimmed;
        expectOne("weapon 'hammer'", "nothing unlocks it");
    }

    @Test
    void upgradeCostsThatDoNotRiseAreCaught() {
        ShopCatalog.Upgrade v = shop.upgrade("vigor");
        ShopCatalog bad = new ShopCatalog();
        for (ShopCatalog.Upgrade u : shop.upgrades()) {
            bad.add(u == v ? new ShopCatalog.Upgrade(v.id, v.nameKey, v.descKey, v.icon,
                v.maxLevel, new int[] {150, 320, 320, 1200}, v.effect, v.magnitudePerLevel) : u);
        }
        for (ShopCatalog.Unlock u : shop.unlocks()) {
            bad.add(u);
        }
        shop = bad;
        expectOne("upgrade 'vigor'", "rising");
    }

    // ---- the vocabularies are the contract --------------------------------------------------

    /**
     * Every name the validator accepts is one another agent has to implement,
     * so a name nothing uses is work asked for nothing.
     */
    @Test
    void everyVocabularyNameIsUsedBySomeContent() {
        Set<String> relicEffects = new HashSet<>();
        reg.allRelics().forEach(r -> relicEffects.add(r.effect));
        assertEquals(ContentValidator.RELIC_EFFECTS, relicEffects, "relic effects");

        Set<String> itemEffects = new HashSet<>();
        reg.allItems().forEach(i -> itemEffects.add(i.effect));
        assertEquals(ContentValidator.ITEM_EFFECTS, itemEffects, "item effects");

        Set<String> upgradeEffects = new HashSet<>();
        shop.upgrades().forEach(u -> upgradeEffects.add(u.effect));
        shop.unlocks().forEach(u -> {
            if (u.effect != null) {
                upgradeEffects.add(u.effect);
            }
        });
        assertEquals(ContentValidator.UPGRADE_EFFECTS, upgradeEffects, "upgrade effects");

        Set<String> brains = new HashSet<>();
        reg.allEnemies().forEach(e -> brains.add(e.brain));
        assertEquals(ContentValidator.BRAINS, brains, "brains");

        Set<String> biomes = new HashSet<>();
        reg.allFloors().forEach(f -> biomes.add(f.biome));
        assertEquals(ContentValidator.BIOMES, biomes, "biomes");

        Set<String> projectiles = new HashSet<>();
        reg.allWeapons().forEach(w -> {
            if (w.projectile != null) {
                projectiles.add(w.projectile);
            }
        });
        assertEquals(ContentValidator.PROJECTILES, projectiles, "projectiles");
    }

    /**
     * notes/d.md is what the combat and ai agents read. A name added to the
     * validator and not to the notes is a name nobody is told to implement.
     */
    @Test
    void everyVocabularyNameIsDocumentedInTheNotes() throws IOException {
        String notes = Files.readString(new File("notes/d.md").toPath(), StandardCharsets.UTF_8);
        for (Set<String> vocab : List.of(ContentValidator.RELIC_EFFECTS, ContentValidator.ITEM_EFFECTS,
                ContentValidator.UPGRADE_EFFECTS, ContentValidator.BRAINS,
                ContentValidator.BIOMES, ContentValidator.PROJECTILES)) {
            for (String name : vocab) {
                assertTrue(notes.contains("`" + name + "`"), "notes/d.md does not mention `" + name + "`");
            }
        }
    }

    /** ICON_COUNT is arithmetic on a measurement; this re-measures it. */
    @Test
    void theIconGridIsSixteenBy137() throws IOException {
        try (DataInputStream in = new DataInputStream(new FileInputStream(Assets.ICONS))) {
            in.skipBytes(16);           // PNG signature, IHDR length and type
            int width = in.readInt();
            int height = in.readInt();
            assertEquals(256, width);
            assertEquals(2192, height);
            assertEquals(ContentValidator.ICON_COUNT, (width / 16) * (height / 16));
        }
    }

    // ---- copying a def with one field changed ----------------------------------------------

    /** EnemyDef is final with 23 constructor arguments; this copies all but one. */
    private static final class E {
        String id, nameKey, sprite, brain, lootTable;
        int cell, maxHp, contactDamage, attackDamage, windupSteps, activeSteps, recoverSteps,
            cooldownSteps, hurtInvulnSteps, goldMin, goldMax, phases;
        float moveSpeed, aggroRange, attackRange, knockbackResist;
        boolean boss, flying;
    }

    private static EnemyDef enemy(EnemyDef d, Consumer<E> change) {
        E e = new E();
        e.id = d.id; e.nameKey = d.nameKey; e.sprite = d.sprite; e.cell = d.cell;
        e.maxHp = d.maxHp; e.contactDamage = d.contactDamage; e.attackDamage = d.attackDamage;
        e.moveSpeed = d.moveSpeed; e.brain = d.brain; e.aggroRange = d.aggroRange;
        e.attackRange = d.attackRange; e.windupSteps = d.windupSteps; e.activeSteps = d.activeSteps;
        e.recoverSteps = d.recoverSteps; e.cooldownSteps = d.cooldownSteps;
        e.knockbackResist = d.knockbackResist; e.hurtInvulnSteps = d.hurtInvulnSteps;
        e.lootTable = d.lootTable; e.goldMin = d.goldMin; e.goldMax = d.goldMax;
        e.boss = d.boss; e.phases = d.phases; e.flying = d.flying;
        change.accept(e);
        return new EnemyDef(e.id, e.nameKey, e.sprite, e.cell, e.maxHp, e.contactDamage,
            e.attackDamage, e.moveSpeed, e.brain, e.aggroRange, e.attackRange, e.windupSteps,
            e.activeSteps, e.recoverSteps, e.cooldownSteps, e.knockbackResist, e.hurtInvulnSteps,
            e.lootTable, e.goldMin, e.goldMax, e.boss, e.phases, e.flying);
    }

    private static final class F {
        String biome;
        String[] enemies;
        int[] enemyWeights;
    }

    private static FloorDef floor(FloorDef d, Consumer<F> change) {
        F f = new F();
        f.biome = d.biome;
        f.enemies = d.enemies;
        f.enemyWeights = d.enemyWeights;
        change.accept(f);
        return new FloorDef(d.number, d.nameKey, d.descKey, f.biome, d.music, d.ambient,
            d.bossMusic,
            d.roomsMin, d.roomsMax, d.treasureRooms, d.shopRooms, f.enemies, f.enemyWeights,
            d.packMin, d.packMax, d.boss);
    }
}
