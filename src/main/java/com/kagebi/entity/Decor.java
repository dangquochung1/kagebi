package com.kagebi.entity;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.kagebi.gfx.Anim;

/**
 * A torch or a banner on a wall: something that animates and nothing else.
 *
 * <p>Deliberately <em>not</em> an {@link Entity}. An Entity carries hit points,
 * i-frames, a knockback vector, a body box and a faction, and a wall torch has
 * none of those - inheriting them would mean six fields that must stay
 * meaningless and a {@code faction()} that has to answer a question nobody
 * should be asking it.
 *
 * <p>Not being an Entity also means it is not in the depth sort, which is
 * correct rather than a shortcut: every one of these sits on a cell the room's
 * perimeter already made solid, so no actor can ever stand on the same pixels
 * and there is no ordering to get right. The world draws them before the
 * actors, once, and that is the whole rule.
 */
public final class Decor {

    /** Steps per frame. Six is 10 flickers a second: alive, not frantic. */
    public static final int FLICKER_STEPS = 6;

    private final Anim anim;
    private final float x;
    private final float y;
    /** Offset into the loop, so a wall of torches does not pulse in unison. */
    private final int phase;
    private final boolean flip;

    public Decor(Anim anim, float x, float y, int phase, boolean flip) {
        this.anim = anim;
        this.x = x;
        this.y = y;
        this.phase = phase;
        this.flip = flip;
    }

    public float x() {
        return x;
    }

    public float y() {
        return y;
    }

    /** Drawn centred on its cell, which is how the generator places it. */
    public void draw(SpriteBatch batch, int steps) {
        if (anim == null) {
            return;
        }
        TextureRegion f = anim.frame(null, steps + phase);
        if (f == null) {
            return;
        }
        int w = f.getRegionWidth();
        int h = f.getRegionHeight();
        int drawX = Math.round(x - w / 2f);
        int drawY = Math.round(y - h / 2f);
        if (flip) {
            batch.draw(f, drawX + w, drawY, -w, h);
        } else {
            batch.draw(f, drawX, drawY, w, h);
        }
    }
}
