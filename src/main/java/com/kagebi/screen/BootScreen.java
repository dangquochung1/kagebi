package com.kagebi.screen;

import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.ScreenUtils;
import com.kagebi.Cfg;
import com.kagebi.Kagebi;
import com.kagebi.assets.Assets;
import com.kagebi.gfx.CameraController;
import com.kagebi.ui.Hud;

/**
 * The loading screen, and the first impression the game makes.
 *
 * <p>Two decisions here are about feel rather than function.
 *
 * <p><b>The bar is eased, not sampled.</b> {@link AssetManager} reports progress
 * per asset, and with four assets that is 0, 25, 50, 75, 100 - a bar that jumps
 * in quarters, which reads as a program that has hung three times. The drawn
 * value chases the real one at a fixed rate, so it always appears to be moving.
 *
 * <p><b>It is held for a minimum time.</b> Everything here loads in well under
 * a second on a warm cache, and a loading screen that flashes past looks like a
 * failure rather than a start. {@link #MIN_STEPS} is the floor, counted in
 * simulation steps like every other duration in the game.
 */
public class BootScreen extends SimScreen {

    /** 1.2 seconds. Long enough to read the title, short enough not to wait. */
    private static final int MIN_STEPS = 72;

    /** Bar travel per step, as a fraction of its length. 100 steps end to end. */
    private static final float EASE = 0.01f;

    private static final int PANEL_W = 188;
    private static final int PANEL_H = 66;
    private static final int BAR_W = 156;
    private static final int BAR_H = 7;

    private static final Color INK = new Color(0x131b1bff);
    private static final Color SOFT = new Color(0x46503cff);
    private static final Color FILL = new Color(0xffad55ff);
    private static final Color TRACK = new Color(0x4c3a52ff);

    private final Kagebi game;
    private final CameraController camera = new CameraController();

    private AssetManager assets;
    private BitmapFont font;
    private TextureRegion pixel;

    private float shown;
    private boolean handedOver;

    public BootScreen(Kagebi game) {
        super(game.input());
        this.game = game;
    }

    @Override
    public void show() {
        assets = Preload.manager();
        font = game.skin().getFont("default");
        pixel = game.skin().getRegion(Assets.Ui.PIXEL);
        camera.snapTo(Cfg.VIRT_W / 2f, Cfg.VIRT_H / 2f);
    }

    @Override
    protected void step() {
        float target = assets.isFinished() ? 1f : assets.getProgress();
        shown = Math.min(target, shown + EASE);
        if (shown >= 1f && steps() >= MIN_STEPS && !handedOver) {
            handedOver = true;
            // set() rather than push(): nothing should ever come back here, and
            // leaving a loading screen at the bottom of the stack would keep
            // its camera and font references alive for the whole session.
            stack().set(new MainMenuScreen(game));
        }
    }

    @Override
    public void render(float delta) {
        // update() is what drives the loader; a frame that runs no simulation
        // step must still make progress, or a slow machine never finishes.
        assets.update(16);

        ScreenUtils.clear(0.05f, 0.04f, 0.07f, 1f);
        camera.apply();
        SpriteBatch batch = game.batch();
        batch.setProjectionMatrix(camera.camera().combined);
        batch.begin();

        int px = (Cfg.VIRT_W - PANEL_W) / 2;
        int py = (Cfg.VIRT_H - PANEL_H) / 2;
        game.skin().getDrawable(Assets.Ui.PANEL_2).draw(batch, px, py, PANEL_W, PANEL_H);

        // Nine pixels under the top border: the panel art is seven of them,
        // and the line box already holds the rows a tone mark needs.
        batch.setColor(INK);
        Hud.centred(batch, font, "KAGEBI", Cfg.VIRT_W / 2f, py + PANEL_H - 9);
        batch.setColor(SOFT);
        Hud.centred(batch, font, game.i18n().get("game.title"),
                    Cfg.VIRT_W / 2f, py + PANEL_H - 9 - Hud.LINE);

        // Clear of the subtitle's descenders - Ngọn carries a dot below the o
        // and a g below that - and as far above the bottom border.
        int bx = (Cfg.VIRT_W - BAR_W) / 2;
        int by = py + 14;
        batch.setColor(TRACK);
        batch.draw(pixel, bx, by, BAR_W, BAR_H);
        batch.setColor(FILL);
        batch.draw(pixel, bx, by, Math.round(BAR_W * shown), BAR_H);
        batch.setColor(Color.WHITE);

        batch.end();
    }

    @Override
    public void resize(int width, int height) {
        camera.resize(width, height);
    }
}
