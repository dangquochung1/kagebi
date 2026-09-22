package com.kagebi.ui;

import com.badlogic.gdx.math.Vector2;
import com.kagebi.gfx.CameraController;
import com.kagebi.input.InputService;

/**
 * Hit-testing for the mouse, against rectangles in one camera's space.
 *
 * <p>The game is keyboard-first and stays that way: every screen must remain
 * playable with no mouse at all, and nothing here creates a control that only
 * a pointer can reach. What this adds is a second way to do what the keyboard
 * already does, which is the difference between a shortcut and a requirement.
 *
 * <p><b>Bind one of these to the camera whose space the rectangle is in.</b>
 * A screen that draws its world with {@code camera} and its overlay with a
 * second {@code ui} controller needs two: a badge in the corner is at a fixed
 * place on the screen, while a villager is at a place on the map, and at any
 * zoom other than 1 those two spaces disagree. Passing the wrong one produces
 * a button that works only when the player happens to be standing in the
 * middle of the island, which is the kind of bug that gets reported as
 * "sometimes it doesn't click".
 *
 * <p>Positions are resolved through {@link CameraController#toVirtual}, so
 * letterboxing is handled; see that method for why the obvious arithmetic is
 * wrong.
 */
public final class Hit {

    private final InputService input;
    private final CameraController camera;

    public Hit(InputService input, CameraController camera) {
        this.input = input;
        this.camera = camera;
    }

    /** Where the pointer is in this camera's space. Reused; read it at once. */
    public Vector2 at() {
        return camera.toVirtual(input.pointerX(), input.pointerY());
    }

    /** Whether the pointer is inside the rectangle, clicked or not. */
    public boolean over(float x, float y, float w, float h) {
        Vector2 p = at();
        return p.x >= x && p.x < x + w && p.y >= y && p.y < y + h;
    }

    /**
     * Whether the rectangle was clicked this step, spending the click if so.
     *
     * <p>Spending it is what lets a screen test its rectangles in drawing
     * order without a press landing on two of them - the topmost asks first
     * and takes it, and an overlapping one underneath sees nothing. A caller
     * that wants to look without taking should use {@link #over} and
     * {@link InputService#justClicked()} directly.
     */
    public boolean clicked(float x, float y, float w, float h) {
        if (!input.justClicked() || !over(x, y, w, h)) {
            return false;
        }
        input.consumeClick();
        return true;
    }
}
