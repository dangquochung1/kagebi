package com.kagebi.entity;

import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.kagebi.Cfg;
import com.kagebi.Dir;
import com.kagebi.combat.Faction;
import com.kagebi.combat.IFrames;
import com.kagebi.combat.Knockback;
import com.kagebi.gen.CollisionGrid;

/**
 * Everything in a room that has a position, a body and hit points.
 *
 * <p>Plain inheritance rather than an ECS, on purpose. A room holds a few dozen
 * entities of five kinds; the cache wins an ECS is for arrive at four or five
 * digits of entity count, and what is actually scarce here is the ability to
 * put a breakpoint on "the player took damage" and see one object with every
 * field on it. A component store trades that away for a win this game never
 * collects.
 *
 * <p><b>Position is the centre of the body box</b>, in y-up virtual pixels
 * relative to the room's bottom-left corner. The sprite is drawn from the
 * bottom of that box upward, so the feet and the collision box agree; drawing
 * the sprite centred instead leaves a 32px character standing ten pixels above
 * the box that actually stops them, which looks like the art is misaligned and
 * is in fact the code being wrong.
 */
public abstract class Entity implements Updatable, Damageable {

    /** How many steps of the blink cycle are visible, out of {@link #BLINK_PERIOD}. */
    private static final int BLINK_ON = 4;
    private static final int BLINK_PERIOD = 8;

    public float x;
    public float y;
    /** Collision and hurt box, centred on {@link #x}, {@link #y}. */
    public float bodyW = 12f;
    public float bodyH = 12f;

    public Dir facing = Dir.DOWN;
    public int hp;
    public int maxHp;

    public final IFrames iframes = new IFrames();
    public final Knockback shove = new Knockback();

    /** Steps since this entity was spawned; drives looping animations. */
    public int animSteps;
    /** While positive the renderer draws the sprite white. */
    public int flashSteps;
    /** Set once the world should drop this entity at the end of the step. */
    public boolean removed;

    public ActorSprites sprites;

    protected boolean moving;

    // ---- geometry --------------------------------------------------------

    @Override
    public float centreX() {
        return x;
    }

    @Override
    public float centreY() {
        return y;
    }

    @Override
    public float bodyWidth() {
        return bodyW;
    }

    @Override
    public float bodyHeight() {
        return bodyH;
    }

    /** The floor line this entity stands on: where the shadow goes. */
    public float footY() {
        return y - bodyH / 2f;
    }

    /**
     * Sort key for the draw order. Higher y draws first, so an actor lower on
     * the screen ends up in front - the one cue that makes a top-down scene
     * read as having depth at all.
     */
    public float depth() {
        return y;
    }

    // ---- state -----------------------------------------------------------

    @Override
    public int hp() {
        return hp;
    }

    @Override
    public int maxHp() {
        return maxHp;
    }

    @Override
    public boolean alive() {
        return hp > 0 && !removed;
    }

    @Override
    public boolean invulnerable() {
        return iframes.invulnerable();
    }

    @Override
    public int armour() {
        return 0;
    }

    @Override
    public abstract Faction faction();

    /** How hard this entity shrugs off a shove, 0 to 1. */
    public float knockbackResist() {
        return 0f;
    }

    // ---- movement --------------------------------------------------------

    /**
     * Moves by a pixel delta, resolving one axis at a time.
     *
     * <p>The two separate tests are the whole point. Testing the combined move
     * and rejecting it wholesale makes a character walking diagonally into a
     * wall stop dead; resolving x and y independently lets the blocked
     * component be dropped while the free one survives, which is what "sliding
     * along a wall" is. Nothing else in movement is felt as strongly.
     *
     * @return true if either axis actually moved
     */
    public boolean moveBy(CollisionGrid grid, float dx, float dy) {
        if (grid == null) {
            x += dx;
            y += dy;
            return dx != 0f || dy != 0f;
        }
        boolean moved = false;
        float halfW = bodyW / 2f;
        float halfH = bodyH / 2f;
        if (dx != 0f && !grid.overlaps(x + dx - halfW, y - halfH, bodyW, bodyH)) {
            x += dx;
            moved = true;
        }
        if (dy != 0f && !grid.overlaps(x - halfW, y + dy - halfH, bodyW, bodyH)) {
            y += dy;
            moved = true;
        }
        return moved;
    }

    /**
     * Applies the current knockback velocity for one step, then decays it.
     *
     * <p>Move first, decay second. The other order silently drops the first
     * and strongest step of every shove - a 70 px/s hit then travels 6.4px
     * rather than 7.6, and every knockback number in the game is a sixth
     * weaker than whoever tuned it believes. {@code PlayerTest} pins this.
     */
    protected void applyShove(CollisionGrid grid) {
        if (shove.active()) {
            moveBy(grid, shove.vx() * Cfg.STEP, shove.vy() * Cfg.STEP);
            shove.step();
        }
    }

    /** Counters every entity runs down, whatever else it is doing. */
    protected void stepTimers() {
        iframes.step();
        if (flashSteps > 0) {
            flashSteps--;
        }
        animSteps++;
    }

    // ---- rendering -------------------------------------------------------

    /** The frame to draw this step, or null when there is no atlas (tests). */
    public abstract TextureRegion frame();

    /**
     * Pixels between the bottom of the body box and the bottom of the frame:
     * minus the empty rows under the figure's feet, so the feet land on the
     * shadow. Measured per sheet shape in {@link ActorSprites}.
     */
    protected int spriteFootOffset() {
        return sprites == null ? 0 : -sprites.footInset;
    }

    protected boolean castsShadow() {
        return true;
    }

    /** Drawn before the sprite so the actor sits on it rather than behind it. */
    public void drawShadow(SpriteBatch batch) {
        if (sprites == null || sprites.shadow == null || !castsShadow()) {
            return;
        }
        TextureRegion s = sprites.shadow;
        // The 12x7 shadow suits a 16px body. Under a 40px frog it reads as a
        // coin on the floor, so it widens to the body and keeps its proportions.
        float w = Math.max(s.getRegionWidth(), bodyW * 0.9f);
        float h = s.getRegionHeight() * w / s.getRegionWidth();
        batch.draw(s, Math.round(x - w / 2f), Math.round(footY() - h / 2f + 1f),
            Math.round(w), Math.round(h));
    }

    /**
     * Single-facing art is mirrored for LEFT; directional sheets have a real
     * left column.
     *
     * <p>{@link #facing} is the honest answer for a sheet drawn side-on, but
     * only two of its four values mean anything to one: walking UP or DOWN,
     * there is nothing in the art to turn. Subclasses that keep a last
     * horizontal heading override this so a hero who walks left and then up
     * does not snap round to face east. See {@code Player.flipX}.
     */
    protected boolean flipX() {
        return sprites != null && sprites.singleFacing && facing == Dir.LEFT;
    }

    public void draw(SpriteBatch batch) {
        TextureRegion f = frame();
        if (f == null) {
            return;
        }
        boolean flip = flipX();
        int shift = sprites == null ? 0 : (flip ? -sprites.shiftX : sprites.shiftX);
        int drawX = Math.round(x - f.getRegionWidth() / 2f) + shift;
        int drawY = Math.round(footY() + spriteFootOffset());

        // Blinking while invulnerable is the only cue the player has that the
        // i-frames they paid a roll for are still running. Dropping frames
        // rather than tinting is what the art can afford: at 16x16 a tint is
        // three pixels of difference and reads as nothing.
        if (flashSteps <= 0 && iframes.invulnerable()
                && animSteps % BLINK_PERIOD >= BLINK_ON) {
            return;
        }

        blit(batch, f, drawX, drawY, flip);
        drawOverlay(batch, drawX, drawY, flip);

        // A second additive pass rather than a colour tint: SpriteBatch's tint
        // multiplies, so it can only ever darken a sprite, and "flash white"
        // through a multiply is a no-op. Additive over the same frame actually
        // brightens it, and costs one flush for the few entities flashing at once.
        if (flashSteps > 0) {
            batch.flush();
            batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
            blit(batch, f, drawX, drawY, flip);
            drawOverlay(batch, drawX, drawY, flip);
            batch.flush();
            batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        }
    }

    /**
     * A second sprite over this one, in the same frame and under the same rules.
     *
     * <p>A hook rather than an override of {@link #draw} because the two guards
     * above it have to apply to both halves: an invulnerable player blinks out
     * entirely, and a player being hit flashes white entirely. Drawing the
     * overlay outside them gives a sword that stays solid while its owner
     * blinks, which reads as the sword being a separate object.
     *
     * @param drawX left edge of the body frame, already rounded
     * @param drawY bottom edge of the body frame, already rounded
     */
    protected void drawOverlay(SpriteBatch batch, int drawX, int drawY, boolean flip) {
    }

    private static void blit(SpriteBatch batch, TextureRegion f, int drawX, int drawY,
                             boolean flip) {
        int w = f.getRegionWidth();
        int h = f.getRegionHeight();
        if (flip) {
            batch.draw(f, drawX + w, drawY, -w, h);
        } else {
            batch.draw(f, drawX, drawY, w, h);
        }
    }
}
