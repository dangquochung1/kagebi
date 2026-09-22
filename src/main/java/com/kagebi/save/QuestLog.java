package com.kagebi.save;

import com.badlogic.gdx.utils.ObjectIntMap;
import com.badlogic.gdx.utils.ObjectMap;

/**
 * How far the player has got with every job they have taken.
 *
 * <p>Progress is counted per step and kept per quest, so a quest taken,
 * half-done and then abandoned mid-run does not start again from nothing the
 * next time. Counts are kept even for a quest that has been handed in, because
 * {@link State#CLAIMED} is the end of the line and there is nothing to reset
 * them for.
 *
 * <p>Deliberately dumb: no rules live here, only the numbers. {@code Quests}
 * decides what a count means and when a state may change, and this is what it
 * writes into. That is what lets the rules be tested with no save file and the
 * save file be tested with no rules.
 */
public final class QuestLog {

    /**
     * Where a quest is.
     *
     * <p>{@code DONE} and {@code CLAIMED} are different on purpose: a quest
     * whose steps are all finished is done, and stays done until the player
     * goes back to whoever gave it. That walk is the reward's delivery, and
     * paying out the moment the last slime died would remove it.
     */
    public enum State { OFFERED, ACTIVE, DONE, CLAIMED }

    /** Quest id to where it is. A quest not in here has never been offered. */
    public final ObjectMap<String, State> state = new ObjectMap<>();
    /** Quest id to per-step counts, packed as "step index" -> count. */
    public final ObjectMap<String, ObjectIntMap<Integer>> progress = new ObjectMap<>();

    public State state(String questId) {
        return state.get(questId);
    }

    public boolean is(String questId, State want) {
        return state.get(questId) == want;
    }

    public void set(String questId, State to) {
        state.put(questId, to);
    }

    public int progress(String questId, int step) {
        ObjectIntMap<Integer> counts = progress.get(questId);
        return counts == null ? 0 : counts.get(step, 0);
    }

    /** Adds to one step's count and returns the new total. */
    public int advance(String questId, int step, int by) {
        ObjectIntMap<Integer> counts = progress.get(questId);
        if (counts == null) {
            counts = new ObjectIntMap<>();
            progress.put(questId, counts);
        }
        int now = counts.get(step, 0) + by;
        counts.put(step, now);
        return now;
    }
}
