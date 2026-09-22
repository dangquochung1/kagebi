package com.kagebi.screen;

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
import com.kagebi.ui.Hit;
import com.kagebi.ui.Hud;
import com.kagebi.ui.I18n;

/**
 * "Are you sure?", for the one question in this game worth asking.
 *
 * <p>Deliberately general - a title, a warning and two buttons - but written
 * for New Game, which destroys a profile that may represent weeks. A button
 * that quietly erases everything the moment it is pressed is not a feature, and
 * the cost of the extra press is one press.
 *
 * <p><b>It opens on No.</b> A player who reflexively confirms a dialog they did
 * not read should keep their save. The dangerous answer is one deliberate
 * movement away, and it is the one drawn in the danger colour.
 *
 * <p>Immediate mode over a panel rather than a scene2d dialog, which is how
 * every other overlay in this game is drawn; see {@link CodeScreen}.
 */
public final class ConfirmScreen extends SimScreen {

    private static final int PANEL_X = 30;
    private static final int PANEL_Y = 52;
    private static final int PANEL_W = Cfg.VIRT_W - 60;
    private static final int PANEL_H = 78;

    private static final int BUTTON_W = 60;
    /** 15, not 18: the height the skin's plate and a 9px label were sized for. */
    private static final int BUTTON_H = 15;
    private static final int BUTTON_Y = 62;
    private static final int NO_X = Cfg.VIRT_W / 2 + 8;
    private static final int YES_X = Cfg.VIRT_W / 2 - 8 - BUTTON_W;

    private static final Color INK = new Color(0x131b1bff);
    private static final Color SOFT = new Color(0x46503cff);
    /** The same red as a tint, which the orange plate has to survive. */
    private static final Color DANGER_PLATE = new Color(1f, 0.52f, 0.46f, 1f);

    private final Kagebi game;
    private final CameraController camera = new CameraController();
    private final Hit hit;
    private final String titleKey;
    private final String bodyKey;
    private final Runnable onYes;

    private BitmapFont font;
    /** False is No, and No is where the cursor starts. */
    private boolean yes;

    public ConfirmScreen(Kagebi game, String titleKey, String bodyKey, Runnable onYes) {
        super(game.input());
        this.game = game;
        this.titleKey = titleKey;
        this.bodyKey = bodyKey;
        this.onYes = onYes;
        this.hit = new Hit(game.input(), camera);
    }

    @Override
    public InputProcessor inputProcessor() {
        return game.input();
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
    }

    @Override
    protected void step() {
        if (hit.clicked(YES_X, BUTTON_Y, BUTTON_W, BUTTON_H)) {
            yes = true;
            answer();
            return;
        }
        if (hit.clicked(NO_X, BUTTON_Y, BUTTON_W, BUTTON_H)) {
            cancel();
            return;
        }
        if (input().justPressed(GameAction.MOVE_LEFT)) {
            move(true);
        } else if (input().justPressed(GameAction.MOVE_RIGHT)) {
            move(false);
        }
        if (input().justPressed(GameAction.PAUSE)) {
            cancel();
            return;
        }
        if (input().justPressed(GameAction.INTERACT)) {
            answer();
        }
    }

    private void move(boolean toYes) {
        if (yes != toYes) {
            yes = toYes;
            game.audio().playSfx(Assets.SFX_MOVE);
        }
    }

    private void answer() {
        if (!yes) {
            cancel();
            return;
        }
        game.audio().playSfx(Assets.SFX_ACCEPT);
        // Popped before the action runs. What follows is usually a change of
        // screen, and a dialog that closes itself afterwards would be popping
        // whatever replaced it.
        stack().pop();
        onYes.run();
    }

    private void cancel() {
        game.audio().playSfx(Assets.SFX_CANCEL);
        stack().pop();
    }

    @Override
    public void render(float delta) {
        camera.apply();
        SpriteBatch batch = game.batch();
        batch.setProjectionMatrix(camera.camera().combined);
        batch.begin();
        I18n t = game.i18n();

        game.skin().getDrawable("scrim").draw(batch, 0, 0, Cfg.VIRT_W, Cfg.VIRT_H);
        game.skin().getDrawable(Assets.Ui.PANEL_2).draw(batch, PANEL_X, PANEL_Y, PANEL_W, PANEL_H);

        batch.setColor(INK);
        Hud.centred(batch, font, t.get(titleKey), Cfg.VIRT_W / 2f, PANEL_Y + PANEL_H - 8);
        batch.setColor(Color.WHITE);

        // Wrapped: the warning is the part that has to be read, and Vietnamese
        // runs about fifteen per cent longer than the English it is sized for.
        font.setColor(SOFT);
        font.draw(batch, t.get(bodyKey), PANEL_X + 10, PANEL_Y + PANEL_H - 24,
                  PANEL_W - 20, Align.center, true);
        font.setColor(Color.WHITE);

        button(batch, YES_X, t.get("common.yes"), yes, true);
        button(batch, NO_X, t.get("common.no"), !yes, false);

        batch.end();
    }

    /**
     * A plate, a label, and a ring around the one the keyboard is on.
     *
     * <p>Not the pack's own {@code HERO_BUTTON}, which is what this drew
     * before: that art has the word CREATE printed into its pixels, so both
     * buttons read "CREATE" with a label smeared over the top. It is also a
     * flat 43x18 region, so widening it to 60 stretched the baked word rather
     * than the frame. These are the skin's blank wooden plates, and being
     * nine-patches they widen without dragging the art with them.
     *
     * <p>The ring is new. The only thing separating the two buttons was which
     * one was lit, and on a plate that already changes under the pointer that
     * is not enough to say where the keyboard is standing.
     */
    private void button(SpriteBatch batch, int x, String label, boolean on, boolean danger) {
        boolean over = hit.over(x, BUTTON_Y, BUTTON_W, BUTTON_H);
        // The warning is in the plate, not in the letters. Red ink on the
        // skin's orange wood is about two to one and reads as a smudge; the
        // same red multiplied into the plate turns the whole button red and
        // leaves the label on the dark ink everything else here is written in.
        batch.setColor(danger ? DANGER_PLATE : Color.WHITE);
        game.skin().getDrawable(on || over ? Assets.Ui.BUTTON_OVER : Assets.Ui.BUTTON_UP)
            .draw(batch, x, BUTTON_Y, BUTTON_W, BUTTON_H);
        batch.setColor(INK);
        Hud.centred(batch, font, label, x + BUTTON_W / 2f, BUTTON_Y + BUTTON_H - 2);
        batch.setColor(Color.WHITE);
        if (on) {
            game.skin().getDrawable(Assets.Ui.FOCUS)
                .draw(batch, x - 2, BUTTON_Y - 2, BUTTON_W + 4, BUTTON_H + 4);
        }
    }

    @Override
    public void resize(int width, int height) {
        camera.resize(width, height);
    }
}
