package com.kagebi.save;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.kagebi.assets.Assets;
import com.kagebi.data.ContentRegistry;
import com.kagebi.data.def.GearDef;
import com.kagebi.data.def.QuestDef;
import com.kagebi.data.def.WeaponDef;
import com.kagebi.save.QuestLog.State;

/** What a typed code does to a profile, and what it must not do twice. */
class CodesTest {

    private ContentRegistry content;
    private Profile p;

    @BeforeEach
    void setUp() {
        content = new ContentRegistry();
        content.put(new WeaponDef("katana", "n", "d", "s", 1, 7, 22, 9, 4, 3, 11, 40, 0, null));
        content.put(new WeaponDef("axe", "n", "d", "s", 1, 9, 20, 11, 6, 3, 14, 60, 0, null));
        content.put(new GearDef("hood", "n", "d", 1, GearDef.Slot.HEAD, 1, 2,
            new String[] {"max_hp_add"}, new float[] {5f}, 100));
        content.put(new QuestDef("slay", "n", "d", null, null,
            new QuestDef.Step[] {new QuestDef.Step(QuestDef.Kind.KILL, "slime", 3)},
            100, new String[0], null, null));
        p = new Profile();
    }

    @Test
    void anUnknownStringIsNotACode() {
        assertFalse(Codes.redeem("please", content, p));
        assertFalse(Codes.known("please"));
        assertEquals(0, p.gold);
    }

    @Test
    void theMoneyCodePays() {
        assertTrue(Codes.redeem(Codes.MONEY, content, p));
        assertEquals(Codes.MONEY_AMOUNT, p.gold);
    }

    /** Typed off a screenshot, capitals and all, or none of them. */
    @Test
    void caseDoesNotMatter() {
        assertTrue(Codes.redeem("katamoney", content, p));
        assertTrue(Codes.redeem("  HUNGDZ  ", content, p));
        assertTrue(p.unlockedCharacters.contains("ninjafire"));
    }

    /**
     * Two uses of a ten-million code must not overflow an int into a negative
     * purse, which would read as the code stealing the player's gold.
     */
    @Test
    void theMoneyCodeSaturatesRatherThanWrapping() {
        p.gold = Integer.MAX_VALUE - 10;
        Codes.redeem(Codes.MONEY, content, p);
        assertEquals(Integer.MAX_VALUE, p.gold);
        assertTrue(p.gold > 0);
    }

    @Test
    void theEverythingCodeOpensTheWholeRoster() {
        Codes.redeem(Codes.EVERYTHING, content, p);
        for (String id : Assets.Actor.CHARACTERS) {
            assertTrue(p.unlockedCharacters.contains(id), id);
        }
        assertTrue(p.unlockedWeapons.contains("axe"));
    }

    /**
     * Claimed, not merely done. A quest left DONE would leave a villager with a
     * mark over their head and nothing to hand over.
     */
    @Test
    void theEverythingCodeFinishesEveryJob() {
        Codes.redeem(Codes.EVERYTHING, content, p);
        assertTrue(p.quests.is("slay", State.CLAIMED));
        assertEquals(null, p.tracked);
    }

    @Test
    void theEverythingCodeStocksTheStashWithItsHoles() {
        Codes.redeem(Codes.EVERYTHING, content, p);
        assertEquals(1, p.stash.size);
        assertEquals(2, p.stash.first().sockets.length);
    }

    @Test
    void aCodeIsRememberedSoTheScreenCanSayItWasUsed() {
        assertFalse(Codes.spent(Codes.MONEY, p));
        Codes.redeem(Codes.MONEY, content, p);
        assertTrue(Codes.spent(Codes.MONEY, p));
        assertFalse(Codes.spent(Codes.EVERYTHING, p));
    }

    /** The money one may be used again; it just keeps paying. */
    @Test
    void theMoneyCodeStillWorksTheSecondTime() {
        Codes.redeem(Codes.MONEY, content, p);
        Codes.redeem(Codes.MONEY, content, p);
        assertEquals(2L * Codes.MONEY_AMOUNT, (long) p.gold);
    }
}
