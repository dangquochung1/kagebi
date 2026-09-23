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
    /**
     * The throw button, which for a long time nothing read.
     *
     * <p>{@code GameAction.THROW} was declared, bound to K, translated, and
     * listed on the controls screen as rebindable - and no line of code asked
     * about it. The player could see and rebind a key that did nothing, which
     * is the worst shape a missing feature can take.
     */
    public boolean throwing;
    public boolean roll;
    public boolean interact;
    public boolean useItem;
    /**
     * One per skill slot, indexed from zero.
     *
     * <p>An array rather than three fields, because the slot a skill sits in
     * is content - assets/data/skills.json decides which skill is on which
     * key - so the code that casts one has a number in its hand rather than a
     * name, and swapping two skills is an edit to that file.
     */
    public final boolean[] skill = new boolean[SKILLS];

    /**
     * The slot being asked about, or -1: shift and a skill key together.
     *
     * <p>Here rather than in the HUD because it is input, and the HUD is where
     * it is drawn. Held rather than buffered: the panel is up exactly while
     * the keys are, and letting go puts it away, which is the one thing a
     * player expects of a key they are holding down to read something.
     */
    public int asking = -1;

    /**
     * Shift alone: the question about the ability that has no key.
     *
     * <p>A character's passive is never cast, so there is no key to hold over
     * it - and the character sheet has room for its name and not for what it
     * does. Shift on its own is the gesture the player has already learnt for
     * "tell me about my abilities", with nothing else bound to it.
     */
    public boolean askingPassive;

    private void asking(int slot, boolean yes) {
        if (yes && asking < 0) {
            asking = slot;
        } else if (!yes && asking == slot) {
            asking = -1;
        }
    }

    /** Skill slots, matching {@code ContentValidator.SKILL_SLOTS}. */
    public static final int SKILLS = 3;

    private static final GameAction[] SKILL_KEYS = {
        GameAction.SKILL_1, GameAction.SKILL_2, GameAction.SKILL_3,
    };

    private ActionSource source;

    public void read(ActionSource src) {
        this.source = src;
        moveX = (src.isDown(GameAction.MOVE_RIGHT) ? 1 : 0)
              - (src.isDown(GameAction.MOVE_LEFT) ? 1 : 0);
        moveY = (src.isDown(GameAction.MOVE_UP) ? 1 : 0)
              - (src.isDown(GameAction.MOVE_DOWN) ? 1 : 0);
        attack = src.buffered(GameAction.ATTACK, BUFFER_STEPS);
        throwing = src.buffered(GameAction.THROW, BUFFER_STEPS);
        roll = src.buffered(GameAction.ROLL, BUFFER_STEPS);
        interact = src.buffered(GameAction.INTERACT, BUFFER_STEPS);
        useItem = src.buffered(GameAction.USE_ITEM, BUFFER_STEPS);
        // Shift turns the three skill keys into three questions. Without this
        // the popup would come up on a skill that had just been cast, which is
        // the one moment its cooldown makes it useless to read about.
        boolean reading = src.infoHeld();
        if (!reading) {
            asking = -1;
        }
        for (int i = 0; i < SKILLS; i++) {
            boolean held = reading && src.isDown(SKILL_KEYS[i]);
            asking(i, held);
            if (held) {
                // Spent, not merely ignored. The press is buffered for six
                // steps whether or not anything used it, so without this a
                // player who reads a skill and lets go of shift casts it -
                // the one outcome a key held to ask a question must not have.
                src.consume(SKILL_KEYS[i]);
            }
            skill[i] = !reading && src.buffered(SKILL_KEYS[i], BUFFER_STEPS);
        }
        askingPassive = reading && asking < 0;
    }

    public void consumeAttack() {
        spend(GameAction.ATTACK);
        attack = false;
    }

    public void consumeThrow() {
        spend(GameAction.THROW);
        throwing = false;
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

    public void consumeSkill(int slot) {
        spend(SKILL_KEYS[slot]);
        skill[slot] = false;
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
        throwing = false;
        roll = false;
        interact = false;
        useItem = false;
        java.util.Arrays.fill(skill, false);
    }

    private void spend(GameAction action) {
        if (source != null) {
            source.consume(action);
        }
    }
}
