package com.kagebi.screen;

import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ScreenUtils;
import com.kagebi.Cfg;
import com.kagebi.Kagebi;
import com.kagebi.assets.Assets;
import com.kagebi.gfx.CameraController;
import com.kagebi.input.GameAction;
import com.kagebi.ui.Hud;
import com.kagebi.ui.I18n;

/**
 * The credits roll.
 *
 * <p>Who is named comes from {@code CREDITS.md} and nowhere else, and that file
 * is the authority if the two ever disagree. A pack that was downloaded, tried
 * and then cut is not named here and is not named there: crediting art that is
 * not in the game is its own kind of misattribution. {@code ScreenContractTest}
 * checks the roll against {@code CREDITS.md} rather than against a list of
 * names, so a pack that leaves the game cannot stay in the credits.
 *
 * <p>Pack titles, authors and licences are identical in both language files.
 * They are attribution, and attribution is quoted, not translated; what each
 * pack was <em>used for</em> is ordinary interface text and is translated.
 *
 * <p>The roll advances by whole pixels. A sub-pixel scroll through nearest
 * filtering is a line of text that shimmers as it moves, which on a screen that
 * is nothing but moving text is the whole screen.
 */
public class CreditsScreen extends SimScreen {

    /** Virtual pixels per second. A line of 14 px passes in about a second. */
    private static final float SPEED = 14f;
    /** Held ATTACK or MOVE_DOWN, for anyone who has read it before. */
    private static final float FAST = 6f;

    private enum Style { LOGO, HEAD, NAME, BY, FOR, GAP, END }

    /** The roll, top to bottom, as i18n keys. */
    private static final Object[][] ROLL = {
        {Style.LOGO, null},
        {Style.FOR, "game.title"},
        {Style.GAP, null},
        {Style.FOR, "credits.note"},
        {Style.GAP, null},

        {Style.HEAD, "credits.art"},
        {Style.NAME, "credits.ninja.name"},
        {Style.BY, "credits.ninja.by"},
        {Style.FOR, "credits.ninja.for"},
        {Style.GAP, null},
        {Style.NAME, "credits.dungeon.name"},
        {Style.BY, "credits.dungeon.by"},
        {Style.FOR, "credits.dungeon.for"},
        {Style.GAP, null},
        {Style.NAME, "credits.enemies.name"},
        {Style.BY, "credits.enemies.by"},
        {Style.FOR, "credits.enemies.for"},
        {Style.GAP, null},
        {Style.NAME, "credits.raven.name"},
        {Style.BY, "credits.raven.by"},
        {Style.FOR, "credits.raven.for"},
        {Style.GAP, null},
        {Style.NAME, "credits.home.name"},
        {Style.BY, "credits.home.by"},
        {Style.FOR, "credits.home.for"},
        {Style.GAP, null},
        {Style.NAME, "credits.sunnyside.name"},
        {Style.BY, "credits.sunnyside.by"},
        {Style.FOR, "credits.sunnyside.for"},
        {Style.GAP, null},
        {Style.NAME, "credits.cove.name"},
        {Style.BY, "credits.cove.by"},
        {Style.FOR, "credits.cove.for"},
        {Style.GAP, null},
        {Style.NAME, "credits.skillfx.name"},
        {Style.BY, "credits.skillfx.by"},
        {Style.FOR, "credits.skillfx.for"},
        {Style.GAP, null},
        {Style.NAME, "credits.fx.name"},
        {Style.BY, "credits.fx.by"},
        {Style.FOR, "credits.fx.for"},
        {Style.GAP, null},
        {Style.NAME, "credits.fire2.name"},
        {Style.BY, "credits.fire2.by"},
        {Style.FOR, "credits.fire2.for"},

        {Style.NAME, "credits.untied.name"},
        {Style.BY, "credits.untied.by"},
        {Style.FOR, "credits.untied.for"},
        {Style.GAP, null},

        {Style.HEAD, "credits.audio"},
        {Style.NAME, "credits.ninja.name"},
        {Style.BY, "credits.ninja.by"},
        {Style.GAP, null},
        {Style.NAME, "credits.sunny.name"},
        {Style.BY, "credits.sunny.by"},
        {Style.GAP, null},

        {Style.HEAD, "credits.font"},
        {Style.NAME, "credits.pixeloid.name"},
        {Style.BY, "credits.pixeloid.by"},
        {Style.FOR, "credits.pixeloid.for"},
        {Style.GAP, null},
        {Style.GAP, null},

        {Style.END, "credits.thanks"},
    };

    /** Every i18n key the roll draws, for the test that checks they exist. */
    static Array<String> rollKeys() {
        Array<String> keys = new Array<>();
        for (Object[] row : ROLL) {
            if (row[1] != null) {
                keys.add((String) row[1]);
            }
        }
        return keys;
    }

    /** Just the pack titles, for the test that checks them against CREDITS.md. */
    static Array<String> rollNameKeys() {
        Array<String> keys = new Array<>();
        for (Object[] row : ROLL) {
            if (row[0] == Style.NAME) {
                keys.add((String) row[1]);
            }
        }
        return keys;
    }

    /** The row each {@code --page} starts at, so any section can be screenshotted. */
    private static final int[] SECTIONS = {0, 5, 22, 34, 45};

    private static final Color HEAD = new Color(0xffad55ff);
    private static final Color NAME = new Color(0xffe6c4ff);
    private static final Color BY = new Color(0xe8cfa9ff);
    private static final Color FOR = new Color(0xab8a6eff);

    private final Kagebi game;
    private final CameraController camera = new CameraController();
    private BitmapFont font;

    /** Pixels scrolled; fractional between steps, drawn rounded. */
    private float scroll;
    private final float startScroll;
    private int height;

    public CreditsScreen(Kagebi game, int page) {
        super(game.input());
        this.game = game;
        int section = SECTIONS[Math.max(0, Math.min(page - 1, SECTIONS.length - 1))];
        // Page 1 opens on the title already a third of the way up, rather than
        // on an empty screen with the roll still below the bottom edge - at
        // this speed that emptiness lasts eight seconds, and reads as broken.
        // The other pages start with their section's first row at the top.
        this.startScroll = section == 0 ? Cfg.VIRT_H * 2 / 3f
                                        : Cfg.VIRT_H - 16f + rowOffset(section);
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
        scroll = startScroll;
        height = rowOffset(ROLL.length);
        game.audio().playMusic(Assets.MUSIC_CREDITS);
    }

    private static int rowHeight(Style style) {
        switch (style) {
            case LOGO: return Hud.LINE + 4;
            case HEAD: return Hud.LINE + 6;
            case GAP: return 8;
            case END: return Hud.LINE;
            default: return Hud.LINE;
        }
    }

    private static int rowOffset(int row) {
        int y = 0;
        for (int i = 0; i < row; i++) {
            y += rowHeight((Style) ROLL[i][0]);
        }
        return y;
    }

    /** Where the roll stops: the last line held in the middle of the screen. */
    private float endScroll() {
        return height + Cfg.VIRT_H / 2f;
    }

    @Override
    protected void step() {
        if (input().justPressed(GameAction.PAUSE) || input().justPressed(GameAction.INTERACT)) {
            game.audio().playSfx(Assets.SFX_CANCEL);
            stack().set(new MainMenuScreen(game));
            return;
        }
        boolean fast = input().isDown(GameAction.ATTACK) || input().isDown(GameAction.MOVE_DOWN);
        scroll = Math.min(endScroll(), scroll + SPEED * (fast ? FAST : 1f) * Cfg.STEP);
    }

    @Override
    public void render(float delta) {
        ScreenUtils.clear(0.05f, 0.04f, 0.07f, 1f);
        camera.apply();
        SpriteBatch batch = game.batch();
        batch.setProjectionMatrix(camera.camera().combined);
        batch.begin();

        I18n t = game.i18n();
        // Row 0 starts just below the bottom edge and rises. Rounded once
        // here, so every row moves by the same whole pixel on the same frame.
        int base = Math.round(scroll);
        int y = 0;
        float cx = Cfg.VIRT_W / 2f;
        for (Object[] row : ROLL) {
            Style style = (Style) row[0];
            String key = (String) row[1];
            int top = base - y;
            y += rowHeight(style);
            if (top < -Hud.LINE || top - Hud.LINE > Cfg.VIRT_H) {
                continue;
            }
            switch (style) {
                case LOGO:
                    batch.setColor(HEAD);
                    Hud.centred(batch, font, "KAGEBI", cx, top);
                    break;
                case HEAD:
                    batch.setColor(HEAD);
                    // Upper case is written into the language files rather
                    // than applied here, so the font test sees the capitals
                    // that are actually drawn - every one of them tone-marked.
                    Hud.centred(batch, font, t.get(key), cx, top);
                    break;
                case NAME:
                    batch.setColor(NAME);
                    Hud.centred(batch, font, t.get(key), cx, top);
                    break;
                case BY:
                    batch.setColor(BY);
                    Hud.centred(batch, font, t.get(key), cx, top);
                    break;
                case FOR:
                    batch.setColor(FOR);
                    Hud.centred(batch, font, t.get(key), cx, top);
                    break;
                case END:
                    batch.setColor(NAME);
                    Hud.centred(batch, font, t.get(key), cx, top);
                    break;
                default:
                    break;
            }
        }
        batch.setColor(Color.WHITE);
        if (scroll >= endScroll()) {
            Hud.prompt(batch, game.skin(), font, game.input().map().primary(GameAction.INTERACT),
                       t.get("common.back"), cx, 12);
        }
        batch.end();
    }

    @Override
    public void resize(int width, int height) {
        camera.resize(width, height);
    }
}
