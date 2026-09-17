package com.kagebi.screen;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectIntMap;
import com.kagebi.Kagebi;
import com.kagebi.data.VillageCatalog;
import com.kagebi.data.def.ItemDef;
import com.kagebi.run.RunState;
import com.kagebi.save.Profile;
import com.kagebi.save.VillageState;
import com.kagebi.village.Farm;
import com.kagebi.village.Market;
import com.kagebi.village.Pantry;
import com.kagebi.village.Workshops;

/**
 * The player's bag in the village, on Tab: what is in the storehouse, the
 * workers' tools, how each of the island's people is getting on, what is packed
 * to go down, and the way to the kit.
 *
 * <p>A place to look rather than to trade. Only two things on it do anything:
 * a packed meal or potion can be put on the quick key, and the last tab opens
 * the loadout. Everything else is bought, sold and cooked at a counter, so the
 * bag never has to decide a price.
 */
public class BagScreen extends ShelfScreen {

    private static final String[] TABS = {
        "bag.tab.items", "bag.tab.tools", "bag.tab.people", "bag.tab.carry", "bag.tab.kit",
    };
    private static final int ITEMS = 0;
    private static final int TOOLS = 1;
    private static final int PEOPLE = 2;
    private static final int CARRY = 3;
    private static final int KIT = 4;

    /** The meal that stands for the cook, who has no tool. */
    private static final String COOK_ICON = "food_onigiri";

    private final Runnable openLoadout;

    /**
     * @param openLoadout how the village opens the kit, so the kit it comes back
     *                    from is the one the village walks with
     */
    public BagScreen(Kagebi game, Runnable openLoadout) {
        super(game);
        this.openLoadout = openLoadout;
    }

    /** Opens on the n-th tab, for {@code --screen bag}. */
    BagScreen onTab(int index) {
        openTab(index);
        return this;
    }

    @Override
    protected String[] tabKeys() {
        return TABS;
    }

    @Override
    protected String title() {
        return game.i18n().get("bag.title");
    }

    @Override
    protected String aside(int tab) {
        Profile p = game.profile();
        if (tab == CARRY) {
            return game.i18n().format("trade.pantry", Pantry.packed(p.village), Pantry.capacity(p, game.shop()));
        }
        return null;
    }

    @Override
    protected void stock(int tab, Array<Entry> into) {
        VillageCatalog village = game.village();
        VillageState v = game.profile().village;
        switch (tab) {
            case ITEMS:
                for (VillageCatalog.Good good : village.goods()) {
                    if (v.stock.get(good.id, 0) > 0) {
                        into.add(new GoodEntry(good));
                    }
                }
                for (VillageCatalog.Crop crop : village.crops()) {
                    if (v.seeds.get(crop.id, 0) > 0) {
                        into.add(new SeedEntry(crop));
                    }
                }
                break;
            case TOOLS:
                for (VillageCatalog.Tool tool : village.tools()) {
                    into.add(new ToolEntry(tool));
                }
                break;
            case PEOPLE:
                into.add(new FarmerEntry());
                for (VillageCatalog.Workshop workshop : village.workshops()) {
                    into.add(new WorkerEntry(workshop));
                }
                into.add(new CookEntry());
                break;
            case CARRY:
                Array<String> packed = new Array<>();
                for (ObjectIntMap.Entry<String> e : new ObjectIntMap.Entries<>(v.pantry)) {
                    if (e.value > 0 && game.content().hasItem(e.key)) {
                        packed.add(e.key);
                    }
                }
                packed.sort();
                for (String id : packed) {
                    into.add(new CarryEntry(game.content().item(id)));
                }
                break;
            case KIT:
                into.add(new KitEntry());
                break;
            default:
                break;
        }
    }

    /** The crop that grows a good, or null for a good nobody plants. */
    private VillageCatalog.Crop cropOf(String goodId) {
        for (VillageCatalog.Crop crop : game.village().crops()) {
            if (crop.good.equals(goodId)) {
                return crop;
            }
        }
        return null;
    }

    private static int minutes(double seconds) {
        return Math.max(1, (int) Math.ceil(seconds / 60.0));
    }

    // ---- what is in the bag ----------------------------------------------------------

    /** A good in the storehouse: how many, what it fetches, and how a crop is grown. */
    private final class GoodEntry extends Entry {
        final VillageCatalog.Good good;

        GoodEntry(VillageCatalog.Good good) {
            this.good = good;
        }

        int held() {
            return game.profile().village.stock.get(good.id, 0);
        }

        @Override
        TextureRegion icon() {
            return TradeScreen.goodIcon(game, good.id);
        }

        @Override
        String name() {
            return game.i18n().get(good.nameKey);
        }

        @Override
        String description() {
            return game.i18n().format("trade.held", held());
        }

        @Override
        String more() {
            VillageCatalog.Crop crop = cropOf(good.id);
            return crop == null ? null
                : game.i18n().format("bag.ripens", minutes(crop.stageSeconds * Farm.RIPE), crop.farmLevel);
        }

        @Override
        String state() {
            return good.price + "g";
        }

        @Override
        String pip() {
            return held() > 1 ? String.valueOf(held()) : null;
        }

        @Override
        String verb() {
            return null;
        }
    }

    /** Seed in hand, shown as its crop just sown. */
    private final class SeedEntry extends Entry {
        final VillageCatalog.Crop crop;

        SeedEntry(VillageCatalog.Crop crop) {
            this.crop = crop;
        }

        @Override
        TextureRegion icon() {
            TextureRegion sown = region("ui/sunny/crops/" + crop.id + "_01");
            return sown != null ? sown : TradeScreen.goodIcon(game, crop.good);
        }

        @Override
        String name() {
            return game.i18n().format("trade.seed", goodName(crop.good));
        }

        @Override
        String description() {
            return game.i18n().format("trade.held", game.profile().village.seeds.get(crop.id, 0));
        }

        @Override
        String more() {
            return game.i18n().format("bag.ripens", minutes(crop.stageSeconds * Farm.RIPE), crop.farmLevel);
        }

        @Override
        String state() {
            return null;
        }

        @Override
        String pip() {
            int held = game.profile().village.seeds.get(crop.id, 0);
            return held > 1 ? String.valueOf(held) : null;
        }

        @Override
        String verb() {
            return null;
        }
    }

    /** A worker's tool: its level, and what that level makes of their work. */
    private final class ToolEntry extends Entry {
        final VillageCatalog.Tool tool;

        ToolEntry(VillageCatalog.Tool tool) {
            this.tool = tool;
        }

        VillageCatalog.Workshop workshop() {
            for (VillageCatalog.Workshop w : game.village().workshops()) {
                if (tool.id.equals(w.tool)) {
                    return w;
                }
            }
            return null;
        }

        @Override
        TextureRegion icon() {
            return toolIcon(tool);
        }

        @Override
        String name() {
            return game.i18n().get(tool.nameKey);
        }

        @Override
        String description() {
            return game.i18n().get(tool.descKey);
        }

        @Override
        String more() {
            VillageCatalog.Workshop w = workshop();
            if (w == null) {
                return null;
            }
            VillageState v = game.profile().village;
            return game.i18n().format("bag.tool_effect",
                minutes(Workshops.period(game.village(), v, w)), Workshops.capacity(game.village(), v, w));
        }

        @Override
        String state() {
            return game.i18n().format("trade.level", Market.toolLevel(game.profile(), tool), tool.maxLevel);
        }

        @Override
        String pip() {
            int level = Market.toolLevel(game.profile(), tool);
            return level > 0 ? String.valueOf(level) : null;
        }

        @Override
        String verb() {
            return null;
        }
    }

    /** The goblin farmer: the farm's level and how much of it is ripe. */
    private final class FarmerEntry extends Entry {
        @Override
        TextureRegion icon() {
            return region("ui/sunny/icons/shovel");
        }

        @Override
        String name() {
            return game.i18n().get("npc.worker_farm.name");
        }

        @Override
        String description() {
            VillageCatalog village = game.village();
            VillageState v = game.profile().village;
            int open = Farm.openPlots(village, v);
            int ripe = 0;
            for (int i = 0; i < open; i++) {
                if (Farm.ripe(village, v, i)) {
                    ripe++;
                }
            }
            return game.i18n().format("bag.farm", ripe, open);
        }

        @Override
        String state() {
            return game.i18n().format("trade.farm_level", Farm.level(game.village(), game.profile().village));
        }

        @Override
        String verb() {
            return null;
        }
    }

    /** A region's worker: what they make, how much is waiting, and when the next is due. */
    private final class WorkerEntry extends Entry {
        final VillageCatalog.Workshop workshop;

        WorkerEntry(VillageCatalog.Workshop workshop) {
            this.workshop = workshop;
        }

        @Override
        TextureRegion icon() {
            VillageCatalog.Tool tool = game.village().tool(workshop.tool);
            return tool == null ? TradeScreen.goodIcon(game, workshop.goods[0]) : toolIcon(tool);
        }

        @Override
        String name() {
            return game.i18n().get("npc.worker_" + workshop.id + ".name");
        }

        @Override
        String description() {
            StringBuilder goods = new StringBuilder();
            for (String good : workshop.goods) {
                if (goods.length() > 0) {
                    goods.append(", ");
                }
                goods.append(goodName(good));
            }
            return game.i18n().format("bag.makes", goods);
        }

        @Override
        String more() {
            VillageState v = game.profile().village;
            double until = Workshops.untilNext(game.village(), v, workshop);
            return until < 0 ? game.i18n().get("bag.full") : game.i18n().format("bag.next", minutes(until));
        }

        @Override
        String state() {
            VillageState v = game.profile().village;
            return game.i18n().format("bag.waiting", Workshops.ready(game.village(), v, workshop),
                                      Workshops.capacity(game.village(), v, workshop));
        }

        @Override
        String pip() {
            int ready = Workshops.ready(game.village(), game.profile().village, workshop);
            return ready > 0 ? String.valueOf(ready) : null;
        }

        @Override
        String verb() {
            return null;
        }
    }

    /** The cook: how much of the pantry is packed. */
    private final class CookEntry extends Entry {
        @Override
        TextureRegion icon() {
            return InventoryScreen.itemIcon(game, COOK_ICON);
        }

        @Override
        String name() {
            return game.i18n().get("npc.worker_kitchen.name");
        }

        @Override
        String description() {
            Profile p = game.profile();
            return game.i18n().format("trade.pantry", Pantry.packed(p.village), Pantry.capacity(p, game.shop()));
        }

        @Override
        String state() {
            return null;
        }

        @Override
        String verb() {
            return null;
        }
    }

    /** Something packed to go down; the key puts it on the quick key. */
    private final class CarryEntry extends Entry {
        final ItemDef item;

        CarryEntry(ItemDef item) {
            this.item = item;
        }

        boolean onQuick() {
            RunState run = game.run();
            return run != null && item.id.equals(run.quickItem);
        }

        @Override
        TextureRegion icon() {
            return InventoryScreen.itemIcon(game, item.id);
        }

        @Override
        String name() {
            return game.i18n().get(item.nameKey);
        }

        @Override
        String description() {
            return game.i18n().get(item.descKey);
        }

        @Override
        String state() {
            return onQuick() ? game.i18n().get("inv.on_quick") : null;
        }

        @Override
        String pip() {
            int packed = game.profile().village.pantry.get(item.id, 0);
            return packed > 1 ? String.valueOf(packed) : null;
        }

        @Override
        boolean can() {
            return game.run() != null && !onQuick();
        }

        @Override
        boolean act() {
            game.run().quickItem = item.id;
            return true;
        }

        @Override
        String verb() {
            return "inv.set_quick";
        }

        /** What is already on the key is not greyed out for being on it. */
        @Override
        boolean dimmed() {
            return false;
        }
    }

    /** The loadout: who goes down, with what. */
    private final class KitEntry extends Entry {
        @Override
        TextureRegion icon() {
            return region("ui/sunny/icons/sword");
        }

        @Override
        String name() {
            return game.i18n().get("bag.kit.name");
        }

        @Override
        String description() {
            return game.i18n().get("bag.kit.desc");
        }

        @Override
        String state() {
            return null;
        }

        @Override
        boolean can() {
            return true;
        }

        /** Closes the bag first, so the kit closes back onto the village and it re-reads the ninja. */
        @Override
        boolean act() {
            stack().pop();
            openLoadout.run();
            return false;
        }

        @Override
        String verb() {
            return "trade.open";
        }
    }
}
