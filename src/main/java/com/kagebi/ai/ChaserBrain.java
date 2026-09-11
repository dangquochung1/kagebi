package com.kagebi.ai;

/**
 * Walks at the player and swings when it arrives. The default for trash.
 *
 * <p>Everything it does is in {@link BaseBrain}; it exists as its own type so
 * that {@code EnemyDef.brain = "chaser"} resolves to something, and so that the
 * fallback for an unknown brain id has a name rather than being "the base
 * class, used directly".
 */
public final class ChaserBrain extends BaseBrain {

    @Override
    public String id() {
        return "chaser";
    }
}
