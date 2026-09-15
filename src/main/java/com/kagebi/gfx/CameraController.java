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
 * <p>"Whole pixels" means whole pixels of the view, not of the map. Zoomed out
 * to 3x, one step of the view is three map pixels, so the position is rounded
 * to multiples of three: the picture then moves a whole view pixel at a time
 * and samples the art the same way every frame. Zoomed in to half, it is
 * rounded to halves.
 *
 * <p>Fractional positions are still tracked internally. Rounding the
 * <em>stored</em> position instead makes a slow follow quantise to nothing:
 * a camera moving 0.4 px a step would round to the same integer forever.
 *
 * <p>Two modes, because the game has two kinds of space. The dungeon snaps a
 * room at a time - {@link #snapTo} - and slides between them; the village is
 * larger than the screen, so it {@link #follow}s the player inside the map
 * bounds, and may be zoomed.
 */
public final class CameraController {

    /** The zoom levels short of a whole map: close, as drawn, and two further out. */
    static final float[] LADDER = {0.5f, 1f, 2f, 3f};
    /** Fixed steps a change of zoom takes: a sixth of a second. */
    static final int ZOOM_STEPS = 10;

    private final OrthographicCamera camera = new OrthographicCamera();
    private final PixelViewport viewport;

    /** Where the camera wants to be, in virtual pixels, before rounding. */
    private float x;
    private float y;

    private float shakeX;
    private float shakeY;

    /** Map pixels per view pixel: what is drawn now, where it is going, and where it came from. */
    private float zoom = 1f;
    private float targetZoom = 1f;
    private float fromZoom = 1f;
    private int zoomStep;

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

    // ---- zoom --------------------------------------------------------------

    /** The zoom that fits a whole map of this pixel size on the screen, and never closer than 1. */
    public static float fitZoom(float mapWidth, float mapHeight) {
        return Math.max(1f, Math.max(mapWidth / Cfg.VIRT_W, mapHeight / Cfg.VIRT_H));
    }

    /** The zoom being drawn this frame. */
    public float zoom() {
        return zoom;
    }

    /** The zoom being eased towards; the same as {@link #zoom} once it arrives. */
    public float targetZoom() {
        return targetZoom;
    }

    /**
     * Moves the target {@code notches} levels further out, or in for a negative
     * count, on the ladder that ends at {@code fit}. Levels at or past the fit
     * are dropped: past it, all that is added is sky.
     *
     * @return whether the target changed
     */
    public boolean zoomBy(int notches, float fit) {
        if (notches == 0) {
            return false;
        }
        float[] levels = levels(fit);
        int at = nearest(levels, targetZoom);
        float to = levels[MathUtils.clamp(at + notches, 0, levels.length - 1)];
        if (to == targetZoom) {
            return false;
        }
        fromZoom = zoom;
        targetZoom = to;
        zoomStep = 0;
        return true;
    }

    /** One fixed step of easing towards the target zoom. */
    public void stepZoom() {
        if (zoom == targetZoom) {
            return;
        }
        zoomStep++;
        if (zoomStep >= ZOOM_STEPS) {
            zoom = targetZoom;
            return;
        }
        // Eased by ratio rather than by difference, so going from 1 to 3 feels
        // like going from 3 to 1 backwards.
        float t = MathUtils.sin(zoomStep / (float) ZOOM_STEPS * MathUtils.HALF_PI);
        zoom = fromZoom * (float) Math.pow(targetZoom / fromZoom, t);
    }

    /** Arrives at the target zoom at once, for a screen opening already zoomed. */
    public void snapZoom() {
        zoom = targetZoom;
    }

    static float[] levels(float fit) {
        int n = 0;
        for (float level : LADDER) {
            if (level < fit - 0.01f) {
                n++;
            }
        }
        float[] out = new float[n + 1];
        System.arraycopy(LADDER, 0, out, 0, n);
        out[n] = fit;
        return out;
    }

    private static int nearest(float[] levels, float zoom) {
        int best = 0;
        for (int i = 1; i < levels.length; i++) {
            if (Math.abs(levels[i] - zoom) < Math.abs(levels[best] - zoom)) {
                best = i;
            }
        }
        return best;
    }

    // ---- position ----------------------------------------------------------

    /**
     * Centres on a point, clamped so the view never leaves a map of the given
     * pixel size. A map smaller than the view on an axis is centred on it
     * instead, which is what stops a 320-wide room from jittering between two
     * equally valid clamps - and what holds a whole island still when zoomed
     * out far enough to see all of it.
     */
    public void follow(float px, float py, float mapWidth, float mapHeight) {
        float viewW = Cfg.VIRT_W * zoom;
        float viewH = Cfg.VIRT_H * zoom;
        x = mapWidth <= viewW ? mapWidth / 2f
            : MathUtils.clamp(px, viewW / 2f, mapWidth - viewW / 2f);
        y = mapHeight <= viewH ? mapHeight / 2f
            : MathUtils.clamp(py, viewH / 2f, mapHeight - viewH / 2f);
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
        camera.zoom = zoom;
        camera.position.set(snap(cx + shakeX), snap(cy + shakeY), 0f);
        viewport.apply();
        camera.update();
    }

    /** A map coordinate rounded to a whole pixel of the view. */
    float snap(float v) {
        return Math.round(v / zoom) * zoom;
    }

    public void resize(int screenWidth, int screenHeight) {
        // centerCamera false: this class owns the position, and letting the
        // viewport recentre it would snap the view back to the map origin
        // every time the window is resized.
        viewport.update(screenWidth, screenHeight, false);
    }
}
