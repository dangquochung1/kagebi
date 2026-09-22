package com.kagebi.data.def;

/**
 * One floor of the dungeon, from {@code assets/data/floors.json}.
 *
 * <p>This is where the art direction is enforced as data rather than as good
 * intentions. The packs in use have almost no palette in common - measured
 * overlap between any two is nought to two colours - so mixing them inside one
 * room reads as a mistake. A floor therefore names exactly one {@link #biome},
 * and its enemy pool is expected to come from the same family: floors 1 to 3
 * draw on the Ninja Adventure monsters, floors 4 and 5 on the dungeon packs,
 * whose outlines are both {@code #25131A}.
 */
public final class FloorDef {

    /** 1 upwards. Floor 0 is the village hub, which is not procedural. */
    public final int number;
    public final String nameKey;
    /**
     * A sentence or two for the world map's panel, where a stage is chosen
     * before it is seen.
     *
     * <p>In the content rather than built as {@code nameKey + ".desc"} so that
     * ContentValidator checks it exists in every language at boot, instead of
     * a missing one showing up as an empty panel on a screen nobody opened.
     */
    public final String descKey;

    /** Which room templates to draw on; also picks the tileset. */
    public final String biome;

    public final String music;
    /** Looping ambience, streamed rather than held in RAM. Nullable. */
    public final String ambient;
    /** Music for the boss room, or null to keep playing {@link #music}. */
    public final String bossMusic;

    public final int roomsMin;
    public final int roomsMax;
    public final int treasureRooms;
    public final int shopRooms;

    /** Enemy ids this floor may spawn, parallel to {@link #enemyWeights}. */
    public final String[] enemies;
    public final int[] enemyWeights;
    /** How many enemies a normal room holds. */
    public final int packMin;
    public final int packMax;

    /** Boss enemy id, or null on a floor with no boss. */
    public final String boss;

    /**
     * A stage off the main descent: open from the start, and not the ending.
     *
     * <p>The five numbered floors are a sequence - each opens the next, and
     * clearing the deepest one is the win. A side stage is neither. It exists
     * so content can be added without renumbering the descent or moving where
     * the story ends, which is what floors 6 and 7 - the Drowned Cove and the
     * Sunken Vault - are.
     *
     * <p>Two screens read it: the world map, which unlocks it regardless of
     * what has been cleared, and the dungeon, which must not show the victory
     * screen for finishing it.
     */
    public final boolean side;

    /**
     * What this floor multiplies its roster's hit points and damage by.
     *
     * <p>One, unless a floor says otherwise. It exists because a roster and a
     * difficulty are different things and the data could only say the first:
     * floor 5 wanted floor 4's enemies at floor 5's numbers and had to be
     * given four defs of its own instead, and stage 7 wants stage 6's slimes
     * a floor deeper. Without this the only way to say "the same enemy, but
     * harder" is a second id, a second name in two languages and a second row
     * in every table that lists enemies - which is four places for one number
     * to drift out of step.
     *
     * <p>Applied where an enemy is spawned, not baked into the def: the same
     * {@code EnemyDef} is shared by every floor that names it.
     */
    public final float hpScale;
    public final float damageScale;

    public FloorDef(int number, String nameKey, String descKey, String biome,
                    String music, String ambient, String bossMusic,
                    int roomsMin, int roomsMax,
                    int treasureRooms, int shopRooms, String[] enemies,
                    int[] enemyWeights, int packMin, int packMax, String boss) {
        this(number, nameKey, descKey, biome, music, ambient, bossMusic,
             roomsMin, roomsMax, treasureRooms, shopRooms, enemies, enemyWeights,
             packMin, packMax, boss, false, 1f, 1f);
    }

    public FloorDef(int number, String nameKey, String descKey, String biome,
                    String music, String ambient, String bossMusic,
                    int roomsMin, int roomsMax,
                    int treasureRooms, int shopRooms, String[] enemies,
                    int[] enemyWeights, int packMin, int packMax, String boss,
                    boolean side) {
        this(number, nameKey, descKey, biome, music, ambient, bossMusic,
             roomsMin, roomsMax, treasureRooms, shopRooms, enemies, enemyWeights,
             packMin, packMax, boss, side, 1f, 1f);
    }

    public FloorDef(int number, String nameKey, String descKey, String biome,
                    String music, String ambient, String bossMusic,
                    int roomsMin, int roomsMax,
                    int treasureRooms, int shopRooms, String[] enemies,
                    int[] enemyWeights, int packMin, int packMax, String boss,
                    boolean side, float hpScale, float damageScale) {
        this.number = number;
        this.nameKey = nameKey;
        this.descKey = descKey;
        this.biome = biome;
        this.music = music;
        this.ambient = ambient;
        this.bossMusic = bossMusic;
        this.roomsMin = roomsMin;
        this.roomsMax = roomsMax;
        this.treasureRooms = treasureRooms;
        this.shopRooms = shopRooms;
        this.enemies = enemies;
        this.enemyWeights = enemyWeights;
        this.packMin = packMin;
        this.packMax = packMax;
        this.boss = boss;
        this.side = side;
        this.hpScale = hpScale;
        this.damageScale = damageScale;
    }

    public boolean hasBoss() {
        return boss != null;
    }

    @Override
    public String toString() {
        return "FloorDef(" + number + ")";
    }
}
