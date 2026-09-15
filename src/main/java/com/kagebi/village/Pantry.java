package com.kagebi.village;

import com.kagebi.data.ShopCatalog;
import com.kagebi.save.Profile;
import com.kagebi.save.VillageState;

/**
 * What the ninja carries down into the dungeon: meals from the kitchen and
 * potions bought from the herbalist.
 *
 * <p>Three things, and one more for every level of the satchel - the upgrade
 * that was always about carrying more, and is what the pantry is measured by.
 * Counted as things, not as kinds: three sushi fill it as surely as a sushi, a
 * potion and a bowl of noodles.
 */
public final class Pantry {

    /** What a ninja with no satchel carries. */
    public static final int BASE = 3;

    public static int capacity(Profile p, ShopCatalog shop) {
        return BASE + Math.round(shop.upgradeValue("potion_capacity_add", p));
    }

    /** Everything packed, counted one by one. */
    public static int packed(VillageState v) {
        return Counts.total(v.pantry);
    }

    public static int room(Profile p, ShopCatalog shop) {
        return Math.max(0, capacity(p, shop) - packed(p.village));
    }

    /** Takes things back out, all of them or none. */
    public static boolean take(VillageState v, String itemId, int count) {
        if (count <= 0 || v.pantry.get(itemId, 0) < count) {
            return false;
        }
        Counts.add(v.pantry, itemId, -count);
        return true;
    }

    private Pantry() {}
}
