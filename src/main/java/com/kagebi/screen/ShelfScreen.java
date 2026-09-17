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
import com.kagebi.gfx.CameraController;
import com.kagebi.input.GameAction;
import com.kagebi.save.Profile;
import com.kagebi.ui.Hud;
import com.kagebi.ui.I18n;

/**
 * A shelf of icons in the Sunnyside pack's own boxes: a label with a name on
 * it, a row of tabs, a grid of cells with a focus in the pack's corners, a box
 * of words about what is focused, and a key that does something with it.
 *
 * <p>The village's trading counters and the player's bag are this one shape
 * with different things on the shelf, so they are learned once. Built straight
 * to the batch, as the dungeon's screens are: the movement keys drive the
 * focus, running off the top or the bottom of a shelf turns to the tab before
 * or after, and the back key or the key that opened it closes it.
 *
 * <p>Something done on a shelf writes the save at once. On a counter the goods
 * have left the storehouse and the gold and the goods must land together; in the
 * bag it costs nothing and keeps the one rule.
 */
public abstract class ShelfScreen extends SimScreen {

    protected static final int COLUMNS = 9;
    protected static final int CELL = 20;
    protected static final int GAP = 3;

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

    /** How long "done" stands where the state was, after something is done. */
    private static final int FLASH_STEPS = 60;

    private static final String BOX_DARK = "ui/sunny/box_dark";
    private static final String BOX_LIGHT = "ui/sunny/box_light";
    private static final String LABEL = "ui/sunny/label";
    private static final String CORNER = "ui/sunny/icons/selectbox_";

    /** On the dark box. */
    protected static final Color CREAM = new Color(0xffe6c4ff);
    protected static final Color DIM = new Color(0xb89a78ff);
    protected static final Color GOLD = new Color(0xffad55ff);
    /** On the light box and the label. */
    protected static final Color INK = new Color(0x2e1d16ff);
    protected static final Color SOFT = new Color(0x6b4a36ff);
    protected static final Color DENIED = new Color(0xa8443aff);
    protected static final Color DONE = new Color(0x4f7f34ff);
    protected static final Color LOCKED = new Color(0.45f, 0.42f, 0.46f, 1f);

    protected final Kagebi game;
    private final CameraController camera = new CameraController();
    protected BitmapFont font;

    private final Array<Entry> entries = new Array<>();
    private int tab;
    private int focus;
    private int flash;

    protected ShelfScreen(Kagebi game) {
        super(game.input());
        this.game = game;
    }

    /** The translation keys of the tabs, in order. At least one. */
    protected abstract String[] tabKeys();

    /** The name on the label at the top, already translated. */
    protected abstract String title();

    /** Fills the shelf for a tab. Called again after everything done on it. */
    protected abstract void stock(int tab, Array<Entry> into);

    /** What the tab row says on the far right, measured against the shelf, or null. */
    protected String aside(int tab) {
        return null;
    }

    /** Opens on the n-th tab, for a screenshot flag, which presses nothing. */
    protected final void openTab(int index) {
        tab = Math.max(0, Math.min(tabKeys().length - 1, index));
    }

    protected final int tab() {
        return tab;
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
        restock();
    }

    private void restock() {
        entries.clear();
        stock(tab, entries);
        focus = Math.max(0, Math.min(focus, entries.size - 1));
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
            activate();
            return;
        }
        move();
    }

    private void activate() {
        Entry entry = selected();
        if (entry != null && entry.verb() == null) {
            return;         // something to read about, not to do
        }
        if (entry == null || !entry.can()) {
            game.audio().playSfx(Assets.SFX_CANCEL);
            return;
        }
        if (!entry.act()) {
            return;
        }
        flash = FLASH_STEPS;
        game.audio().playSfx(Assets.SFX_ACCEPT);
        Profile p = game.profile();
        if (!game.saves().save(p)) {
            Gdx.app.error("save", "profile not written; the change is only in memory");
        }
        restock();
    }

    /**
     * Left and right wrap along the shelf, up and down step between its rows, and
     * running off the top or the bottom turns to the tab before or after.
     */
    private void move() {
        int size = entries.size;
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

    /** Turns to the next or previous tab; false for a shelf with only one. */
    private boolean turn(int by) {
        int count = tabKeys().length;
        if (count < 2) {
            return false;
        }
        tab = (tab + by + count) % count;
        focus = 0;
        flash = 0;
        restock();
        return true;
    }

    private Entry selected() {
        return focus >= 0 && focus < entries.size ? entries.get(focus) : null;
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
        drawHeader(batch);
        drawTabs(batch, t);
        drawShelf(batch);
        drawDetail(batch, t);

        Entry entry = selected();
        if (entry != null && entry.verb() != null) {
            Hud.prompt(batch, game.skin(), font, game.input().map().primary(GameAction.INTERACT),
                       t.get(entry.verb()), PANEL_X + 8, PROMPT_BOTTOM, Align.left);
        }
        Hud.prompt(batch, game.skin(), font, game.input().map().primary(GameAction.PAUSE),
                   t.get("common.back"), PANEL_X + PANEL_W - 8, PROMPT_BOTTOM, Align.right);

        batch.setColor(Color.WHITE);
        batch.end();
    }

    /** The title on the pack's label, and the purse opposite. */
    private void drawHeader(SpriteBatch batch) {
        String name = title();
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

    private void drawTabs(SpriteBatch batch, I18n t) {
        String[] keys = tabKeys();
        int x = PANEL_X + 10;
        for (int i = 0; i < keys.length; i++) {
            String label = t.get(keys[i]);
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
        String aside = aside(tab);
        if (aside != null) {
            batch.setColor(DIM);
            int right = PANEL_X + PANEL_W - 10;
            if (x + Hud.width(font, aside) <= right) {
                Hud.right(batch, font, aside, right, TAB_TOP);
            } else {
                // Five tabs leave no room at the end of their row, so it goes
                // up a line, beside the purse.
                String gold = String.valueOf(game.profile().gold);
                TextureRegion coin = game.skin().getRegion(Assets.Ui.COIN);
                float coinLeft = PANEL_X + PANEL_W - 9 - Hud.width(font, gold) - coin.getRegionWidth() - 2;
                Hud.right(batch, font, aside, Math.round(coinLeft - 8), TITLE_TOP);
            }
        }
        batch.setColor(Color.WHITE);
    }

    private void drawShelf(SpriteBatch batch) {
        int left = (Cfg.VIRT_W - (COLUMNS * CELL + (COLUMNS - 1) * GAP)) / 2;
        for (int i = 0; i < entries.size; i++) {
            int x = left + (i % COLUMNS) * (CELL + GAP);
            int y = GRID_TOP - CELL - (i / COLUMNS) * (CELL + GAP);
            game.skin().getDrawable(BOX_LIGHT).draw(batch, x, y, CELL, CELL);
            Entry entry = entries.get(i);
            TextureRegion icon = entry.icon();
            if (icon != null) {
                // Darkened rather than hidden: what cannot be had yet is still
                // worth knowing about.
                batch.setColor(entry.dimmed() ? LOCKED : Color.WHITE);
                batch.draw(icon, x + (CELL - icon.getRegionWidth()) / 2, y + (CELL - icon.getRegionHeight()) / 2);
            }
            String pip = entry.pip();
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

        Entry entry = selected();
        if (entry == null) {
            batch.setColor(SOFT);
            Hud.line(batch, font, t.get("trade.empty"), text, top);
            batch.setColor(Color.WHITE);
            return;
        }
        batch.setColor(INK);
        Hud.line(batch, font, entry.name(), text, top);
        String state = flash > 0 ? t.get("trade.done") : entry.state();
        if (state != null) {
            batch.setColor(flash > 0 ? DONE : entry.verb() == null || entry.can() ? INK : DENIED);
            Hud.right(batch, font, state, right, top);
        }
        String[] lines = {entry.description(), entry.more()};
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

    protected final TextureRegion region(String name) {
        return game.skin().has(name, TextureRegion.class) ? game.skin().getRegion(name) : null;
    }

    /** A tool's picture. The pack draws no pail; the basket the rancher gathers into stands in. */
    protected final TextureRegion toolIcon(VillageCatalog.Tool tool) {
        return region("ui/sunny/icons/" + ("pail".equals(tool.id) ? "basket" : tool.id));
    }

    /** A good's name in the current language, or its id for one the catalog does not know. */
    protected final String goodName(String goodId) {
        VillageCatalog.Good good = game.village().good(goodId);
        return good == null ? goodId : game.i18n().get(good.nameKey);
    }

    @Override
    public void resize(int width, int height) {
        camera.resize(width, height);
    }

    // ---- what can be on a shelf ----------------------------------------------------

    /** One thing on the shelf: its picture, its words, and what the key does. */
    protected abstract class Entry {
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
        boolean can() {
            return false;
        }

        /** Does it. True when something changed, and the save is to be written. */
        boolean act() {
            return false;
        }

        /** The word beside the key, or null for something only to be read about. */
        abstract String verb();

        /** Drawn darkened: something that could be done, but not yet. */
        boolean dimmed() {
            return verb() != null && !can();
        }
    }
}
