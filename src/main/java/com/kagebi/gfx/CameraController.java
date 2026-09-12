package com.kagebi.gfx;

import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.MathUtils;
import com.kagebi.Cfg;

/**
 * The world camera, and the one rule it may never break.
 *
 * <p><b>The camera is written to the GPU on whole pixels only.</b> At 4x
 * magnification half a virtual pixel of offset puts every edge in the scene on
 * a screen-pixel boundary one frame and off it the next, and the whole image
 * crawls. It is the single most common way pixel art goes wrong, and it is
 * invisible in a still screenshot - which is why the rounding lives here, in
 * one place, rather than at each call site where it can be forgotten.
 *
 * <p>Fractional positions are still tracked internally. Rounding the
 * <em>stored</em> position instead makes a slow follow quantise to nothing:
 * a camera moving 0.4 px a step would round to the same integer forever.
 *
 * <p>Two modes, because the game has two kinds of space. The dungeon snaps a
 * room at a time - {@link #snapTo} - and slides between them; the village is
 * larger than the screen, so it {@link #follow}s the player inside the map
 * bounds.
 */
public final class CameraController {

    private final OrthographicCamera camera = new OrthographicCamera();
    private final PixelViewport viewport;

    /** Where the camera wants to be, in virtual pixels, before rounding. */
    private float x;
    private float y;

    private float shakeX;
    private float shakeY;

    public CameraController() {
        camera.setToOrtho(false, Cfg.VIRT_W, Cfg.VIRT_H);
        viewport = new PixelViewport(Cfg.VIRT_W, Cfg.VIRT_H, camera);
        x = Cfg.VIRT_W / 2f;
        y = Cfg.VIRT_H / 2f;
    }

    public OrthographicCamera camera() {
        return camera;
    }

    public PixelViewport viewport() {
        return viewport;
    }

    /** Jumps the centre of the view, with no interpolation. */
    public void snapTo(float cx, float cy) {
        x = cx;
        y = cy;
    }

    public float x() {
        return x;
    }

    public float y() {
        return y;
    }

    /**
     * Centres on a point, clamped so the view never leaves a map of the given
     * pixel size. A map smaller than the screen on an axis is centred on it
     * instead, which is what stops a 320-wide room from jittering between two
     * equally valid clamps.
     */
    public void follow(float px, float py, float mapWidth, float mapHeight) {
        float halfW = Cfg.VIRT_W / 2f;
        float halfH = Cfg.VIRT_H / 2f;
        x = mapWidth <= Cfg.VIRT_W ? mapWidth / 2f
            : MathUtils.clamp(px, halfW, mapWidth - halfW);
        y = mapHeight <= Cfg.VIRT_H ? mapHeight / 2f
            : MathUtils.clamp(py, halfH, mapHeight - halfH);
    }

    /**
     * Offsets the view by up to {@code amount} pixels in a random direction for
     * this frame only. The world reports an already-faded amount, so the shape
     * of the decay is combat's business and the jitter is the camera's.
     */
    public void shake(float amount) {
        if (amount <= 0f) {
            shakeX = 0f;
            shakeY = 0f;
            return;
        }
        float angle = MathUtils.random(MathUtils.PI2);
        shakeX = MathUtils.cos(angle) * amount;
        shakeY = MathUtils.sin(angle) * amount;
    }

    /**
     * Binds the viewport and pushes the rounded position to the camera. Call
     * once before drawing the world; {@code camera().combined} is only correct
     * after it.
     */
    public void apply() {
        applyAt(x, y);
    }

    /**
     * As {@link #apply}, from a position other than the tracked one. The room
     * slide draws the same two maps from two places in one frame, and neither
     * of them is where the camera is going to end up.
     */
    public void applyAt(float cx, float cy) {
        camera.position.set(Math.round(cx + shakeX), Math.round(cy + shakeY), 0f);
        viewport.apply();
        camera.update();
    }

    public void resize(int screenWidth, int screenHeight) {
        // centerCamera false: this class owns the position, and letting the
        // viewport recentre it would snap the view back to the map origin
        // every time the window is resized.
        viewport.update(screenWidth, screenHeight, false);
    }
}
