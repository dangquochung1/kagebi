package com.kagebi.run;

/** What the victory and game-over screens report. Frozen at the moment of death. */
public final class RunSummary {

    public final int deepestFloor;
    public final int kills;
    public final int gold;
    public final int diamonds;
    public final float seconds;
    public final boolean victory;
    /**
     * Whether the stage this ended on is off the descent.
     *
     * <p>Carried here rather than looked up when banking, because
     * {@code Progression} is given a summary and no content registry, and the
     * screen that builds the summary has the {@code FloorDef} in hand.
     */
    public final boolean side;

    /**
     * Enemy ids fought on this run, for the bestiary.
     *
     * <p>Carried on the summary with the gold and the kills because it is the
     * same kind of thing - something the run produced that the profile keeps -
     * and because {@code Progression} is the one place a death is turned into
     * profile changes. Empty rather than null, so a summary built by a test
     * needs no extra argument.
     */
    public final java.util.Set<String> met;

    public RunSummary(int deepestFloor, int kills, int gold, int diamonds,
                      float seconds, boolean victory) {
        this(deepestFloor, kills, gold, diamonds, seconds, victory, false);
    }

    public RunSummary(int deepestFloor, int kills, int gold, int diamonds,
                      float seconds, boolean victory, boolean side) {
        this(deepestFloor, kills, gold, diamonds, seconds, victory, side,
             java.util.Collections.emptySet());
    }

    public RunSummary(int deepestFloor, int kills, int gold, int diamonds,
                      float seconds, boolean victory, boolean side,
                      java.util.Set<String> met) {
        this.deepestFloor = deepestFloor;
        this.kills = kills;
        this.gold = gold;
        this.diamonds = diamonds;
        this.seconds = seconds;
        this.victory = victory;
        this.side = side;
        this.met = met == null ? java.util.Collections.emptySet() : met;
    }

    /** mm:ss, which is all a run ever needs. */
    public String time() {
        int total = (int) seconds;
        return String.format("%d:%02d", total / 60, total % 60);
    }
}
