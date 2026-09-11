package com.kagebi.data;

import com.badlogic.gdx.Gdx;
import com.kagebi.assets.Assets;

/**
 * PLACEHOLDER. Reads the JSON in {@code assets/data/} into a
 * {@link ContentRegistry}, and today reads nothing at all.
 *
 * <p>It exists so the game can boot and the screens can be written while the
 * content is being authored beside them. Expected to be replaced whole; what
 * must survive is {@link #load()}, which is what {@code Kagebi} calls.
 *
 * <p>When it is real it should also validate: every enemy's loot table, every
 * floor's enemy ids, every relic's effect name resolving to something combat
 * knows. A typo in a relic effect otherwise produces a relic that simply does
 * nothing, which is nearly impossible to notice while playing.
 */
public final class ContentLoader {

    public static ContentRegistry load() {
        ContentRegistry registry = new ContentRegistry();
        Gdx.app.log("content", "placeholder loader: "
            + Assets.DATA_DIR + " not read yet");
        return registry;
    }

    private ContentLoader() {}
}
