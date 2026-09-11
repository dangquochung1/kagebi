package com.kagebi.save;

import com.kagebi.run.RunSummary;

/**
 * What a finished run does to the profile: the one place a death is turned
 * into meta-progression.
 *
 * <p>Pure arithmetic over a {@link Profile}, so the rules - how much gold
 * banks, when the village dims - are tested rather than read off a screen.
 * The shop's flamekeeper upgrade arrives as a number rather than as the shop
 * itself, which keeps {@code save} from depending on {@code data}.
 */
public final class Progression {

    /**
     * How dark the village can get. The hub renders one step darker per
     * point, and past ten steps there is nothing left of the art to read -
     * which is a decision for the screen, so this is the one number here the
     * hub is welcome to change.
     */
    public static final int MAX_DARKNESS = 10;

    /**
     * Banks a run.
     *
     * <p>Gold banks in full. A roguelite that taxes death teaches the player to
     * quit runs early rather than to push them, and the shop is priced for it:
     * a first-timer dies on floor 3 with about 635 banked, and the two
     * cheapest upgrades are 350 and 380 so that the first death buys exactly
     * one.
     *
     * <p>A win restores the flame, so the village returns to full light. A
     * loss dims it by one step, less the {@code darknessResist} the
     * flamekeeper upgrade buys: at 0.5 it dims on every second failure, at 1.0
     * never. That is computed from the failure count rather than accumulated,
     * so it is deterministic and survives a save and reload.
     */
    public static void bank(Profile p, RunSummary run, float darknessResist) {
        p.gold += Math.max(0, run.gold);
        p.runs++;
        p.deepestFloor = Math.max(p.deepestFloor, run.deepestFloor);
        if (run.victory) {
            p.wins++;
            p.villageDarkness = 0;
            return;
        }
        float keep = 1f - Math.max(0f, Math.min(1f, darknessResist));
        int failures = p.runs - p.wins;
        int before = (int) Math.floor((failures - 1) * keep);
        int after = (int) Math.floor(failures * keep);
        p.villageDarkness = Math.min(MAX_DARKNESS, p.villageDarkness + (after - before));
    }

    private Progression() {}
}
