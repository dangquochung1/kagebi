package com.kagebi.save;

import com.badlogic.gdx.utils.ObjectIntMap;
import com.badlogic.gdx.utils.ObjectSet;

/**
 * What survives death: the meta-progression.
 *
 * <p>Saved as JSON rather than through libGDX Preferences, for two reasons that
 * matter during development. It is readable, so a broken save can be inspected
 * and repaired by hand; and it carries a {@link #version}, so a field added
 * next month does not silently reset everyone's bank.
 */
public final class Profile {

    /** Bump when the shape changes, and add a migration beside it. */
    public static final int CURRENT_VERSION = 1;

    public int version = CURRENT_VERSION;

    /** Banked gold, spent in the village between runs. */
    public int gold;
    /** Permanent upgrade id to level. */
    public final ObjectIntMap<String> upgrades = new ObjectIntMap<>();

    public final ObjectSet<String> unlockedCharacters = new ObjectSet<>();
    public final ObjectSet<String> unlockedWeapons = new ObjectSet<>();
    /** Enemy ids the player has met, for the bestiary screen. */
    public final ObjectSet<String> bestiary = new ObjectSet<>();

    public int runs;
    public int wins;
    public int deepestFloor;

    /**
     * How far the village has dimmed. The story says the flame weakens with
     * every failed descent, and the hub renders one step darker per point - the
     * one place the narrative is stated by the art rather than by text.
     */
    public int villageDarkness;

    public Profile() {
        unlockedCharacters.add("ninjagreen");
        unlockedWeapons.add("katana");
    }

    public int upgrade(String id) {
        return upgrades.get(id, 0);
    }
}
