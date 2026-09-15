package com.kagebi.village;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.kagebi.data.ShopCatalog;
import com.kagebi.data.VillageCatalog;
import com.kagebi.data.def.ItemDef;
import com.kagebi.save.Profile;

/** Trade in the village: every trade happens whole, or not at all. */
class MarketTest {

    private VillageCatalog cat;
    private ShopCatalog shop;
    private Profile p;

    @BeforeEach
    void setUp() {
        cat = TestCatalog.small();
        shop = TestCatalog.shop();
        p = new Profile();
    }

    @Test
    void sellingMovesGoodsFromTheStorehouseIntoThePurse() {
        p.village.stock.put("egg", 3);
        assertTrue(Market.sell(p, cat.good("egg"), 2));
        assertEquals(8, p.gold);
        assertEquals(1, p.village.stock.get("egg", 0));
        assertFalse(Market.sell(p, cat.good("egg"), 2), "not more than is there");
        assertFalse(Market.sell(p, cat.good("egg"), 0), "and not nothing");
        assertEquals(8, p.gold);
        assertEquals(1, p.village.stock.get("egg", 0));
    }

    @Test
    void seedIsSoldAtTheFarmsLevelForItsPrice() {
        VillageCatalog.Crop pumpkin = cat.crop("pumpkin");
        p.gold = 100;
        assertFalse(Market.buySeeds(p, cat, pumpkin, 2), "a level-two seed at a level-one farm");
        p.village.harvests = 3;
        assertTrue(Market.buySeeds(p, cat, pumpkin, 2));
        assertEquals(90, p.gold);
        assertEquals(2, p.village.seeds.get("pumpkin", 0));
        p.gold = 9;
        assertFalse(Market.buySeeds(p, cat, pumpkin, 2), "and never on credit");
        assertEquals(9, p.gold);
        assertEquals(2, p.village.seeds.get("pumpkin", 0));
    }

    @Test
    void aToolClimbsItsPriceLadderAndStopsAtTheTop() {
        VillageCatalog.Tool pail = cat.tool("pail");
        p.gold = 1000;
        assertEquals(50, Market.toolCost(p, pail));
        assertTrue(Market.buyTool(p, pail));
        assertEquals(120, Market.toolCost(p, pail));
        assertTrue(Market.buyTool(p, pail));
        assertEquals(-1, Market.toolCost(p, pail));
        assertFalse(Market.buyTool(p, pail));
        assertEquals(830, p.gold);
        assertEquals(2, p.village.tools.get("pail", 0));
    }

    @Test
    void theHerbalistSellsWhatTheTraderSellsAndOnlyIntoRoomInThePantry() {
        ItemDef potion = new ItemDef("potion_small", "item.potion_small.name", "item.potion_small.desc",
            null, 1, ItemDef.Kind.CONSUMABLE, "heal", 25, 5, 45);
        ItemDef meal = new ItemDef("food_omelette", "item.food_omelette.name", "item.food_omelette.desc",
            null, 1, ItemDef.Kind.CONSUMABLE, "heal", 10, 3);
        p.gold = 1000;
        assertFalse(Market.buyItem(p, shop, meal), "a meal is cooked, not bought");
        for (int i = 0; i < Pantry.BASE; i++) {
            assertTrue(Market.buyItem(p, shop, potion));
        }
        assertFalse(Market.buyItem(p, shop, potion), "three is all a ninja without a satchel carries");
        assertEquals(1000 - Pantry.BASE * 45, p.gold);
        assertEquals(Pantry.BASE, p.village.pantry.get("potion_small", 0));
    }
}
