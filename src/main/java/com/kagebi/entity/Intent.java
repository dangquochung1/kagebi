package com.kagebi.entity;

import com.kagebi.input.GameAction;

/**
 * What the player is asking for this step, read once and spent deliberately.
 *
 * <p>The important part is that reading and spending are separate. A press is
 * <em>reported</em> for as many steps as the buffer lasts, and is only
 * <em>consumed</em> at the moment the player entity actually acts on it. Read
 * and consume in the same breath and buffering stops working: an attack pressed
 * while the previous swing is still rooted would be marked spent and then
 * dropped, which is the exact bug the buffer exists to fix, now hidden one
 * layer deeper.
 *
 * <p>Splitting intent out of the entity has a second payoff - the same
 * {@link Player} can be driven by a test, a replay or a demo attract mode
 * without any of them faking an input device.
 */
public final class Intent {

    /**
     * 6 steps, 100ms at 60Hz - the same window {@code InputService} uses.
     * Long enough to cover the recovery tail of a fast weapon, short enough
     * that a press does not fire after the player has changed their mind.
     */
    public static final int BUFFER_STEPS = 6;

    /** -1, 0 or 1. */
    public int moveX;
    public int moveY;

    public boolean attack;
    public boolean roll;
    public boolean interact;
    public boolean useItem;

    private ActionSource source;

    public void read(ActionSource src) {
        this.source = src;
        moveX = (src.isDown(GameAction.MOVE_RIGHT) ? 1 : 0)
              - (src.isDown(GameAction.MOVE_LEFT) ? 1 : 0);
        moveY = (src.isDown(GameAction.MOVE_UP) ? 1 : 0)
              - (src.isDown(GameAction.MOVE_DOWN) ? 1 : 0);
        attack = src.buffered(GameAction.ATTACK, BUFFER_STEPS);
        roll = src.buffered(GameAction.ROLL, BUFFER_STEPS);
        interact = src.buffered(GameAction.INTERACT, BUFFER_STEPS);
        useItem = src.buffered(GameAction.USE_ITEM, BUFFER_STEPS);
    }

    public void consumeAttack() {
        spend(GameAction.ATTACK);
        attack = false;
    }

    public void consumeRoll() {
        spend(GameAction.ROLL);
        roll = false;
    }

    public void consumeInteract() {
        spend(GameAction.INTERACT);
        interact = false;
    }

    public void consumeUseItem() {
        spend(GameAction.USE_ITEM);
        useItem = false;
    }

    public boolean moving() {
        return moveX != 0 || moveY != 0;
    }

    /** Diagonals are normalised, or moving at 45 degrees would be 41% faster. */
    public float moveScale() {
        return moveX != 0 && moveY != 0 ? 0.70710678f : 1f;
    }

    public void clear() {
        moveX = 0;
        moveY = 0;
        attack = false;
        roll = false;
        interact = false;
        useItem = false;
    }

    private void spend(GameAction action) {
        if (source != null) {
            source.consume(action);
        }
    }
}
