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
import com.kagebi.data.def.SkillDef;
import com.kagebi.entity.Intent;
import com.kagebi.entity.Player;
import com.kagebi.gen.FloorLayout;
import com.kagebi.input.GameAction;
import com.kagebi.input.InputMap;
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
     * The skill bar: three cells along the bottom right, clear of everything.
     *
     * <p>Twenty-four because that is what the icons are. They are drawn on an
     * opaque plate with a rim of their own - they have to be, being drawn over
     * a dungeon floor rather than a panel - so the plate is the cell, and the
     * skin's cell art behind it would only be a frame nobody can see.
     */
    public static final int SKILL_CELL = 24;
    private static final int SKILL_GAP = 4;
    private static final int SKILL_Y = MARGIN;
    private static final int SKILL_X =
        Cfg.VIRT_W - MARGIN - 3 * SKILL_CELL - 2 * SKILL_GAP;
    /** The shutter over a skill that is not ready, and the rim on a live one. */
    private static final Color COOLDOWN = new Color(0f, 0f, 0f, 0.62f);
    private static final Color READY = new Color(0xffd45aff);

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
        drawBossBar(batch, screenW, screenH);
        batch.setColor(Color.WHITE);
    }

    // ---- the skill bar -----------------------------------------------------

    /**
     * Three cells in the bottom-right corner: what each key does, and how long
     * until it will do it again.
     *
     * <p>That corner because it is the only empty one left. The hearts and the
     * purse own the top left, the map the top right, the quick slot the bottom
     * left, and the interact prompt sits centred just above the bottom edge -
     * so the bar starts far enough right to clear it.
     *
     * <p>The cooldown is drawn as a shutter falling down the cell rather than
     * as a number alone. A number has to be read; a shutter is understood from
     * the corner of the eye, which is where a player fighting will see it.
     */
    public void drawSkills(SpriteBatch batch, Skin skin, Player player, InputMap keys) {
        for (int slot = 0; slot < Intent.SKILLS; slot++) {
            SkillDef s = player.skill(slot);
            if (s == null) {
                continue;
            }
            int x = skillX(slot);
            TextureRegion icon = skin.getRegion(Assets.Ui.skillIcon(s.icon));
            if (icon != null) {
                batch.draw(icon, x + (SKILL_CELL - icon.getRegionWidth()) / 2f,
                           SKILL_Y + (SKILL_CELL - icon.getRegionHeight()) / 2f);
            }

            int left = player.cooldown(slot);
            if (left > 0) {
                drawCooldown(batch, x, left, s.cooldownSteps);
            } else if (s.kind == SkillDef.Kind.AVATAR && player.transformed()) {
                // Lit while it is running, so the eight seconds the player
                // spent health on are visible without counting.
                batch.setColor(READY);
                batch.draw(pixel, x - 1, SKILL_Y - 1, SKILL_CELL + 2, 1);
                batch.draw(pixel, x - 1, SKILL_Y + SKILL_CELL, SKILL_CELL + 2, 1);
                batch.draw(pixel, x - 1, SKILL_Y, 1, SKILL_CELL);
                batch.draw(pixel, x + SKILL_CELL, SKILL_Y, 1, SKILL_CELL);
                batch.setColor(Color.WHITE);
            }

            // The key cap under the cell, from the pack's art rather than the
            // letter: every action is rebindable, and a drawn "U" becomes a lie
            // the first time somebody opens the controls screen.
            int key = keys == null ? -1 : keys.primary(skillAction(slot));
            Drawable cap = key < 0 ? null : KeyPrompts.drawable(skin, key);
            if (cap != null) {
                cap.draw(batch, x + (SKILL_CELL - cap.getMinWidth()) / 2f,
                         SKILL_Y + SKILL_CELL + 1, cap.getMinWidth(), cap.getMinHeight());
            }
        }
        batch.setColor(Color.WHITE);
    }

    /** The shutter, and the seconds left written over it. */
    private void drawCooldown(SpriteBatch batch, int x, int left, int total) {
        int shade = Math.max(1, Math.round(SKILL_CELL * (left / (float) Math.max(1, total))));
        batch.setColor(COOLDOWN);
        batch.draw(pixel, x, SKILL_Y + SKILL_CELL - shade, SKILL_CELL, shade);
        batch.setColor(Color.WHITE);
        // Rounded up, so a skill showing "1" is never already available: a
        // player who sees zero and presses is told no, which reads as the
        // button being broken rather than early.
        int seconds = (int) Math.ceil(left * Cfg.STEP);
        shadowed(batch, font, String.valueOf(seconds),
                 x + SKILL_CELL / 2f, SKILL_Y + SKILL_CELL - 3, Align.center);
    }

    private static GameAction skillAction(int slot) {
        switch (slot) {
            case 0: return GameAction.SKILL_1;
            case 1: return GameAction.SKILL_2;
            default: return GameAction.SKILL_3;
        }
    }

    public static int skillX(int slot) {
        return SKILL_X + slot * (SKILL_CELL + SKILL_GAP);
    }

    // ---- what a skill does -------------------------------------------------

    /**
     * How wide the description panel is, and how many lines of it fit.
     *
     * <p>Public because {@code ContentContractTest} measures every
     * {@code skill.*.desc} against them in both languages, the way it already
     * measures the stage blurbs. Vietnamese runs about a fifth longer than
     * English, so a line budget checked in English only is a line budget
     * checked in the easy language.
     */
    public static final int SKILL_INFO_W = 136;
    public static final int SKILL_INFO_LINES = 5;

    /** Where the panel sits above the bar, and how much air is inside it. */
    private static final int INFO_PAD = 6;
    private static final int INFO_GAP = 12;

    private static final Color INFO_TITLE = new Color(0xffe6c4ff);
    private static final Color INFO_TEXT = new Color(0xe8cfa9ff);
    private static final Color INFO_FACT = new Color(0xffad55ff);

    /**
     * What a skill does, while the player holds shift and its key.
     *
     * <p>Held, not toggled, and gone the moment either key is released. A
     * panel that has to be dismissed is a panel a player reads once; one that
     * costs nothing to open is one they check mid-fight, which is when the
     * question "what does this actually do" is asked.
     *
     * <p>Drawn over the world with no shroud behind it. The game does not
     * pause for this - reading is something the player chooses to do while
     * something is walking towards them, and freezing the world to explain a
     * button would be a worse answer than the question deserves.
     */
    public void drawSkillInfo(SpriteBatch batch, Skin skin, Player player, int slot) {
        SkillDef s = slot < 0 ? null : player.skill(slot);
        if (s == null) {
            return;
        }
        // Right-aligned to the cell it belongs to: the panel is wider than
        // three cells, so it is anchored to the key being read rather than
        // centred, and clamped on screen inside drawInfo.
        drawInfo(batch, skin, s, skillX(slot) + SKILL_CELL);
    }

    /**
     * The same panel for the ability that has no key, raised by shift alone.
     *
     * <p>Anchored to the right of the bar, where the third key's panel would
     * be, because there is no cell of its own to point at and the bar is what
     * shift is about.
     */
    public void drawPassiveInfo(SpriteBatch batch, Skin skin, SkillDef passive) {
        if (passive == null) {
            return;
        }
        drawInfo(batch, skin, passive, skillX(Intent.SKILLS - 1) + SKILL_CELL);
    }

    private void drawInfo(SpriteBatch batch, Skin skin, SkillDef s, float rightEdge) {
        String name = i18n.get(s.nameKey);
        String desc = i18n.get(s.descKey);
        String facts = facts(s);

        float textH = wrappedHeight(desc, SKILL_INFO_W);
        float h = INFO_PAD * 2 + LINE + textH + LINE;
        float w = SKILL_INFO_W + INFO_PAD * 2;
        // Pulled back on screen: the third cell is four pixels from the edge
        // and the panel is wider than three cells.
        float x = Math.round(Math.min(rightEdge - w, Cfg.VIRT_W - MARGIN - w));
        x = Math.max(MARGIN, x);
        float y = Math.round(SKILL_Y + SKILL_CELL + INFO_GAP);

        Drawable panel = skin.getDrawable(Assets.Ui.PANEL_2);
        if (panel != null) {
            panel.draw(batch, x, y, w, h);
        }

        float top = y + h - INFO_PAD;
        batch.setColor(INFO_TITLE);
        line(batch, font, name, x + INFO_PAD, top);
        batch.setColor(Color.WHITE);

        font.setColor(INFO_TEXT);
        font.draw(batch, desc, x + INFO_PAD, top - LINE - font.getAscent(),
                  SKILL_INFO_W, Align.left, true);
        font.setColor(Color.WHITE);

        batch.setColor(INFO_FACT);
        line(batch, font, facts, x + INFO_PAD, top - LINE - textH);
        batch.setColor(Color.WHITE);
    }

    /**
     * The two numbers worth reading off a skill: how long the wait is, and how
     * long it lasts when it is not instant.
     *
     * <p>Not every number it has. A panel that listed six multipliers would be
     * a statistics screen, and the player holding a key mid-fight is asking a
     * shorter question than that.
     */
    private String facts(SkillDef s) {
        // A passive has no cooldown and no duration, and "Cooldown 0s" is a
        // worse answer than the true one.
        if (s.kind == SkillDef.Kind.PASSIVE) {
            return i18n.get("skill.info.passive");
        }
        String out = i18n.format("skill.info.cooldown", seconds(s.cooldownSteps));
        if (s.durationSteps > 0) {
            out = out + "   " + i18n.format("skill.info.duration", seconds(s.durationSteps));
        }
        return out;
    }

    private static String seconds(int steps) {
        float s = steps * Cfg.STEP;
        return s >= 10f || s == Math.round(s)
            ? String.valueOf(Math.round(s))
            : String.valueOf(Math.round(s * 10f) / 10f);
    }

    /**
     * How tall a block of text is once the font has wrapped it, plus the air
     * under it.
     *
     * <p>{@code GlyphLayout.height} is measured to the last line's baseline
     * box, not to the bottom of its descenders - and Vietnamese hangs below
     * the baseline. Without the padding the last line of a description and the
     * cooldown under it share the same few pixels.
     */
    private float wrappedHeight(String text, float width) {
        LAYOUT.setText(font, text, Color.WHITE, width, Align.left, true);
        return Math.max(LINE, LAYOUT.height + 8);
    }

    // ---- the boss bar ------------------------------------------------------

    /** Width of the boss bar, and the thickness of its filled strip. */
    public static final int BOSS_BAR_W = 140;
    public static final int BOSS_BAR_H = 4;
    /** Rows between the bar and the bottom of the screen. */
    public static final int BOSS_BAR_Y = 16;

    private static final Color BOSS_TROUGH = new Color(0x1a1016ff);
    private static final Color BOSS_FILL = new Color(0xc8443cff);
    private static final Color BOSS_FILL_ENRAGED = new Color(0xe8a63cff);

    private String bossName;
    private float bossFraction;
    private int bossBody;
    private int bossBodies;
    private boolean bossEnraged;

    /**
     * Tells the HUD what the room's boss is doing, or that there is none.
     *
     * <p>Pushed in by the screen rather than pulled from the world, so that
     * {@code ui} keeps knowing nothing about {@code entity} - the same reason
     * every other number here arrives on a {@link RunState}.
     *
     * @param body which body of a chain this is, 1-based, and how many there
     *             are in it. A boss that is only itself passes 1 and 1, and
     *             the counter is left off.
     */
    public void boss(String name, float fraction, int body, int bodies, boolean enraged) {
        this.bossName = name;
        this.bossFraction = Math.max(0f, Math.min(1f, fraction));
        this.bossBody = body;
        this.bossBodies = bodies;
        this.bossEnraged = enraged;
    }

    public void noBoss() {
        this.bossName = null;
    }

    /**
     * A bar across the bottom of the screen while a boss is alive.
     *
     * <p>Bosses used to share the 14-pixel bar every trash mob wears over its
     * head, which hides itself ninety steps after the last hit. In a room the
     * size of the screen that is enough; in stage 6's arena, four screens
     * across, it is a detail on a sprite that is often not even in shot. And
     * it cannot say which of three bodies is being fought, which is the one
     * thing that fight has to communicate - without it, killing the Drowned
     * Captain and watching him stand back up reads as the game cheating.
     *
     * <p>At the bottom because the top is already hearts and the minimap, and
     * because a boss is usually the thing in the upper half of a room being
     * walked towards.
     */
    private void drawBossBar(SpriteBatch batch, int screenW, int screenH) {
        if (bossName == null || pixel == null) {
            return;
        }
        int x = (screenW - BOSS_BAR_W) / 2;
        int y = BOSS_BAR_Y;

        batch.setColor(BOSS_TROUGH);
        batch.draw(pixel, x - 1, y - 1, BOSS_BAR_W + 2, BOSS_BAR_H + 2);
        batch.setColor(bossEnraged ? BOSS_FILL_ENRAGED : BOSS_FILL);
        int filled = Math.round(BOSS_BAR_W * bossFraction);
        if (filled > 0) {
            batch.draw(pixel, x, y, filled, BOSS_BAR_H);
        }
        batch.setColor(Color.WHITE);

        String label = bossBodies > 1
            ? bossName + "  " + bossBody + "/" + bossBodies : bossName;
        // Centred over the bar, which is itself centred on the screen.
        float textX = x + (BOSS_BAR_W - width(font, label)) / 2f;
        line(batch, font, label, textX, y + BOSS_BAR_H + 2 + LINE);
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
