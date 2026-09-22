package com.kagebi.ui;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.kagebi.Cfg;

/**
 * An arrow that says which way to walk.
 *
 * <p>The island is thirty screens of map and a job that says "go and see the
 * herbalist" is no help to someone who has not learnt where she stands. This
 * is the answer to "where now", and it is deliberately the only one: one
 * tracked job at a time, one arrow.
 *
 * <p><b>It orbits the player rather than pinning to the edge of the screen.</b>
 * An edge marker tells you a direction relative to the frame, which is a
 * different question from the one being asked - and at the zoomed-out view,
 * where the whole island fits, every edge marker points at the middle. A ring
 * around the player reads the same at any zoom because it is drawn in screen
 * space around a point that is always on screen.
 *
 * <p>It is hidden once the target is close enough to see, which is the moment
 * it stops being useful and starts being clutter.
 */
public final class Waypoint {

    /** How far from the player's feet the arrow floats, in screen pixels. */
    private static final float ORBIT = 26f;
    /** Inside this, the player can see the thing; the arrow would be noise. */
    private static final float ARRIVED = 40f;

    private Waypoint() {}

    /**
     * Draws the arrow, if the target is far enough away to need one.
     *
     * @param arrow the art, drawn pointing up; rotated to the heading
     * @param fromX where the player is, in screen pixels
     * @param toX   where the target is, in the same space
     * @return whether anything was drawn
     */
    public static boolean draw(SpriteBatch batch, TextureRegion arrow,
                               float fromX, float fromY, float toX, float toY) {
        if (arrow == null) {
            return false;
        }
        float dx = toX - fromX;
        float dy = toY - fromY;
        float distance = (float) Math.sqrt(dx * dx + dy * dy);
        if (distance < ARRIVED) {
            return false;
        }
        float x = fromX + dx / distance * ORBIT;
        float y = fromY + dy / distance * ORBIT;
        // Off the top or the side of the screen it would be invisible, so it is
        // held just inside: the arrow's job is to be seen.
        x = Math.max(6f, Math.min(Cfg.VIRT_W - 6f, x));
        y = Math.max(6f, Math.min(Cfg.VIRT_H - 6f, y));
        float w = arrow.getRegionWidth();
        float h = arrow.getRegionHeight();
        // The art points up, so its zero is north and the heading is measured
        // from there rather than from east.
        float degrees = (float) Math.toDegrees(Math.atan2(dy, dx)) - 90f;
        batch.draw(arrow, Math.round(x - w / 2f), Math.round(y - h / 2f),
                   w / 2f, h / 2f, w, h, 1f, 1f, degrees);
        return true;
    }
}
