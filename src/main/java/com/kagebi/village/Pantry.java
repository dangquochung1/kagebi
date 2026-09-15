package com.kagebi.village;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

import com.badlogic.gdx.utils.ObjectIntMap;
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

    /**
     * Packs everything into a run as it starts. The pantry is empty afterwards:
     * what goes down belongs to the run now, and a death loses it with the rest.
     */
    public static void packInto(VillageState v, ObjectIntMap<String> runItems) {
        for (ObjectIntMap.Entry<String> e : new ObjectIntMap.Entries<>(v.pantry)) {
            runItems.getAndIncrement(e.key, 0, e.value);
        }
        v.pantry.clear();
    }

    /**
     * What a cleared stage brings home: whatever is left of the run's
     * consumables, as far as the pantry has room, lowest id first. Things found
     * down there come home too - a potion out of a chest is as much the player's
     * as one bought. Returns how many came home.
     *
     * @param carriable which ids may come home at all: the consumables, as the content says
     */
    public static int bringHome(Profile p, ShopCatalog shop, ObjectIntMap<String> runItems,
                                Predicate<String> carriable) {
        List<String> ids = new ArrayList<>();
        for (ObjectIntMap.Entry<String> e : new ObjectIntMap.Entries<>(runItems)) {
            if (e.value > 0 && carriable.test(e.key)) {
                ids.add(e.key);
            }
        }
        Collections.sort(ids);
        int brought = 0;
        for (String id : ids) {
            int n = Math.min(room(p, shop), runItems.get(id, 0));
            if (n <= 0) {
                break;
            }
            Counts.add(p.village.pantry, id, n);
            brought += n;
        }
        return brought;
    }

    private Pantry() {}
}
