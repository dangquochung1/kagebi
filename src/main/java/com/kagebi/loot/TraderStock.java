package com.kagebi.loot;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.kagebi.data.def.ItemDef;

/**
 * What the shopkeeper has on the shelf this time.
 *
 * <p>Pure: it takes the items the content declares for sale and a seed, and
 * gives back a few of them. No Gdx, so the rules - how many, never twice the
 * same thing, always something cheap - are tested rather than played.
 *
 * <p><b>Seeded from the room, not from a fresh random.</b> A run is meant to
 * replay identically from its seed, and a shop that rolled from
 * {@code new Random()} would break that quietly: the same seed would give the
 * same floor with different things in it. Deriving the seed from the run and
 * the room also means walking out of a shop and back in does not reroll the
 * stock, which is what turns a shop into a slot machine.
 */
public final class TraderStock {

    /** How many things one shopkeeper carries. */
    public static final int SIZE = 3;

    /**
     * The dearest thing that still counts as cheap, in gold.
     *
     * <p>One slot is reserved for something at or under this, because a shelf
     * of three things a player cannot afford is a room that wasted their time.
     * Floor 1 yields 164 gold over its whole length, so a shop met halfway
     * through it faces a purse of about eighty.
     */
    public static final int AFFORDABLE = 55;

    private TraderStock() {}

    /**
     * Rolls a shelf.
     *
     * @param catalogue every item the content knows; the ones not for sale are
     *                  ignored, so callers may pass the lot
     * @param seed      the run seed mixed with the room, so the same room in
     *                  the same run always holds the same goods
     */
    public static List<ItemDef> roll(Iterable<ItemDef> catalogue, long seed) {
        List<ItemDef> pool = new ArrayList<>();
        for (ItemDef d : catalogue) {
            if (d.forSale()) {
                pool.add(d);
            }
        }
        // Sorted before shuffling: the registry hands items back in whatever
        // order its map iterates, which is not the order the file declares
        // them. Without this the same seed would stock different shelves
        // between two runs of the same build.
        pool.sort((a, b) -> a.id.compareTo(b.id));

        List<ItemDef> out = new ArrayList<>();
        if (pool.isEmpty()) {
            return out;
        }
        Random rng = new Random(seed);

        // The cheap slot first, so it cannot be crowded out by the shuffle.
        List<ItemDef> cheap = new ArrayList<>();
        for (ItemDef d : pool) {
            if (d.price <= AFFORDABLE) {
                cheap.add(d);
            }
        }
        if (!cheap.isEmpty()) {
            ItemDef pick = cheap.get(rng.nextInt(cheap.size()));
            out.add(pick);
            pool.remove(pick);
        }
        while (out.size() < SIZE && !pool.isEmpty()) {
            out.add(pool.remove(rng.nextInt(pool.size())));
        }
        // Cheapest on the left. A shelf a player reads left to right should
        // start with what they can almost certainly buy.
        out.sort((a, b) -> Integer.compare(a.price, b.price));
        return out;
    }

    /**
     * The seed for one shop room. Mixing the room's grid position in means two
     * shops on one floor do not stock the same three things.
     */
    public static long seedFor(long runSeed, int floor, int roomX, int roomY) {
        long h = runSeed * 31L + floor;
        h = h * 31L + roomX;
        h = h * 31L + roomY;
        return h ^ 0x5DEECE66DL;
    }
}
