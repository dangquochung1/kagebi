package com.kagebi.run;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectIntMap;
import com.kagebi.gen.FloorLayout;
import com.kagebi.gen.Room;

/**
 * Everything true about the run in progress.
 *
 * <p>This is the one object the four halves of the game all touch: the screens
 * read it to draw the HUD, combat writes hit points into it, loot writes items,
 * and generation hands it a floor. Making it a plain mutable bag rather than
 * routing each of those through its own interface is the right call at this
 * size - the alternative is four indirections that all resolve to the same
 * five fields.
 *
 * <p>It is deliberately <em>not</em> saved. A roguelite that lets you reload a
 * bad fight is a different game, and the meta-progression that does persist
 * lives in {@code save.Profile}.
 */
public final class RunState {

    public final long seed;
    /** Player sprite folder under {@code player/}, e.g. {@code ninjagreen}. */
    public final String characterId;

    public String weaponId;

    /** 1 to 5 while in the dungeon; 0 in the village. */
    public int floor;
    public FloorLayout layout;
    public Room room;

    public int hp;
    public int maxHp;
    public int gold;
    public int keys;

    public final Array<String> relics = new Array<>();
    public final ObjectIntMap<String> items = new ObjectIntMap<>();

    public int kills;
    public int deepestFloor;
    public float elapsedSeconds;
    public boolean victory;

    public RunState(long seed, String characterId, String weaponId, int maxHp) {
        this.seed = seed;
        this.characterId = characterId;
        this.weaponId = weaponId;
        this.maxHp = maxHp;
        this.hp = maxHp;
    }

    public boolean dead() {
        return hp <= 0;
    }

    public void addItem(String id, int count) {
        items.getAndIncrement(id, 0, count);
    }

    /** Removes one and reports whether there was one to remove. */
    public boolean spendItem(String id) {
        int held = items.get(id, 0);
        if (held <= 0) {
            return false;
        }
        items.put(id, held - 1);
        return true;
    }

    public boolean hasRelic(String id) {
        return relics.contains(id, false);
    }

    public RunSummary summary() {
        return new RunSummary(deepestFloor, kills, gold, elapsedSeconds, victory);
    }
}
