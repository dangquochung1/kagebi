package com.kagebi.loot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.kagebi.data.def.ItemDef;

/** What the dungeon shopkeeper is allowed to put on the shelf. */
class TraderStockTest {

    private static ItemDef item(String id, int price) {
        return new ItemDef(id, "n", "d", null, 1, ItemDef.Kind.CONSUMABLE,
                           "heal", 1f, 5, price);
    }

    private static List<ItemDef> catalogue() {
        List<ItemDef> out = new ArrayList<>();
        out.add(item("antidote", 35));
        out.add(item("map_scrap", 40));
        out.add(item("potion_small", 45));
        out.add(item("smoke_bomb", 55));
        out.add(item("draught_swift", 60));
        out.add(item("key_iron", 70));
        out.add(item("potion_large", 90));
        out.add(item("elixir_ward", 110));
        out.add(item("gold_coin", 0));          // never for sale
        return out;
    }

    @Test
    void nothingWithoutAPriceIsEverStocked() {
        for (long seed = 0; seed < 200; seed++) {
            for (ItemDef d : TraderStock.roll(catalogue(), seed)) {
                assertTrue(d.forSale(), d.id + " has no price and must not be sold");
            }
        }
    }

    @Test
    void theShelfNeverHoldsTheSameThingTwice() {
        for (long seed = 0; seed < 200; seed++) {
            Set<String> seen = new HashSet<>();
            for (ItemDef d : TraderStock.roll(catalogue(), seed)) {
                assertTrue(seen.add(d.id), "stocked " + d.id + " twice at seed " + seed);
            }
        }
    }

    /**
     * A shelf of three things the player cannot afford is a room that wasted
     * their time, so one slot is always something cheap.
     */
    @Test
    void thereIsAlwaysSomethingAPlayerCanNearlyAfford() {
        for (long seed = 0; seed < 200; seed++) {
            List<ItemDef> shelf = TraderStock.roll(catalogue(), seed);
            assertEquals(TraderStock.SIZE, shelf.size());
            assertTrue(shelf.get(0).price <= TraderStock.AFFORDABLE,
                "nothing affordable at seed " + seed + ": " + shelf);
        }
    }

    /** Cheapest on the left, so the shelf reads in one direction. */
    @Test
    void theShelfIsOrderedByPrice() {
        for (long seed = 0; seed < 50; seed++) {
            List<ItemDef> shelf = TraderStock.roll(catalogue(), seed);
            for (int i = 1; i < shelf.size(); i++) {
                assertTrue(shelf.get(i - 1).price <= shelf.get(i).price,
                    "out of order at seed " + seed + ": " + shelf);
            }
        }
    }

    /**
     * A run replays identically from its seed, so a shop must too - including
     * when the registry hands its items back in a different order, which an
     * unordered map is entitled to do between two runs of the same build.
     */
    @Test
    void theSameSeedStocksTheSameShelfWhateverOrderTheItemsArriveIn() {
        List<ItemDef> forwards = catalogue();
        List<ItemDef> backwards = catalogue();
        Collections.reverse(backwards);
        for (long seed = 0; seed < 100; seed++) {
            assertEquals(ids(TraderStock.roll(forwards, seed)),
                         ids(TraderStock.roll(backwards, seed)),
                         "shelf depended on catalogue order at seed " + seed);
        }
    }

    /** Two shops on one floor should not hold the same three things. */
    @Test
    void twoRoomsOnOneFloorStockDifferently() {
        long a = TraderStock.seedFor(1234L, 2, 3, 4);
        long b = TraderStock.seedFor(1234L, 2, 5, 4);
        assertFalse(a == b, "two rooms produced the same seed");
        // Same room, same run, same shelf - walking out and back in must not
        // reroll, or the shop is a slot machine.
        assertEquals(a, TraderStock.seedFor(1234L, 2, 3, 4));
    }

    @Test
    void anEmptyCatalogueIsAnEmptyShelfRatherThanACrash() {
        assertTrue(TraderStock.roll(new ArrayList<>(), 7L).isEmpty());
    }

    private static List<String> ids(List<ItemDef> shelf) {
        List<String> out = new ArrayList<>();
        for (ItemDef d : shelf) {
            out.add(d.id);
        }
        return out;
    }
}
