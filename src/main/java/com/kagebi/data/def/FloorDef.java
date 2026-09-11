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

    /** 1 to 5. Floor 0 is the village hub, which is not procedural. */
    public final int number;
    public final String nameKey;

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

    public FloorDef(int number, String nameKey, String biome, String music,
                    String ambient, String bossMusic, int roomsMin, int roomsMax,
                    int treasureRooms, int shopRooms, String[] enemies,
                    int[] enemyWeights, int packMin, int packMax, String boss) {
        this.number = number;
        this.nameKey = nameKey;
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
    }

    public boolean hasBoss() {
        return boss != null;
    }

    @Override
    public String toString() {
        return "FloorDef(" + number + ")";
    }
}
