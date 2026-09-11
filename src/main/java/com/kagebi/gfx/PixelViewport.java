package com.kagebi.gfx;

import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.utils.viewport.Viewport;

/**
 * A viewport that only ever scales by a whole number, then centres the result.
 *
 * libGDX ships FitViewport, which scales to fractional factors. For pixel art
 * that is fatal: at 2.6x some source pixels land on 3 screen pixels and their
 * neighbours on 2, so straight lines visibly wobble. Here we take the largest
 * integer factor that fits and letterbox whatever is left over.
 */
public class PixelViewport extends Viewport {

    private final int baseWidth;
    private final int baseHeight;
    private int scale = 1;

    public PixelViewport(int baseWidth, int baseHeight, Camera camera) {
        this.baseWidth = baseWidth;
        this.baseHeight = baseHeight;
        setWorldSize(baseWidth, baseHeight);
        setCamera(camera);
    }

    @Override
    public void update(int screenWidth, int screenHeight, boolean centerCamera) {
        scale = Math.max(1, Math.min(screenWidth / baseWidth, screenHeight / baseHeight));
        int viewWidth = baseWidth * scale;
        int viewHeight = baseHeight * scale;
        setScreenBounds((screenWidth - viewWidth) / 2, (screenHeight - viewHeight) / 2,
                        viewWidth, viewHeight);
        setWorldSize(baseWidth, baseHeight);
        apply(centerCamera);
    }

    /** Current whole-number magnification; the UI layer renders text at this size. */
    public int scale() {
        return scale;
    }
}
