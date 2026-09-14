package com.kagebi.save;

import com.kagebi.run.RunSummary;

/**
 * What a finished run does to the profile: the one place a death is turned
 * into meta-progression.
 *
 * <p>Pure arithmetic over a {@link Profile}, so the rules - how much gold
 * banks, what counts as a win - are tested rather than read off a screen.
 */
public final class Progression {

    /**
     * Banks a run.
     *
     * <p>Gold banks in full. A roguelite that taxes death teaches the player to
     * quit runs early rather than to push them, and the shop is priced for it:
     * a first-timer dies on floor 3 with about 635 banked, and the two
     * cheapest upgrades are 350 and 380 so that the first death buys exactly
     * one.
     *
     * <p>A win counts, and records the last stage as cleared along with it.
     */
    public static void bank(Profile p, RunSummary run) {
        p.gold += Math.max(0, run.gold);
        p.diamonds += Math.max(0, run.diamonds);
        p.runs++;
        p.deepestFloor = Math.max(p.deepestFloor, run.deepestFloor);
        if (run.victory) {
            p.wins++;
            p.clearedStages = Math.max(p.clearedStages, run.deepestFloor);
        }
    }

    /**
     * Banks a cleared stage, and opens the next one.
     *
     * <p>Its own method rather than a flag on {@link #bank}, because clearing
     * a stage is not winning the game. {@code wins} is what the shop's unlock
     * requirements count, so folding the two together would pay for a
     * character with stage one.
     */
    public static void bankStage(Profile p, RunSummary run) {
        p.gold += Math.max(0, run.gold);
        p.diamonds += Math.max(0, run.diamonds);
        p.runs++;
        p.deepestFloor = Math.max(p.deepestFloor, run.deepestFloor);
        p.clearedStages = Math.max(p.clearedStages, run.deepestFloor);
    }

    private Progression() {}
}
