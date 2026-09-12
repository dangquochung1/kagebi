package com.kagebi.entity;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.utils.Disposable;
import com.kagebi.Dir;
import com.kagebi.gen.CollisionGrid;
import com.kagebi.gen.Room;
import com.kagebi.input.InputService;

/**
 * The simulation, as the screen that owns it sees it.
 *
 * <p>This is the seam between the dungeon screen and everything that lives in
 * it. The screen drives time and draws the map; the world moves the actors,
 * resolves combat, and writes results into the {@code RunState} it was handed.
 * Neither side reaches into the other, which is the only reason the two can be
 * written at the same time.
 *
 * <p>The map is drawn by the screen rather than here, and in two passes, with
 * {@link #renderActors} between them. That is what lets a character walk behind
 * a tree canopy or an archway - the same overhead-layer split the village map
 * already uses.
 */
public interface World extends Disposable {

    /**
     * Hands over atlases the screen already holds, so the world stops loading
     * its own copies - about 20MB of texture for art that is on the GPU twice.
     *
     * <p>On the interface rather than on the implementation because it is part
     * of this seam: the screen owns the atlases, and this is how it says so.
     * Doing nothing is a correct implementation - the effect is memory, not
     * behaviour - so a test double need not care.
     */
    default void useSharedAtlases(TextureAtlas ui, TextureAtlas fx) {
    }

    /**
     * Advances one fixed step. Called from the accumulator loop, never from
     * render, so that combat timing does not depend on frame rate.
     */
    void step(InputService input);

    /**
     * Moves the simulation into a room, spawning its contents.
     *
     * @param collision  solid tiles, built from the room's tile layers
     * @param enteredFrom the door the player came through, so they appear at the
     *                   matching side; null when arriving on the floor, in which
     *                   case the room's ENTRY spawn is used
     */
    void enterRoom(Room room, CollisionGrid collision, Dir enteredFrom);

    /** Draws actors only, between the screen's two map passes. */
    void renderActors(SpriteBatch batch);

    float playerX();

    float playerY();

    Dir playerFacing();

    /** True once nothing hostile is left; the screen then opens the doors. */
    boolean roomCleared();

    boolean playerDead();

    /**
     * The door the player is standing in, or null. The screen owns room
     * transitions because it owns the maps, so the world only reports this.
     */
    Dir doorReached();

    /**
     * An i18n key for the interaction prompt to show, or null for none - a
     * chest, a shopkeeper, the stairs down. The world knows what the player is
     * near; the screen knows how to draw a prompt.
     */
    String promptKey();

    /** Runs the interaction {@link #promptKey} was advertising. */
    void interact();

    /**
     * Camera shake in virtual pixels, already faded. Zero when the player has
     * turned screen shake off in settings.
     */
    float shake();
}
