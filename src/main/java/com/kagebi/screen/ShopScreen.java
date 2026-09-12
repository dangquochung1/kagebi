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
import com.kagebi.data.ShopCatalog;
import com.kagebi.gfx.CameraController;
import com.kagebi.input.GameAction;
import com.kagebi.save.Profile;
import com.kagebi.ui.Hud;
import com.kagebi.ui.I18n;

/**
 * The village shop: what banked gold buys, and what it opens.
 *
 * <p>Everything behind this screen was finished long before the screen was.
 * {@link ShopCatalog} has held the prices, the requirements and the arithmetic
 * since the content milestone, {@code RunEndScreen} has banked gold into the
 * profile on every death, and {@link Profile} has saved it. What none of it had
 * was a way in: until this class existed, {@code ShopCatalog.buy} was called
 * from nowhere but its own test, and a player's gold went into the bank and
 * stayed there.
 *
 * <p>Drawn straight to the batch like {@link InventoryScreen} rather than
 * through scene2d, which means the two tabs are drawn by hand instead of with
 * {@code ui.TabBar}. TabBar is a scene2d {@code Table} and needs a {@code
 * Stage}; this screen is a grid with a focus ring driven by the movement keys,
 * which is InventoryScreen's shape, not SettingsScreen's. Two labels and a
 * highlight are cheaper than reconciling a Stage's focus with this one's.
 *
 * <p><b>It writes the save itself.</b> Buying is the only thing outside a
 * finished run that changes the profile, and a player who buys an upgrade and
 * then closes the game has every right to expect it to still be there. Saving
 * on the purchase rather than on the way out means a crash between the two
 * cannot eat the gold <em>and</em> the upgrade.
 */
public class ShopScreen extends SimScreen {

    /** Tab indices, in the order they are drawn. */
    private static final int UPGRADES = 0;
    private static final int UNLOCKS = 1;

    private static final int COLUMNS = 6;
    private static final int CELL = 24;
    private static final int GAP = 4;

    private static final int PANEL_X = 8;
    private static final int PANEL_W = Cfg.VIRT_W - 2 * PANEL_X;
    private static final int PANEL_Y = 4;
    private static final int PANEL_H = Cfg.VIRT_H - 2 * PANEL_Y;

    // The column, top to bottom. Line tops leave room for the four rows above
    // cap height that a stacked Vietnamese tone mark needs; see Hud.line.
    private static final int TITLE_TOP = PANEL_Y + PANEL_H - 10;
    /** Three pixels of air, so the selected tab's box clears the title's tails. */
    private static final int TAB_TOP = TITLE_TOP - Hud.LINE - 3;
    private static final int GRID_TOP = TAB_TOP - Hud.LINE - 4;
    private static final int DETAIL_TOP = 66;
    private static final int PROMPT_BOTTOM = 10;

    /** How long a purchase stays announced under the detail block. */
    private static final int FLASH_STEPS = 90;

    private static final Color INK = new Color(0x131b1bff);
    private static final Color SOFT = new Color(0x46503cff);
    private static final Color GOLD = new Color(0xffad55ff);
    private static final Color LOCKED = new Color(0.32f, 0.30f, 0.36f, 1f);
    private static final Color DENIED = new Color(0xa8544aff);
    private static final Color BOUGHT = new Color(0x7fae54ff);
    /** The level number inside a cell, which has a dark interior. */
    private static final Color PIP = new Color(0xe8cfa9ff);

    private final Kagebi game;
    private final CameraController camera = new CameraController();
    private BitmapFont font;

    private final Array<Entry> upgrades = new Array<>();
    private final Array<Entry> unlocks = new Array<>();

    private int tab = UPGRADES;
    private int focus;
    /** Steps left on the "bought" line, which replaces the price for a moment. */
    private int flash;

    public ShopScreen(Kagebi game) {
        super(game.input());
        this.game = game;
    }

    /**
     * Opens on the unlocks tab. For {@code --screen unlocks}: the second tab is
     * two key presses away, and a screenshot run presses nothing.
     */
    ShopScreen onUnlocks() {
        tab = UNLOCKS;
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
        collect();
    }

    private void collect() {
        upgrades.clear();
        unlocks.clear();
        ShopCatalog shop = game.shop();
        if (shop == null) {
            return;
        }
        for (ShopCatalog.Upgrade u : shop.upgrades()) {
            upgrades.add(new Entry(u, null));
        }
        for (ShopCatalog.Unlock u : shop.unlocks()) {
            unlocks.add(new Entry(null, u));
        }
    }

    private Array<Entry> shelf() {
        return tab == UPGRADES ? upgrades : unlocks;
    }

    // ---- simulation --------------------------------------------------------

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
            buy();
            return;
        }
        move();
    }

    /**
     * The grid wraps left and right within a row and steps between rows with up
     * and down; running off the top or bottom changes tab. One shelf is eight
     * entries and the other eleven, so the focus is clamped rather than kept -
     * cell seven of the upgrades has no counterpart among the unlocks.
     */
    private void move() {
        int size = shelf().size;
        if (size == 0) {
            return;
        }
        int row = focus / COLUMNS;
        int col = focus % COLUMNS;
        int rows = (size + COLUMNS - 1) / COLUMNS;

        if (input().justPressed(GameAction.MOVE_RIGHT)) {
            focus = focus + 1 >= size ? 0 : focus + 1;
        } else if (input().justPressed(GameAction.MOVE_LEFT)) {
            focus = focus == 0 ? size - 1 : focus - 1;
        } else if (input().justPressed(GameAction.MOVE_DOWN)) {
            if (row + 1 < rows) {
                focus = Math.min(size - 1, focus + COLUMNS);
            } else {
                switchTab(col);
            }
        } else if (input().justPressed(GameAction.MOVE_UP)) {
            if (row > 0) {
                focus -= COLUMNS;
            } else {
                switchTab(col);
            }
        } else {
            return;
        }
        game.audio().playSfx(Assets.SFX_MOVE);
    }

    private void switchTab(int column) {
        tab = tab == UPGRADES ? UNLOCKS : UPGRADES;
        focus = Math.min(column, Math.max(0, shelf().size - 1));
    }

    private void buy() {
        Entry e = selected();
        Profile p = game.profile();
        if (e == null || !e.canBuy(p)) {
            game.audio().playSfx(Assets.SFX_CANCEL);
            return;
        }
        e.buy(p);
        flash = FLASH_STEPS;
        game.audio().playSfx(Assets.SFX_ACCEPT);
        // Written now, not on the way out. See the class comment: the gold has
        // already left the purse, and the two halves must land together.
        if (!game.saves().save(p)) {
            Gdx.app.error("save", "profile not written; the purchase is only in memory");
        }
    }

    private Entry selected() {
        Array<Entry> shelf = shelf();
        return focus >= 0 && focus < shelf.size ? shelf.get(focus) : null;
    }

    // ---- drawing -----------------------------------------------------------

    @Override
    public void render(float delta) {
        camera.apply();
        SpriteBatch batch = game.batch();
        batch.setProjectionMatrix(camera.camera().combined);
        batch.begin();

        game.skin().getDrawable("scrim").draw(batch, 0, 0, Cfg.VIRT_W, Cfg.VIRT_H);
        game.skin().getDrawable(Assets.Ui.PANEL_2)
            .draw(batch, PANEL_X, PANEL_Y, PANEL_W, PANEL_H);

        I18n t = game.i18n();
        drawHeader(batch, t);
        drawTabs(batch, t);
        drawGrid(batch);
        drawDetail(batch, t);

        Hud.prompt(batch, game.skin(), font,
                   game.input().map().primary(GameAction.INTERACT),
                   t.get("shop.buy"), PANEL_X + 8, PROMPT_BOTTOM, Align.left);
        Hud.prompt(batch, game.skin(), font,
                   game.input().map().primary(GameAction.PAUSE),
                   t.get("common.back"), PANEL_X + PANEL_W - 8, PROMPT_BOTTOM, Align.right);

        batch.setColor(Color.WHITE);
        batch.end();
    }

    private void drawHeader(SpriteBatch batch, I18n t) {
        batch.setColor(INK);
        Hud.line(batch, font, t.get("shop.title"), PANEL_X + 10, TITLE_TOP);

        // The purse sits where the player's eye already goes for a price: hard
        // right, on the title's own line, so that a glance covers both.
        String gold = String.valueOf(game.profile().gold);
        int right = PANEL_X + PANEL_W - 10;
        batch.setColor(GOLD);
        Hud.right(batch, font, gold, right, TITLE_TOP);
        batch.setColor(Color.WHITE);
        TextureRegion coin = game.skin().getRegion(Assets.Ui.COIN);
        batch.draw(coin, Math.round(right - Hud.width(font, gold) - coin.getRegionWidth() - 2),
                   TITLE_TOP - Hud.LINE + 2);
    }

    private void drawTabs(SpriteBatch batch, I18n t) {
        String[] labels = {t.get("shop.tab.upgrades"), t.get("shop.tab.unlocks")};
        int x = PANEL_X + 10;
        for (int i = 0; i < labels.length; i++) {
            float w = Hud.width(font, labels[i]);
            if (i == tab) {
                // A boxed label rather than the pack's tab art: the art is 16px
                // tall, and a Vietnamese line box is 14 with the tone marks in
                // it, so the glyphs would sit on the tab's own border.
                game.skin().getDrawable(Assets.Ui.BG)
                    .draw(batch, x - 4, TAB_TOP - Hud.LINE - 1, w + 8, Hud.LINE + 3);
            }
            batch.setColor(i == tab ? Color.WHITE : SOFT);
            Hud.line(batch, font, labels[i], x, TAB_TOP);
            x += Math.round(w) + 14;
        }
        batch.setColor(Color.WHITE);
    }

    private void drawGrid(SpriteBatch batch) {
        Array<Entry> shelf = shelf();
        Profile p = game.profile();
        int left = (Cfg.VIRT_W - (COLUMNS * CELL + (COLUMNS - 1) * GAP)) / 2;
        for (int i = 0; i < shelf.size; i++) {
            int x = left + (i % COLUMNS) * (CELL + GAP);
            int y = GRID_TOP - CELL - (i / COLUMNS) * (CELL + GAP);
            game.skin().getDrawable(Assets.Ui.CELL).draw(batch, x, y, CELL, CELL);

            Entry e = shelf.get(i);
            TextureRegion icon = Preload.icon(e.icon());
            if (icon != null) {
                // Darkened rather than hidden, the same rule the character
                // select screen uses: a player who cannot see what is still
                // ahead of them has no reason to believe there is anything.
                batch.setColor(e.available(p) ? Color.WHITE : LOCKED);
                batch.draw(icon, x + (CELL - icon.getRegionWidth()) / 2,
                           y + (CELL - icon.getRegionHeight()) / 2);
                batch.setColor(Color.WHITE);
            }
            String pip = e.pip(p);
            if (pip != null) {
                // Level, or a tick for something owned outright, in the cell's
                // lower right - where an inventory puts a stack count. Light,
                // because the cell's interior is dark: drawn in the panel's own
                // ink it was a smudge in the corner rather than a number.
                batch.setColor(e.done(p) ? BOUGHT : PIP);
                Hud.right(batch, font, pip, x + CELL - 1, y + 11);
                batch.setColor(Color.WHITE);
            }
            if (i == focus) {
                game.skin().getDrawable(Assets.Ui.FOCUS)
                    .draw(batch, x - 2, y - 2, CELL + 4, CELL + 4);
            }
        }
    }

    private void drawDetail(SpriteBatch batch, I18n t) {
        Entry e = selected();
        if (e == null) {
            return;
        }
        Profile p = game.profile();
        int left = PANEL_X + 10;
        int right = PANEL_X + PANEL_W - 10;

        batch.setColor(INK);
        Hud.line(batch, font, t.get(e.nameKey()), left, DETAIL_TOP);

        // Price on the name's line, hard right, so the description below has the
        // full width to wrap into.
        String state = flash > 0 ? t.get("shop.bought") : e.state(t, p);
        batch.setColor(flash > 0 ? BOUGHT : e.stateColour(p));
        Hud.right(batch, font, state, right, DETAIL_TOP);

        // Wrapped, not broken by hand in the language files: Vietnamese runs
        // longer than English for the same sentence, so a break placed for one
        // is wrong for the other. Same handling as DialogBox.
        batch.setColor(Color.WHITE);
        font.setColor(SOFT);
        font.draw(batch, t.get(e.descKey()), left, DETAIL_TOP - Hud.LINE - font.getAscent(),
                  right - left, Align.left, true);
        font.setColor(Color.WHITE);
    }

    @Override
    public void resize(int width, int height) {
        camera.resize(width, height);
    }

    /**
     * One thing on a shelf: exactly one of the two is set.
     *
     * <p>An upgrade and an unlock are priced, gated and owned by different
     * rules, and both are already spelt out in {@link ShopCatalog}. Wrapping
     * them here rather than branching at five draw sites keeps every one of
     * those rules in the catalogue, where the tests can still reach it.
     */
    private static final class Entry {
        final ShopCatalog.Upgrade upgrade;
        final ShopCatalog.Unlock unlock;

        Entry(ShopCatalog.Upgrade upgrade, ShopCatalog.Unlock unlock) {
            this.upgrade = upgrade;
            this.unlock = unlock;
        }

        int icon() {
            return upgrade != null ? upgrade.icon : unlock.icon;
        }

        String nameKey() {
            return upgrade != null ? upgrade.nameKey : unlock.nameKey;
        }

        String descKey() {
            return upgrade != null ? upgrade.descKey : unlock.descKey;
        }

        boolean canBuy(Profile p) {
            return upgrade != null ? ShopCatalog.canBuy(upgrade, p)
                                   : ShopCatalog.canBuy(unlock, p);
        }

        void buy(Profile p) {
            if (upgrade != null) {
                ShopCatalog.buy(upgrade, p);
            } else {
                ShopCatalog.buy(unlock, p);
            }
        }

        /** Finished with: a maxed track, or something already owned. */
        boolean done(Profile p) {
            return upgrade != null ? p.upgrade(upgrade.id) >= upgrade.maxLevel
                                   : ShopCatalog.owned(unlock, p);
        }

        /** Whether the shelf will sell it at all - gold aside. */
        boolean available(Profile p) {
            if (done(p)) {
                return true;        // owned, so drawn at full strength
            }
            return upgrade != null || ShopCatalog.requirementMet(unlock, p);
        }

        /**
         * The corner mark: how many levels are owned, or a tick for an unlock
         * that is. Nothing at all for something untouched - a zero in every
         * cell of an untouched shop is eight numbers that say nothing.
         */
        String pip(Profile p) {
            if (upgrade != null) {
                int level = p.upgrade(upgrade.id);
                return level > 0 ? String.valueOf(level) : null;
            }
            return ShopCatalog.owned(unlock, p) ? "*" : null;
        }

        /**
         * The right-hand line: what it costs, or why it cannot be had. The
         * cost of a maxed track is -1, which must never reach the screen.
         */
        String state(I18n t, Profile p) {
            if (upgrade != null) {
                int price = ShopCatalog.cost(upgrade, p);
                if (price < 0) {
                    return t.get("shop.max");
                }
                return p.gold >= price ? price + "g" : t.get("shop.poor");
            }
            if (ShopCatalog.owned(unlock, p)) {
                return t.get("shop.owned");
            }
            if (!ShopCatalog.requirementMet(unlock, p)) {
                return t.format("shop.need." + unlock.requirement, unlock.requirementValue);
            }
            return p.gold >= unlock.cost ? unlock.cost + "g" : t.get("shop.poor");
        }

        Color stateColour(Profile p) {
            if (done(p)) {
                return BOUGHT;
            }
            return canBuy(p) ? GOLD : DENIED;
        }
    }
}
