package com.kagebi.entity;

import com.kagebi.input.GameAction;
import com.kagebi.input.InputService;

/**
 * The three questions the player entity asks about input, and nothing else.
 *
 * <p>{@link InputService} cannot be built without {@code Gdx.app} - its
 * {@code InputMap} reads {@code Preferences} in its constructor - so a test
 * that wanted to drive the player would need a headless application just to
 * press a button. This interface is the seam that avoids it: the player reads
 * intent through three methods, and a test implements them in ten lines.
 *
 * <p>Note what is <em>not</em> here: keycodes. Gameplay asks about a
 * {@link GameAction}, never about a key, which is what keeps the controls
 * screen a settings screen rather than a refactor.
 */
public interface ActionSource {

    boolean isDown(GameAction action);

    /** Pressed within the last {@code steps} fixed steps and not yet spent. */
    boolean buffered(GameAction action, int steps);

    /** Spends a buffered press so it cannot fire a second time. */
    void consume(GameAction action);

    /** Adapts the real service. Declared here so nothing else has to know how. */
    static ActionSource of(InputService input) {
        return new ActionSource() {
            @Override
            public boolean isDown(GameAction action) {
                return input.isDown(action);
            }

            @Override
            public boolean buffered(GameAction action, int steps) {
                return input.buffered(action, steps);
            }

            @Override
            public void consume(GameAction action) {
                input.consume(action);
            }
        };
    }
}
