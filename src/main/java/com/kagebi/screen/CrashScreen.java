package com.kagebi.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.Align;
import com.kagebi.Cfg;
import com.kagebi.Kagebi;
import com.kagebi.assets.Assets;
import com.kagebi.gfx.CameraController;

/**
 * What the player sees instead of the window disappearing.
 *
 * <p>Shown when something escaped {@code render()}. The state behind it cannot
 * be trusted - whatever threw was halfway through a job - so this screen offers
 * no way back into the game, only a way out of it, and the stack it replaces is
 * gone by the time it appears.
 *
 * <p><b>It leans on as little as possible.</b> No i18n, no atlas region, no
 * simulation: only the font and a solid fill, both of which were working a
 * frame ago. A crash screen that needs the thing that just broke is not a crash
 * screen. For the same reason the text is English rather than translated - the
 * file path is the payload, and it has to survive a broken language load.
 */
public final class CrashScreen extends GameScreen {

    private static final Color PAPER = new Color(0x1a1016ff);
    private static final Color TITLE = new Color(0xd94a3aff);
    private static final Color BODY = new Color(0xe8cfa9ff);
    private static final Color SOFT = new Color(0xab8a6eff);

    private final Kagebi game;
    private final CameraController camera = new CameraController();
    private final String headline;
    private final String logPath;

    private BitmapFont font;
    private InputProcessor inputs;

    public CrashScreen(Kagebi game, String headline, String logPath) {
        this.game = game;
        this.headline = headline == null ? "unknown error" : headline;
        this.logPath = logPath;
    }

    @Override
    public InputProcessor inputProcessor() {
        return inputs;
    }

    @Override
    public void show() {
        font = game.skin().getFont("default");
        camera.snapTo(Cfg.VIRT_W / 2f, Cfg.VIRT_H / 2f);
        // Its own adapter rather than the action map: the map is configurable,
        // and a player who rebound escape must still be able to leave.
        inputs = new InputAdapter() {
            @Override
            public boolean keyDown(int keycode) {
                if (keycode == Keys.ESCAPE || keycode == Keys.ENTER || keycode == Keys.SPACE) {
                    Gdx.app.exit();
                    return true;
                }
                return false;
            }
        };
    }

    @Override
    public void render(float delta) {
        camera.apply();
        SpriteBatch batch = game.batch();
        batch.setProjectionMatrix(camera.camera().combined);
        batch.begin();

        batch.setColor(PAPER);
        batch.draw(game.skin().getRegion(Assets.Ui.PIXEL), 0, 0, Cfg.VIRT_W, Cfg.VIRT_H);
        batch.setColor(Color.WHITE);

        font.setColor(TITLE);
        font.draw(batch, "The game hit a problem", 0, 150, Cfg.VIRT_W, Align.center, false);

        // Wrapped, not clipped: the exception message is the one thing here
        // worth reading in full, and it is never a predictable length.
        font.setColor(BODY);
        font.draw(batch, headline, 16, 128, Cfg.VIRT_W - 32, Align.left, true);

        font.setColor(SOFT);
        if (logPath != null) {
            font.draw(batch, "Details written to:", 16, 66, Cfg.VIRT_W - 32, Align.left, false);
            font.draw(batch, logPath, 16, 52, Cfg.VIRT_W - 32, Align.left, true);
        } else {
            font.draw(batch, "The details could not be written to a file.",
                16, 66, Cfg.VIRT_W - 32, Align.left, true);
        }
        font.draw(batch, "Press Esc to close", 0, 22, Cfg.VIRT_W, Align.center, false);
        font.setColor(Color.WHITE);

        batch.end();
    }

    @Override
    public void resize(int width, int height) {
        camera.resize(width, height);
    }
}
