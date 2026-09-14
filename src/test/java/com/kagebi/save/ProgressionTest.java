package com.kagebi.save;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.kagebi.run.RunSummary;

/** What a finished run does to the profile. */
class ProgressionTest {

    private static RunSummary died(int floor, int gold) {
        return new RunSummary(floor, 40, gold, 0, 600f, false);
    }

    private static RunSummary won(int gold) {
        return new RunSummary(5, 250, gold, 0, 1900f, true);
    }

    @Test
    void goldBanksInFullAndTheRecordsMove() {
        Profile p = new Profile();
        Progression.bank(p, died(2, 240), 0f);
        assertEquals(240, p.gold, "no tax on death");
        assertEquals(1, p.runs);
        assertEquals(0, p.wins);
        assertEquals(2, p.deepestFloor);

        Progression.bank(p, died(1, 90), 0f);
        assertEquals(330, p.gold);
        assertEquals(2, p.deepestFloor, "the record is a maximum, not the last run");
    }

    /**
     * The second purse banks on the same terms as the first. Whole, on a
     * death as much as on a win: a currency the player can only earn a few of
     * per run and then lose would teach them to stop while they are ahead,
     * which is the behaviour the no-tax rule exists to prevent.
     */
    @Test
    void gemsBankWholeOnDeathAsWellAsOnAWin() {
        Profile p = new Profile();
        Progression.bank(p, new RunSummary(3, 40, 500, 6, 600f, false), 0f);
        assertEquals(6, p.diamonds, "nothing taxed on death");
        Progression.bank(p, new RunSummary(5, 250, 900, 11, 1900f, true), 0f);
        assertEquals(17, p.diamonds, "and they accumulate across runs");
    }

    @Test
    void eachFailureDimsTheVillageUpToTheCap() {
        Profile p = new Profile();
        for (int i = 1; i <= Progression.MAX_DARKNESS + 5; i++) {
            Progression.bank(p, died(1, 0), 0f);
            assertEquals(Math.min(i, Progression.MAX_DARKNESS), p.villageDarkness, "after " + i);
        }
    }

    @Test
    void aWinBringsTheFlameHome() {
        Profile p = new Profile();
        Progression.bank(p, died(3, 0), 0f);
        Progression.bank(p, died(4, 0), 0f);
        Progression.bank(p, won(3000), 0f);
        assertEquals(0, p.villageDarkness);
        assertEquals(1, p.wins);
        assertEquals(5, p.deepestFloor);
    }

    /** Flamekeeper level 1: the village dims on every second failure. */
    @Test
    void halfResistDimsEveryOtherFailure() {
        Profile p = new Profile();
        int[] expected = {0, 1, 1, 2, 2, 3};
        for (int i = 0; i < expected.length; i++) {
            Progression.bank(p, died(1, 0), 0.5f);
            assertEquals(expected[i], p.villageDarkness, "after failure " + (i + 1));
        }
    }

    @Test
    void fullResistNeverDims() {
        Profile p = new Profile();
        for (int i = 0; i < 20; i++) {
            Progression.bank(p, died(1, 0), 1f);
        }
        assertEquals(0, p.villageDarkness);
    }

    @Test
    void negativeRunGoldIsNeverDebited() {
        Profile p = new Profile();
        p.gold = 100;
        Progression.bank(p, died(1, -50), 0f);
        assertEquals(100, p.gold);
    }
}
