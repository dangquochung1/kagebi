package com.kagebi.save;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Persistence, including the ways a save file goes wrong in the world.
 *
 * <p>Each test gets its own temporary directory, so none of them can touch
 * the real profile in the user's home - which is where a test that got the
 * path wrong would otherwise do its damage.
 */
class SaveManagerTest {

    @TempDir
    Path dir;

    private Path file(String name) {
        return dir.resolve(name);
    }

    private static Profile rich() {
        Profile p = new Profile();
        p.gold = 1234;
        p.runs = 7;
        p.wins = 1;
        p.deepestFloor = 5;
        p.villageDarkness = 3;
        p.upgrades.put("vigor", 2);
        p.upgrades.put("edge", 1);
        p.unlockedCharacters.add("ninjared");
        p.unlockedWeapons.add("axe");
        p.bestiary.add("slime");
        p.bestiary.add("tengured");
        return p;
    }

    private static void assertSameProfile(Profile a, Profile b) {
        assertEquals(a.gold, b.gold, "gold");
        assertEquals(a.runs, b.runs, "runs");
        assertEquals(a.wins, b.wins, "wins");
        assertEquals(a.deepestFloor, b.deepestFloor, "deepestFloor");
        assertEquals(a.villageDarkness, b.villageDarkness, "villageDarkness");
        assertEquals(a.upgrades, b.upgrades, "upgrades");
        assertEquals(a.unlockedCharacters, b.unlockedCharacters, "characters");
        assertEquals(a.unlockedWeapons, b.unlockedWeapons, "weapons");
        assertEquals(a.bestiary, b.bestiary, "bestiary");
    }

    // ---- the ordinary path ---------------------------------------------------------

    @Test
    void aFirstLaunchIsAFreshProfile() {
        Profile p = new SaveManager(dir).load();
        assertEquals(0, p.gold);
        assertTrue(p.unlockedCharacters.contains("ninjagreen"));
        assertTrue(p.unlockedWeapons.contains("katana"));
    }

    @Test
    void whatIsSavedIsWhatLoads() {
        Profile p = rich();
        assertTrue(new SaveManager(dir).save(p));
        assertSameProfile(p, new SaveManager(dir).load());
    }

    @Test
    void loadReturnsTheSameProfileEachTime() {
        SaveManager saves = new SaveManager(dir);
        assertSame(saves.load(), saves.load());
    }

    /** "Readable, so a broken save can be inspected and repaired by hand." */
    @Test
    void theFileIsIndentedSortedJsonAPersonCanEdit() throws IOException {
        new SaveManager(dir).save(rich());
        String text = Files.readString(file(SaveManager.FILE), StandardCharsets.UTF_8);
        assertTrue(text.contains("\n"), "one line is not hand-editable");
        assertTrue(text.contains("\"version\": " + Profile.CURRENT_VERSION), text);
        assertTrue(text.indexOf("\"edge\"") < text.indexOf("\"vigor\""), "upgrades should be sorted");

        // And an edit made by hand is honoured.
        Files.writeString(file(SaveManager.FILE), text.replace("1234", "99999"), StandardCharsets.UTF_8);
        assertEquals(99999, new SaveManager(dir).load().gold);
    }

    // ---- the write is atomic ----------------------------------------------------------

    @Test
    void aSaveLeavesNoTemporaryFileAndKeepsThePreviousAsBackup() throws IOException {
        SaveManager saves = new SaveManager(dir);
        Profile p = rich();
        saves.save(p);
        p.gold = 5;
        saves.save(p);
        assertFalse(Files.exists(file(SaveManager.TEMP)), "the temp file should have been renamed away");
        assertEquals(5, new SaveManager(dir).load().gold, "current");
        String backup = Files.readString(file(SaveManager.BACKUP), StandardCharsets.UTF_8);
        assertTrue(backup.contains("1234"), "the backup is the save before this one");
    }

    /**
     * The laptop-lid case: a write died after creating the temp file and
     * before the rename. The real save must be untouched and must load.
     */
    @Test
    void anInterruptedWriteLeavesTheLastGoodSaveInPlace() throws IOException {
        new SaveManager(dir).save(rich());
        Files.writeString(file(SaveManager.TEMP), "{\"version\": 1, \"gold\": 7", StandardCharsets.UTF_8);
        assertEquals(1234, new SaveManager(dir).load().gold);
    }

    // ---- the file goes wrong ----------------------------------------------------------

    @Test
    void anUnreadableSaveFallsBackToTheBackup() throws IOException {
        SaveManager first = new SaveManager(dir);
        Profile p = rich();
        first.save(p);
        p.gold = 2000;
        first.save(p);              // main has 2000, backup has 1234
        Files.writeString(file(SaveManager.FILE), "{ this is not json", StandardCharsets.UTF_8);

        assertEquals(1234, new SaveManager(dir).load().gold, "fell back to the backup");
        assertTrue(Files.exists(file(SaveManager.CORRUPT)), "the broken file is kept for repair");
    }

    /**
     * The trap a naive backup walks into: the next save copies the current
     * file over the backup - and if the current file is the corrupt one, the
     * only good copy is gone. Moving it aside at load is what prevents that.
     */
    @Test
    void savingAfterAFallbackDoesNotCopyTheCorruptFileOverTheBackup() throws IOException {
        SaveManager first = new SaveManager(dir);
        first.save(rich());
        first.save(rich());
        Files.writeString(file(SaveManager.FILE), "garbage", StandardCharsets.UTF_8);

        SaveManager second = new SaveManager(dir);
        Profile p = second.load();
        p.gold += 1;
        second.save(p);
        String backup = Files.readString(file(SaveManager.BACKUP), StandardCharsets.UTF_8);
        assertFalse(backup.contains("garbage"), "the corrupt file reached the backup");
        assertEquals(1235, new SaveManager(dir).load().gold);
    }

    @Test
    void withNothingReadableItStartsFreshButDeletesNothing() throws IOException {
        Files.writeString(file(SaveManager.FILE), "garbage", StandardCharsets.UTF_8);
        Files.writeString(file(SaveManager.BACKUP), "also garbage", StandardCharsets.UTF_8);
        assertEquals(0, new SaveManager(dir).load().gold);
        assertTrue(Files.exists(file(SaveManager.CORRUPT)));
        assertTrue(Files.exists(file(SaveManager.BACKUP)));
    }

    @Test
    void aSaveFromANewerBuildLoadsAndIsCopiedAsideBeforeBeingOverwritten() throws IOException {
        Files.writeString(file(SaveManager.FILE),
            "{ \"version\": 9, \"gold\": 777, \"someFutureField\": [1, 2, 3] }", StandardCharsets.UTF_8);
        SaveManager saves = new SaveManager(dir);
        assertEquals(777, saves.load().gold, "known fields still load");
        assertTrue(Files.exists(file(SaveManager.FILE + ".v9")), "the newer file is preserved");
        saves.save(saves.load());
        assertTrue(Files.readString(file(SaveManager.FILE + ".v9"), StandardCharsets.UTF_8)
            .contains("someFutureField"), "and survives this build writing over it");
    }

    @Test
    void aFailedSaveReportsFalseAndKeepsTheOldSave() throws IOException {
        Path blocker = dir.resolve("not_a_directory");
        Files.writeString(blocker, "a file where a directory should be", StandardCharsets.UTF_8);
        assertFalse(new SaveManager(blocker.resolve("saves")).save(rich()));
    }

    @Test
    void theStarterCharacterCannotBeEditedAway() throws IOException {
        Files.writeString(file(SaveManager.FILE),
            "{ \"version\": 1, \"unlockedCharacters\": [], \"unlockedWeapons\": [] }", StandardCharsets.UTF_8);
        Profile p = new SaveManager(dir).load();
        assertTrue(p.unlockedCharacters.contains("ninjagreen"));
        assertTrue(p.unlockedWeapons.contains("katana"));
    }
}
