package com.kagebi.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.utils.Align;
import com.kagebi.Cfg;
import com.kagebi.assets.Assets;
import com.kagebi.gen.FloorLayout;
import com.kagebi.run.RunState;

/**
 * Hearts, purse and floor map, drawn straight to the batch.
 *
 * <p>No scene2d here on purpose. The HUD sits in the same 320x180 space as the
 * world and changes every frame; a Stage would add a second viewport, a second
 * set of float positions to round, and a layout pass, to buy nothing - there
 * are four things on screen and none of them move.
 *
 * <p>Ruthlessness is the design. 320x180 with 16px art has room for about four
 * readable elements, so what is here is what a player acts on: how close to
 * death they are, what they can spend, where they can still go. Relics live in
 * the inventory, the run timer on the end screen, and the weapon is in the
 * player's hand where it can already be seen.
 */
public final class Hud {

    /** The line box the font was authored for; see the {@code .fnt} header. */
    public static final int LINE = 14;

    private static final int MARGIN = 4;
    private static final int HEART = 16;
    private static final int MAP_W = 58;
    private static final int MAP_H = 42;

    private static final Color GOLD = new Color(0xffad55ff);
    /** The gem's own violet, so the number reads as the same currency. */
    private static final Color GEM = new Color(0xc08ce0ff);
    private static final Color SOFT = new Color(0xe8cfa9ff);
    private static final Color SHADOW = new Color(0x0c0a10c0);
    private static final Color CHIP_TEXT = new Color(0xffe6c4ff);

    private final BitmapFont font;
    private final I18n i18n;
    private final Minimap minimap;

    private final TextureRegion[] hearts;
    private final TextureRegion coin;
    private final TextureRegion key;
    private final TextureRegion gem;
    private final TextureRegion pixel;

    /** Whether the floor map is expanded over the whole screen. */
    private boolean mapOpen;

    public Hud(Skin skin, I18n i18n) {
        this.font = skin.getFont("default");
        this.i18n = i18n;
        this.minimap = new Minimap(skin);

        // One 80x16 strip of five states, so the frame index is the number of
        // quarter-hearts still filled.
        TextureRegion strip = skin.getRegion(Assets.Ui.HEART);
        int frames = strip.getRegionWidth() / HEART;
        hearts = new TextureRegion[frames];
        for (int i = 0; i < frames; i++) {
            hearts[i] = new TextureRegion(strip, i * HEART, 0, HEART, HEART);
        }
        coin = skin.getRegion(Assets.Ui.COIN);
        key = skin.getRegion(Assets.Ui.KEY);
        gem = skin.getRegion(Assets.Ui.GEM);
        pixel = skin.getRegion(Assets.Ui.PIXEL);
    }

    public void toggleMap() {
        mapOpen = !mapOpen;
    }

    public boolean mapOpen() {
        return mapOpen;
    }

    public void closeMap() {
        mapOpen = false;
    }

    /** The quick slot's cell, square. */
    public static final int QUICK_CELL = 20;

    /**
     * The quick key's slot, bottom left: what it will use, and how many are
     * left. The dungeon draws it only while something is carried - an empty
     * slot says nothing - and this corner is the only one not already taken.
     */
    public void drawQuickSlot(SpriteBatch batch, Skin skin, TextureRegion icon, int count) {
        skin.getDrawable(Assets.Ui.CELL).draw(batch, MARGIN, MARGIN, QUICK_CELL, QUICK_CELL);
        batch.draw(icon, MARGIN + (QUICK_CELL - icon.getRegionWidth()) / 2,
                   MARGIN + (QUICK_CELL - icon.getRegionHeight()) / 2);
        if (count > 1) {
            batch.setColor(SOFT);
            right(batch, font, String.valueOf(count), MARGIN + QUICK_CELL - 1, MARGIN + 11);
            batch.setColor(Color.WHITE);
        }
    }

    /** Draws the whole HUD. The batch must already be open, in screen space. */
    public void draw(SpriteBatch batch, RunState run, int screenW, int screenH) {
        drawHearts(batch, run, screenH);
        drawPurse(batch, run, screenH);
        drawMap(batch, run, screenW, screenH);
        batch.setColor(Color.WHITE);
    }

    /**
     * Hit points behind one quarter of a heart.
     *
     * <p>Not one, which is what this drew at first. The content is balanced
     * around a hundred starting hit points - twenty-two enemies and five floors
     * were tuned against that number - and at one point a quarter that is
     * twenty-five hearts across a three-hundred-and-twenty pixel screen. Five
     * hit points a quarter makes the starting bar exactly five hearts, and a
     * fully upgraded one nine.
     */
    public static final int HP_PER_QUARTER = 5;
    /** Hit points in a whole heart. */
    public static final int HP_PER_HEART = HP_PER_QUARTER * Assets.Ui.HEART_STEPS;

    /** Hearts drawn for a maximum: a part-heart of capacity still gets a heart. */
    public static int heartCount(int maxHp) {
        return Math.max(1, (maxHp + HP_PER_HEART - 1) / HP_PER_HEART);
    }

    /**
     * Quarters filled in heart {@code index} (0 = leftmost), which is its frame.
     *
     * <p>Rounded up, so any hit points at all in a heart show as at least a
     * quarter. A player on their last point of health seeing an empty bar would
     * reasonably conclude they were already dead.
     */
    public static int quarters(int hp, int index) {
        int inThisHeart = hp - index * HP_PER_HEART;
        if (inThisHeart <= 0) {
            return 0;
        }
        int filled = (inThisHeart + HP_PER_QUARTER - 1) / HP_PER_QUARTER;
        return Math.min(Assets.Ui.HEART_STEPS, filled);
    }

    private void drawHearts(SpriteBatch batch, RunState run, int screenH) {
        int y = screenH - MARGIN - HEART;
        for (int i = 0; i < heartCount(run.maxHp); i++) {
            int frame = Math.min(hearts.length - 1, quarters(run.hp, i));
            // The hearts overlap by a pixel: the art has a one-pixel outline on
            // each side, and butting them up leaves a two-pixel trench that
            // reads as a gap between two separate icons.
            batch.draw(hearts[frame], MARGIN + i * (HEART - 1), y);
        }
    }

    private void drawPurse(SpriteBatch batch, RunState run, int screenH) {
        int top = screenH - MARGIN - HEART - 3;
        int x = MARGIN + 1;
        String gold = String.valueOf(run.gold);
        batch.draw(coin, x, top - 10);
        batch.setColor(GOLD);
        shadowed(batch, font, gold, x + 11, top, Align.left);
        batch.setColor(Color.WHITE);
        // Keys and gems earn their pixels only while the player holds one. A
        // permanent "0" is a line of HUD that never says anything - and at
        // 320x180 the top-left corner is shared with five hearts.
        int next = x + 11 + Math.round(width(font, gold)) + 7;
        if (run.keys > 0) {
            batch.draw(key, next, top - 10);
            batch.setColor(SOFT);
            String keys = String.valueOf(run.keys);
            shadowed(batch, font, keys, next + 15, top, Align.left);
            batch.setColor(Color.WHITE);
            next += 15 + Math.round(width(font, keys)) + 7;
        }
        if (run.diamonds > 0) {
            batch.draw(gem, next, top - 10);
            batch.setColor(GEM);
            shadowed(batch, font, String.valueOf(run.diamonds), next + 11, top, Align.left);
            batch.setColor(Color.WHITE);
        }
    }

    private void drawMap(SpriteBatch batch, RunState run, int screenW, int screenH) {
        FloorLayout layout = run.layout;
        if (mapOpen) {
            batch.setColor(SHADOW);
            batch.draw(pixel, 0, 0, screenW, screenH);
            batch.setColor(Color.WHITE);
            int w = screenW - 88;
            int h = screenH - 64;
            int bx = (screenW - w) / 2;
            int by = (screenH - h) / 2;
            minimap.draw(batch, layout, run.room, bx, by, w, h, Minimap.CELL_LARGE);
            batch.setColor(SOFT);
            centred(batch, font, floorName(run), screenW / 2f, by + h + LINE + 2);
            batch.setColor(Color.WHITE);
            return;
        }
        int x = screenW - MARGIN - MAP_W;
        int y = screenH - MARGIN - MAP_H;
        minimap.draw(batch, layout, run.room, x, y, MAP_W, MAP_H, Minimap.CELL);
        batch.setColor(SOFT);
        shadowed(batch, font, floorName(run), screenW - MARGIN, y - 2, Align.right);
        batch.setColor(Color.WHITE);
    }

    private String floorName(RunState run) {
        String key = "floor." + run.floor;
        return i18n.has(key) ? i18n.get(key) : i18n.format("game.floor", run.floor);
    }

    // ---- text ------------------------------------------------------------
    //
    // These live here because every screen needs them and none of them needs
    // anything else from a HUD. What they encode is one fact about this font
    // that is expensive to rediscover per call site: see line().

    private static final GlyphLayout LAYOUT = new GlyphLayout();

    /**
     * Draws one line of text with the <em>top of its line box</em> at
     * {@code topY}.
     *
     * <p>libGDX puts the y it is handed at the top of the capitals, not at the
     * top of the line box. In Latin text the difference is invisible, because
     * nothing reaches higher than a capital. In Vietnamese it is four pixels:
     * measured in {@code pixeloid_9}, a capital is 7 rows tall and {@code U+1EA6}
     * is 11, because a grave stacks on a circumflex above it. Passing the box
     * top straight to {@code font.draw} therefore pushes every tone mark four
     * rows out of the space reserved for the text - over a border, through a
     * panel edge, or off the top of the screen.
     *
     * <p>The correction is the font's own ascent, so it stays right if the font
     * is ever regenerated at another size.
     *
     * <p>Text takes the <em>batch's</em> colour. {@link BitmapFont} ignores the
     * batch colour and draws in its own, which is how the first screenshots of
     * this code came out white wherever ink had been asked for. Copying it
     * across here keeps every caller's {@code setColor} meaning what it says,
     * alpha included, and the font is put back to white so a scene2d label
     * sharing it is never left tinted.
     */
    public static void line(SpriteBatch batch, BitmapFont font, CharSequence text,
                            float x, float topY) {
        font.setColor(batch.getColor());
        font.draw(batch, text, Math.round(x), Math.round(topY - font.getAscent()));
        font.setColor(Color.WHITE);
    }

    public static void centred(SpriteBatch batch, BitmapFont font, CharSequence text,
                               float centreX, float topY) {
        line(batch, font, text, Math.round(centreX - width(font, text) / 2f), topY);
    }

    public static void right(SpriteBatch batch, BitmapFont font, CharSequence text,
                             float rightX, float topY) {
        line(batch, font, text, Math.round(rightX - width(font, text)), topY);
    }

    public static float width(BitmapFont font, CharSequence text) {
        LAYOUT.setText(font, text);
        return LAYOUT.width;
    }

    private static final Color SHADOW_INK = new Color(0x0c0a10ff);
    private static final Color SAVED = new Color();

    /**
     * A line over a one-pixel drop shadow, for text drawn straight onto the
     * world rather than onto a panel. Under it can be any colour at all:
     * measured on the first hub screenshot, cream text over the house roofs
     * was unreadable. A shadow down and to the right is the lightest thing
     * that fixes that, and it is how the pack's own title art does it.
     *
     * @param align {@code Align.left}, {@code center} or {@code right}; x is
     *              the left edge, centre or right edge accordingly
     */
    public static void shadowed(SpriteBatch batch, BitmapFont font, CharSequence text,
                                float x, float topY, int align) {
        float left = Align.isRight(align) ? x - width(font, text)
            : Align.isCenterHorizontal(align) ? x - width(font, text) / 2f : x;
        left = Math.round(left);
        SAVED.set(batch.getColor());
        batch.setColor(SHADOW_INK.r, SHADOW_INK.g, SHADOW_INK.b, SAVED.a);
        line(batch, font, text, left + 1, topY - 1);
        batch.setColor(SAVED);
        line(batch, font, text, left, topY);
    }

    // ---- title cards -----------------------------------------------------

    /** Steps a title card holds for, fades included. 2.5 seconds. */
    public static final int CARD_STEPS = 150;
    /**
     * In fast, out slow. A card that fades in over half a second is half a
     * second of text too faint to read, arriving exactly when the player is
     * looking for it; the fade out is where the softness belongs.
     */
    private static final int CARD_IN = 8;
    private static final int CARD_OUT = 40;
    private static final Color CARD_GROUND = new Color(0x0c0a10ff);
    private static final Color CARD_TOP = new Color(0xe8cfa9ff);

    /**
     * The name of a place, over the scene, on arrival. {@code remaining} counts
     * down from {@link #CARD_STEPS}; nothing is drawn at zero.
     *
     * @param second a second line, or null
     */
    public static void card(SpriteBatch batch, Skin skin, BitmapFont font,
                            int remaining, String first, String second) {
        if (remaining <= 0) {
            return;
        }
        int shown = CARD_STEPS - remaining;
        float a = Math.min(1f, Math.min(shown / (float) CARD_IN, remaining / (float) CARD_OUT));
        float w = Math.max(width(font, first), second == null ? 0f : width(font, second)) + 28;
        float h = LINE * (second == null ? 1 : 2) + 10;
        float x = Math.round((Cfg.VIRT_W - w) / 2f);
        float y = Math.round(Cfg.VIRT_H / 2f + 14);
        batch.setColor(CARD_GROUND.r, CARD_GROUND.g, CARD_GROUND.b, 0.72f * a);
        batch.draw(skin.getRegion(Assets.Ui.PIXEL), x, y, w, h);
        batch.setColor(CARD_TOP.r, CARD_TOP.g, CARD_TOP.b, a);
        centred(batch, font, first, Cfg.VIRT_W / 2f, y + h - 4);
        if (second != null) {
            batch.setColor(1f, 1f, 1f, a);
            centred(batch, font, second, Cfg.VIRT_W / 2f, y + h - 4 - LINE);
        }
        batch.setColor(Color.WHITE);
    }

    // ---- prompts ---------------------------------------------------------

    /**
     * The "key, then what it does" chip, centred on {@code centreX}.
     *
     * <p>Drawn as the pack's key-cap art rather than as the letter, because
     * every action is rebindable and a hard-coded letter becomes a lie the
     * first time anyone opens the controls screen.
     */
    public static void prompt(SpriteBatch batch, Skin skin, BitmapFont font,
                              int keycode, String text, float centreX, float bottomY) {
        prompt(batch, skin, font, keycode, text, centreX, bottomY, Align.center);
    }

    /**
     * As above, anchored by {@code align}: x is the chip's left edge, centre or
     * right edge. The chip's outer box extends six pixels past the content on
     * each side, and the anchor is the outer box, so a right-aligned chip sits
     * flush with whatever margin x names.
     */
    public static void prompt(SpriteBatch batch, Skin skin, BitmapFont font,
                              int keycode, String text, float x, float bottomY, int align) {
        Drawable cap = KeyPrompts.drawable(skin, keycode);
        float capW = cap == null ? 0f : cap.getMinWidth();
        float capH = cap == null ? 0f : cap.getMinHeight();
        float gap = cap == null ? 0f : 4f;
        float textW = width(font, text);
        float total = capW + gap + textW;
        float height = Math.max(capH, LINE);

        float left = Align.isRight(align) ? x - 6 - total
            : Align.isLeft(align) ? x + 6 : x - total / 2f;
        left = Math.round(left);
        skin.getDrawable(Assets.Ui.BG).draw(batch, left - 6, bottomY - 4,
                                            total + 12, height + 7);
        if (cap != null) {
            cap.draw(batch, left, Math.round(bottomY + (height - capH) / 2f), capW, capH);
        }
        // Light on the dark ui/bg chip - the same pairing as the skin's
        // "boxed" label, which puts text_inverse on this background.
        batch.setColor(CHIP_TEXT);
        line(batch, font, text, left + capW + gap, bottomY + height);
        batch.setColor(Color.WHITE);
    }
}
