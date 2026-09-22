package com.kagebi.screen;

import java.util.List;

import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Align;
import com.kagebi.Cfg;
import com.kagebi.Kagebi;
import com.kagebi.assets.Assets;
import com.kagebi.data.def.ItemDef;
import com.kagebi.gen.Room;
import com.kagebi.gfx.CameraController;
import com.kagebi.input.GameAction;
import com.kagebi.loot.TraderStock;
import com.kagebi.run.RunState;
import com.kagebi.ui.Hud;
import com.kagebi.ui.I18n;

/**
 * The shopkeeper five floors down, and what he will part with.
 *
 * <p>A different shop from the village's, and deliberately so. This one spends
 * the purse the run is carrying and sells things that last until the run ends;
 * the herbalist spends what death banks and sells things that outlive it. The
 * two are never open at the same time and never share a coin.
 *
 * <p>The shop room has existed since the content milestone, with a shopkeeper
 * standing in it and a "Trade" prompt over his head. Pressing the key set
 * {@code EntityWorld.shopRequested} and <em>no screen ever read it</em>, so the
 * prompt was a promise the game could not keep - which is exactly how it was
 * found, by someone walking up and pressing the key.
 *
 * <p>Stock is rolled by {@link TraderStock} from the run seed and the room, so
 * leaving and coming back does not reroll it. What the player has already
 * bought stays bought, and the slot is drawn empty rather than removed, so the
 * shelf does not shuffle under the cursor mid-purchase.
 */
public class TraderScreen extends SimScreen {

    private static final int CELL = 24;
    private static final int GAP = 8;

    private static final int PANEL_X = 30;
    private static final int PANEL_W = Cfg.VIRT_W - 2 * PANEL_X;
    private static final int PANEL_Y = 18;
    private static final int PANEL_H = Cfg.VIRT_H - 2 * PANEL_Y;

    // The column, top to bottom. The description is budgeted two wrapped lines
    // and the key chips stand seventeen pixels tall, which together is what
    // sets the panel's height - a shorter one put the chips over its own frame.
    private static final int TITLE_TOP = PANEL_Y + PANEL_H - 10;
    private static final int GRID_TOP = TITLE_TOP - Hud.LINE - 8;
    private static final int DETAIL_TOP = PANEL_Y + 70;
    private static final int PROMPT_BOTTOM = PANEL_Y + 8;

    /** How long a purchase stays announced where the price was. */
    private static final int FLASH_STEPS = 90;

    private static final Color INK = new Color(0x131b1bff);
    private static final Color SOFT = new Color(0x46503cff);
    private static final Color GOLD = new Color(0xffad55ff);
    private static final Color DENIED = new Color(0xa8544aff);
    private static final Color SOLD = new Color(0x7fae54ff);
    private static final Color EMPTY = new Color(0.32f, 0.30f, 0.36f, 1f);

    private final Kagebi game;
    private final CameraController camera = new CameraController();
    private BitmapFont font;

    private List<ItemDef> stock;
    /**
     * The room this shelf belongs to, which is where the sold slots are kept.
     *
     * <p>Not a field on this screen. {@code DungeonScreen} builds a new
     * TraderScreen every time the player presses the interact key, so anything
     * remembered here is forgotten on the way out - while {@code
     * TraderStock.roll} is seeded from the room and hands back the identical
     * three items. That pair turned a three-item shelf into an unlimited one:
     * close, reopen, and everything was for sale again.
     */
    private Room shelf;
    private int focus;
    private int flash;

    public TraderScreen(Kagebi game) {
        super(game.input());
        this.game = game;
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
        RunState run = game.run();
        long seed = run == null ? 0L
            : TraderStock.seedFor(run.seed, run.floor,
                run.room == null ? 0 : run.room.gx,
                run.room == null ? 0 : run.room.gy);
        stock = TraderStock.roll(game.content().allItems(), seed);
        shelf = run == null ? null : run.room;
        focus = Math.min(focus, Math.max(0, stock.size() - 1));
    }

    // ---- simulation --------------------------------------------------------

    @Override
    protected void step() {
        if (flash > 0) {
            flash--;
        }
        if (input().justPressed(GameAction.PAUSE) || input().justPressed(GameAction.BAG)) {
            game.audio().playSfx(Assets.SFX_CANCEL);
            stack().pop();
            return;
        }
        if (input().justPressed(GameAction.INTERACT) || input().justPressed(GameAction.ATTACK)) {
            buy();
            return;
        }
        if (stock.isEmpty()) {
            return;
        }
        if (input().justPressed(GameAction.MOVE_RIGHT)) {
            focus = (focus + 1) % stock.size();
        } else if (input().justPressed(GameAction.MOVE_LEFT)) {
            focus = (focus + stock.size() - 1) % stock.size();
        } else {
            return;
        }
        game.audio().playSfx(Assets.SFX_MOVE);
    }

    private void buy() {
        RunState run = game.run();
        ItemDef item = selected();
        if (run == null || item == null || sold(focus) || run.gold < item.price) {
            game.audio().playSfx(Assets.SFX_CANCEL);
            return;
        }
        run.gold -= item.price;
        // A key is a plain counter on the run, not an inventory stack; the
        // item's own effect says which it is, and this is the only place in
        // the game that turns gold into either.
        if (item.kind == ItemDef.Kind.KEY) {
            run.keys += Math.max(1, Math.round(item.magnitude));
        } else {
            run.addItem(item.id, 1);
        }
        if (shelf != null) {
            shelf.markSlotBought(focus);
        }
        flash = FLASH_STEPS;
        game.audio().playSfx(Assets.SFX_ACCEPT);
    }

    private boolean sold(int slot) {
        return shelf != null && shelf.slotBought(slot);
    }

    private ItemDef selected() {
        return stock != null && focus >= 0 && focus < stock.size() ? stock.get(focus) : null;
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
        drawShelf(batch);
        drawDetail(batch, t);

        Hud.prompt(batch, game.skin(), font,
                   game.input().map().primary(GameAction.INTERACT),
                   t.get("shop.buy"), PANEL_X + 6, PROMPT_BOTTOM, Align.left);
        Hud.prompt(batch, game.skin(), font,
                   game.input().map().primary(GameAction.PAUSE),
                   t.get("common.back"), PANEL_X + PANEL_W - 6, PROMPT_BOTTOM, Align.right);

        batch.setColor(Color.WHITE);
        batch.end();
    }

    private void drawHeader(SpriteBatch batch, I18n t) {
        batch.setColor(INK);
        Hud.line(batch, font, t.get("trader.title"), PANEL_X + 8, TITLE_TOP);

        RunState run = game.run();
        String purse = String.valueOf(run == null ? 0 : run.gold);
        int right = PANEL_X + PANEL_W - 8;
        batch.setColor(GOLD);
        Hud.right(batch, font, purse, right, TITLE_TOP);
        batch.setColor(Color.WHITE);
        TextureRegion coin = game.skin().getRegion(Assets.Ui.COIN);
        batch.draw(coin, Math.round(right - Hud.width(font, purse) - coin.getRegionWidth() - 2),
                   TITLE_TOP - Hud.LINE + 2);
    }

    private void drawShelf(SpriteBatch batch) {
        int count = Math.max(1, stock.size());
        int left = (Cfg.VIRT_W - (count * CELL + (count - 1) * GAP)) / 2;
        RunState run = game.run();
        int purse = run == null ? 0 : run.gold;
        for (int i = 0; i < stock.size(); i++) {
            ItemDef item = stock.get(i);
            int x = left + i * (CELL + GAP);
            int y = GRID_TOP - CELL;
            game.skin().getDrawable(Assets.Ui.CELL).draw(batch, x, y, CELL, CELL);

            if (!sold(i)) {
                TextureRegion icon = iconFor(item);
                if (icon != null) {
                    // Dimmed when it is out of reach, which says "come back"
                    // without a word and matches the two other shop screens.
                    batch.setColor(purse >= item.price ? Color.WHITE : EMPTY);
                    batch.draw(icon, x + (CELL - icon.getRegionWidth()) / 2,
                               y + (CELL - icon.getRegionHeight()) / 2);
                    batch.setColor(Color.WHITE);
                }
                // The price under the cell rather than in it: at 24 pixels a
                // three-digit number over the art is unreadable, and the price
                // is the thing being compared across the shelf.
                batch.setColor(purse >= item.price ? GOLD : DENIED);
                Hud.centred(batch, font, item.price + "g", x + CELL / 2f, y - 2);
            } else {
                batch.setColor(SOLD);
                Hud.centred(batch, font, "*", x + CELL / 2f, y + CELL - 5);
            }
            batch.setColor(Color.WHITE);
            if (i == focus) {
                game.skin().getDrawable(Assets.Ui.FOCUS)
                    .draw(batch, x - 2, y - 2, CELL + 4, CELL + 4);
            }
        }
    }

    /** A def names either an atlas region or an index into the icon sheet. */
    private TextureRegion iconFor(ItemDef item) {
        if (item.sprite != null && game.skin().has(item.sprite, TextureRegion.class)) {
            return game.skin().getRegion(item.sprite);
        }
        return Preload.icon(item.icon);
    }

    private void drawDetail(SpriteBatch batch, I18n t) {
        int left = PANEL_X + 8;
        int right = PANEL_X + PANEL_W - 8;
        ItemDef item = selected();
        if (item == null) {
            batch.setColor(SOFT);
            Hud.line(batch, font, t.get("trader.empty"), left, DETAIL_TOP);
            batch.setColor(Color.WHITE);
            return;
        }
        batch.setColor(INK);
        Hud.line(batch, font, t.get(item.nameKey), left, DETAIL_TOP);
        if (flash > 0 || sold(focus)) {
            batch.setColor(SOLD);
            Hud.right(batch, font, t.get(flash > 0 ? "shop.bought" : "trader.sold"),
                      right, DETAIL_TOP);
        }
        batch.setColor(Color.WHITE);
        font.setColor(SOFT);
        font.draw(batch, t.get(item.descKey), left, DETAIL_TOP - Hud.LINE - font.getAscent(),
                  right - left, Align.left, true);
        font.setColor(Color.WHITE);
    }

    @Override
    public void resize(int width, int height) {
        camera.resize(width, height);
    }
}
