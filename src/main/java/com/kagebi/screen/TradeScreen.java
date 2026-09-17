package com.kagebi.screen;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Array;
import com.kagebi.Kagebi;
import com.kagebi.assets.Assets;
import com.kagebi.data.VillageCatalog;
import com.kagebi.data.def.ItemDef;
import com.kagebi.save.Profile;
import com.kagebi.village.Farm;
import com.kagebi.village.Kitchen;
import com.kagebi.village.Market;
import com.kagebi.village.Pantry;

/**
 * Trading on the island, in the Sunnyside pack's own boxes: what one of the
 * village's people will buy, sell or make.
 *
 * <p>One screen for three counters, because they are one shape - a shelf of
 * icons, a detail box, a key that does the thing - and differ only in what is
 * on the shelf. The herbalist buys the island's goods and sells potions for the
 * pantry and tools for the workers, and keeps the old shelf of upgrades and
 * unlocks one tab along; the farmer sells seed; the cook turns goods into meals.
 * The shape itself is {@link ShelfScreen}'s, shared with the bag.
 *
 * <p>The rules are {@code com.kagebi.village}'s. This decides only what is on
 * the shelf and what the words say.
 */
public class TradeScreen extends ShelfScreen {

    /** Who is behind the counter. */
    public enum Counter { HERBALIST, FARMER, COOK }

    private enum Tab {
        SELL("trade.tab.sell"),
        BUY("trade.tab.buy"),
        TOOLS("trade.tab.tools"),
        UPGRADES("trade.tab.upgrades"),
        SEEDS("trade.tab.seeds"),
        KITCHEN("trade.tab.kitchen");

        final String key;

        Tab(String key) {
            this.key = key;
        }
    }

    private final Counter counter;
    private final Tab[] tabs;
    private final String[] keys;

    public TradeScreen(Kagebi game, Counter counter) {
        super(game);
        this.counter = counter;
        switch (counter) {
            case FARMER:
                tabs = new Tab[] {Tab.SEEDS};
                break;
            case COOK:
                tabs = new Tab[] {Tab.KITCHEN};
                break;
            default:
                tabs = new Tab[] {Tab.SELL, Tab.BUY, Tab.TOOLS, Tab.UPGRADES};
                break;
        }
        keys = new String[tabs.length];
        for (int i = 0; i < tabs.length; i++) {
            keys[i] = tabs[i].key;
        }
    }

    /** Opens on the n-th tab, for {@code --screen counter}, which presses nothing. */
    TradeScreen onTab(int index) {
        openTab(index);
        return this;
    }

    @Override
    protected String[] tabKeys() {
        return keys;
    }

    /** The name of who is trading. */
    @Override
    protected String title() {
        return game.i18n().get(counter == Counter.FARMER ? "npc.worker_farm.name"
            : counter == Counter.COOK ? "npc.worker_kitchen.name"
            : "npc." + Assets.Npc.HERBALIST + ".name");
    }

    /** The pantry's room where things go into it, the farm's level where seed does. */
    @Override
    protected String aside(int tab) {
        Tab current = tabs[tab];
        Profile p = game.profile();
        return current == Tab.BUY || current == Tab.KITCHEN
            ? game.i18n().format("trade.pantry", Pantry.packed(p.village), Pantry.capacity(p, game.shop()))
            : current == Tab.SEEDS ? game.i18n().format("trade.farm_level", Farm.level(game.village(), p.village))
            : null;
    }

    // ---- the shelf ---------------------------------------------------------------

    @Override
    protected void stock(int tab, Array<Entry> into) {
        switch (tabs[tab]) {
            case SELL:
                stockGoods(into);
                break;
            case BUY:
                stockPotions(into);
                break;
            case TOOLS:
                for (VillageCatalog.Tool tool : game.village().tools()) {
                    into.add(new ToolOffer(tool));
                }
                break;
            case UPGRADES:
                into.add(new Shelf());
                break;
            case SEEDS:
                for (VillageCatalog.Crop crop : game.village().crops()) {
                    into.add(new SeedOffer(crop));
                }
                break;
            case KITCHEN:
                for (VillageCatalog.Recipe recipe : game.village().recipes()) {
                    if (game.content().hasItem(recipe.item)) {
                        into.add(new MealOffer(recipe));
                    }
                }
                break;
            default:
                break;
        }
    }

    private void stockGoods(Array<Entry> into) {
        for (VillageCatalog.Good good : game.village().goods()) {
            if (game.profile().village.stock.get(good.id, 0) > 0) {
                into.add(new GoodOffer(good));
            }
        }
    }

    /** What the dungeon's trader stocks, cheapest first: the herbalist sells at his prices. */
    private void stockPotions(Array<Entry> into) {
        Array<ItemDef> sold = new Array<>();
        for (ItemDef item : game.content().allItems()) {
            if (item.forSale() && item.kind == ItemDef.Kind.CONSUMABLE) {
                sold.add(item);
            }
        }
        sold.sort((a, b) -> a.price != b.price ? Integer.compare(a.price, b.price) : a.id.compareTo(b.id));
        for (ItemDef item : sold) {
            into.add(new PotionOffer(item));
        }
    }

    /**
     * A good's picture: the pack's ripe crop for a crop, and for everything else
     * the picture of the same name - except stone, which the pack calls rock.
     */
    static TextureRegion goodIcon(Kagebi game, String goodId) {
        String base = "ui/sunny/crops/";
        String[] names = {base + goodId + "_05", base + ("stone".equals(goodId) ? "rock" : goodId)};
        for (String name : names) {
            if (game.skin().has(name, TextureRegion.class)) {
                return game.skin().getRegion(name);
            }
        }
        return null;
    }

    // ---- what can be on the counter --------------------------------------------------

    private final class GoodOffer extends Entry {
        final VillageCatalog.Good good;

        GoodOffer(VillageCatalog.Good good) {
            this.good = good;
        }

        int held() {
            return game.profile().village.stock.get(good.id, 0);
        }

        @Override
        TextureRegion icon() {
            return goodIcon(game, good.id);
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
        String state() {
            return good.price + "g";
        }

        @Override
        String pip() {
            return held() > 1 ? String.valueOf(held()) : null;
        }

        @Override
        boolean can() {
            return held() > 0;
        }

        @Override
        boolean act() {
            return Market.sell(game.profile(), good, 1);
        }

        @Override
        String verb() {
            return "trade.sell";
        }
    }

    private final class PotionOffer extends Entry {
        final ItemDef item;

        PotionOffer(ItemDef item) {
            this.item = item;
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
            return Pantry.room(game.profile(), game.shop()) <= 0
                ? game.i18n().get("trade.pantry_full") : item.price + "g";
        }

        @Override
        String pip() {
            int packed = game.profile().village.pantry.get(item.id, 0);
            return packed > 0 ? String.valueOf(packed) : null;
        }

        @Override
        boolean can() {
            return Market.canBuyItem(game.profile(), game.shop(), item);
        }

        @Override
        boolean act() {
            return Market.buyItem(game.profile(), game.shop(), item);
        }

        @Override
        String verb() {
            return "trade.buy";
        }
    }

    private final class ToolOffer extends Entry {
        final VillageCatalog.Tool tool;

        ToolOffer(VillageCatalog.Tool tool) {
            this.tool = tool;
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
            return game.i18n().format("trade.level", Market.toolLevel(game.profile(), tool), tool.maxLevel);
        }

        @Override
        String state() {
            int cost = Market.toolCost(game.profile(), tool);
            return cost < 0 ? game.i18n().get("trade.max") : cost + "g";
        }

        @Override
        String pip() {
            int level = Market.toolLevel(game.profile(), tool);
            return level > 0 ? String.valueOf(level) : null;
        }

        @Override
        boolean can() {
            int cost = Market.toolCost(game.profile(), tool);
            return cost >= 0 && game.profile().gold >= cost;
        }

        @Override
        boolean act() {
            return Market.buyTool(game.profile(), tool);
        }

        @Override
        String verb() {
            return "trade.buy";
        }
    }

    private final class SeedOffer extends Entry {
        final VillageCatalog.Crop crop;

        SeedOffer(VillageCatalog.Crop crop) {
            this.crop = crop;
        }

        boolean locked() {
            return crop.farmLevel > Farm.level(game.village(), game.profile().village);
        }

        @Override
        TextureRegion icon() {
            return goodIcon(game, crop.good);
        }

        @Override
        String name() {
            return game.i18n().format("trade.seed", goodName(crop.good));
        }

        @Override
        String description() {
            int minutes = (int) Math.ceil(crop.stageSeconds * Farm.RIPE / 60.0);
            return game.i18n().format("trade.grows", minutes);
        }

        @Override
        String more() {
            return game.i18n().format("trade.held", game.profile().village.seeds.get(crop.id, 0));
        }

        @Override
        String state() {
            return locked() ? game.i18n().format("trade.farm_level", crop.farmLevel) : crop.seedPrice + "g";
        }

        @Override
        String pip() {
            int held = game.profile().village.seeds.get(crop.id, 0);
            return held > 0 ? String.valueOf(held) : null;
        }

        @Override
        boolean can() {
            return Market.canBuySeeds(game.profile(), game.village(), crop, 1);
        }

        @Override
        boolean act() {
            return Market.buySeeds(game.profile(), game.village(), crop, 1);
        }

        @Override
        String verb() {
            return "trade.buy";
        }
    }

    private final class MealOffer extends Entry {
        final VillageCatalog.Recipe recipe;
        final ItemDef item;

        MealOffer(VillageCatalog.Recipe recipe) {
            this.recipe = recipe;
            this.item = game.content().item(recipe.item);
        }

        @Override
        TextureRegion icon() {
            return InventoryScreen.itemIcon(game, item.id);
        }

        @Override
        String name() {
            return game.i18n().get(item.nameKey);
        }

        /** What goes in, each followed by how many are in the storehouse. */
        @Override
        String description() {
            StringBuilder needs = new StringBuilder();
            for (int i = 0; i < recipe.inputs.length && i < recipe.counts.length; i++) {
                if (needs.length() > 0) {
                    needs.append(", ");
                }
                int held = game.profile().village.stock.get(recipe.inputs[i], 0);
                needs.append(game.i18n().format("trade.amount", recipe.counts[i], goodName(recipe.inputs[i])))
                     .append(" (").append(held).append(')');
            }
            return game.i18n().format("trade.needs", needs);
        }

        @Override
        String more() {
            return game.i18n().get(item.descKey);
        }

        @Override
        String state() {
            return Pantry.room(game.profile(), game.shop()) <= 0 ? game.i18n().get("trade.pantry_full") : null;
        }

        @Override
        String pip() {
            int packed = game.profile().village.pantry.get(item.id, 0);
            return packed > 0 ? String.valueOf(packed) : null;
        }

        @Override
        boolean can() {
            return Kitchen.canCook(game.profile(), game.shop(), recipe);
        }

        @Override
        boolean act() {
            return Kitchen.cook(game.profile(), game.shop(), recipe);
        }

        @Override
        String verb() {
            return "trade.cook";
        }
    }

    /** The herbalist's old shelf - upgrades and unlocks - one key away. */
    private final class Shelf extends Entry {
        @Override
        TextureRegion icon() {
            return region("ui/sunny/icons/hammer");
        }

        @Override
        String name() {
            return game.i18n().get("trade.upgrades.name");
        }

        @Override
        String description() {
            return game.i18n().get("trade.upgrades.desc");
        }

        @Override
        String state() {
            return null;
        }

        @Override
        boolean can() {
            return true;
        }

        @Override
        boolean act() {
            game.audio().playSfx(Assets.SFX_ACCEPT);
            stack().push(new ShopScreen(game));
            return false;
        }

        @Override
        String verb() {
            return "trade.open";
        }
    }
}
