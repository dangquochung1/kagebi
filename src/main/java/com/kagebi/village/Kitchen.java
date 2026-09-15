package com.kagebi.village;

import com.kagebi.data.ShopCatalog;
import com.kagebi.data.VillageCatalog;
import com.kagebi.save.Profile;

/**
 * The cook: goods from the storehouse in, a meal into the pantry out.
 *
 * <p>A meal goes straight into what the player carries down, because that is
 * the only thing a meal is for. So the kitchen will not cook when the pantry is
 * full, and spends nothing trying.
 */
public final class Kitchen {

    /**
     * What a meal's ingredients would sell for: its price, in the only currency
     * it has. BalanceTest holds each meal to its potion by this number.
     */
    public static int cost(VillageCatalog cat, VillageCatalog.Recipe r) {
        int total = 0;
        for (int i = 0; i < r.inputs.length && i < r.counts.length; i++) {
            VillageCatalog.Good good = cat.good(r.inputs[i]);
            total += (good == null ? 0 : good.price) * r.counts[i];
        }
        return total;
    }

    /** Whether every ingredient is in the storehouse and there is room to carry the meal. */
    public static boolean canCook(Profile p, ShopCatalog shop, VillageCatalog.Recipe r) {
        if (r == null || Pantry.room(p, shop) <= 0 || r.inputs.length != r.counts.length) {
            return false;
        }
        for (int i = 0; i < r.inputs.length; i++) {
            if (p.village.stock.get(r.inputs[i], 0) < r.counts[i]) {
                return false;
            }
        }
        return true;
    }

    /** Cooks one meal, or changes nothing and says so. */
    public static boolean cook(Profile p, ShopCatalog shop, VillageCatalog.Recipe r) {
        if (!canCook(p, shop, r)) {
            return false;
        }
        for (int i = 0; i < r.inputs.length; i++) {
            Counts.add(p.village.stock, r.inputs[i], -r.counts[i]);
        }
        Counts.add(p.village.pantry, r.item, 1);
        return true;
    }

    private Kitchen() {}
}
