package com.kagebi.screen.island;

import java.util.Random;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.Array;
import com.kagebi.Cfg;

/**
 * The island's small motions of work: goods flying from whoever gave them to
 * the player, a fish leaping at the fisher's line, and a unit hopping over the
 * head of the worker who just made it.
 *
 * <p>Everything moves in map coordinates on the fixed step, so how an effect
 * moves is testable without a screen. What is drawn where differs: a flying
 * good is a mark for the player and is drawn at the screen's size, as the "!"
 * over a worker is, so it still reads zoomed out to the whole island; a fish
 * and a hop are part of the scene and are drawn at the map's.
 */
public final class HarvestFx {

    /** Steps a good spends thrown up from where it was taken, before it heads for the player. */
    public static final int POP_STEPS = 18;
    /** Steps it then takes to reach the player, wherever they have walked to meanwhile. */
    public static final int FLY_STEPS = 24;
    /** Steps a leaping fish is out of the water. */
    public static final int JUMP_STEPS = 40;
    /** How far above the water a fish's leap peaks: over the head of a fisher standing on the jetty. */
    public static final float JUMP_HEIGHT = 34f;
    /** Steps a finished unit hops over its worker's head. */
    public static final int HOP_STEPS = 24;

    private static final float GRAVITY = 0.3f;

    /** A good on its way to the player. */
    public static final class Flyer {
        final TextureRegion icon;
        float x;
        float y;
        private float vx;
        private float vy;
        private int step;

        Flyer(TextureRegion icon, float x, float y, float vx, float vy) {
            this.icon = icon;
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
        }

        /**
         * One step towards where the player is now. Thrown up and falling for
         * {@link #POP_STEPS}, then closing on the target by a larger share of
         * what is left each step, so it speeds up as it goes and is exactly
         * there on the last one however the target moved.
         *
         * @return true on the step it arrives
         */
        boolean step(float targetX, float targetY) {
            step++;
            if (step <= POP_STEPS) {
                x += vx;
                y += vy;
                vy -= GRAVITY;
                return false;
            }
            int left = POP_STEPS + FLY_STEPS - step + 1;
            x += (targetX - x) / left;
            y += (targetY - y) / left;
            return left == 1;
        }
    }

    /** A fish out of the water, from one spot and back into it. */
    public static final class Jump {
        final TextureRegion icon;
        final float x;
        final float water;
        /** 1 when the fish leaps towards increasing x, -1 the other way. */
        final int facing;
        int step;

        Jump(TextureRegion icon, float x, float water, int facing) {
            this.icon = icon;
            this.x = x;
            this.water = water;
            this.facing = facing;
        }

        float t() {
            return Math.min(1f, step / (float) JUMP_STEPS);
        }

        /** Height over the water: a parabola that peaks at {@link #JUMP_HEIGHT} halfway. */
        float y() {
            float t = t();
            return water + 4f * JUMP_HEIGHT * t * (1f - t);
        }

        /** Nose up as it leaves the water, level at the top, nose down as it goes back in. */
        float degrees() {
            return facing * (90f - 180f * t());
        }

        boolean done() {
            return step >= JUMP_STEPS;
        }
    }

    /** A finished unit rising a little over its worker's head and fading. */
    public static final class Hop {
        final TextureRegion icon;
        final float x;
        final float y;
        int step;

        Hop(TextureRegion icon, float x, float y) {
            this.icon = icon;
            this.x = x;
            this.y = y;
        }

        float rise() {
            float t = Math.min(1f, step / (float) HOP_STEPS);
            return 10f * (float) Math.sin(t * Math.PI / 2);
        }

        float alpha() {
            float t = step / (float) HOP_STEPS;
            return t < 0.6f ? 1f : Math.max(0f, 1f - (t - 0.6f) / 0.4f);
        }

        boolean done() {
            return step >= HOP_STEPS;
        }
    }

    private final Array<Flyer> flyers = new Array<>();
    private final Array<Jump> jumps = new Array<>();
    private final Array<Hop> hops = new Array<>();
    private final Random random;

    /** Goods that reached the player on the last step, and fish that went back in. */
    private int arrived;
    private int splashed;

    public HarvestFx(long seed) {
        random = new Random(seed);
    }

    /** A good thrown up from a point, to fly to the player. */
    public void fly(TextureRegion icon, float x, float y) {
        float vx = (random.nextFloat() * 2f - 1f) * 1.2f;
        float vy = 2.2f + random.nextFloat() * 1.2f;
        flyers.add(new Flyer(icon, x, y, vx, vy));
    }

    /** A fish leaping from the water at a point, towards increasing x or away from it. */
    public void leap(TextureRegion icon, float x, float water, boolean towardsRight) {
        jumps.add(new Jump(icon, x, water, towardsRight ? 1 : -1));
    }

    /** A unit rising over a worker whose head is at this point. */
    public void hop(TextureRegion icon, float x, float headY) {
        hops.add(new Hop(icon, x, headY));
    }

    /** Moves everything one step, the flyers towards the player's middle. */
    public void step(float playerX, float playerY) {
        arrived = 0;
        splashed = 0;
        for (int i = flyers.size - 1; i >= 0; i--) {
            if (flyers.get(i).step(playerX, playerY)) {
                flyers.removeIndex(i);
                arrived++;
            }
        }
        for (int i = jumps.size - 1; i >= 0; i--) {
            Jump j = jumps.get(i);
            j.step++;
            if (j.done()) {
                jumps.removeIndex(i);
                splashed++;
            }
        }
        for (int i = hops.size - 1; i >= 0; i--) {
            Hop h = hops.get(i);
            h.step++;
            if (h.done()) {
                hops.removeIndex(i);
            }
        }
    }

    /** How many goods reached the player on the last step. */
    public int arrived() {
        return arrived;
    }

    /** How many fish went back into the water on the last step. */
    public int splashed() {
        return splashed;
    }

    public boolean idle() {
        return flyers.size == 0 && jumps.size == 0 && hops.size == 0;
    }

    /** Whether any good is still on its way to the player. */
    public boolean flying() {
        return flyers.size > 0;
    }

    Array<Flyer> flyers() {
        return flyers;
    }

    Array<Jump> jumps() {
        return jumps;
    }

    Array<Hop> hops() {
        return hops;
    }

    // ---- drawing -----------------------------------------------------------

    /** The fish and the hops, in the world camera's projection. */
    public void drawWorld(SpriteBatch batch) {
        for (Jump j : jumps) {
            if (j.icon == null) {
                continue;
            }
            float w = j.icon.getRegionWidth();
            float h = j.icon.getRegionHeight();
            batch.draw(j.icon, Math.round(j.x - w / 2f), Math.round(j.y() - h / 2f), w / 2f, h / 2f,
                       w, h, 1f, 1f, j.degrees());
        }
        for (Hop h : hops) {
            if (h.icon == null) {
                continue;
            }
            batch.setColor(1f, 1f, 1f, h.alpha());
            batch.draw(h.icon, Math.round(h.x - h.icon.getRegionWidth() / 2f), Math.round(h.y + h.rise()));
        }
        batch.setColor(Color.WHITE);
    }

    /**
     * The flying goods, in the screen's projection, each at the screen point
     * over its map position and ringed in white: the outlines first, all in
     * one batch through the silhouette shader, then the pictures on top.
     */
    public void drawScreen(SpriteBatch batch, OrthographicCamera world, ShaderProgram silhouette) {
        if (flyers.size == 0) {
            return;
        }
        if (silhouette != null) {
            batch.setShader(silhouette);
            for (Flyer f : flyers) {
                if (f.icon == null) {
                    continue;
                }
                float x = left(world, f);
                float y = bottom(world, f);
                batch.draw(f.icon, x - 1, y);
                batch.draw(f.icon, x + 1, y);
                batch.draw(f.icon, x, y - 1);
                batch.draw(f.icon, x, y + 1);
            }
            batch.setShader(null);
        }
        for (Flyer f : flyers) {
            if (f.icon != null) {
                batch.draw(f.icon, left(world, f), bottom(world, f));
            }
        }
    }

    private static float left(OrthographicCamera cam, Flyer f) {
        return Math.round((f.x - cam.position.x) / cam.zoom + Cfg.VIRT_W / 2f - f.icon.getRegionWidth() / 2f);
    }

    private static float bottom(OrthographicCamera cam, Flyer f) {
        return Math.round((f.y - cam.position.y) / cam.zoom + Cfg.VIRT_H / 2f - f.icon.getRegionHeight() / 2f);
    }
}
