package com.kagebi.save.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.kagebi.save.Profile;
import com.kagebi.save.SaveManager;

/**
 * The migration hook, proven before anything depends on it.
 *
 * <p>The one outcome that must never happen is an old save loading as a fresh
 * profile: to the player that is every upgrade and every unlock gone, with no
 * error anywhere. These tests walk a real old file through the real loader.
 */
class MigrationTest {

    @TempDir
    Path dir;

    /** A save from before the version field existed: version 0 by definition. */
    private static final String V0 =
        "{ \"gold\": 4321, \"runs\": 12, \"wins\": 2, \"deepestFloor\": 5,"
        + " \"upgrades\": { \"vigor\": 3, \"fortune\": 1 },"
        + " \"unlockedCharacters\": [\"ninjagreen\", \"ninjablue\"],"
        + " \"unlockedWeapons\": [\"katana\", \"hammer\"],"
        + " \"bestiary\": [\"slime\", \"mouse\"] }";

    /** A save from the build where the village still darkened, flamekeeper bought. */
    private static final String V1_WITH_FLAMEKEEPER =
        "{ \"version\": 1, \"gold\": 34, \"runs\": 25, \"wins\": 2, \"villageDarkness\": 3,"
        + " \"upgrades\": { \"edge\": 1, \"flamekeeper\": 1, \"vigor\": 2 } }";

    @Test
    void anOldSaveUpgradesRatherThanResetting() throws IOException {
        Files.writeString(dir.resolve("kagebi_profile.json"), V0, StandardCharsets.UTF_8);
        Profile p = new SaveManager(dir).load();

        assertEquals(Profile.CURRENT_VERSION, p.version);
        assertEquals(4321, p.gold, "the bank survived");
        assertEquals(12, p.runs);
        assertEquals(2, p.wins);
        assertEquals(3, p.upgrade("vigor"));
        assertEquals(1, p.upgrade("fortune"));
        assertTrue(p.unlockedCharacters.contains("ninjablue"));
        assertTrue(p.unlockedWeapons.contains("hammer"));
        assertEquals(2, p.bestiary.size);
    }

    @Test
    void theUpgradedSaveIsWrittenBackAtTheCurrentVersion() throws IOException {
        Files.writeString(dir.resolve("kagebi_profile.json"), V0, StandardCharsets.UTF_8);
        SaveManager saves = new SaveManager(dir);
        saves.save(saves.load());
        JsonValue written = new JsonReader().parse(
            Files.readString(dir.resolve("kagebi_profile.json"), StandardCharsets.UTF_8));
        assertEquals(Profile.CURRENT_VERSION, written.getInt("version"));
        assertEquals(4321, written.getInt("gold"));
    }

    /** Every version from 0 to the current one has a step, or boot would throw. */
    @Test
    void theShippedChainReachesTheCurrentVersion() {
        JsonValue root = new JsonReader().parse("{}");
        assertEquals(Profile.CURRENT_VERSION, Migrations.upgrade(root, 0, Profile.CURRENT_VERSION));
        assertEquals(Profile.CURRENT_VERSION, root.getInt("version"));
    }

    // ---- v1 -> v2: the village stops darkening -------------------------------------

    /**
     * The flamekeeper left the shop. A player who bought it is paid back rather
     * than left holding gold spent on an upgrade that no longer does anything.
     */
    @Test
    void aWithdrawnUpgradeIsPaidBackInGold() throws IOException {
        Files.writeString(dir.resolve("kagebi_profile.json"), V1_WITH_FLAMEKEEPER,
                          StandardCharsets.UTF_8);
        Profile p = new SaveManager(dir).load();
        assertEquals(34 + 1800, p.gold, "the one level owned, at the price paid");
        assertEquals(0, p.upgrade("flamekeeper"), "and it is no longer owned");
        assertEquals(1, p.upgrade("edge"), "the other upgrades are untouched");
        assertEquals(2, p.upgrade("vigor"));
        assertEquals(25, p.runs);
    }

    @Test
    void everyLevelOwnedIsRefunded() {
        JsonValue root = new JsonReader().parse(
            "{ \"gold\": 10, \"upgrades\": { \"flamekeeper\": 2 } }");
        new V1ToV2().apply(root);
        assertEquals(10 + 1800 + 3800, root.getInt("gold"));
        assertNull(root.get("upgrades").get("flamekeeper"));
    }

    @Test
    void aSaveThatNeverBoughtItKeepsItsGoldAndItsUpgrades() {
        JsonValue root = new JsonReader().parse(V0);
        new V1ToV2().apply(root);
        assertEquals(4321, root.getInt("gold"));
        assertEquals(3, root.get("upgrades").getInt("vigor"));
    }

    /** Nothing of the old feature is written back out once the save is loaded. */
    @Test
    void theDarknessCountAndTheUpgradeAreGoneFromTheWrittenFile() throws IOException {
        Files.writeString(dir.resolve("kagebi_profile.json"), V1_WITH_FLAMEKEEPER,
                          StandardCharsets.UTF_8);
        SaveManager saves = new SaveManager(dir);
        saves.save(saves.load());
        JsonValue written = new JsonReader().parse(
            Files.readString(dir.resolve("kagebi_profile.json"), StandardCharsets.UTF_8));
        assertNull(written.get("villageDarkness"));
        assertNull(written.get("upgrades").get("flamekeeper"));
        assertEquals(1834, written.getInt("gold"));
        assertEquals(2, written.getInt("version"));
    }

    // ---- the machinery -----------------------------------------------------------

    /**
     * What a future migration will look like: a rename, which is the change
     * that loses data if nobody writes a step for it. The hook is exercised end
     * to end with a hypothetical v2 -> v3 step on top of the shipped chain.
     */
    @Test
    void aRealMigrationCanRenameAFieldWithoutLosingIt() {
        Migration renameGoldToBank = new Migration() {
            @Override
            public int from() {
                return 2;
            }

            @Override
            public void apply(JsonValue root) {
                JsonValue gold = root.get("gold");
                root.remove("gold");
                root.addChild("bank", new JsonValue(gold.asInt()));
            }
        };
        JsonValue root = new JsonReader().parse(V0);
        int version = Migrations.upgrade(root, 0, 3,
            new V0ToV1(), new V1ToV2(), renameGoldToBank);
        assertEquals(3, version);
        assertEquals(3, root.getInt("version"));
        assertEquals(4321, root.getInt("bank"));
        assertNull(root.get("gold"));
    }

    @Test
    void aMissingStepThrowsInsteadOfSkipping() {
        JsonValue root = new JsonReader().parse(V0);
        IllegalStateException e = assertThrows(IllegalStateException.class,
            () -> Migrations.upgrade(root, 0, 3, new V0ToV1()));
        assertTrue(e.getMessage().contains("from save version 1"), e.getMessage());
    }

    @Test
    void theNoOpStepChangesNothing() {
        JsonValue root = new JsonReader().parse(V0);
        String before = root.toJson(com.badlogic.gdx.utils.JsonWriter.OutputType.json);
        new V0ToV1().apply(root);
        assertEquals(before, root.toJson(com.badlogic.gdx.utils.JsonWriter.OutputType.json));
    }
}
