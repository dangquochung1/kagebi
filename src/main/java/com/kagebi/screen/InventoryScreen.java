package com.kagebi.screen;

import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectIntMap;
import com.kagebi.Cfg;
import com.kagebi.Kagebi;
import com.kagebi.assets.Assets;
import com.kagebi.data.ContentRegistry;
import com.kagebi.data.def.ItemDef;
import com.kagebi.data.def.RelicDef;
import com.kagebi.gfx.CameraController;
import com.kagebi.input.GameAction;
import com.kagebi.run.RunState;
import com.kagebi.ui.Hud;
import com.kagebi.ui.I18n;

/**
 * What the player is carrying: relics above, consumables below.
 *
 * <p>Translucent over the dungeon, like the pause menu, and for the same
 * reason. Checking a relic's description is a glance away from a fight, and
 * the fight should still be visible behind it.
 *
 * <p>Every lookup into content is guarded. The registry is allowed to be empty,
 * and a run can hold an id the registry has never heard of - a relic granted by
 * a debug key, an item renamed in the JSON between builds. An unknown id draws
 * as a generic icon with its raw id for a name, which is visible and harmless;
 * the registry's own lookups throw, which is neither.
 */
public class InventoryScreen extends SimScreen {

    private static final int SLOTS = 10;
    private static final int CELL = 20;
    private static final int GAP = 2;

    private static final Color INK = new Color(0x131b1bff);
    private static final Color SOFT = new Color(0x46503cff);

    private final Kagebi game;
    private final CameraController camera = new CameraController();
    private BitmapFont font;

    /** Slot contents in draw order: relics, then items. Rebuilt on show. */
    private final Array<Entry> relics = new Array<>();
    private final Array<Entry> items = new Array<>();
    private int focus;

    public InventoryScreen(Kagebi game) {
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
        collect();
    }

    private void collect() {
        relics.clear();
        items.clear();
        RunState run = game.run();
        if (run == null) {
            return;
        }
        ContentRegistry content = game.content();
        for (String id : run.relics) {
            Entry e = new Entry(id, 1);
            if (content.hasRelic(id)) {
                RelicDef def = content.relic(id);
                e.nameKey = def.nameKey;
                e.descKey = def.descKey;
                e.icon = ravenIcon(def.icon);
            }
            if (e.icon == null) {
                e.icon = game.skin().getRegion(Assets.Ui.ICON_RELIC);
            }
            relics.add(e);
        }
        for (ObjectIntMap.Entry<String> held : run.items) {
            if (held.value <= 0) {
                continue;
            }
            Entry e = new Entry(held.key, held.value);
            if (content.hasItem(held.key)) {
                ItemDef def = content.item(held.key);
                e.nameKey = def.nameKey;
                e.descKey = def.descKey;
            }
            e.icon = itemIcon(game, held.key);
            items.add(e);
        }
        items.sort((a, b) -> a.id.compareTo(b.id));
        focus = Math.min(focus, Math.max(0, slotCount() - 1));
    }

    private TextureRegion ravenIcon(int index) {
        return Preload.icon(index);
    }

    /**
     * An item's picture: its atlas region, else its icon from the sheet, else the
     * generic one for an id the content does not know. Shared with the dungeon's
     * quick slot, so the two never draw one item two ways.
     */
    static TextureRegion itemIcon(Kagebi game, String itemId) {
        ContentRegistry content = game.content();
        TextureRegion icon = null;
        if (content.hasItem(itemId)) {
            ItemDef def = content.item(itemId);
            // A def names either an atlas region or an index into the icon
            // sheet; both come from data, so neither is a literal here.
            icon = def.sprite != null && game.skin().has(def.sprite, TextureRegion.class)
                ? game.skin().getRegion(def.sprite) : Preload.icon(def.icon);
        }
        return icon != null ? icon : game.skin().getRegion(Assets.Ui.ICON_RELIC);
    }

    private int slotCount() {
        return SLOTS * 2;
    }

    @Override
    protected void step() {
        if (input().justPressed(GameAction.INVENTORY) || input().justPressed(GameAction.PAUSE)) {
            game.audio().playSfx(Assets.SFX_CANCEL);
            stack().pop();
            return;
        }
        if (input().justPressed(GameAction.INTERACT) && focus >= SLOTS && focus - SLOTS < items.size) {
            // What the quick key uses is chosen here, and kept on the run.
            RunState run = game.run();
            if (run != null) {
                run.quickItem = items.get(focus - SLOTS).id;
                game.audio().playSfx(Assets.SFX_ACCEPT);
            }
            return;
        }
        int col = focus % SLOTS;
        int row = focus / SLOTS;
        if (input().justPressed(GameAction.MOVE_RIGHT)) {
            col = (col + 1) % SLOTS;
        } else if (input().justPressed(GameAction.MOVE_LEFT)) {
            col = (col + SLOTS - 1) % SLOTS;
        } else if (input().justPressed(GameAction.MOVE_DOWN) || input().justPressed(GameAction.MOVE_UP)) {
            row = 1 - row;
        } else {
            return;
        }
        focus = row * SLOTS + col;
        game.audio().playSfx(Assets.SFX_MOVE);
    }

    @Override
    public void render(float delta) {
        camera.apply();
        SpriteBatch batch = game.batch();
        batch.setProjectionMatrix(camera.camera().combined);
        batch.begin();

        game.skin().getDrawable("scrim").draw(batch, 0, 0, Cfg.VIRT_W, Cfg.VIRT_H);

        int pw = SLOTS * (CELL + GAP) - GAP + 28;
        int ph = 150;
        int px = (Cfg.VIRT_W - pw) / 2;
        int py = (Cfg.VIRT_H - ph) / 2;
        game.skin().getDrawable(Assets.Ui.PANEL_2).draw(batch, px, py, pw, ph);

        I18n t = game.i18n();
        int left = px + 14;
        int top = py + ph - 11;
        batch.setColor(INK);
        Hud.line(batch, font, t.get("inv.title"), left, top);
        batch.setColor(Color.WHITE);
        // The way out shares the title's row, at the right. At the bottom it
        // crowded the description, which is the one line here that grows.
        Hud.prompt(batch, game.skin(), font,
                   game.input().map().primary(GameAction.INVENTORY),
                   t.get("common.back"), px + pw - 8, top - Hud.LINE, Align.right);

        top -= Hud.LINE + 4;
        batch.setColor(SOFT);
        Hud.line(batch, font, t.get("inv.relics"), left, top);
        drawRow(batch, relics, 0, left, top - Hud.LINE - 2 - CELL);

        top -= Hud.LINE + 2 + CELL + 8;
        batch.setColor(SOFT);
        Hud.line(batch, font, t.get("inv.items"), left, top);
        drawRow(batch, items, 1, left, top - Hud.LINE - 2 - CELL);

        drawDetail(batch, t, left, top - Hud.LINE - 2 - CELL - 6);

        batch.setColor(Color.WHITE);
        batch.end();
    }

    private void drawRow(SpriteBatch batch, Array<Entry> entries, int row, int left, int bottom) {
        batch.setColor(Color.WHITE);
        for (int i = 0; i < SLOTS; i++) {
            int x = left + i * (CELL + GAP);
            game.skin().getDrawable(Assets.Ui.CELL).draw(batch, x, bottom, CELL, CELL);
            if (i < entries.size) {
                Entry e = entries.get(i);
                batch.draw(e.icon, x + (CELL - e.icon.getRegionWidth()) / 2,
                           bottom + (CELL - e.icon.getRegionHeight()) / 2);
                if (e.count > 1) {
                    // The count sits inside the cell's lower-right corner,
                    // over the icon, where every inventory puts it.
                    Hud.right(batch, font, String.valueOf(e.count), x + CELL - 1, bottom + 11);
                }
            }
            if (row * SLOTS + i == focus) {
                game.skin().getDrawable(Assets.Ui.FOCUS)
                    .draw(batch, x - 2, bottom - 2, CELL + 4, CELL + 4);
            }
        }
    }

    private void drawDetail(SpriteBatch batch, I18n t, int left, int top) {
        Array<Entry> list = focus < SLOTS ? relics : items;
        int index = focus % SLOTS;
        if (index >= list.size) {
            batch.setColor(SOFT);
            Hud.line(batch, font, t.get(list.isEmpty() ? "inv.empty" : "inv.slot_empty"), left, top);
            batch.setColor(Color.WHITE);
            return;
        }
        Entry e = list.get(index);
        batch.setColor(INK);
        Hud.line(batch, font, e.nameKey != null ? t.get(e.nameKey) : e.id, left, top);
        if (e.descKey != null) {
            batch.setColor(SOFT);
            Hud.line(batch, font, t.get(e.descKey), left, top - Hud.LINE);
        }
        batch.setColor(Color.WHITE);
        if (list == items) {
            // On the name's row at the right, where the way out sits on the
            // title's: the key that uses this now, or the one that makes it so.
            RunState run = game.run();
            boolean onKey = run != null && e.id.equals(run.quickItem);
            Hud.prompt(batch, game.skin(), font,
                       game.input().map().primary(onKey ? GameAction.USE_ITEM : GameAction.INTERACT),
                       t.get(onKey ? "inv.on_quick" : "inv.set_quick"),
                       left + SLOTS * (CELL + GAP) - GAP, top - Hud.LINE, Align.right);
        }
    }

    @Override
    public void resize(int width, int height) {
        camera.resize(width, height);
    }

    private static final class Entry {
        final String id;
        final int count;
        String nameKey;
        String descKey;
        TextureRegion icon;

        Entry(String id, int count) {
            this.id = id;
            this.count = count;
        }
    }
}
