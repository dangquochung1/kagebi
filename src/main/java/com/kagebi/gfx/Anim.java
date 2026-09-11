package com.kagebi.gfx;

import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.kagebi.Dir;

/**
 * One animation, sliced out of a packed sheet.
 *
 * <p>Two sheet shapes exist in the asset packs, and both are handled here so
 * that no caller has to remember which is which:
 *
 * <ul>
 * <li><b>Directional</b> - 4 columns (one per {@link Dir}) by N rows of frames.
 *     Every monster sheet and every player animation is this shape.
 * <li><b>Strip</b> - a single row of frames, used by every boss: each boss ships
 *     one file per animation at a size specific to that boss, so the frame size
 *     is derived from the region height rather than assumed.
 * </ul>
 *
 * <p>Timing is counted in fixed simulation steps rather than seconds. That is
 * not a stylistic choice: attack windup and i-frames are counted in steps
 * elsewhere, and an animation measured in seconds would drift out of phase with
 * them on a machine that misses a frame.
 */
public final class Anim {

    /** A sensible default: 8 steps a frame is about 7.5 frames a second. */
    public static final int DEFAULT_STEPS_PER_FRAME = 8;

    private final TextureRegion[][] frames;   // [direction][frame]
    private final int stepsPerFrame;
    private final boolean looping;

    private Anim(TextureRegion[][] frames, int stepsPerFrame, boolean looping) {
        this.frames = frames;
        this.stepsPerFrame = stepsPerFrame;
        this.looping = looping;
    }

    /**
     * Slices a 4-column directional sheet.
     *
     * @param cell the size of one frame in pixels; 16 for monsters and NPCs,
     *             32 for the player
     */
    public static Anim directional(TextureAtlas atlas, String region, int cell,
                                   int stepsPerFrame, boolean looping) {
        TextureRegion sheet = require(atlas, region);
        int columns = sheet.getRegionWidth() / cell;
        int rows = sheet.getRegionHeight() / cell;
        if (columns != Dir.ALL.length) {
            throw new IllegalArgumentException(region + " is " + columns
                + " columns wide at cell " + cell + "; a directional sheet has "
                + Dir.ALL.length);
        }
        TextureRegion[][] out = new TextureRegion[columns][rows];
        for (int c = 0; c < columns; c++) {
            for (int r = 0; r < rows; r++) {
                out[c][r] = new TextureRegion(sheet, c * cell, r * cell, cell, cell);
            }
        }
        return new Anim(out, stepsPerFrame, looping);
    }

    /** Slices a single-row strip; frame size is taken from the region height. */
    public static Anim strip(TextureAtlas atlas, String region,
                             int stepsPerFrame, boolean looping) {
        TextureRegion sheet = require(atlas, region);
        int cell = sheet.getRegionHeight();
        int count = sheet.getRegionWidth() / cell;
        TextureRegion[][] out = new TextureRegion[1][count];
        for (int i = 0; i < count; i++) {
            out[0][i] = new TextureRegion(sheet, i * cell, 0, cell, cell);
        }
        return new Anim(out, stepsPerFrame, looping);
    }

    private static TextureRegion require(TextureAtlas atlas, String region) {
        TextureRegion r = atlas.findRegion(region);
        if (r == null) {
            // Worth failing loudly: a missing region otherwise surfaces as an
            // invisible entity three screens into a run.
            throw new IllegalArgumentException("no atlas region '" + region + "'");
        }
        return r;
    }

    public boolean directional() {
        return frames.length > 1;
    }

    public int frameCount() {
        return frames[0].length;
    }

    /** Total length in fixed steps; what a state machine waits on. */
    public int durationSteps() {
        return frameCount() * stepsPerFrame;
    }

    /** The frame at a given age in steps. A strip ignores {@code facing}. */
    public TextureRegion frame(Dir facing, int steps) {
        int index = steps / stepsPerFrame;
        int count = frameCount();
        index = looping ? index % count : Math.min(index, count - 1);
        return frames[directional() ? facing.ordinal() : 0][index];
    }

    /** True once a non-looping animation has shown its last frame. */
    public boolean finished(int steps) {
        return !looping && steps >= durationSteps();
    }
}
