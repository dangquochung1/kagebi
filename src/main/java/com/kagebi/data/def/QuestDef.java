package com.kagebi.data.def;

/**
 * A job, its steps, and what it pays.
 *
 * <p><b>Steps, not a single goal.</b> The shortest quest in the content has one
 * step and the longest has three, and a step is always a count of something the
 * game already counts - monsters killed, things picked up, floors reached,
 * people spoken to. Nothing here can ask for something the simulation does not
 * already know, which is what keeps a quest from needing its own code.
 *
 * <p><b>A giver is optional.</b> Some of these are handed out by a villager who
 * explains them, and some are standing bounties that simply exist. The ones
 * with a giver are the tutorial chain; the rest are the work.
 */
public final class QuestDef {

    /** What a step counts. Each maps to a place the game already increments. */
    public enum Kind {
        /** Enemies of one id, killed. */
        KILL,
        /** Items of one id, picked up during a run. */
        COLLECT,
        /** Reached a floor number, at all. */
        REACH,
        /** Spoke to a villager. */
        TALK
    }

    /** One line of "do this, that many times". */
    public static final class Step {
        public final Kind kind;
        /** An enemy id, item id, villager id, or a floor number as text. */
        public final String target;
        public final int count;

        public Step(Kind kind, String target, int count) {
            this.kind = kind;
            this.target = target;
            this.count = count;
        }

        @Override
        public String toString() {
            return kind + " " + target + " x" + count;
        }
    }

    public final String id;
    public final String nameKey;
    public final String descKey;
    /** The villager who offers and receives this, or null for a standing bounty. */
    public final String giver;
    /** The quest that has to be finished first, or null. */
    public final String requires;
    public final Step[] steps;
    public final int rewardGold;
    /** Ids of items paid out, or empty. */
    public final String[] rewardItems;
    /** A piece of gear paid out, or null. */
    public final String rewardGear;
    /** A weapon unlocked by finishing this, or null. */
    public final String rewardWeapon;

    public QuestDef(String id, String nameKey, String descKey, String giver, String requires,
                    Step[] steps, int rewardGold, String[] rewardItems,
                    String rewardGear, String rewardWeapon) {
        this.id = id;
        this.nameKey = nameKey;
        this.descKey = descKey;
        this.giver = giver;
        this.requires = requires;
        this.steps = steps;
        this.rewardGold = rewardGold;
        this.rewardItems = rewardItems;
        this.rewardGear = rewardGear;
        this.rewardWeapon = rewardWeapon;
    }

    public boolean hasGiver() {
        return giver != null;
    }

    @Override
    public String toString() {
        return "QuestDef(" + id + ", " + steps.length + " steps)";
    }
}
