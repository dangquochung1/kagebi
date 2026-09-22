package com.kagebi;

/** Values the whole game agrees on. Deliberately tiny: everything else is data. */
public final class Cfg {

    /**
     * What this build calls itself.
     *
     * <p>Kept in step with {@code pom.xml} by {@code VersionTest}, because two
     * places holding the same number is two places to forget one. It reaches a
     * player twice: the corner of the main menu, and the head of every crash
     * report - a trace that does not say which build produced it can only be
     * guessed at.
     */
    public static final String VERSION = "0.6.0";

    /**
     * Virtual render size. The Ninja Adventure pack ships its full-screen
     * overlays (FX/Environment/Fog.png) at exactly 320x180, which is the
     * clearest statement of the resolution the art was drawn for. Scaling up by
     * whole numbers from here keeps every pixel square: x4 = 720p, x6 = 1080p.
     */
    public static final int VIRT_W = 320;
    public static final int VIRT_H = 180;

    /** Every pack in use is built on a 16px tile grid. */
    public static final int TILE = 16;

    /**
     * Fixed simulation step. Combat, i-frames and knockback are counted in
     * steps, not seconds, so the game plays identically on a 60Hz and a 144Hz
     * display.
     */
    public static final float STEP = 1f / 60f;

    /** Guard against the spiral of death if a frame takes far too long. */
    public static final int MAX_STEPS_PER_FRAME = 5;

    private Cfg() {}
}
