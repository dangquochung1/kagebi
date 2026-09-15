package com.kagebi.village;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.kagebi.data.ShopCatalog;
import com.kagebi.data.VillageCatalog;
import com.kagebi.save.Profile;

/** The cook: the storehouse in, the pantry out. */
class KitchenTest {

    private VillageCatalog cat;
    private ShopCatalog shop;
    private Profile p;
    private VillageCatalog.Recipe omelette;

    @BeforeEach
    void setUp() {
        cat = TestCatalog.small();
        shop = TestCatalog.shop();
        p = new Profile();
        omelette = cat.recipe("omelette");
    }

    @Test
    void aMealIsWorthWhatItsIngredientsSellFor() {
        assertEquals(2 * 4 + 10, Kitchen.cost(cat, omelette));
    }

    @Test
    void cookingSpendsTheIngredientsAndPacksTheMeal() {
        p.village.stock.put("egg", 3);
        p.village.stock.put("milk", 1);
        assertTrue(Kitchen.cook(p, shop, omelette));
        assertEquals(1, p.village.stock.get("egg", 0));
        assertFalse(p.village.stock.containsKey("milk"), "used up, not left at zero");
        assertEquals(1, p.village.pantry.get("food_omelette", 0));
    }

    @Test
    void noMealWithoutEveryIngredient() {
        p.village.stock.put("egg", 2);
        assertFalse(Kitchen.canCook(p, shop, omelette));
        assertFalse(Kitchen.cook(p, shop, omelette));
        assertEquals(2, p.village.stock.get("egg", 0));
    }

    @Test
    void noMealWithoutRoomInThePantryAndNothingSpentTrying() {
        p.village.stock.put("egg", 2);
        p.village.stock.put("milk", 1);
        p.village.pantry.put("potion_small", Pantry.BASE);
        assertFalse(Kitchen.cook(p, shop, omelette));
        assertEquals(2, p.village.stock.get("egg", 0));
        assertEquals(1, p.village.stock.get("milk", 0));
    }
}
