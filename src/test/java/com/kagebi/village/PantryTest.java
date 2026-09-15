package com.kagebi.village;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.badlogic.gdx.utils.ObjectIntMap;
import com.kagebi.data.ShopCatalog;
import com.kagebi.save.Profile;

/** What the ninja carries down: three things, and one more per satchel level. */
class PantryTest {

    private final ShopCatalog shop = TestCatalog.shop();
    private final Profile p = new Profile();

    @Test
    void aNinjaCarriesThreeThingsAndOneMorePerLevelOfSatchel() {
        assertEquals(3, Pantry.capacity(p, shop));
        p.upgrades.put("satchel", 2);
        assertEquals(5, Pantry.capacity(p, shop));
    }

    @Test
    void whatIsPackedTakesRoomAndTakingItOutGivesItBack() {
        p.village.pantry.put("food_omelette", 2);
        p.village.pantry.put("potion_small", 1);
        assertEquals(3, Pantry.packed(p.village), "counted as things, not as kinds");
        assertEquals(0, Pantry.room(p, shop));
        assertTrue(Pantry.take(p.village, "food_omelette", 1));
        assertEquals(1, Pantry.room(p, shop));
        assertFalse(Pantry.take(p.village, "food_omelette", 5), "not more than is there");
        assertEquals(1, p.village.pantry.get("food_omelette", 0));
        assertTrue(Pantry.take(p.village, "food_omelette", 1));
        assertFalse(p.village.pantry.containsKey("food_omelette"), "and nothing left at zero");
    }

    @Test
    void whatIsPackedGoesDownWithTheRunAndLeavesThePantryEmpty() {
        p.village.pantry.put("food_omelette", 2);
        ObjectIntMap<String> runItems = new ObjectIntMap<>();
        runItems.put("potion_small", 1);
        Pantry.packInto(p.village, runItems);
        assertEquals(2, runItems.get("food_omelette", 0));
        assertEquals(1, runItems.get("potion_small", 0), "added to what the run already had");
        assertEquals(0, p.village.pantry.size);
    }

    @Test
    void aClearedStageBringsTheLeftoversHomeAsFarAsThereIsRoom() {
        ObjectIntMap<String> runItems = new ObjectIntMap<>();
        runItems.put("food_omelette", 2);
        runItems.put("potion_small", 3);
        runItems.put("antidote", 0);
        runItems.put("key_iron", 5);
        int brought = Pantry.bringHome(p, shop, runItems, id -> !id.startsWith("key"));
        assertEquals(3, brought, "three is all there is room for");
        assertEquals(2, p.village.pantry.get("food_omelette", 0), "lowest id first");
        assertEquals(1, p.village.pantry.get("potion_small", 0));
        assertFalse(p.village.pantry.containsKey("key_iron"), "only what may come home does");
        assertFalse(p.village.pantry.containsKey("antidote"), "and nothing of which none is left");
    }
}
