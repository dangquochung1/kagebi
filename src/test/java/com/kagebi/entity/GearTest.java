package com.kagebi.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.kagebi.combat.Modifiers;
import com.kagebi.data.ContentRegistry;
import com.kagebi.data.def.GearDef;
import com.kagebi.data.def.GemDef;
import com.kagebi.save.OwnedGear;
import com.kagebi.save.Profile;

/**
 * What wearing something does to the numbers.
 *
 * <p>Built from hand-made defs rather than the shipped content, so a balance
 * pass that changes what a Tempered Hood is worth cannot break a test about
 * whether wearing one counts at all.
 */
class GearTest {

    private ContentRegistry content;
    private Profile profile;

    private static GearDef gear(String id, GearDef.Slot slot, int sockets,
                                String[] effects, float[] magnitudes) {
        return new GearDef(id, "n", "d", 1, slot, 1, sockets, effects, magnitudes, 100);
    }

    @BeforeEach
    void setUp() {
        content = new ContentRegistry();
        content.put(gear("hood", GearDef.Slot.HEAD, 2,
            new String[] {"max_hp_add", "crit_chance_add"}, new float[] {20f, 0.05f}));
        content.put(gear("robe", GearDef.Slot.BODY, 0,
            new String[] {"armour_add"}, new float[] {3f}));
        content.put(gear("wraps", GearDef.Slot.HANDS, 0,
            new String[] {"damage_mult"}, new float[] {1.10f}));
        content.put(new GemDef("ruby", "n", 1, GemDef.Colour.RED, 1,
            "damage_mult", 1.05f, 10));
        content.put(new GemDef("moss", "n", 1, GemDef.Colour.GREEN, 1,
            "max_hp_add", 5f, 10));
        profile = new Profile();
    }

    /** Puts a piece in the stash and wears it, the way the equipment screen will. */
    private OwnedGear wear(String defId) {
        GearDef def = content.gear(defId);
        OwnedGear piece = new OwnedGear(profile.nextGearId(), defId, def.sockets);
        profile.stash.add(piece);
        profile.equipped.put(def.slot.name(), piece.instance);
        return piece;
    }

    private Modifiers mods() {
        return Loadout.of(null, content, null, profile);
    }

    @Test
    void anEmptyProfileChangesNothing() {
        Modifiers m = mods();
        assertEquals(0, m.maxHpAdd());
        assertEquals(0, m.armourAdd());
        assertEquals(1f, m.outgoingMult(1f), 1e-4);
    }

    @Test
    void wearingAPieceAddsItsEffects() {
        wear("hood");
        Modifiers m = mods();
        assertEquals(20, m.maxHpAdd());
        assertEquals(0.05f, m.critChanceAdd(), 1e-4);
    }

    /** Nothing counts until it is on. A full stash is not a build. */
    @Test
    void owningWithoutWearingCountsForNothing() {
        GearDef def = content.gear("hood");
        profile.stash.add(new OwnedGear(profile.nextGearId(), "hood", def.sockets));
        assertEquals(0, mods().maxHpAdd());
    }

    @Test
    void everySlotIsCountedAtOnce() {
        wear("hood");
        wear("robe");
        wear("wraps");
        Modifiers m = mods();
        assertEquals(20, m.maxHpAdd());
        assertEquals(3, m.armourAdd());
        assertEquals(1.10f, m.outgoingMult(1f), 1e-4);
    }

    /**
     * Armour was plumbed through combat from the beginning and nothing ever
     * wrote to it, so every hit in the game subtracted zero. This is the test
     * that says it is connected.
     */
    @Test
    void armourReachesTheStatTheDamageCodeReads() {
        wear("robe");
        assertEquals(3, mods().armourAdd());
    }

    @Test
    void stonesSetIntoAPieceCountAsWell() {
        OwnedGear hood = wear("hood");
        hood.sockets[0] = "moss";
        hood.sockets[1] = "moss";
        assertEquals(30, mods().maxHpAdd(), "20 from the hood and 5 from each stone");
    }

    /** Multiplicative effects compound rather than summing; two 1.05s are 1.1025. */
    @Test
    void twoMultipliersCompound() {
        OwnedGear wraps = wear("wraps");
        assertEquals(0, wraps.sockets.length, "this one has no sockets");
        OwnedGear hood = wear("hood");
        hood.sockets[0] = "ruby";
        assertEquals(1.10f * 1.05f, mods().outgoingMult(1f), 1e-4);
    }

    @Test
    void anEmptySocketIsIgnoredAndKeepsItsPlace() {
        OwnedGear hood = wear("hood");
        hood.sockets[1] = "ruby";
        assertNull(hood.sockets[0]);
        assertEquals(0, hood.freeSocket());
        assertEquals(1, hood.stonesSet());
        assertEquals(1.05f, mods().outgoingMult(1f), 1e-4);
    }

    /**
     * A slot pointing at a piece that is no longer in the stash is not worn.
     * The save reader drops these, but a forge that melted a worn piece could
     * make one at runtime.
     */
    @Test
    void aSlotPointingAtNothingIsNotWorn() {
        profile.equipped.put("HEAD", 999);
        assertNull(profile.worn("HEAD"));
        assertEquals(0, mods().maxHpAdd());
    }

    @Test
    void materialCountsDropTheKeyWhenTheyReachZero() {
        profile.addMaterial("ruby", 3);
        assertEquals(3, profile.material("ruby"));
        profile.addMaterial("ruby", -3);
        assertEquals(0, profile.material("ruby"));
        assertTrue(!profile.materials.containsKey("ruby"), "a zero count is not written");
    }

    @Test
    void instanceIdsAreNeverReused() {
        int first = profile.nextGearId();
        int second = profile.nextGearId();
        assertTrue(second > first);
    }
}
