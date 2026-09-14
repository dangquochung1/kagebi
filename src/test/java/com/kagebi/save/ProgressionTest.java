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
        Progression.bank(p, died(2, 240));
        assertEquals(240, p.gold, "no tax on death");
        assertEquals(1, p.runs);
        assertEquals(0, p.wins);
        assertEquals(2, p.deepestFloor);

        Progression.bank(p, died(1, 90));
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
        Progression.bank(p, new RunSummary(3, 40, 500, 6, 600f, false));
        assertEquals(6, p.diamonds, "nothing taxed on death");
        Progression.bank(p, new RunSummary(5, 250, 900, 11, 1900f, true));
        assertEquals(17, p.diamonds, "and they accumulate across runs");
    }

    // ---- clearing a stage, which is not winning the game -----------------------

    private static RunSummary cleared(int stage, int gold) {
        return new RunSummary(stage, 60, gold, 2, 480f, true);
    }

    @Test
    void aClearedStageBanksItsGoldAndOpensTheNextOne() {
        Profile p = new Profile();
        Progression.bankStage(p, cleared(1, 320));
        assertEquals(320, p.gold);
        assertEquals(2, p.diamonds);
        assertEquals(1, p.runs);
        assertEquals(1, p.clearedStages);
        assertEquals(1, p.deepestFloor);
    }

    /**
     * The distinction the second method exists for. {@code wins} is what the
     * shop's unlock requirements count, so folding stage clears into
     * {@link Progression#bank} would buy a character with stage one.
     */
    @Test
    void aClearedStageIsNotAWin() {
        Profile p = new Profile();
        Progression.bankStage(p, cleared(1, 100));
        assertEquals(0, p.wins, "clearing stage one has not won the game");
    }

    @Test
    void clearingTheLastStageBothWinsAndRecordsTheStage() {
        Profile p = new Profile();
        p.clearedStages = 4;
        Progression.bank(p, won(2000));
        assertEquals(1, p.wins);
        assertEquals(5, p.clearedStages, "the final stage records itself too");
    }

    @Test
    void replayingAClearedStageOpensNothingFurther() {
        Profile p = new Profile();
        p.clearedStages = 3;
        Progression.bankStage(p, cleared(1, 90));
        assertEquals(3, p.clearedStages, "a record is a maximum, not the last run");
        assertEquals(90, p.gold, "but the gold is still earned");
    }

    @Test
    void aWinAfterDeathsCountsOnceAndKeepsTheDeepestRecord() {
        Profile p = new Profile();
        Progression.bank(p, died(3, 0));
        Progression.bank(p, died(4, 0));
        Progression.bank(p, won(3000));
        assertEquals(1, p.wins);
        assertEquals(3, p.runs);
        assertEquals(5, p.deepestFloor);
    }

    @Test
    void negativeRunGoldIsNeverDebited() {
        Profile p = new Profile();
        p.gold = 100;
        Progression.bank(p, died(1, -50));
        assertEquals(100, p.gold);
    }
}
