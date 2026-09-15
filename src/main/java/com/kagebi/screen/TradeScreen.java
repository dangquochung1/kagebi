package com.kagebi.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;
import com.kagebi.Cfg;
import com.kagebi.Kagebi;
import com.kagebi.assets.Assets;
import com.kagebi.data.VillageCatalog;
import com.kagebi.data.def.ItemDef;
import com.kagebi.gfx.CameraController;
import com.kagebi.input.GameAction;
import com.kagebi.save.Profile;
import com.kagebi.ui.Hud;
import com.kagebi.ui.I18n;
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
 *
 * <p>Built the way {@link ShopScreen} is - straight to the batch, a focus ring
 * driven by the movement keys, running off the top or bottom of a shelf to
 * change tab - so the village's two trading screens are learned once. Like it,
 * every trade writes the save at once: the goods have left the storehouse, and
 * the gold and the goods must land together.
 *
 * <p>The rules are {@code com.kagebi.village}'s. This decides only what is on
 * the shelf and what the words say.
 */
public class TradeScreen extends SimScreen {

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

    private static final int COLUMNS = 9;
    private static final int CELL = 20;
    private static final int GAP = 3;

    private static final int PANEL_X = 8;
    private static final int PANEL_W = Cfg.VIRT_W - 2 * PANEL_X;
    private static final int PANEL_Y = 4;
    private static final int PANEL_H = Cfg.VIRT_H - 2 * PANEL_Y;

    // Line tops leave the four rows above cap height a stacked tone mark needs.
    private static final int TITLE_TOP = PANEL_Y + PANEL_H - 7;
    private static final int TAB_TOP = TITLE_TOP - Hud.LINE - 5;
    private static final int GRID_TOP = TAB_TOP - Hud.LINE - 6;
    private static final int DETAIL_Y = PANEL_Y + 26;
    private static final int DETAIL_H = 50;
    private static final int PROMPT_BOTTOM = PANEL_Y + 8;

    /** How long "done" stands where the price was, after a trade. */
    private static final int FLASH_STEPS = 60;

    private static final String BOX_DARK = "ui/sunny/box_dark";
    private static final String BOX_LIGHT = "ui/sunny/box_light";
    private static final String LABEL = "ui/sunny/label";
    private static final String CORNER = "ui/sunny/icons/selectbox_";

    /** On the dark box. */
    private static final Color CREAM = new Color(0xffe6c4ff);
    private static final Color DIM = new Color(0xb89a78ff);
    private static final Color GOLD = new Color(0xffad55ff);
    /** On the light box and the label. */
    private static final Color INK = new Color(0x2e1d16ff);
    private static final Color SOFT = new Color(0x6b4a36ff);
    private static final Color DENIED = new Color(0xa8443aff);
    private static final Color DONE = new Color(0x4f7f34ff);
    private static final Color LOCKED = new Color(0.45f, 0.42f, 0.46f, 1f);

    private final Kagebi game;
    private final Counter counter;
    private final Tab[] tabs;
    private final CameraController camera = new CameraController();
    private BitmapFont font;

    private final Array<Offer> offers = new Array<>();
    private int tab;
    private int focus;
    private int flash;

    public TradeScreen(Kagebi game, Counter counter) {
        super(game.input());
        this.game = game;
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
    }

    /** Opens on the n-th tab, for {@code --screen counter}, which presses nothing. */
    TradeScreen onTab(int index) {
        tab = Math.max(0, Math.min(tabs.length - 1, index));
        return this;
    }

    @Override
    public boolean isOpaque() {
        return false;
    }

    @Override
    public InputProcessor inputProcessor() {
        return game.input();
    }

    @Override
    public void show() {
        game.input().clear();
        font = game.skin().getFont("default");
        camera.snapTo(Cfg.VIRT_W / 2f, Cfg.VIRT_H / 2f);
        stock();
    }

    // ---- the shelf ---------------------------------------------------------------

    /** Fills the shelf for the current tab. Called again after every trade, which changes it. */
    private void stock() {
        offers.clear();
        switch (tabs[tab]) {
            case SELL:
                stockGoods();
                break;
            case BUY:
                stockPotions();
                break;
            case TOOLS:
                stockTools();
                break;
            case UPGRADES:
                offers.add(new Shelf());
                break;
            case SEEDS:
                stockSeeds();
                break;
            case KITCHEN:
                stockMeals();
                break;
            default:
                break;
        }
        focus = Math.max(0, Math.min(focus, offers.size - 1));
    }

    private void stockGoods() {
        VillageCatalog village = game.village();
        for (VillageCatalog.Good good : village.goods()) {
            if (game.profile().village.stock.get(good.id, 0) > 0) {
                offers.add(new GoodOffer(good));
            }
        }
    }

    /** What the dungeon's trader stocks, cheapest first: the herbalist sells at his prices. */
    private void stockPotions() {
        Array<ItemDef> sold = new Array<>();
        for (ItemDef item : game.content().allItems()) {
            if (item.forSale() && item.kind == ItemDef.Kind.CONSUMABLE) {
                sold.add(item);
            }
        }
        sold.sort((a, b) -> a.price != b.price ? Integer.compare(a.price, b.price) : a.id.compareTo(b.id));
        for (ItemDef item : sold) {
            offers.add(new PotionOffer(item));
        }
    }

    private void stockTools() {
        for (VillageCatalog.Tool tool : game.village().tools()) {
            offers.add(new ToolOffer(tool));
        }
    }

    private void stockSeeds() {
        for (VillageCatalog.Crop crop : game.village().crops()) {
            offers.add(new SeedOffer(crop));
        }
    }

    private void stockMeals() {
        for (VillageCatalog.Recipe recipe : game.village().recipes()) {
            if (game.content().hasItem(recipe.item)) {
                offers.add(new MealOffer(recipe));
            }
        }
    }

    // ---- simulation --------------------------------------------------------------

    @Override
    protected void step() {
        if (flash > 0) {
            flash--;
        }
        if (input().justPressed(GameAction.PAUSE) || input().justPressed(GameAction.INVENTORY)) {
            game.audio().playSfx(Assets.SFX_CANCEL);
            stack().pop();
            return;
        }
        if (input().justPressed(GameAction.INTERACT) || input().justPressed(GameAction.ATTACK)) {
            trade();
            return;
        }
        move();
    }

    private void trade() {
        Offer offer = selected();
        if (offer == null || !offer.can()) {
            game.audio().playSfx(Assets.SFX_CANCEL);
            return;
        }
        if (!offer.act()) {
            return;
        }
        flash = FLASH_STEPS;
        game.audio().playSfx(Assets.SFX_ACCEPT);
        Profile p = game.profile();
        if (!game.saves().save(p)) {
            Gdx.app.error("save", "profile not written; the trade is only in memory");
        }
        stock();
    }

    /**
     * Left and right wrap along the shelf, up and down step between its rows, and
     * running off the top or the bottom turns to the tab before or after.
     */
    private void move() {
        int size = offers.size;
        int row = size == 0 ? 0 : focus / COLUMNS;
        int rows = Math.max(1, (size + COLUMNS - 1) / COLUMNS);
        if (input().justPressed(GameAction.MOVE_RIGHT) && size > 0) {
            focus = focus + 1 >= size ? 0 : focus + 1;
        } else if (input().justPressed(GameAction.MOVE_LEFT) && size > 0) {
            focus = focus == 0 ? size - 1 : focus - 1;
        } else if (input().justPressed(GameAction.MOVE_DOWN)) {
            if (row + 1 < rows) {
                focus = Math.min(size - 1, focus + COLUMNS);
            } else if (!turn(1)) {
                return;
            }
        } else if (input().justPressed(GameAction.MOVE_UP)) {
            if (row > 0) {
                focus -= COLUMNS;
            } else if (!turn(-1)) {
                return;
            }
        } else {
            return;
        }
        game.audio().playSfx(Assets.SFX_MOVE);
    }

    /** Turns to the next or previous tab; false for a counter with only one. */
    private boolean turn(int by) {
        if (tabs.length < 2) {
            return false;
        }
        tab = (tab + by + tabs.length) % tabs.length;
        focus = 0;
        flash = 0;
        stock();
        return true;
    }

    private Offer selected() {
        return focus >= 0 && focus < offers.size ? offers.get(focus) : null;
    }

    // ---- drawing -----------------------------------------------------------------

    @Override
    public void render(float delta) {
        camera.apply();
        SpriteBatch batch = game.batch();
        batch.setProjectionMatrix(camera.camera().combined);
        batch.begin();

        game.skin().getDrawable("scrim").draw(batch, 0, 0, Cfg.VIRT_W, Cfg.VIRT_H);
        game.skin().getDrawable(BOX_DARK).draw(batch, PANEL_X, PANEL_Y, PANEL_W, PANEL_H);

        I18n t = game.i18n();
        drawHeader(batch, t);
        drawTabs(batch, t);
        drawShelf(batch);
        drawDetail(batch, t);

        Offer offer = selected();
        if (offer != null) {
            Hud.prompt(batch, game.skin(), font, game.input().map().primary(GameAction.INTERACT),
                       t.get(offer.verb()), PANEL_X + 8, PROMPT_BOTTOM, Align.left);
        }
        Hud.prompt(batch, game.skin(), font, game.input().map().primary(GameAction.PAUSE),
                   t.get("common.back"), PANEL_X + PANEL_W - 8, PROMPT_BOTTOM, Align.right);

        batch.setColor(Color.WHITE);
        batch.end();
    }

    /** The name of who is trading, on the pack's label, and the purse opposite. */
    private void drawHeader(SpriteBatch batch, I18n t) {
        String name = t.get(counter == Counter.FARMER ? "npc.worker_farm.name"
            : counter == Counter.COOK ? "npc.worker_kitchen.name"
            : "npc." + Assets.Npc.HERBALIST + ".name");
        float w = Hud.width(font, name) + 14;
        int left = PANEL_X + 8;
        game.skin().getDrawable(LABEL).draw(batch, left, TITLE_TOP - Hud.LINE, w, Hud.LINE);
        batch.setColor(INK);
        Hud.line(batch, font, name, left + 7, TITLE_TOP + 1);

        String gold = String.valueOf(game.profile().gold);
        int right = PANEL_X + PANEL_W - 9;
        batch.setColor(GOLD);
        Hud.right(batch, font, gold, right, TITLE_TOP);
        batch.setColor(Color.WHITE);
        TextureRegion coin = game.skin().getRegion(Assets.Ui.COIN);
        batch.draw(coin, Math.round(right - Hud.width(font, gold) - coin.getRegionWidth() - 2),
                   TITLE_TOP - Hud.LINE + 2);
    }

    /**
     * The tabs, and on the far right what the shelf is measured against: the
     * pantry's room where things go into it, the farm's level where seed does.
     */
    private void drawTabs(SpriteBatch batch, I18n t) {
        int x = PANEL_X + 10;
        for (int i = 0; i < tabs.length; i++) {
            String label = t.get(tabs[i].key);
            float w = Hud.width(font, label);
            if (i == tab) {
                // White first: the label before it left the batch tinted, and a
                // nine-patch takes the batch's colour like any other drawing.
                batch.setColor(Color.WHITE);
                game.skin().getDrawable(BOX_LIGHT).draw(batch, x - 4, TAB_TOP - Hud.LINE - 1, w + 8, Hud.LINE + 3);
            }
            batch.setColor(i == tab ? INK : DIM);
            Hud.line(batch, font, label, x, TAB_TOP);
            x += Math.round(w) + 14;
        }
        Tab current = tabs[tab];
        Profile p = game.profile();
        String aside = current == Tab.BUY || current == Tab.KITCHEN
            ? t.format("trade.pantry", Pantry.packed(p.village), Pantry.capacity(p, game.shop()))
            : current == Tab.SEEDS ? t.format("trade.farm_level", Farm.level(game.village(), p.village))
            : null;
        if (aside != null) {
            batch.setColor(DIM);
            Hud.right(batch, font, aside, PANEL_X + PANEL_W - 10, TAB_TOP);
        }
        batch.setColor(Color.WHITE);
    }

    private void drawShelf(SpriteBatch batch) {
        int left = (Cfg.VIRT_W - (COLUMNS * CELL + (COLUMNS - 1) * GAP)) / 2;
        for (int i = 0; i < offers.size; i++) {
            int x = left + (i % COLUMNS) * (CELL + GAP);
            int y = GRID_TOP - CELL - (i / COLUMNS) * (CELL + GAP);
            game.skin().getDrawable(BOX_LIGHT).draw(batch, x, y, CELL, CELL);
            Offer offer = offers.get(i);
            TextureRegion icon = offer.icon();
            if (icon != null) {
                // Darkened rather than hidden: what cannot be had yet is still
                // worth knowing about.
                batch.setColor(offer.can() ? Color.WHITE : LOCKED);
                batch.draw(icon, x + (CELL - icon.getRegionWidth()) / 2, y + (CELL - icon.getRegionHeight()) / 2);
            }
            String pip = offer.pip();
            if (pip != null) {
                // Shadowed cream, because the count sits over the icon as much as
                // over the cell, and ink vanished into the dark half of either.
                batch.setColor(CREAM);
                Hud.shadowed(batch, font, pip, x + CELL, y + 10, Align.right);
            }
            batch.setColor(Color.WHITE);
            if (i == focus) {
                drawCorners(batch, x, y);
            }
        }
    }

    /** The pack's four selection corners, just outside a cell. */
    private void drawCorners(SpriteBatch batch, int x, int y) {
        TextureRegion tl = region(CORNER + "tl");
        TextureRegion tr = region(CORNER + "tr");
        TextureRegion bl = region(CORNER + "bl");
        TextureRegion br = region(CORNER + "br");
        if (tl == null || tr == null || bl == null || br == null) {
            game.skin().getDrawable(Assets.Ui.FOCUS).draw(batch, x - 2, y - 2, CELL + 4, CELL + 4);
            return;
        }
        batch.draw(tl, x - 3, y + CELL + 3 - tl.getRegionHeight());
        batch.draw(tr, x + CELL + 3 - tr.getRegionWidth(), y + CELL + 3 - tr.getRegionHeight());
        batch.draw(bl, x - 3, y - 3);
        batch.draw(br, x + CELL + 3 - br.getRegionWidth(), y - 3);
    }

    private void drawDetail(SpriteBatch batch, I18n t) {
        int left = PANEL_X + 6;
        int width = PANEL_W - 12;
        game.skin().getDrawable(BOX_LIGHT).draw(batch, left, DETAIL_Y, width, DETAIL_H);
        int text = left + 6;
        int right = left + width - 6;
        int top = DETAIL_Y + DETAIL_H - 3;

        Offer offer = selected();
        if (offer == null) {
            batch.setColor(SOFT);
            Hud.line(batch, font, t.get("trade.empty"), text, top);
            batch.setColor(Color.WHITE);
            return;
        }
        batch.setColor(INK);
        Hud.line(batch, font, offer.name(), text, top);
        String state = flash > 0 ? t.get("trade.done") : offer.state();
        if (state != null) {
            batch.setColor(flash > 0 ? DONE : offer.can() ? INK : DENIED);
            Hud.right(batch, font, state, right, top);
        }
        String[] lines = {offer.description(), offer.more()};
        batch.setColor(SOFT);
        int line = 1;
        for (String l : lines) {
            if (l != null) {
                Hud.line(batch, font, l, text, top - line * Hud.LINE);
                line++;
            }
        }
        batch.setColor(Color.WHITE);
    }

    private TextureRegion region(String name) {
        return game.skin().has(name, TextureRegion.class) ? game.skin().getRegion(name) : null;
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

    private String goodName(String goodId) {
        VillageCatalog.Good good = game.village().good(goodId);
        return good == null ? goodId : game.i18n().get(good.nameKey);
    }

    @Override
    public void resize(int width, int height) {
        camera.resize(width, height);
    }

    // ---- what can be on a shelf ----------------------------------------------------

    /** One thing on the shelf: its picture, its words, and what the key does. */
    private abstract class Offer {
        abstract TextureRegion icon();

        abstract String name();

        /** The first line under the name, or null. */
        String description() {
            return null;
        }

        /** A second line, or null. */
        String more() {
            return null;
        }

        /** On the name's line, hard right: a price, a level, a reason. */
        abstract String state();

        /** The number in the cell's corner, where an inventory keeps a count. */
        String pip() {
            return null;
        }

        /** Whether the key would do anything now. */
        abstract boolean can();

        /** Does it. True when a trade was made, and the save is to be written. */
        abstract boolean act();

        /** The word beside the key. */
        abstract String verb();
    }

    private final class GoodOffer extends Offer {
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

    private final class PotionOffer extends Offer {
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

    private final class ToolOffer extends Offer {
        final VillageCatalog.Tool tool;

        ToolOffer(VillageCatalog.Tool tool) {
            this.tool = tool;
        }

        @Override
        TextureRegion icon() {
            // The pack draws no pail; the basket the rancher gathers into stands in.
            String name = "pail".equals(tool.id) ? "basket" : tool.id;
            return region("ui/sunny/icons/" + name);
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

    private final class SeedOffer extends Offer {
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

    private final class MealOffer extends Offer {
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
    private final class Shelf extends Offer {
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
