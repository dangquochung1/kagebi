package com.kagebi.save;

import com.badlogic.gdx.Gdx;

/**
 * PLACEHOLDER. Loads and stores the {@link Profile}, in memory only.
 *
 * <p>Here so the hub and the game-over screen have somewhere to put banked gold
 * while persistence is being written. Expected to be replaced whole; what must
 * survive is {@link #load()} and {@link #save(Profile)}.
 *
 * <p>When it is real: JSON under the same directory libGDX Preferences uses,
 * written to a temporary file and renamed over the old one. A save written in
 * place is a save that can be destroyed by closing the laptop lid at the wrong
 * moment, and losing a meta-progression file is losing everything the player
 * has.
 */
public final class SaveManager {

    private Profile cached;

    public Profile load() {
        if (cached == null) {
            cached = new Profile();
            Gdx.app.log("save", "placeholder save manager: nothing is persisted");
        }
        return cached;
    }

    public void save(Profile profile) {
        cached = profile;
    }
}
