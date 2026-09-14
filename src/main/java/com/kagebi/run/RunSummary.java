package com.kagebi.run;

/** What the victory and game-over screens report. Frozen at the moment of death. */
public final class RunSummary {

    public final int deepestFloor;
    public final int kills;
    public final int gold;
    public final int diamonds;
    public final float seconds;
    public final boolean victory;

    public RunSummary(int deepestFloor, int kills, int gold, int diamonds,
                      float seconds, boolean victory) {
        this.deepestFloor = deepestFloor;
        this.kills = kills;
        this.gold = gold;
        this.diamonds = diamonds;
        this.seconds = seconds;
        this.victory = victory;
    }

    /** mm:ss, which is all a run ever needs. */
    public String time() {
        int total = (int) seconds;
        return String.format("%d:%02d", total / 60, total % 60);
    }
}
