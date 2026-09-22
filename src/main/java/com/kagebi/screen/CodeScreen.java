package com.kagebi.screen;

import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.Align;
import com.kagebi.Cfg;
import com.kagebi.Kagebi;
import com.kagebi.assets.Assets;
import com.kagebi.gfx.CameraController;
import com.kagebi.input.GameAction;
import com.kagebi.save.Codes;
import com.kagebi.ui.Hud;
import com.kagebi.ui.I18n;

/**
 * A box to type a code into.
 *
 * <p><b>Raw key events, not a scene2d TextField.</b> The rest of this screen is
 * drawn immediate-mode over the game's own panel, and a single scene2d widget
 * would drag a Stage, a skin entry and a second input model in for one line of
 * text. {@code SettingsScreen.RebindCapture} took the same route for the same
 * reason and is the pattern this follows: an {@link InputAdapter} ahead of the
 * action map in a multiplexer, reading {@code keyTyped}.
 *
 * <p>Ahead of it, deliberately. The typing has to win: a player spelling
 * "KataMoney" presses A, which is also walk-left, and M, which is also the map.
 * Nothing else on this screen reads an action except escape, which the adapter
 * lets past.
 */
public class CodeScreen extends SimScreen {

    /** Long enough for anything the game has, short enough to draw on one line. */
    private static final int MAX = 24;
    private static final int BOX_W = 200;
    private static final int BOX_H = 18;
    private static final int BOX_X = (Cfg.VIRT_W - BOX_W) / 2;
    private static final int BOX_Y = 86;
    /** Steps the answer stays up before the box is ready again. */
    private static final int SAID_STEPS = 150;

    private static final Color INK = new Color(0x131b1bff);
    private static final Color SOFT = new Color(0x46503cff);
    private static final Color GOOD = new Color(0x4f9d52ff);
    private static final Color DANGER = new Color(0xd94a3aff);

    private final Kagebi game;
    private final CameraController camera = new CameraController();
    private final StringBuilder typed = new StringBuilder();

    private BitmapFont font;
    private InputProcessor inputs;
    /** What to say about the last thing entered, and how long to say it for. */
    private String said;
    private boolean saidGood;
    private int saidSteps;

    public CodeScreen(Kagebi game) {
        super(game.input());
        this.game = game;
    }

    @Override
    public InputProcessor inputProcessor() {
        return inputs;
    }

    @Override
    public boolean isOpaque() {
        return false;
    }

    @Override
    public void show() {
        font = game.skin().getFont("default");
        game.input().clear();
        camera.snapTo(Cfg.VIRT_W / 2f, Cfg.VIRT_H / 2f);
        inputs = new InputMultiplexer(new Typing(), game.input());
    }

    /**
     * The one thing on this screen that reads the keyboard directly.
     *
     * <p>Letters and digits go into the box; backspace takes one out; enter
     * submits. Everything else is passed on, which is how escape still closes
     * the screen through the ordinary action map.
     */
    private final class Typing extends InputAdapter {

        @Override
        public boolean keyTyped(char character) {
            if (character == '\b') {
                if (typed.length() > 0) {
                    typed.deleteCharAt(typed.length() - 1);
                }
                return true;
            }
            if (character == '\r' || character == '\n') {
                submit();
                return true;
            }
            if (typed.length() < MAX && (Character.isLetterOrDigit(character))) {
                typed.append(character);
                return true;
            }
            return false;
        }

        @Override
        public boolean keyDown(int keycode) {
            // Enter arrives as a typed character on desktop and as a key on
            // some layouts; both submit, and swallowing it here stops the
            // action map reading it as INTERACT.
            return keycode == Keys.ENTER || keycode == Keys.NUMPAD_ENTER
                || keycode == Keys.BACKSPACE;
        }
    }

    private void submit() {
        String code = typed.toString();
        if (code.isEmpty()) {
            return;
        }
        boolean already = Codes.spent(code, game.profile());
        if (Codes.redeem(code, game.content(), game.profile())) {
            game.saves().save(game.profile());
            game.audio().playSfx(Assets.SFX_ACCEPT);
            said = game.i18n().get(already ? "code.again" : "code.ok");
            saidGood = true;
        } else {
            game.audio().playSfx(Assets.SFX_CANCEL);
            said = game.i18n().get("code.bad");
            saidGood = false;
        }
        saidSteps = SAID_STEPS;
        typed.setLength(0);
    }

    @Override
    protected void step() {
        if (saidSteps > 0) {
            saidSteps--;
        }
        if (input().justPressed(GameAction.PAUSE)) {
            game.audio().playSfx(Assets.SFX_CANCEL);
            stack().pop();
        }
    }

    @Override
    public void render(float delta) {
        camera.apply();
        SpriteBatch batch = game.batch();
        batch.setProjectionMatrix(camera.camera().combined);
        batch.begin();
        game.skin().getDrawable("scrim").draw(batch, 0, 0, Cfg.VIRT_W, Cfg.VIRT_H);
        game.skin().getDrawable(Assets.Ui.PANEL_2).draw(batch, 20, 52, Cfg.VIRT_W - 40, 76);
        I18n t = game.i18n();

        batch.setColor(INK);
        Hud.centred(batch, font, t.get("code.title"), Cfg.VIRT_W / 2f, 122);
        batch.setColor(Color.WHITE);

        game.skin().getDrawable(Assets.Ui.CELL).draw(batch, BOX_X, BOX_Y, BOX_W, BOX_H);
        // A caret that blinks, so an empty box still reads as one waiting for
        // typing rather than as one that is not listening.
        String shown = typed + (steps() % 60 < 30 ? "_" : " ");
        batch.setColor(INK);
        Hud.centred(batch, font, shown, Cfg.VIRT_W / 2f, BOX_Y + BOX_H - 3);
        batch.setColor(Color.WHITE);

        if (saidSteps > 0 && said != null) {
            batch.setColor(saidGood ? GOOD : DANGER);
            Hud.centred(batch, font, said, Cfg.VIRT_W / 2f, BOX_Y - 6);
            batch.setColor(Color.WHITE);
        } else {
            batch.setColor(SOFT);
            Hud.centred(batch, font, t.get("code.hint"), Cfg.VIRT_W / 2f, BOX_Y - 6);
            batch.setColor(Color.WHITE);
        }

        Hud.prompt(batch, game.skin(), font,
            game.input().map().primary(GameAction.PAUSE), t.get("common.back"),
            Cfg.VIRT_W / 2f, 28, Align.center);
        batch.setColor(Color.WHITE);
        batch.end();
    }

    @Override
    public void resize(int width, int height) {
        camera.resize(width, height);
    }
}
