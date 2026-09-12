package com.kagebi.screen;

import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.kagebi.assets.Assets;
import com.kagebi.assets.IconSheet;

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

    public static TextureAtlas actors() {
        return atlas(Assets.ATLAS_ACTORS);
    }

    public static TextureAtlas npc() {
        return atlas(Assets.ATLAS_NPC);
    }

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
