package com.kagebi.entity;

import java.util.HashMap;
import java.util.Map;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.TextureData;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.FileTextureData;
import com.kagebi.Dir;
import com.kagebi.assets.Assets;
import com.kagebi.data.def.EnemyDef;
import com.kagebi.gfx.Anim;

/**
 * The animations one actor draws with, sliced once when it is built.
 *
 * <p><b>Every field may be null, and the whole object may be null.</b> That is
 * what makes the simulation testable: slicing a sheet needs a
 * {@link TextureAtlas}, an atlas needs a texture, and a texture needs a GL
 * context. Handing {@code EntityWorld} a null atlas gives every entity null
 * sprites, the render pass becomes a no-op, and movement, collision, AI and
 * combat all run in plain JUnit at the real fixed step.
 *
 * <p>Four sheet shapes reach this class, and they are told apart by measuring
 * the region rather than by trusting a naming convention:
 *
 * <ul>
 * <li><b>4x4 directional</b>, 64x64 at 16px - all 66 Ninja Adventure monsters.
 * <li><b>Depths set</b> - five single-facing 32px strips per actor
 *     ({@code _idle}, {@code _movement}, {@code _attack}, {@code _take_damage},
 *     {@code _death}). Checked at 8x: the skeletons face right, so they are
 *     drawn flipped when facing left.
 * <li><b>Single strip</b>, 64x16 - the small depths idles, four frames of one
 *     facing. This is the dangerous one: it is exactly four cells wide, so
 *     {@link Anim#directional} accepts it without complaint and reads its four
 *     animation frames as four facings of a one-frame animation. Checking the
 *     height is what tells them apart.
 * <li><b>Boss strips</b> - one file per animation at a size per boss.
 * <li><b>Directional sets</b> - stage 6's slimes and pirates: one file per
 *     animation, each a four-column directional sheet, so they have a real
 *     left and right and a frame count per animation. The two shapes above
 *     each give up one of those; this gives up neither, at the cost of six
 *     regions per body instead of one.
 * </ul>
 *
 * <p>The player's {@code dead} sheet is a fifth shape - 32x64, one column of
 * two frames - sliced here by hand because {@link Anim} has no column mode.
 */
public final class ActorSprites {

    /** Player frames are 32x32 in a 4-column sheet; monsters are 16x16. */
    public static final int PLAYER_CELL = 32;
    public static final int MONSTER_CELL = 16;

    /*
     * Transparent rows below the feet, measured on the source sheets. The ninja
     * is a 15x15 figure in the middle of a 32px cell with 8 empty rows beneath
     * it - all six are recolours of one sheet, so one number serves. The depths
     * skeletons and vampire leave 3. The 16px monsters fill their cells to within
     * a pixel. Bosses vary too much for a constant (giantfrog2 leaves 7 of 40,
     * tengured 24 of 82) and are measured at load; see measureFigure.
     */
    private static final int PLAYER_FOOT = 8;
    private static final int PLAYER_FIGURE = 15;
    private static final int DEPTHS_FOOT = 3;
    /*
     * The depths figures do not stand in the middle of their 32px cells: the
     * feet average x = 12.9 across the idle, movement and attack strips of all
     * three actors, against a cell centre of 15.5. Drawn centred, a skeleton
     * stands three pixels beside its own shadow - on the other side when flipped.
     */
    private static final int DEPTHS_SHIFT = 3;
    private static final int MONSTER_FOOT = 1;

    /** Boss figures, measured once per region per session. */
    private static final Map<String, int[]> FIGURES = new HashMap<>();

    public final int cell;
    /**
     * Drawn one way only, so facing left means flipping. The directional sheets
     * have a real left column and must never be flipped.
     */
    public final boolean singleFacing;
    public final Anim idle;
    public final Anim walk;
    /** Chasing, for the bodies whose art has a second gait. Null falls back to walk. */
    public final Anim run;
    /** One step per frame; the caller remaps elapsed steps with {@link #frameOf}. */
    public final Anim attack;
    public final Anim hurt;
    public final Anim roll;
    /** Enemy death strip at its own pace, or null when the art has none. */
    public final Anim death;
    /** Player only: one column of frames. */
    public final TextureRegion[] dead;
    public final TextureRegion shadow;

    /** Empty rows between the bottom of a frame and the figure's feet. */
    public int footInset;
    /**
     * Pixels to move the frame right so the figure's feet, not the cell,
     * are centred on the entity. Mirrored when the frame is flipped.
     */
    public int shiftX;
    /** The opaque figure inside a frame, for sizing a boss's body and shadow. */
    public int figureW;
    public int figureH;

    /**
     * Empty rows above the figure's head, which is where a portrait crop starts.
     *
     * <p>Derived rather than stored: the measurement already knows where the
     * feet are and how tall the figure is, and a fourth number kept alongside
     * them is a fourth number that can disagree with them. Clamped at zero for
     * the sprites whose box is declared rather than measured - the 16px trash
     * fills its cell, so there is nothing above it.
     */
    public int figureTop() {
        return Math.max(0, cell - footInset - figureH);
    }

    private ActorSprites(int cell, boolean singleFacing, Anim idle, Anim walk, Anim attack,
                         Anim hurt, Anim roll, Anim death, TextureRegion[] dead,
                         TextureRegion shadow) {
        this(cell, singleFacing, idle, walk, null, attack, hurt, roll, death, dead, shadow);
    }

    private ActorSprites(int cell, boolean singleFacing, Anim idle, Anim walk, Anim run,
                         Anim attack, Anim hurt, Anim roll, Anim death, TextureRegion[] dead,
                         TextureRegion shadow) {
        this.cell = cell;
        this.figureW = cell;
        this.figureH = cell;
        this.singleFacing = singleFacing;
        this.idle = idle;
        this.walk = walk;
        this.run = run;
        this.attack = attack;
        this.hurt = hurt;
        this.roll = roll;
        this.death = death;
        this.dead = dead;
        this.shadow = shadow;
    }

    /**
     * A playable character: a 32px four-column sheet with a full animation set.
     *
     * <p>There used to be a {@code forCharacter} in front of this that picked
     * between two shapes, because three of the heroes were 48px side-view
     * strips. One shape is left, so the chooser was a layer that could only be
     * got wrong - and was, three times.
     */
    public static ActorSprites player(TextureAtlas atlas, String characterId) {
        if (atlas == null) {
            return null;
        }
        return new ActorSprites(PLAYER_CELL, false,
            ninja(atlas, characterId, Assets.Actor.PlayerAnim.IDLE, 12, true),
            ninja(atlas, characterId, Assets.Actor.PlayerAnim.WALK, 6, true),
            // One step per frame: a swing lasts as long as its weapon says, so
            // frameOf() stretches the art over it rather than the art guessing.
            ninja(atlas, characterId, Assets.Actor.PlayerAnim.ATTACK, 1, false),
            ninja(atlas, characterId, Assets.Actor.PlayerAnim.HIT, 1, false),
            ninja(atlas, characterId, Assets.Actor.PlayerAnim.ROLL, 1, false),
            null,
            column(atlas, Assets.Actor.player(characterId, Assets.Actor.PlayerAnim.DEAD), PLAYER_CELL),
            atlas.findRegion(Assets.Actor.SHADOW)).figure(PLAYER_FOOT, PLAYER_FIGURE, PLAYER_FIGURE);
    }

    private ActorSprites figure(int foot, int w, int h) {
        footInset = foot;
        figureW = w;
        figureH = h;
        return this;
    }

    private static Anim ninja(TextureAtlas atlas, String characterId, String animation,
                              int stepsPerFrame, boolean looping) {
        return Anim.directional(atlas, Assets.Actor.player(characterId, animation),
            PLAYER_CELL, stepsPerFrame, looping);
    }

    /**
     * Whatever an {@link EnemyDef} names, or null if the atlas does not have it.
     * Null rather than an exception: content is being authored alongside this
     * code, and one bad sprite path should cost one invisible enemy, not a crash.
     */
    public static ActorSprites enemy(TextureAtlas atlas, EnemyDef def) {
        if (atlas == null || def == null || def.sprite == null) {
            return null;
        }
        String setId = Assets.Actor.slimeIdOf(def.sprite);
        if (setId != null) {
            return directionalSet(atlas, "slimes/" + setId, def.cell);
        }
        setId = Assets.Actor.boss6IdOf(def.sprite);
        if (setId != null) {
            return directionalSet(atlas, "bosses6/" + setId, def.cell);
        }
        String bossId = Assets.Actor.bossIdOf(def.sprite);
        if (bossId != null) {
            return boss(atlas, bossId);
        }
        if (Assets.Actor.isDepthsSet(def.sprite)) {
            return depths(atlas, def.sprite);
        }
        TextureRegion sheet = atlas.findRegion(def.sprite);
        if (sheet == null) {
            return null;
        }
        int cell = def.cell > 0 ? def.cell : sheet.getRegionHeight();
        int w = sheet.getRegionWidth();
        int h = sheet.getRegionHeight();
        TextureRegion shadow = atlas.findRegion(Assets.Actor.SHADOW);
        if (w == cell * Dir.ALL.length && h == cell * 4) {
            return new ActorSprites(cell, false,
                Anim.directional(atlas, def.sprite, cell, 14, true),
                Anim.directional(atlas, def.sprite, cell, 6, true),
                null, null, null, null, null, shadow).figure(MONSTER_FOOT, cell, cell);
        }
        if (h == cell) {
            Anim strip = Anim.strip(atlas, def.sprite, 8, true);
            return new ActorSprites(cell, true, strip, strip, null, null, null, null, null, shadow)
                .figure(0, cell, cell);
        }
        return null;
    }

    /** A boss, from one horizontal strip per animation, each probed rather than assumed. */
    /**
     * One body from a folder of four-column directional sheets.
     *
     * <p>Idle is required and everything else is optional, so a body can ship
     * whatever its pack drew. The steps-per-frame numbers are the same ones
     * the 4x4 monsters use, except that attack and hurt get one step per frame
     * and are stretched over the def's own windup by {@link #frameOf} - the
     * art must not decide how long a swing lasts.
     *
     * <p>The figure is measured off the idle sheet the way a boss's is. These
     * cells are 64px around a body half that size, and drawn centred in the
     * cell a slime floats a dozen pixels above its own shadow.
     */
    public static ActorSprites directionalSet(TextureAtlas atlas, String prefix, int cell) {
        if (atlas == null) {
            return null;
        }
        String idleRegion = prefix + "/" + Assets.Actor.SET_ANIMS[0];
        TextureRegion sheet = atlas.findRegion(idleRegion);
        if (sheet == null) {
            return null;
        }
        int size = cell > 0 ? cell : sheet.getRegionWidth() / Dir.ALL.length;
        ActorSprites out = new ActorSprites(size, false,
            setAnim(atlas, prefix, "idle", size, 10, true),
            setAnim(atlas, prefix, "walk", size, 6, true),
            setAnim(atlas, prefix, "run", size, 4, true),
            setAnim(atlas, prefix, "attack", size, 1, false),
            setAnim(atlas, prefix, "hurt", size, 1, false),
            null,
            setAnim(atlas, prefix, "death", size, 5, false),
            null, atlas.findRegion(Assets.Actor.SHADOW));
        int[] fig = measureFigure(sheet, size);
        if (fig != null) {
            out.figure(fig[0], fig[1], fig[2]);
        }
        return out;
    }

    /**
     * One animation of a directional set, sliced by whichever shape it is.
     *
     * <p>Measured rather than assumed, the way {@link #enemy} tells its two
     * trash shapes apart. Most of a set is four columns wide, one per facing -
     * but a pack that draws a death once rather than four times ships it as a
     * single row, and the pirates' do exactly that. Handing that to
     * {@link Anim#directional} throws, which is how this was found.
     */
    private static Anim setAnim(TextureAtlas atlas, String prefix, String name, int cell,
                                int stepsPerFrame, boolean looping) {
        String region = prefix + "/" + name;
        TextureRegion sheet = atlas.findRegion(region);
        if (sheet == null) {
            return null;
        }
        if (sheet.getRegionWidth() == cell * Dir.ALL.length) {
            return Anim.directional(atlas, region, cell, stepsPerFrame, looping);
        }
        if (sheet.getRegionHeight() == cell) {
            return Anim.strip(atlas, region, stepsPerFrame, looping);
        }
        return null;
    }

    public static ActorSprites boss(TextureAtlas atlas, String id) {
        if (atlas == null) {
            return null;
        }
        String idleName = firstPresent(atlas, id, Assets.Actor.BOSS_IDLE);
        if (idleName == null) {
            return null;
        }
        Anim idle = Anim.strip(atlas, Assets.Actor.boss(id, idleName), 8, true);
        Anim walk = firstStrip(atlas, id, Assets.Actor.BOSS_MOVE, 6, true);
        Anim attack = firstStrip(atlas, id, Assets.Actor.BOSS_ATTACK, 1, false);
        Anim hurt = firstStrip(atlas, id, Assets.Actor.BOSS_HURT, 1, false);
        String idleRegion = Assets.Actor.boss(id, idleName);
        int cell = atlas.findRegion(idleRegion).getRegionHeight();
        ActorSprites out = new ActorSprites(cell, false, idle, walk != null ? walk : idle,
            attack, hurt, null, null, null, atlas.findRegion(Assets.Actor.SHADOW));
        int[] fig = measureFigure(atlas.findRegion(idleRegion), cell);
        if (fig != null) {
            out.figure(fig[0], fig[1], fig[2]);
        }
        return out;
    }

    /**
     * The opaque figure in a strip's first frame: {foot inset, width, height}.
     *
     * <p>Read from the atlas page itself, the same way a texture reloads after
     * a lost context, so no path is written down here. Decoding the page costs
     * tens of milliseconds, which is why only bosses are measured - one per
     * floor - and why the answer is kept for the session. Null if the page
     * cannot be re-read, in which case the cell stands in for the figure.
     */
    private static int[] measureFigure(TextureRegion region, int cell) {
        TextureData data = region.getTexture().getTextureData();
        if (!(data instanceof FileTextureData)) {
            return null;
        }
        String key = ((FileTextureData) data).getFileHandle().path() + "@"
            + region.getRegionX() + "," + region.getRegionY();
        int[] cached = FIGURES.get(key);
        if (cached != null) {
            return cached;
        }
        Pixmap page = new Pixmap(((FileTextureData) data).getFileHandle());
        try {
            int left = cell;
            int right = -1;
            int top = cell;
            int bottom = -1;
            for (int py = 0; py < cell; py++) {
                for (int px = 0; px < cell; px++) {
                    int rgba = page.getPixel(region.getRegionX() + px, region.getRegionY() + py);
                    if ((rgba & 0xFF) > 20) {
                        left = Math.min(left, px);
                        right = Math.max(right, px);
                        top = Math.min(top, py);
                        bottom = Math.max(bottom, py);
                    }
                }
            }
            if (right < 0) {
                return null;
            }
            int[] fig = {cell - 1 - bottom, right - left + 1, bottom - top + 1};
            FIGURES.put(key, fig);
            return fig;
        } finally {
            page.dispose();
        }
    }

    /** The transformation strip, which only the two-phase bosses ship. */
    public static Anim transformation(TextureAtlas atlas, String bossId) {
        return atlas == null || bossId == null
            ? null : firstStrip(atlas, bossId, Assets.Actor.BOSS_TRANSFORM, 5, false);
    }

    private static ActorSprites depths(TextureAtlas atlas, String idleRegion) {
        TextureRegion idleSheet = atlas.findRegion(idleRegion);
        if (idleSheet == null) {
            return null;
        }
        Anim idle = Anim.strip(atlas, idleRegion, 8, true);
        Anim move = strip(atlas, Assets.Actor.depthsMove(idleRegion), 5, true);
        return new ActorSprites(idleSheet.getRegionHeight(), true,
            idle, move != null ? move : idle,
            strip(atlas, Assets.Actor.depthsAttack(idleRegion), 1, false),
            strip(atlas, Assets.Actor.depthsHurt(idleRegion), 1, false),
            null,
            // Four steps a frame: the 17-frame skeleton death plays in 1.1s.
            // The room counts the kill on the step hp reaches zero, not when
            // this finishes, so a long death never holds a door shut.
            strip(atlas, Assets.Actor.depthsDeath(idleRegion), 4, false),
            null, atlas.findRegion(Assets.Actor.SHADOW)).figure(DEPTHS_FOOT, 15, 16)
            .shifted(DEPTHS_SHIFT);
    }

    private ActorSprites shifted(int dx) {
        shiftX = dx;
        return this;
    }

    /**
     * Maps a state lasting {@code total} steps onto an animation's frames.
     *
     * <p>Needed because attack timing is per weapon and per enemy while the art
     * has a fixed frame count. Played at its own pace a four-frame swing is
     * either frozen on its last frame or cut off halfway, and both read as the
     * animation being broken.
     */
    public static TextureRegion frameOf(Anim anim, Dir facing, int elapsed, int total) {
        if (anim == null) {
            return null;
        }
        int count = anim.frameCount();
        int index = total <= 0 ? 0 : Math.min(count - 1, elapsed * count / total);
        return anim.frame(facing, Math.max(0, index));
    }

    private static String firstPresent(TextureAtlas atlas, String id, String[] names) {
        for (String name : names) {
            if (atlas.findRegion(Assets.Actor.boss(id, name)) != null) {
                return name;
            }
        }
        return null;
    }

    private static Anim firstStrip(TextureAtlas atlas, String id, String[] names,
                                   int stepsPerFrame, boolean looping) {
        String name = firstPresent(atlas, id, names);
        return name == null ? null
            : Anim.strip(atlas, Assets.Actor.boss(id, name), stepsPerFrame, looping);
    }

    private static Anim strip(TextureAtlas atlas, String region, int stepsPerFrame,
                              boolean looping) {
        return atlas.findRegion(region) == null
            ? null : Anim.strip(atlas, region, stepsPerFrame, looping);
    }

    /** One column of square frames, top first, for the player's dead sheet. */
    private static TextureRegion[] column(TextureAtlas atlas, String region, int cell) {
        TextureRegion sheet = atlas.findRegion(region);
        if (sheet == null) {
            return null;
        }
        int rows = sheet.getRegionHeight() / cell;
        TextureRegion[] out = new TextureRegion[rows];
        for (int r = 0; r < rows; r++) {
            out[r] = new TextureRegion(sheet, 0, r * cell, cell, cell);
        }
        return out;
    }
}
