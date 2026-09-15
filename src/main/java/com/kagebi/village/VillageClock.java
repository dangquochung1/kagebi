package com.kagebi.village;

import com.kagebi.save.VillageState;

/**
 * The village's time: seconds of play, counted on every screen.
 *
 * <p>{@code Kagebi.render} advances it by each frame's delta, so it runs in the
 * menus and the dungeon as much as on the island, and stops when the game is
 * closed. A frame's delta is capped: the first frame after a window is dragged
 * or a debugger is paused reports whole seconds, and the village should not grow
 * a crop in a stall.
 */
public final class VillageClock {

    /** The most one frame may move the clock. Four frames a second is already a very bad frame. */
    public static final float LONGEST_FRAME = 0.25f;

    public static void advance(VillageState v, float delta) {
        // Written so that NaN, which compares false with everything, moves nothing.
        if (delta > 0f) {
            v.clock += Math.min(delta, LONGEST_FRAME);
        }
    }

    private VillageClock() {}
}
