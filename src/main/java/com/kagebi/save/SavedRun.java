package com.kagebi.save;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectIntMap;
import com.kagebi.run.RunState;
import com.kagebi.settings.Difficulty;

/**
 * A run, flattened so it can be put down and picked up again.
 *
 * <p><b>Why this is not just {@code RunState}.</b> A run in flight owns two
 * object graphs - the generated {@link com.kagebi.gen.FloorLayout} and the
 * {@link com.kagebi.gen.Room} inside it - and serialising those would mean
 * freezing the dungeon generator's output format into the save file, where a
 * change to room generation becomes a migration. They are left out, and the
 * price is stated in the design rather than hidden: <b>quitting inside a
 * dungeon costs the floor you were on, not the run.</b> Continue puts you back
 * in the village with everything you were carrying.
 *
 * <p>That price is also the point. Saving the floor as well would let a player
 * quit out of a fight going badly and reload it, which is the one thing a
 * roguelite may not allow. The two moments a snapshot is taken - reaching the
 * village, and taking the stairs down - are both moments where nothing is at
 * stake, so there is no fight to reload.
 *
 * <p>{@link #seed} is carried rather than redrawn, so a restored run generates
 * the same dungeon it would have; a reported bug stays reproducible across a
 * restart.
 */
public final class SavedRun {

    public long seed;
    public String characterId;
    public String weaponId;
    public String throwWeaponId;
    public Difficulty difficulty = Difficulty.DEFAULT;

    public int hp;
    public int maxHp;
    public int baseMaxHp;

    public int gold;
    public int diamonds;
    public int keys;

    public final Array<String> relics = new Array<>();
    public final ObjectIntMap<String> items = new ObjectIntMap<>();
    public String quickItem;

    public int kills;
    public int deepestFloor;
    public float elapsedSeconds;
    /** Enemies met but not yet banked, so a restart does not un-meet them. */
    public final Array<String> met = new Array<>();

    /** Takes the snapshot. Null in, null out: no run is not an error. */
    public static SavedRun of(RunState run) {
        if (run == null) {
            return null;
        }
        SavedRun s = new SavedRun();
        s.seed = run.seed;
        s.characterId = run.characterId;
        s.weaponId = run.weaponId;
        s.throwWeaponId = run.throwWeaponId;
        s.difficulty = run.difficulty;
        s.hp = run.hp;
        s.maxHp = run.maxHp;
        s.baseMaxHp = run.baseMaxHp;
        s.gold = run.gold;
        s.diamonds = run.diamonds;
        s.keys = run.keys;
        s.relics.addAll(run.relics);
        for (ObjectIntMap.Entry<String> e : run.items) {
            s.items.put(e.key, e.value);
        }
        s.quickItem = run.quickItem;
        s.kills = run.kills;
        s.deepestFloor = run.deepestFloor;
        s.elapsedSeconds = run.elapsedSeconds;
        for (String id : run.met) {
            s.met.add(id);
        }
        return s;
    }

    /**
     * Builds the run back.
     *
     * <p>{@code floor} is left at zero and the layout at null on purpose: a
     * restored run starts in the village whatever floor it was saved on, which
     * is the whole of the contract above.
     *
     * <p>The off hand is copied across as it stands. A save is the oldest data
     * in the game and can name a weapon this build no longer has - the two
     * spells, for one - but {@code EntityWorld.resolveThrowWeapon} answers an
     * unknown id with an empty hand rather than a crash, which is the right
     * answer here: a mismatch reaches the player as a run they are in the
     * middle of, and throwing at that point turns a wrong icon into a lost
     * evening. The character id is the one that cannot be wrong, and
     * {@code SaveManager} filters that before it ever reaches here.
     */
    public RunState restore() {
        RunState run = new RunState(seed, characterId, weaponId, baseMaxHp);
        run.throwWeaponId = throwWeaponId;
        run.difficulty = difficulty == null ? Difficulty.DEFAULT : difficulty;
        run.maxHp = Math.max(1, maxHp);
        // Clamped, not copied: a save edited by hand, or written by a build
        // whose maximum was higher, must not produce a player who cannot be
        // healed or one who is already dead in the village.
        run.hp = Math.max(1, Math.min(run.maxHp, hp));
        run.gold = gold;
        run.diamonds = diamonds;
        run.keys = keys;
        run.relics.addAll(relics);
        for (ObjectIntMap.Entry<String> e : items) {
            run.items.put(e.key, e.value);
        }
        run.quickItem = quickItem;
        run.kills = kills;
        run.deepestFloor = deepestFloor;
        run.elapsedSeconds = elapsedSeconds;
        for (String id : met) {
            run.met.add(id);
        }
        return run;
    }
}
