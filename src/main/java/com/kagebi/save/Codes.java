package com.kagebi.save;

import com.kagebi.assets.Assets;
import com.kagebi.data.ContentRegistry;
import com.kagebi.data.def.GearDef;
import com.kagebi.data.def.QuestDef;
import com.kagebi.data.def.WeaponDef;
import com.kagebi.save.QuestLog.State;

/**
 * Codes the player can type in, and what they do to a profile.
 *
 * <p>Pure, like {@code Progression}: a registry, a profile and a string. That
 * is what lets the screen be three lines of drawing and the rules be tested.
 *
 * <p><b>Matched without regard to case.</b> A player typing a code off a
 * screenshot should not have to reproduce its capitals, and a code that only
 * worked in one casing would be reported as broken rather than as mistyped.
 */
public final class Codes {

    /** Everything: the whole roster, every colour, every weapon, every job done. */
    public static final String EVERYTHING = "HungDZ";
    /** Ten million gold, which is more than the shop can spend. */
    public static final String MONEY = "KataMoney";
    public static final int MONEY_AMOUNT = 10_000_000;

    private Codes() {}

    /** Whether a typed string is a code at all, whatever it would do. */
    public static boolean known(String typed) {
        return matches(typed, EVERYTHING) || matches(typed, MONEY);
    }

    private static boolean matches(String typed, String code) {
        return typed != null && typed.trim().equalsIgnoreCase(code);
    }

    /**
     * Applies a code, and says whether it was one.
     *
     * <p>{@link #MONEY} may be used again; {@link #EVERYTHING} does nothing the
     * second time because there is nothing left for it to do, and the screen
     * reads that back from {@link Profile#redeemed} rather than from a failure
     * here - a code that reported itself unknown on the second try would look
     * like a typo.
     */
    public static boolean redeem(String typed, ContentRegistry content, Profile p) {
        if (matches(typed, MONEY)) {
            p.redeemed.add(MONEY);
            // Saturating, not wrapping. Two uses of a ten-million code would
            // otherwise overflow an int into a negative purse, which reads as
            // the code stealing the player's gold.
            p.gold = (int) Math.min(Integer.MAX_VALUE, (long) p.gold + MONEY_AMOUNT);
            return true;
        }
        if (!matches(typed, EVERYTHING)) {
            return false;
        }
        p.redeemed.add(EVERYTHING);
        everything(content, p);
        return true;
    }

    /**
     * Unlocks the lot.
     *
     * <p>Walks the roster and the content rather than listing ids, so a
     * character or a colour added next month is included without anybody
     * remembering to come back here.
     */
    private static void everything(ContentRegistry content, Profile p) {
        for (String id : Assets.Actor.CHARACTERS) {
            p.unlockedCharacters.add(id);
        }
        for (WeaponDef w : content.allWeapons()) {
            p.unlockedWeapons.add(w.id);
        }
        // Every job marked paid, which is what "done all the quests" means: a
        // quest left DONE would have a villager standing there with a mark over
        // their head and nothing to hand over.
        for (QuestDef q : content.allQuests()) {
            p.quests.set(q.id, State.CLAIMED);
        }
        p.tracked = null;
        // One of every piece of gear, so the sheet has something in it. Not the
        // stones: they are the forge's material, and a forge with nothing to do
        // is a screen the code has quietly removed from the game.
        for (GearDef g : content.allGear()) {
            p.stash.add(new OwnedGear(p.nextGearId(), g.id, g.sockets));
        }
    }

    /** Whether this code has been used on this profile before. */
    public static boolean spent(String typed, Profile p) {
        if (matches(typed, EVERYTHING)) {
            return p.redeemed.contains(EVERYTHING);
        }
        return matches(typed, MONEY) && p.redeemed.contains(MONEY);
    }
}
