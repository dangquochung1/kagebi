package com.kagebi.screen;

import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.kagebi.assets.Assets;
import com.kagebi.assets.IconSheet;
import com.kagebi.gfx.Anim;

/**
 * The atlases every in-game screen shares, loaded once.
 *
 * <p>Three screens want the actor sheets and two want the villagers. Loading
 * them per screen means a 660 KB page decoded again on every room-to-hub trip,
 * and - worse - two live copies of the same texture whenever one screen sits on
 * top of another, which the pause and inventory overlays do by design.
 *
 * <p>Static, which deserves an explanation. {@code Kagebi} already owns an
 * {@link AssetManager} for the skin and outlives every screen, and that is
 * where this belongs; it exposes no accessor for it, and the entry point is
 * held still this milestone so that four people can work in parallel. Moving
 * these three lines onto {@code Kagebi} is the first thing to do afterwards -
 * see {@code notes/a.md}.
 *
 * <p>{@link BootScreen} fills it behind a progress bar. Everything else calls
 * the accessors, which block on a synchronous load if boot was skipped - which
 * it is every time a screen is opened directly with {@code --screen}.
 */
public final class Preload {

    private static AssetManager manager;

    /** The manager, with everything queued but not necessarily loaded yet. */
    public static AssetManager manager() {
        if (manager == null) {
            manager = new AssetManager();
            manager.load(Assets.ATLAS_ACTORS, TextureAtlas.class);
            manager.load(Assets.ATLAS_NPC, TextureAtlas.class);
            manager.load(Assets.ATLAS_FX, TextureAtlas.class);
            manager.load(Assets.ICONS, Texture.class);
        }
        return manager;
    }

    /**
     * A character's idle animation, for the screens that show one standing still.
     *
     * <p><b>This exists because the roster used to be two animation shapes and
     * three screens forgot it.</b> Karasu, Kitsune and Yamabushi were single-row
     * 48px strips under {@code jphero/} while the ninja is a four-column sheet
     * of 32px cells under {@code player/}; asking {@code Anim.directional} for a
     * strip throws, and the throw reached the top of {@code render}, so choosing
     * the fox in the village and pressing escape closed the game.
     *
     * <p>The roster is one shape now, so there is nothing left here to get
     * wrong. It stays because the four call sites should still agree on the
     * cell and the pace, and because the next character with a different sheet
     * should have exactly one place to be special in.
     */
    public static Anim idle(String characterId) {
        return Anim.directional(actors(),
            Assets.Actor.player(characterId, Assets.Actor.PlayerAnim.IDLE),
            PLAYER_CELL, Anim.DEFAULT_STEPS_PER_FRAME, true);
    }

    /**
     * A square crop of a frame, showing the head rather than the middle.
     *
     * <p>The roster, the village badge and the bestiary all show an actor in a
     * cell smaller than its sprite. Where the crop should start is the whole
     * question, and guessing it from the frame height alone is what put the
     * village badge on Kitsune's tail: a band a third of the way down finds a
     * head on a tall sprite and a waist on a short one.
     *
     * <p>So the caller says, when it knows. {@code figureTop} is how many empty
     * rows sit above the ink, which {@link com.kagebi.entity.ActorSprites}
     * measures for every enemy it builds; pass -1 and the old guess is used,
     * which is right for the ninja in the middle of its 32px cell.
     *
     * <p>Region coordinates run down from the top, which is the opposite of
     * everywhere else in this game and the reason this is written once.
     */
    public static TextureRegion face(TextureRegion frame, int cell) {
        return face(frame, cell, -1);
    }

    /** @param figureTop empty rows above the ink, or -1 to guess from the height */
    public static TextureRegion face(TextureRegion frame, int cell, int figureTop) {
        int side = Math.min(frame.getRegionHeight(), cell);
        if (side >= frame.getRegionHeight() && side >= frame.getRegionWidth()) {
            return frame;
        }
        int top = figureTop >= 0 ? figureTop
            : frame.getRegionHeight() > 40 ? frame.getRegionHeight() / 3
            : (frame.getRegionHeight() - side) / 2;
        return new TextureRegion(frame,
            Math.max(0, (frame.getRegionWidth() - side) / 2),
            Math.min(Math.max(0, top), frame.getRegionHeight() - side),
            side, side);
    }

    /** The playable cell. */
    private static final int PLAYER_CELL = 32;

    public static TextureAtlas actors() {
        return atlas(Assets.ATLAS_ACTORS);
    }

    public static TextureAtlas npc() {
        return atlas(Assets.ATLAS_NPC);
    }

    /**
     * The purse coin for everywhere outside the dungeon: 16px, face on.
     *
     * <p>{@link Assets.Ui#COIN} is seven pixels square and stays on the
     * dungeon HUD, where the row is already crowded against the hearts and the
     * keys and a bigger coin would push it into the play area. Out here there
     * is room, and a six-figure total beside a seven-pixel picture reads as a
     * number with a speck next to it.
     *
     * <p>Frame zero of the four-frame spin, because the strip turns the coin
     * edge-on halfway round and at this size that reads as the icon having
     * broken rather than as a coin in profile.
     */
    public static TextureRegion coin() {
        if (coin == null) {
            TextureRegion strip = fx().findRegion(Assets.Prop.COIN_SPIN);
            if (strip == null) {
                return null;
            }
            int size = strip.getRegionHeight();
            coin = new TextureRegion(strip, 0, 0, size, size);
        }
        return coin;
    }

    private static TextureRegion coin;

    public static TextureAtlas fx() {
        return atlas(Assets.ATLAS_FX);
    }

    /**
     * The 2,192-icon grid relics and items are addressed into by index. Outside
     * every atlas because at 256x2192 it is taller than a texture page.
     */
    public static Texture icons() {
        AssetManager m = manager();
        if (!m.isLoaded(Assets.ICONS)) {
            m.finishLoadingAsset(Assets.ICONS);
        }
        return m.get(Assets.ICONS, Texture.class);
    }

    /**
     * One icon, by the one-based index the content files use, or null for an
     * index that names nothing. See {@link IconSheet} for why it is one-based -
     * the two screens that used to cut this sheet up themselves both read it as
     * zero-based and drew every icon's neighbour.
     */
    public static TextureRegion icon(int index) {
        if (!IconSheet.has(index)) {
            return null;
        }
        return new TextureRegion(icons(), IconSheet.x(index), IconSheet.y(index),
                                 IconSheet.SIZE, IconSheet.SIZE);
    }

    private static TextureAtlas atlas(String path) {
        AssetManager m = manager();
        if (!m.isLoaded(path)) {
            m.finishLoadingAsset(path);
        }
        return m.get(path, TextureAtlas.class);
    }

    private Preload() {}
}
