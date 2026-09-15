package com.kagebi.village;

import com.kagebi.data.ShopCatalog;
import com.kagebi.data.VillageCatalog;

/**
 * A small village with round numbers, so that every test's arithmetic can be
 * done in the head. The shipped village.json is checked elsewhere - by the
 * validator and by BalanceTest - and its numbers would only get in the way here.
 */
final class TestCatalog {

    static VillageCatalog small() {
        VillageCatalog c = new VillageCatalog();
        c.add(new VillageCatalog.Good("carrot", "good.carrot.name", 5));
        c.add(new VillageCatalog.Good("pumpkin", "good.pumpkin.name", 20));
        c.add(new VillageCatalog.Good("egg", "good.egg.name", 4));
        c.add(new VillageCatalog.Good("milk", "good.milk.name", 10));
        c.add(new VillageCatalog.Good("wood", "good.wood.name", 2));

        c.add(new VillageCatalog.Crop("carrot", "carrot", 2, 1, 100f, 1));
        c.add(new VillageCatalog.Crop("pumpkin", "pumpkin", 5, 2, 200f, 2));
        c.add(new VillageCatalog.FarmLevel(1, 0, 2));
        c.add(new VillageCatalog.FarmLevel(2, 3, 4));

        c.add(new VillageCatalog.Workshop("ranch", new String[] {"egg", "milk"}, new int[] {3, 1},
            60f, 3, "pail"));
        c.add(new VillageCatalog.Workshop("forest", new String[] {"wood"}, new int[] {1},
            10f, 5, "axe"));
        c.add(new VillageCatalog.Tool("pail", "tool.pail.name", "tool.pail.desc", 2,
            new int[] {50, 120}, 2f, 1));
        c.add(new VillageCatalog.Tool("axe", "tool.axe.name", "tool.axe.desc", 1,
            new int[] {30}, 1.5f, 2));

        c.add(new VillageCatalog.Recipe("omelette", "food_omelette",
            new String[] {"egg", "milk"}, new int[] {2, 1}));
        return c;
    }

    /** A shop with nothing in it but the satchel, which is what the pantry is measured by. */
    static ShopCatalog shop() {
        ShopCatalog shop = new ShopCatalog();
        shop.add(new ShopCatalog.Upgrade("satchel", "upgrade.satchel.name", "upgrade.satchel.desc", 1,
            3, new int[] {380, 850, 1700}, "potion_capacity_add", 1f));
        return shop;
    }

    private TestCatalog() {}
}
