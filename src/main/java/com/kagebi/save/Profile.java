package com.kagebi.save;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectIntMap;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.ObjectSet;
import com.kagebi.assets.Assets;

/**
 * What survives death: the meta-progression.
 *
 * <p>Saved as JSON rather than through libGDX Preferences, for two reasons that
 * matter during development. It is readable, so a broken save can be inspected
 * and repaired by hand; and it carries a {@link #version}, so a field added
 * next month does not silently reset everyone's bank.
 */
public final class Profile {

    /**
     * Bump when the shape changes, and add a migration beside it.
     *
     * <p>2: the village stopped darkening, so its darkness count left the file
     * and the flamekeeper upgrade that slowed it was paid back in gold. See
     * {@code migration.V1ToV2}.
     *
     * <p>3: the six ninja recolours stopped being six characters. Five of them
     * move from {@code unlockedCharacters} to {@code unlockedSkins} and the
     * one character they collapse into is added. See {@code migration.V2ToV3}.
     *
     * <p>4: {@code savedRun} arrives. Nothing to convert; the chain may not
     * have gaps. See {@code migration.V3ToV4}.
     *
     * <p>5: the colours are characters again, this time with a perk each, and
     * the three side-view heroes are gone along with the two spells that were
     * one of theirs. See {@code migration.V4ToV5}.
     */
    public static final int CURRENT_VERSION = 5;

    public int version = CURRENT_VERSION;

    /** Banked gold, spent in the village between runs. */
    public int gold;

    /**
     * Banked gems: the second purse, and the scarce one.
     *
     * <p>Gold is what a run produces by the hundred and the village shop is
     * priced in it. Gems come out of chests a few at a time and are meant to
     * buy a different kind of thing - what, exactly, is not decided yet, which
     * is the reason they bank rather than being spent as they are found.
     *
     * <p>Kept whole through death for the same reason gold is: a currency that
     * is taxed on death teaches the player to stop playing while they are
     * ahead, and this one is far too slow to earn to risk that.
     */
    public int diamonds;
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
     * The highest stage finished, which is what opens the next one on the map.
     *
     * <p>Deliberately not {@link #deepestFloor}. That counts the deepest floor
     * <em>reached</em>, and the dungeon sets it on arrival - so a player who
     * walks into stage two and dies there has reached it without clearing it.
     * Keying the map on that would open stage three as a reward for dying.
     *
     * <p>It also could not be borrowed even if the timing were right:
     * {@code deepestFloor} is what the shop's {@code deepest_floor} unlock
     * requirement counts, and changing what it means would quietly re-price
     * every character on that shelf.
     */
    public int clearedStages;

    /**
     * Armour owned, worn and unworn. See {@link OwnedGear} for why these are
     * instances rather than a count of ids.
     */
    public final Array<OwnedGear> stash = new Array<>();
    /**
     * Stones and salvage, by id. Countable, so counted - a red shard is a red
     * shard, and three of them are three of them.
     */
    public final ObjectIntMap<String> materials = new ObjectIntMap<>();
    /**
     * What is worn, by slot: the {@link OwnedGear#instance} of the piece, or
     * absent for an empty slot.
     *
     * <p>Keyed by the slot's name rather than by the enum, because this is
     * written to JSON by hand and an enum constant that was later renamed would
     * silently unequip everyone. The name is the contract, as it is everywhere
     * else in the save file.
     */
    public final ObjectMap<String, Integer> equipped = new ObjectMap<>();
    /** Counter behind {@link #nextGearId}; never reused within a profile. */
    public int gearSeq;

    /** Which jobs have been taken, finished and paid for. */
    public final QuestLog quests = new QuestLog();
    /**
     * The quest the player asked to be pointed at, or null.
     *
     * <p>One at a time. An arrow for every active job at once would be a
     * compass rose, and the point of the arrow is that it answers "where now".
     */
    public String tracked;

    /**
     * Codes typed in on this profile.
     *
     * <p>Kept so the entry screen can say a code has already been used rather
     * than appearing to do nothing. It does not stop one being used again -
     * {@code Codes} decides that, and the money one may be.
     */
    public final ObjectSet<String> redeemed = new ObjectSet<>();

    /** The island between visits: its farm, its workers, its storehouse and pantry. */
    public final VillageState village = new VillageState();

    /**
     * The run to carry on with, or null when there is nothing to continue.
     *
     * <p>The one part of the save that is about a session rather than about
     * everything the player has ever done. It is set when a run reaches a place
     * where nothing is at stake and cleared when the run ends, so its presence
     * is exactly the question the main menu's Continue button asks.
     *
     * <p>Not final, because continuing is the act of putting it down again.
     */
    public SavedRun savedRun;

    public Profile() {
        unlockedCharacters.add(Assets.Actor.DEFAULT_CHARACTER);
        unlockedWeapons.add("katana");
    }

    public int upgrade(String id) {
        return upgrades.get(id, 0);
    }

    /** The next unused instance id. Monotonic, so a sale cannot free one. */
    public int nextGearId() {
        return ++gearSeq;
    }

    /** The piece with this instance id, or null. */
    public OwnedGear gear(int instance) {
        for (OwnedGear g : stash) {
            if (g.instance == instance) {
                return g;
            }
        }
        return null;
    }

    /** What is worn in a slot, or null. */
    public OwnedGear worn(String slot) {
        Integer instance = equipped.get(slot);
        return instance == null ? null : gear(instance);
    }

    public int material(String id) {
        return materials.get(id, 0);
    }

    /**
     * Adds to a material count, and drops the key when it reaches zero.
     *
     * <p>Zero-valued counts are never written, which is the rule
     * {@link VillageState} already follows: a save file listing thirty stones
     * the player does not have is a save file nobody can read.
     */
    public void addMaterial(String id, int delta) {
        int now = material(id) + delta;
        if (now <= 0) {
            materials.remove(id, 0);
        } else {
            materials.put(id, now);
        }
    }
}
