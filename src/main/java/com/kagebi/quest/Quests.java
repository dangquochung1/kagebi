package com.kagebi.quest;

import com.badlogic.gdx.utils.Array;
import com.kagebi.data.ContentRegistry;
import com.kagebi.data.def.QuestDef;
import com.kagebi.data.def.GearDef;
import com.kagebi.save.OwnedGear;
import com.kagebi.save.Profile;
import com.kagebi.save.QuestLog;
import com.kagebi.save.QuestLog.State;

/**
 * The rules about jobs: which are on offer, what counts towards them, when one
 * is finished, and what finishing pays.
 *
 * <p>Pure, like {@code Progression} and {@code LootRoller}: a registry, a
 * profile and some numbers. No Gdx, no screens, no textures. The village and
 * the dungeon call in with "this happened"; nothing here knows which of them
 * did the calling, and that is why the same rule can be tested without either.
 *
 * <p><b>Nothing is offered twice and nothing pays twice.</b> Both follow from
 * the state living in {@link QuestLog} rather than being recomputed: a quest
 * whose steps are all met is {@code DONE} until the player walks back to
 * whoever gave it, and {@code CLAIMED} forever after.
 */
public final class Quests {

    private Quests() {}

    /**
     * Every quest whose prerequisite is finished and which has not been
     * claimed, in content order.
     */
    public static Array<QuestDef> available(ContentRegistry content, Profile p) {
        Array<QuestDef> out = new Array<>();
        for (QuestDef q : content.allQuests()) {
            if (p.quests.is(q.id, State.CLAIMED) || !unlocked(q, p)) {
                continue;
            }
            out.add(q);
        }
        out.sort((a, b) -> a.id.compareTo(b.id));
        return out;
    }

    /** Whether whatever this one waits on has been handed in. */
    public static boolean unlocked(QuestDef q, Profile p) {
        return q.requires == null || p.quests.is(q.requires, State.CLAIMED);
    }

    /** Quests this villager has to hand out right now. */
    public static Array<QuestDef> offeredBy(ContentRegistry content, Profile p, String npc) {
        Array<QuestDef> out = new Array<>();
        for (QuestDef q : available(content, p)) {
            if (npc.equals(q.giver) && p.quests.state(q.id) == null) {
                out.add(q);
            }
        }
        return out;
    }

    /** Quests this villager is waiting to be paid for. */
    public static Array<QuestDef> claimableFrom(ContentRegistry content, Profile p, String npc) {
        Array<QuestDef> out = new Array<>();
        for (QuestDef q : available(content, p)) {
            if (npc.equals(q.giver) && p.quests.is(q.id, State.DONE)) {
                out.add(q);
            }
        }
        return out;
    }

    /** Takes a job on. Returns false if it was already taken or is not offered. */
    public static boolean accept(QuestDef q, Profile p) {
        if (p.quests.state(q.id) != null || !unlocked(q, p)) {
            return false;
        }
        p.quests.set(q.id, State.ACTIVE);
        // A quest whose steps are already satisfied - "reach floor 2" for a
        // player who has - is done the moment it is taken rather than asking
        // them to do it again.
        refresh(q, p);
        return true;
    }

    /**
     * Counts one thing happening towards every active quest that wants it.
     *
     * <p>Every active quest, not the first match: two jobs that both want
     * slimes killed both advance, which is what a player expects and the
     * alternative is a bounty board that silently wastes their time.
     */
    public static void record(ContentRegistry content, Profile p,
                              QuestDef.Kind kind, String target, int amount) {
        if (amount <= 0) {
            return;
        }
        for (QuestDef q : content.allQuests()) {
            if (!p.quests.is(q.id, State.ACTIVE)) {
                continue;
            }
            for (int i = 0; i < q.steps.length; i++) {
                QuestDef.Step step = q.steps[i];
                if (step.kind != kind || !matches(step, kind, target)) {
                    continue;
                }
                if (p.quests.progress(q.id, i) < step.count) {
                    p.quests.advance(q.id, i, amount);
                }
            }
            refresh(q, p);
        }
    }

    /**
     * Whether a step cares about this target.
     *
     * <p>{@code REACH} compares as a number, so "floor 3" is satisfied by
     * arriving on floor 4 - a player who went deeper has plainly been there,
     * and a quest that made them walk back up would be asking for nothing.
     */
    private static boolean matches(QuestDef.Step step, QuestDef.Kind kind, String target) {
        if (kind != QuestDef.Kind.REACH) {
            return step.target.equals(target);
        }
        try {
            return Integer.parseInt(target) >= Integer.parseInt(step.target);
        } catch (NumberFormatException notANumber) {
            return false;
        }
    }

    /** Moves an active quest to DONE once every step is met. */
    private static void refresh(QuestDef q, Profile p) {
        if (!p.quests.is(q.id, State.ACTIVE) || !complete(q, p)) {
            return;
        }
        p.quests.set(q.id, State.DONE);
    }

    public static boolean complete(QuestDef q, Profile p) {
        for (int i = 0; i < q.steps.length; i++) {
            if (p.quests.progress(q.id, i) < q.steps[i].count) {
                return false;
            }
        }
        return true;
    }

    /**
     * Pays a finished quest out, once.
     *
     * <p>Gold, consumables into the pantry, a piece of gear into the stash and
     * a weapon into the unlock set - whichever of those the quest carries.
     * Returns false for a quest that is not finished or has already been paid,
     * so a double press cannot pay twice.
     */
    public static boolean claim(QuestDef q, ContentRegistry content, Profile p) {
        if (!p.quests.is(q.id, State.DONE)) {
            return false;
        }
        p.quests.set(q.id, State.CLAIMED);
        p.gold += Math.max(0, q.rewardGold);
        for (String item : q.rewardItems) {
            p.village.pantry.put(item, p.village.pantry.get(item, 0) + 1);
        }
        if (q.rewardGear != null && content.hasGear(q.rewardGear)) {
            GearDef def = content.gear(q.rewardGear);
            p.stash.add(new OwnedGear(p.nextGearId(), def.id, def.sockets));
        }
        if (q.rewardWeapon != null) {
            p.unlockedWeapons.add(q.rewardWeapon);
        }
        return true;
    }
}
