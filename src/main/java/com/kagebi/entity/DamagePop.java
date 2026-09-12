package com.kagebi.entity;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.kagebi.ui.Hud;

/**
 * A damage number that rises off whatever was just hit and fades.
 *
 * <p>Before this, a hit was a white flash and a knockback: the player could see
 * that something connected and never how hard. Every swing of a given weapon on
 * a given enemy dealt the identical number, the crit roll was discarded the
 * instant it was made, and a relic that raised damage by a tenth changed
 * nothing anybody could perceive.
 *
 * <p>Three cues, in order of how much they carry:
 *
 * <ul>
 * <li><b>Colour</b> separates an ordinary hit from a critical one. That is the
 *     only distinction the numbers alone cannot make, because a big ordinary
 *     hit and a small crit print the same figure.
 * <li><b>Size</b> tracks the number, so a build that doubles damage looks
 *     different rather than merely reading differently.
 * <li><b>Rise and fade</b> keeps two hits in the same place from stacking into
 *     an unreadable smear.
 * </ul>
 *
 * <p>Drawn in world space, after every sprite, so a number is never hidden
 * behind the thing it describes.
 */
public final class DamagePop {

    /** Steps on screen. 45 is three quarters of a second: read, then gone. */
    public static final int LIFE = 45;
    /** Pixels risen over that life. Far enough to read as leaving the body. */
    public static final float RISE = 14f;
    /**
     * Damage from which a number is drawn at double size.
     *
     * <p>Forty is about six katana swings' worth, so it marks a hit that came
     * from a built-up character rather than from the starting kit - which is
     * the distinction worth drawing the player's eye to.
     */
    public static final int BIG = 40;

    private static final Color NORMAL = new Color(0x9fd8ffff);
    private static final Color CRIT = new Color(0xff5a4aff);

    private final int amount;
    private final boolean crit;
    private final float x;
    private final float startY;
    private int steps;

    public DamagePop(int amount, boolean crit, float x, float y) {
        this.amount = amount;
        this.crit = crit;
        this.x = x;
        this.startY = y;
    }

    public boolean done() {
        return steps >= LIFE;
    }

    public void step() {
        steps++;
    }

    /**
     * @param font the HUD font; this draws in world space, but the font does
     *             not care which projection is loaded
     */
    public void draw(SpriteBatch batch, BitmapFont font) {
        if (done()) {
            return;
        }
        float life = steps / (float) LIFE;
        // Fade only in the last third. Fading from the first step makes the
        // number hardest to read at the moment it appears, which is backwards.
        float alpha = life < 0.66f ? 1f : 1f - (life - 0.66f) / 0.34f;

        // Whole-number scale only. A bitmap font at 1.175x resamples a 9px
        // glyph onto a fractional grid and drops pixels out of it; two sizes is
        // all a 9px face can carry, so a hit is either ordinary or heavy and
        // the colour does the rest of the work.
        float scale = amount >= BIG ? 2f : 1f;
        String text = Integer.toString(amount);

        Color was = batch.getColor();
        float r = was.r;
        float g = was.g;
        float b = was.b;
        float a = was.a;
        Color ink = crit ? CRIT : NORMAL;
        batch.setColor(ink.r, ink.g, ink.b, alpha);
        font.getData().setScale(scale);
        // Rise eases off rather than travelling at a constant rate, so the
        // number is legible where it appears and drifts away afterwards.
        float rise = RISE * (float) Math.sqrt(life);
        Hud.centred(batch, font, text, Math.round(x), Math.round(startY + rise));
        font.getData().setScale(1f);
        batch.setColor(r, g, b, a);
    }
}
