package com.kagebi.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.badlogic.gdx.files.FileHandle;
import com.kagebi.assets.Assets;
import com.kagebi.data.def.EnemyDef;
import com.kagebi.data.def.FloorDef;

/**
 * The content on disk, loaded exactly the way the game loads it at boot.
 *
 * <p>{@link ContentLoader#load(Function)} is the same code path as the game's
 * {@code load()}, with plain files standing in for {@code Gdx.files}. So a
 * green run here means the shipped JSON parses, cross-references, finds every
 * sprite in its atlas and every string in both languages - without a window.
 */
class ContentLoaderTest {

    static final Function<String, FileHandle> DISK = p -> new FileHandle(new File(p));

    static ContentRegistry real() {
        return ContentLoader.load(DISK);
    }

    @Test
    void shippedContentLoadsAndPassesEveryCheck() {
        ContentRegistry reg = real();
        assertEquals(31, reg.allEnemies().size, "enemies");
        // Five melee and two thrown. There were two spells as well, which were
        // one character's and only hers; they went when she did.
        assertEquals(7, reg.allWeapons().size, "weapons");
        assertEquals(24, reg.allRelics().size, "relics");
        assertEquals(23, reg.allItems().size, "items");
        assertEquals(17, reg.allLootTables().size, "loot tables");
        assertEquals(7, reg.allFloors().size, "floors");
    }

    @Test
    void floorsHaveTheDesignedShape() {
        ContentRegistry reg = real();
        String[] biomes = {"ruins", "ruins_green", "ruins_orange", "depths", "depths"};
        String[] bosses = {null, null, "giantfrog2", null, "tengured"};
        for (int n = 1; n <= 5; n++) {
            FloorDef f = reg.floor(n);
            assertEquals("floor." + n, f.nameKey);
            assertEquals(biomes[n - 1], f.biome, "floor " + n + " biome");
            assertEquals(bosses[n - 1], f.boss, "floor " + n + " boss");
        }
        EnemyDef tengu = reg.enemy("tengured");
        assertTrue(tengu.boss);
        assertEquals(2, tengu.phases, "the master fights in two forms");
        assertEquals(82, tengu.cell);
    }

    /** Sound names come out as the full paths AudioService.playMusic expects. */
    @Test
    void soundNamesResolveToPathsUnderAssetsDirectories() {
        ContentRegistry reg = real();
        assertEquals(Assets.MUSIC_DUNGEON, reg.floor(1).music, "an Assets constant name");
        assertEquals(Assets.MUSIC_DIR + "18_aquatic.ogg", reg.floor(2).music, "a bare track name");
        assertEquals(Assets.MUSIC_FIGHT, reg.floor(3).bossMusic);
        assertNull(reg.floor(1).bossMusic, "no boss, no boss music");
        for (FloorDef f : reg.allFloors()) {
            assertTrue(f.ambient.startsWith(Assets.SFX_DIR), f + " ambient " + f.ambient);
            assertTrue(new File(f.music).isFile(), f.music);
        }
    }

    @Test
    void iconNamesResolveToGridIndices() {
        ContentRegistry reg = real();
        assertEquals(693, reg.relic("flame_ember").icon, "flame_orange");
        assertEquals(505, reg.item("potion_small").icon, "potion_red");
        assertEquals(1443, reg.weapon("katana").icon, "weapon_katana");
    }

    // ---- authoring mistakes the loader has to catch -----------------------------

    @TempDir
    Path dir;

    private void write(String name, String json) throws IOException {
        Files.writeString(dir.resolve(name), json, StandardCharsets.UTF_8);
    }

    private String parseError() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
            () -> ContentLoader.parse(new FileHandle(dir.toFile())));
        return e.getMessage();
    }

    private static final String SLIME =
        "{ \"id\": \"slime\", \"nameKey\": \"k\", \"sprite\": \"s\", \"cell\": 16,"
        + " \"maxHp\": 10, \"contactDamage\": 1, \"attackDamage\": 0, \"moveSpeed\": 20,"
        + " \"brain\": \"hopper\", \"aggroRange\": 50, \"attackRange\": 0,"
        + " \"windupSteps\": 1, \"activeSteps\": 1, \"recoverSteps\": 1, \"cooldownSteps\": 1,"
        + " \"knockbackResist\": 0, \"hurtInvulnSteps\": 1,"
        + " \"lootTable\": \"t\", \"goldMin\": 1, \"goldMax\": 2 %s }";

    /**
     * The failure this whole reader is shaped around: an optional field with a
     * typo reads as absent and takes its default, and nothing else notices.
     */
    @Test
    void aMisspeltOptionalFieldIsReportedNotIgnored() throws IOException {
        write("e.json", "{ \"enemies\": [ " + String.format(SLIME, ", \"flyng\": true") + " ] }");
        String msg = parseError();
        assertTrue(msg.contains("unknown field 'flyng'"), msg);
        assertTrue(msg.contains("'slime'"), "the message should name the enemy: " + msg);
    }

    @Test
    void aMisspeltSectionIsReported() throws IOException {
        write("e.json", "{ \"enemys\": [] }");
        assertTrue(parseError().contains("unknown section 'enemys'"));
    }

    @Test
    void aDuplicateIdIsReportedEvenAcrossFiles() throws IOException {
        write("a.json", "{ \"enemies\": [ " + String.format(SLIME, "") + " ] }");
        write("b.json", "{ \"enemies\": [ " + String.format(SLIME, "") + " ] }");
        assertTrue(parseError().contains("'slime' is defined twice"));
    }

    @Test
    void aFractionalStepCountIsReported() throws IOException {
        write("e.json", "{ \"enemies\": [ "
            + String.format(SLIME, "").replace("\"windupSteps\": 1", "\"windupSteps\": 1.5") + " ] }");
        assertTrue(parseError().contains("whole number"));
    }

    @Test
    void aGapInTheFloorNumbersIsReported() throws IOException {
        String floor = "{ \"number\": %d, \"nameKey\": \"k\", \"biome\": \"ruins\","
            + " \"music\": \"MUSIC_DUNGEON\", \"roomsMin\": 5, \"roomsMax\": 6,"
            + " \"treasureRooms\": 1, \"shopRooms\": 1, \"enemies\": [\"slime\"],"
            + " \"enemyWeights\": [1], \"packMin\": 1, \"packMax\": 2 }";
        write("f.json", "{ \"floors\": [ " + String.format(floor, 1) + ", "
            + String.format(floor, 3) + " ] }");
        assertTrue(parseError().contains("floor 2 is missing"));
    }

    @Test
    void aPathWhereANameGoesIsRejected() throws IOException {
        write("f.json", "{ \"floors\": [ { \"number\": 1, \"nameKey\": \"k\", \"biome\": \"ruins\","
            + " \"music\": \"assets/audio/music/21_dungeon.ogg\", \"roomsMin\": 5, \"roomsMax\": 6,"
            + " \"treasureRooms\": 1, \"shopRooms\": 1, \"enemies\": [\"slime\"],"
            + " \"enemyWeights\": [1], \"packMin\": 1, \"packMax\": 2 } ] }");
        assertTrue(parseError().contains("not a path"));
    }

    @Test
    void anUnknownIconNameIsReported() throws IOException {
        write("r.json", "{ \"relics\": [ { \"id\": \"x\", \"nameKey\": \"k\", \"descKey\": \"d\","
            + " \"icon\": \"no_such_icon\", \"rarity\": \"COMMON\", \"effect\": \"damage_mult\","
            + " \"magnitude\": 1.1 } ] }");
        assertTrue(parseError().contains("icon 'no_such_icon'"));
    }

    @Test
    void anUnknownRarityNamesTheChoices() throws IOException {
        write("r.json", "{ \"relics\": [ { \"id\": \"x\", \"nameKey\": \"k\", \"descKey\": \"d\","
            + " \"icon\": 1, \"rarity\": \"LEGENDARY\", \"effect\": \"damage_mult\","
            + " \"magnitude\": 1.1 } ] }");
        String msg = parseError();
        assertTrue(msg.contains("LEGENDARY") && msg.contains("EPIC"), msg);
    }

    @Test
    void everyProblemIsListedInOneFailure() throws IOException {
        write("e.json", "{ \"enemies\": [ " + String.format(SLIME, ", \"flyng\": true, \"bos\": true")
            + " ], \"enemys\": [] }");
        String msg = parseError();
        assertTrue(msg.startsWith("3 content problems"), msg);
    }

    @Test
    void anEmptyDirectorySaysSoRatherThanBootingEmpty() {
        assertFalse(dir.toFile().list().length > 0);
        assertTrue(parseError().contains("no .json files"));
    }
}
