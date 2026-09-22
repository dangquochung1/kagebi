package com.kagebi.quest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.kagebi.data.ContentRegistry;
import com.kagebi.data.def.GearDef;
import com.kagebi.data.def.QuestDef;
import com.kagebi.data.def.QuestDef.Kind;
import com.kagebi.data.def.QuestDef.Step;
import com.kagebi.save.Profile;
import com.kagebi.save.QuestLog.State;

/**
 * The rules about jobs, with no village and no dungeon in sight.
 *
 * <p>Built from hand-made defs rather than the shipped quests, so a content
 * pass that re-prices the tutorial cannot break a test about whether handing
 * one in pays twice.
 */
class QuestsTest {

    private ContentRegistry content;
    private Profile p;

    private static QuestDef quest(String id, String giver, String requires, Step... steps) {
        return new QuestDef(id, "n", "d", giver, requires, steps, 100,
            new String[0], null, null);
    }

    @BeforeEach
    void setUp() {
        content = new ContentRegistry();
        content.put(quest("slay", "master", null, new Step(Kind.KILL, "slime", 3)));
        content.put(quest("fetch", "herbalist", "slay",
            new Step(Kind.COLLECT, "heart", 2), new Step(Kind.TALK, "herbalist", 1)));
        content.put(quest("dive", null, null, new Step(Kind.REACH, "3", 1)));
        content.put(new GearDef("hood", "n", "d", 1, GearDef.Slot.HEAD, 1, 1,
            new String[] {"max_hp_add"}, new float[] {5f}, 0));
        p = new Profile();
    }

    @Test
    void aQuestBehindAnotherIsNotOfferedYet() {
        assertEquals(1, Quests.offeredBy(content, p, "master").size);
        assertEquals(0, Quests.offeredBy(content, p, "herbalist").size,
            "the herbalist waits until the master's job is paid");
    }

    @Test
    void theChainOpensAsItIsFinished() {
        Quests.accept(content.quest("slay"), p);
        Quests.record(content, p, Kind.KILL, "slime", 3);
        assertTrue(p.quests.is("slay", State.DONE));
        assertEquals(0, Quests.offeredBy(content, p, "herbalist").size,
            "done is not paid");
        Quests.claim(content.quest("slay"), content, p);
        assertEquals(1, Quests.offeredBy(content, p, "herbalist").size);
    }

    @Test
    void killsOnlyCountForTheThingTheQuestNames() {
        Quests.accept(content.quest("slay"), p);
        Quests.record(content, p, Kind.KILL, "mouse", 10);
        assertEquals(0, p.quests.progress("slay", 0));
        assertTrue(p.quests.is("slay", State.ACTIVE));
    }

    @Test
    void aQuestNotTakenCountsNothing() {
        Quests.record(content, p, Kind.KILL, "slime", 5);
        assertEquals(0, p.quests.progress("slay", 0));
    }

    /** Every step has to be met, not just the first. */
    @Test
    void aTwoStepQuestNeedsBothSteps() {
        Quests.claim(content.quest("slay"), content, p);       // no-op, not done
        p.quests.set("slay", State.DONE);
        Quests.claim(content.quest("slay"), content, p);
        Quests.accept(content.quest("fetch"), p);
        Quests.record(content, p, Kind.COLLECT, "heart", 2);
        assertTrue(p.quests.is("fetch", State.ACTIVE), "still owes a conversation");
        Quests.record(content, p, Kind.TALK, "herbalist", 1);
        assertTrue(p.quests.is("fetch", State.DONE));
    }

    /**
     * Going deeper than asked satisfies the step. A player on floor 5 has
     * plainly been to floor 3, and sending them back up would be asking for
     * nothing.
     */
    @Test
    void reachingDeeperThanAskedCounts() {
        Quests.accept(content.quest("dive"), p);
        Quests.record(content, p, Kind.REACH, "5", 1);
        assertTrue(p.quests.is("dive", State.DONE));
    }

    @Test
    void reachingShallowerDoesNot() {
        Quests.accept(content.quest("dive"), p);
        Quests.record(content, p, Kind.REACH, "2", 1);
        assertTrue(p.quests.is("dive", State.ACTIVE));
    }

    @Test
    void handingInPaysExactlyOnce() {
        Quests.accept(content.quest("slay"), p);
        Quests.record(content, p, Kind.KILL, "slime", 3);
        int before = p.gold;
        assertTrue(Quests.claim(content.quest("slay"), content, p));
        assertEquals(before + 100, p.gold);
        assertFalse(Quests.claim(content.quest("slay"), content, p), "and not again");
        assertEquals(before + 100, p.gold);
    }

    @Test
    void aClaimedQuestLeavesTheBoard() {
        Quests.accept(content.quest("dive"), p);
        Quests.record(content, p, Kind.REACH, "3", 1);
        Quests.claim(content.quest("dive"), content, p);
        for (QuestDef q : Quests.available(content, p)) {
            assertFalse("dive".equals(q.id), "a paid job is off the list");
        }
    }

    @Test
    void takingTheSameJobTwiceChangesNothing() {
        assertTrue(Quests.accept(content.quest("slay"), p));
        assertFalse(Quests.accept(content.quest("slay"), p));
    }

    /**
     * A job whose steps are already met is done on acceptance. Taking "reach
     * floor 3" to someone who is on floor 5 and asking them to go again would
     * be the board not paying attention.
     */
    @Test
    void aJobAlreadySatisfiedIsDoneWhenTaken() {
        p.quests.advance("dive", 0, 1);
        Quests.accept(content.quest("dive"), p);
        assertTrue(p.quests.is("dive", State.DONE));
    }

    @Test
    void gearPaidOutArrivesInTheStash() {
        content.put(new QuestDef("rich", "n", "d", null, null,
            new Step[] {new Step(Kind.KILL, "slime", 1)}, 0, new String[0], "hood", "axe"));
        Quests.accept(content.quest("rich"), p);
        Quests.record(content, p, Kind.KILL, "slime", 1);
        Quests.claim(content.quest("rich"), content, p);
        assertEquals(1, p.stash.size);
        assertEquals("hood", p.stash.first().defId);
        assertEquals(1, p.stash.first().sockets.length, "and with its holes");
        assertTrue(p.unlockedWeapons.contains("axe"));
    }

    /** Two jobs after the same monster both advance. */
    @Test
    void oneKillFeedsEveryJobThatWantsIt() {
        content.put(quest("also_slay", null, null, new Step(Kind.KILL, "slime", 5)));
        Quests.accept(content.quest("slay"), p);
        Quests.accept(content.quest("also_slay"), p);
        Quests.record(content, p, Kind.KILL, "slime", 3);
        assertEquals(3, p.quests.progress("slay", 0));
        assertEquals(3, p.quests.progress("also_slay", 0));
    }
}
