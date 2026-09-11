package com.kagebi;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.BufferUtils;
import com.badlogic.gdx.utils.ScreenUtils;

/**
 * Entry point. Owns the objects that outlive any single screen.
 *
 * <p>Screens are added in later milestones; right now it boots straight into a
 * smoke screen that proves the asset pipeline renders end to end.
 */
public class Kagebi extends Game {

    private SpriteBatch batch;

    /** Frames to render before saving a screenshot and quitting; -1 to disable. */
    private final int screenshotAfterFrames;
    private final String screenshotPath;
    private int framesRendered;

    public Kagebi() {
        this(-1, null);
    }

    /**
     * Screenshot mode. Art direction is the one thing that cannot be unit
     * tested, so the game can render a few frames, write a PNG and exit,
     * letting a build step or a reviewer look at what actually shipped.
     */
    public Kagebi(int screenshotAfterFrames, String screenshotPath) {
        this.screenshotAfterFrames = screenshotAfterFrames;
        this.screenshotPath = screenshotPath;
    }

    public SpriteBatch batch() {
        return batch;
    }

    @Override
    public void render() {
        super.render();
        if (screenshotAfterFrames >= 0 && ++framesRendered >= screenshotAfterFrames) {
            saveScreenshot(screenshotPath);
            Gdx.app.exit();
        }
    }

    private static void saveScreenshot(String path) {
        int w = Gdx.graphics.getBackBufferWidth();
        int h = Gdx.graphics.getBackBufferHeight();
        byte[] pixels = ScreenUtils.getFrameBufferPixels(0, 0, w, h, true);
        Pixmap pixmap = new Pixmap(w, h, Pixmap.Format.RGBA8888);
        BufferUtils.copy(pixels, 0, pixmap.getPixels(), pixels.length);
        PixmapIO.writePNG(Gdx.files.absolute(new java.io.File(path).getAbsolutePath()), pixmap);
        pixmap.dispose();
        Gdx.app.log("kagebi", "screenshot -> " + path + " (" + w + "x" + h + ")");
    }

    @Override
    public void create() {
        Gdx.app.log("kagebi", "working dir: " + new java.io.File(".").getAbsolutePath());
        batch = new SpriteBatch();
        setScreen(new SmokeScreen(this));
    }

    @Override
    public void dispose() {
        if (getScreen() != null) {
            getScreen().dispose();
        }
        batch.dispose();
    }
}
