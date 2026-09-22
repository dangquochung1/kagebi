package com.kagebi.save.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
import com.kagebi.assets.Assets;
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
        assertTrue(p.unlockedCharacters.contains("ninjablue"),
                   "the colour bought as a character is a character again");
        assertTrue(p.unlockedCharacters.contains("ninjagreen"));
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
        assertEquals(Profile.CURRENT_VERSION, written.getInt("version"));
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

    // ---- v2 to v3: six ninjas become one ninja and six colours ------------------

    /** A profile that had bought four of the six recolours as characters. */
    private static final String V2_WITH_FIVE_NINJAS =
        "{ \"version\": 2, \"gold\": 2500,"
        + " \"unlockedCharacters\": [\"ninjagreen\", \"ninjared\", \"ninjafire\", \"ninjawater\"],"
        + " \"unlockedWeapons\": [\"katana\", \"axe\"] }";

    /**
     * Out one door and back in the other: bought as characters at v2, held as
     * colours at v3 and v4, characters again at v5. What a profile owns is the
     * same set of six things throughout, whatever the format calls them.
     */
    @Test
    void everyColourBoughtAsACharacterComesBackAsOne() throws IOException {
        Files.writeString(dir.resolve("kagebi_profile.json"),
            V2_WITH_FIVE_NINJAS, StandardCharsets.UTF_8);
        Profile p = new SaveManager(dir).load();

        assertEquals(Profile.CURRENT_VERSION, p.version);
        assertEquals(2500, p.gold, "no refund, and nothing taken either");
        for (String id : new String[] {"ninjagreen", "ninjared", "ninjafire", "ninjawater"}) {
            assertTrue(p.unlockedCharacters.contains(id), id + " was paid for");
        }
        assertFalse(p.unlockedCharacters.contains("ninjablue"), "blue was never bought");
        assertFalse(p.unlockedCharacters.contains("ninjadark"), "dark was never bought");
        assertFalse(p.unlockedCharacters.contains("ninja"), "the collapsed entry is gone");
        assertTrue(p.unlockedWeapons.contains("axe"), "weapons are untouched");
    }

    /**
     * Green is free and always was, so it is owned even by a file that somehow
     * does not list it - a hand edit, or the empty-set case SaveManager guards
     * for characters and weapons already.
     */
    @Test
    void theFreeColourSurvivesAFileThatOmitsIt() throws IOException {
        Files.writeString(dir.resolve("kagebi_profile.json"),
            "{ \"version\": 2, \"unlockedCharacters\": [] }", StandardCharsets.UTF_8);
        Profile p = new SaveManager(dir).load();
        assertTrue(p.unlockedCharacters.contains("ninjagreen"));
    }

    /**
     * A character id that is not one of the six is left where it is. It is
     * either a hand edit or a roster entry added after this step was written,
     * and dropping either would be this migration exceeding its remit.
     */
    @Test
    void anUnknownCharacterIsNotSweptUp() {
        JsonValue root = new JsonReader().parse(
            "{ \"version\": 2, \"unlockedCharacters\": [\"ninjared\", \"karasu\"] }");
        new V2ToV3().apply(root);
        assertTrue(contains(root.get("unlockedCharacters"), "karasu"));
        assertTrue(contains(root.get("unlockedSkins"), "red"));
        assertFalse(contains(root.get("unlockedSkins"), "karasu"));
    }

    // ---- v4 to v5: the colours become characters again ---------------------------

    /**
     * A profile at v4 exactly as the format left it: one ninja, six colours,
     * three bought heroes, two spells that came free with one of them, and a
     * run in progress as the blue ninja.
     */
    private static final String V4_WITH_EVERYTHING =
        "{ \"version\": 4, \"gold\": 4695,"
        + " \"unlockedCharacters\": [\"karasu\", \"kitsune\", \"ninja\", \"yamabushi\"],"
        + " \"unlockedSkins\": [\"blue\", \"dark\", \"fire\", \"green\", \"red\", \"water\"],"
        + " \"ninjaSkin\": \"red\","
        + " \"unlockedWeapons\": [\"axe\", \"fireball\", \"katana\", \"waterball\"],"
        + " \"savedRun\": { \"seed\": 99, \"characterId\": \"ninja\", \"skinId\": \"blue\","
        + " \"weaponId\": \"hammer\", \"throwWeaponId\": \"shuriken\", \"maxHp\": 100,"
        + " \"baseMaxHp\": 100, \"hp\": 100 } }";

    @Test
    void everyColourOwnedBecomesANinjaAndTheThreeHeroesGo() throws IOException {
        Files.writeString(dir.resolve("kagebi_profile.json"),
            V4_WITH_EVERYTHING, StandardCharsets.UTF_8);
        Profile p = new SaveManager(dir).load();

        assertEquals(Profile.CURRENT_VERSION, p.version);
        assertEquals(4695, p.gold, "no refund for the three that go");
        for (String id : Assets.Actor.CHARACTERS) {
            assertTrue(p.unlockedCharacters.contains(id), id + " was paid for as a colour");
        }
        assertEquals(6, p.unlockedCharacters.size, "and nothing else is in there");
        assertFalse(p.unlockedWeapons.contains("fireball"), "the spells went with her");
        assertFalse(p.unlockedWeapons.contains("waterball"));
        assertTrue(p.unlockedWeapons.contains("axe"), "the steel did not");
    }

    /**
     * The run in progress comes back as the ninja it was actually being played
     * as, which is the character plus the colour.
     */
    @Test
    void theRunInProgressKeepsItsColourAsItsCharacter() throws IOException {
        Files.writeString(dir.resolve("kagebi_profile.json"),
            V4_WITH_EVERYTHING, StandardCharsets.UTF_8);
        Profile p = new SaveManager(dir).load();

        assertNotNull(p.savedRun, "the run survived the change of roster");
        assertEquals("ninjablue", p.savedRun.characterId);
        assertEquals("hammer", p.savedRun.weaponId);
        assertEquals("shuriken", p.savedRun.throwWeaponId);
    }

    /**
     * A run saved as one of the three is not dropped, it changes colour.
     *
     * <p>Dropping it would be the tidier code and the worse outcome: a player
     * who left off on floor five would rather come back as a different ninja
     * than come back to a menu with the Continue button greyed out. The off
     * hand does go, because a spell has nobody to hold it - and empty rather
     * than substituted, since handing over a kunai answers a question the
     * player did not ask.
     */
    @Test
    void aRunSavedAsADeletedHeroComesBackAsTheStarter() {
        JsonValue root = new JsonReader().parse(
            "{ \"version\": 4, \"savedRun\": { \"characterId\": \"kitsune\","
            + " \"throwWeaponId\": \"fireball\" } }");
        new V4ToV5().apply(root);
        JsonValue run = root.get("savedRun");
        assertEquals("ninjagreen", run.getString("characterId"));
        assertNull(run.get("throwWeaponId"));
    }

    /**
     * A character id from neither roster is left alone, the way {@link V2ToV3}
     * leaves one alone going the other way. It is a hand edit or a branch, and
     * dropping it would be this step exceeding its remit - {@code SaveManager}
     * is what refuses to load one, and it does that for every id equally.
     */
    @Test
    void anUnknownCharacterIsStillNotSweptUp() {
        JsonValue root = new JsonReader().parse(
            "{ \"version\": 4, \"unlockedCharacters\": [\"ninja\", \"someoneelse\"],"
            + " \"unlockedSkins\": [\"green\"] }");
        new V4ToV5().apply(root);
        assertTrue(contains(root.get("unlockedCharacters"), "someoneelse"));
        assertTrue(contains(root.get("unlockedCharacters"), "ninjagreen"));
        assertFalse(contains(root.get("unlockedCharacters"), "ninja"), "the collapsed entry goes");
        assertNull(root.get("unlockedSkins"));
    }

    /** Green is free and always was, however the file got to be empty. */
    @Test
    void theStarterSurvivesAFileThatOmitsEverything() {
        JsonValue root = new JsonReader().parse("{ \"version\": 4 }");
        new V4ToV5().apply(root);
        assertTrue(contains(root.get("unlockedCharacters"), "ninjagreen"));
    }

    private static boolean contains(JsonValue array, String value) {
        for (JsonValue e = array.child; e != null; e = e.next) {
            if (value.equals(e.asString())) {
                return true;
            }
        }
        return false;
    }
}
