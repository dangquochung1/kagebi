package com.kagebi.village;

import com.kagebi.data.ShopCatalog;
import com.kagebi.data.VillageCatalog;
import com.kagebi.data.def.ItemDef;
import com.kagebi.save.Profile;

/**
 * Trade in the village: goods sold to the herbalist, seed bought from the
 * farmer, and tools and potions bought from the herbalist.
 *
 * <p>Every method makes the whole trade or none of it. A trade the purse or the
 * storehouse cannot cover returns false and leaves the profile as it was, which
 * is the one thing a trade screen has to be able to trust.
 */
public final class Market {

    /** Sells goods out of the storehouse at their price. */
    public static boolean sell(Profile p, VillageCatalog.Good good, int count) {
        if (good == null || count <= 0 || p.village.stock.get(good.id, 0) < count) {
            return false;
        }
        Counts.add(p.village.stock, good.id, -count);
        p.gold += good.price * count;
        return true;
    }

    public static int seedCost(VillageCatalog.Crop crop, int count) {
        return crop.seedPrice * count;
    }

    /** Whether the farmer sells this seed now: the farm has reached its level, and the purse covers it. */
    public static boolean canBuySeeds(Profile p, VillageCatalog cat, VillageCatalog.Crop crop, int count) {
        return crop != null && count > 0
            && crop.farmLevel <= Farm.level(cat, p.village)
            && p.gold >= seedCost(crop, count);
    }

    public static boolean buySeeds(Profile p, VillageCatalog cat, VillageCatalog.Crop crop, int count) {
        if (!canBuySeeds(p, cat, crop, count)) {
            return false;
        }
        p.gold -= seedCost(crop, count);
        Counts.add(p.village.seeds, crop.id, count);
        return true;
    }

    /** The tool's level, capped at its top: a hand-edited level is not trusted. */
    public static int toolLevel(Profile p, VillageCatalog.Tool tool) {
        return Math.max(0, Math.min(tool.maxLevel, p.village.tools.get(tool.id, 0)));
    }

    /** Price of the tool's next level, or -1 once it is at the top. */
    public static int toolCost(Profile p, VillageCatalog.Tool tool) {
        int level = toolLevel(p, tool);
        return level >= tool.maxLevel ? -1 : tool.costs[level];
    }

    public static boolean buyTool(Profile p, VillageCatalog.Tool tool) {
        int price = toolCost(p, tool);
        if (price < 0 || p.gold < price) {
            return false;
        }
        p.gold -= price;
        p.village.tools.put(tool.id, toolLevel(p, tool) + 1);
        return true;
    }

    /**
     * Whether the herbalist sells this: a consumable the dungeon's trader
     * stocks, at the trader's price, with room for it in the pantry. A meal has
     * no price, so it is never for sale here either.
     */
    public static boolean canBuyItem(Profile p, ShopCatalog shop, ItemDef item) {
        return item != null && item.forSale() && item.kind == ItemDef.Kind.CONSUMABLE
            && p.gold >= item.price && Pantry.room(p, shop) > 0;
    }

    public static boolean buyItem(Profile p, ShopCatalog shop, ItemDef item) {
        if (!canBuyItem(p, shop, item)) {
            return false;
        }
        p.gold -= item.price;
        Counts.add(p.village.pantry, item.id, 1);
        return true;
    }

    private Market() {}
}
