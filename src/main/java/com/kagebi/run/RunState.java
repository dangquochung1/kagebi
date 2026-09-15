package com.kagebi.run;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectIntMap;
import com.kagebi.gen.FloorLayout;
import com.kagebi.gen.Room;
import com.kagebi.settings.Difficulty;

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
    /**
     * Player sprite folder under {@code player/}, e.g. {@code ninjagreen}.
     *
     * <p>Final until the village grew a loadout screen, and the reasoning for
     * that was that a run's ninja is chosen once. It still is - nothing changes
     * this underground - but the run that sits in the village between stages is
     * a carrier for the kit rather than a fight in progress, and it is the thing
     * {@code Screens.stageRun} copies the next stage's ninja out of. Whoever
     * changes it is responsible for the sprites already built from it; see
     * {@code EntityWorld.enterRoom}, which re-reads this the way it re-reads
     * both weapons.
     */
    public String characterId;

    public String weaponId;

    /**
     * The thrown weapon in the off hand, or null for none.
     *
     * <p>Empty at the start of the game and filled only once a kunai or a
     * shuriken has been bought, which is the whole shape of the feature: the
     * player begins with reach and nothing else, and buying a throwable is what
     * turns one attack button into two. Before this existed, a thrown weapon
     * was a REPLACEMENT for the sword - picking one meant giving up melee
     * entirely - and the throw key did nothing at all.
     */
    public String throwWeaponId;

    /**
     * How hard this run is, fixed when it started.
     *
     * <p>A copy of the setting rather than a reading of it. The setting can be
     * changed from the pause menu at any moment, and a run that read it live
     * would let a player drop the difficulty for one bad room and put it back -
     * which makes the choice meaningless and the death screen a lie.
     */
    public Difficulty difficulty = Difficulty.DEFAULT;

    /** 1 to 5 while in the dungeon; 0 in the village. */
    public int floor;
    public FloorLayout layout;
    public Room room;

    public int hp;
    public int maxHp;

    /**
     * What {@link #maxHp} was before any relic or bought upgrade raised it.
     *
     * <p>Belongs to the run rather than to whoever is simulating it, because a
     * run passes through two worlds before its first fight: the village builds
     * one and the dungeon builds another over this same object. Each used to
     * snapshot the current maximum as the base it added to, so a bought level
     * of vigor was applied once in the village and then again on the way down -
     * 100 to 115 to 130. Reading the base from here makes the sum idempotent
     * however many worlds are built.
     */
    public final int baseMaxHp;

    public int gold;
    /** Gems found this run, banked whole when it ends. See Profile.diamonds. */
    public int diamonds;
    public int keys;

    public final Array<String> relics = new Array<>();
    public final ObjectIntMap<String> items = new ObjectIntMap<>();

    /**
     * The consumable the quick key uses, chosen in the inventory, or null to let
     * the world pick - whatever heals first. Kept when its stack runs out, so the
     * same thing found again goes straight back on the key the player chose.
     */
    public String quickItem;

    public int kills;
    public int deepestFloor;
    public float elapsedSeconds;
    public boolean victory;

    public RunState(long seed, String characterId, String weaponId, int maxHp) {
        this.seed = seed;
        this.characterId = characterId;
        this.weaponId = weaponId;
        this.maxHp = maxHp;
        this.baseMaxHp = maxHp;
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

    /**
     * Spends one key, and reports whether there was one to spend.
     *
     * <p>The counterpart to every {@code keys++} in the game. Until this
     * existed keys were a currency that only ever went up: locked rooms
     * documented themselves as costing one to enter and charged nothing, so
     * the keyring upgrade, the iron keys in every loot table and the trader's
     * key at seventy gold all bought the player a number on the HUD.
     */
    public boolean spendKey() {
        if (keys <= 0) {
            return false;
        }
        keys--;
        return true;
    }

    public boolean hasRelic(String id) {
        return relics.contains(id, false);
    }

    public RunSummary summary() {
        return new RunSummary(deepestFloor, kills, gold, diamonds, elapsedSeconds, victory);
    }
}
