package com.kagebi.save;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.kagebi.run.RunState;
import com.kagebi.settings.Difficulty;

/**
 * Putting a run down and picking it up again.
 *
 * <p>The contract has two halves and the second is the easy one to lose: what
 * comes back, and what deliberately does not. A snapshot that quietly carried
 * the floor would turn quitting into a way of reloading a fight.
 */
class SavedRunTest {

    private static RunState aRunInProgress() {
        RunState run = new RunState(12345L, "ninjadark", "axe", 100);
        run.throwWeaponId = "kunai";
        run.difficulty = Difficulty.HARD;
        run.maxHp = 130;
        run.hp = 71;
        run.gold = 412;
        run.diamonds = 3;
        run.keys = 2;
        run.relics.add("emberheart");
        run.relics.add("swiftsole");
        run.addItem("potion", 4);
        run.quickItem = "potion";
        run.kills = 57;
        run.deepestFloor = 4;
        run.elapsedSeconds = 903.5f;
        run.met.add("slime");
        run.met.add("bat");
        // Mid-dungeon, which is the interesting case.
        run.floor = 4;
        return run;
    }

    @Test
    void everythingTheRunWasCarryingComesBack() {
        RunState back = SavedRun.of(aRunInProgress()).restore();

        assertEquals(12345L, back.seed, "the same seed generates the same dungeon");
        assertEquals("ninjadark", back.characterId);
        assertEquals("axe", back.weaponId);
        assertEquals("kunai", back.throwWeaponId);
        assertEquals(Difficulty.HARD, back.difficulty);
        assertEquals(130, back.maxHp);
        assertEquals(71, back.hp);
        assertEquals(100, back.baseMaxHp);
        assertEquals(412, back.gold);
        assertEquals(3, back.diamonds);
        assertEquals(2, back.keys);
        assertEquals(2, back.relics.size);
        assertEquals("emberheart", back.relics.get(0));
        assertEquals("swiftsole", back.relics.get(1));
        assertEquals(4, back.items.get("potion", 0));
        assertEquals("potion", back.quickItem);
        assertEquals(57, back.kills);
        assertEquals(4, back.deepestFloor);
        assertEquals(903.5f, back.elapsedSeconds, 0.001f);
        assertTrue(back.met.contains("slime"));
        assertTrue(back.met.contains("bat"));
    }

    @Test
    void theFloorIsLeftBehindOnPurpose() {
        // The whole no-reload rule rests on this line. A restored run starts in
        // the village whatever floor it was saved on.
        RunState back = SavedRun.of(aRunInProgress()).restore();
        assertEquals(0, back.floor);
        assertNull(back.layout);
        assertNull(back.room);
    }

    @Test
    void noRunSnapshotsToNothing() {
        assertNull(SavedRun.of(null));
    }

    @Test
    void anOffHandThisBuildDoesNotHaveIsCarriedAndThenIgnored() {
        // A save is the oldest data in the game and can name a weapon that has
        // since been deleted - the two spells, for instance. The snapshot
        // carries it rather than second-guessing it, and the world answers an
        // unknown id with an empty hand. The id that cannot be wrong is the
        // character, and SaveManager filters that on the way in.
        RunState run = new RunState(1L, "ninjagreen", "katana", 100);
        run.throwWeaponId = "fireball";
        assertEquals("fireball", SavedRun.of(run).restore().throwWeaponId);
    }

    @Test
    void healthIsClampedIntoARangeThePlayerCanSurvive() {
        RunState run = new RunState(1L, "ninjagreen", "katana", 100);
        run.maxHp = 100;

        run.hp = 0;
        assertEquals(1, SavedRun.of(run).restore().hp, "nobody is restored already dead");

        run.hp = 9999;
        assertEquals(100, SavedRun.of(run).restore().hp, "nor above the maximum");
    }

    @Test
    void theRoundTripThroughDiskKeepsTheRun(@TempDir Path dir) {
        Profile p = new Profile();
        p.gold = 900;
        p.savedRun = SavedRun.of(aRunInProgress());

        SaveManager saves = new SaveManager(dir);
        assertTrue(saves.save(p));
        Profile read = new SaveManager(dir).load();

        assertNotNull(read.savedRun, "the run must survive the file");
        RunState back = read.savedRun.restore();
        assertEquals("ninjadark", back.characterId);
        assertEquals("kunai", back.throwWeaponId);
        assertEquals(Difficulty.HARD, back.difficulty);
        assertEquals(412, back.gold);
        assertEquals(71, back.hp);
        assertEquals(2, back.relics.size);
        assertEquals(4, back.items.get("potion", 0));
        assertTrue(back.met.contains("bat"));
    }

    @Test
    void aProfileWithNothingToContinueSaysSo(@TempDir Path dir) {
        Profile p = new Profile();
        SaveManager saves = new SaveManager(dir);
        assertTrue(saves.save(p));
        // Absent from the file rather than written as null, and read back as
        // "no run" - which is exactly what greys out the Continue button.
        assertNull(new SaveManager(dir).load().savedRun);
    }

    @Test
    void relicOrderIsPartOfWhatThePlayerHas(@TempDir Path dir) {
        // Sets in this file are sorted for diffability; a relic list is not,
        // because the order they were picked up in is real.
        Profile p = new Profile();
        RunState run = new RunState(1L, "ninjagreen", "katana", 100);
        run.relics.add("zzz_last");
        run.relics.add("aaa_first");
        p.savedRun = SavedRun.of(run);
        new SaveManager(dir).save(p);

        RunState back = new SaveManager(dir).load().savedRun.restore();
        assertEquals("zzz_last", back.relics.get(0));
        assertEquals("aaa_first", back.relics.get(1));
    }
}
