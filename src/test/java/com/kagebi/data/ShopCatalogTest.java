package com.kagebi.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.kagebi.assets.Assets;
import com.kagebi.run.RunSummary;
import com.kagebi.save.Profile;

/** The village shop's rules, over a plain Profile. */
class ShopCatalogTest {

    private ShopCatalog shop;
    private Profile p;

    @BeforeEach
    void setUp() {
        shop = ShopCatalog.parse(ContentLoaderTest.DISK.apply(Assets.DATA_DIR));
        p = new Profile();
    }

    @Test
    void theShippedShopHasSevenTracksAndElevenUnlocks() {
        assertEquals(7, shop.upgrades().size);
        // Five ninja and six weapons. The sixth ninja is free, so it has no
        // row - which is what lets it be the one character with no perk.
        assertEquals(11, shop.unlocks().size);
    }

    @Test
    void anUpgradeClimbsItsPriceLadderAndThenStops() {
        ShopCatalog.Upgrade vigor = shop.upgrade("vigor");
        p.gold = 100_000;
        int spent = 0;
        for (int level = 0; level < vigor.maxLevel; level++) {
            int price = ShopCatalog.cost(vigor, p);
            assertEquals(vigor.costs[level], price);
            assertTrue(ShopCatalog.buy(vigor, p));
            spent += price;
            assertEquals(level + 1, p.upgrade("vigor"));
        }
        assertEquals(-1, ShopCatalog.cost(vigor, p), "maxed");
        assertFalse(ShopCatalog.buy(vigor, p), "cannot buy past the top");
        assertEquals(100_000 - spent, p.gold);
    }

    @Test
    void aPurchaseThatCannotBeAffordedChangesNothing() {
        ShopCatalog.Upgrade vigor = shop.upgrade("vigor");
        p.gold = vigor.costs[0] - 1;
        assertFalse(ShopCatalog.buy(vigor, p));
        assertEquals(vigor.costs[0] - 1, p.gold);
        assertEquals(0, p.upgrade("vigor"));
    }

    @Test
    void multipliersCompoundAndEverythingElseAdds() {
        assertEquals(0f, shop.upgradeValue("max_hp_add", p), "no levels, additive identity");
        assertEquals(1f, shop.upgradeValue("melee_damage_mult", p), "no levels, multiplicative identity");
        p.upgrades.put("vigor", 3);
        p.upgrades.put("edge", 2);
        assertEquals(45f, shop.upgradeValue("max_hp_add", p), 1e-4);
        assertEquals(1.08f * 1.08f, shop.upgradeValue("melee_damage_mult", p), 1e-4);
    }

    /** A hand-edited save with a level above the track's top is capped, not trusted. */
    @Test
    void anImpossibleLevelIsCappedAtTheTrackMaximum() {
        p.upgrades.put("vigor", 99);
        assertEquals(60f, shop.upgradeValue("max_hp_add", p), 1e-4);
    }

    @Test
    void anUnlockNeedsBothTheMilestoneAndTheGold() {
        ShopCatalog.Unlock red = shop.unlock("ninjared");
        assertEquals("deepest_floor", red.requirement);
        p.gold = red.cost;
        assertFalse(ShopCatalog.requirementMet(red, p));
        assertFalse(ShopCatalog.buy(red, p), "gold alone is not enough");

        p.deepestFloor = red.requirementValue;
        p.gold = red.cost - 1;
        assertTrue(ShopCatalog.requirementMet(red, p));
        assertFalse(ShopCatalog.buy(red, p), "and the milestone alone is not either");

        p.gold = red.cost;
        assertTrue(ShopCatalog.buy(red, p));
        assertTrue(p.unlockedCharacters.contains("ninjared"));
        assertEquals(0, p.gold);
        assertFalse(ShopCatalog.canBuy(red, p), "and it cannot be bought twice");
    }

    @Test
    void everyRequirementKindReadsTheRightField() {
        for (ShopCatalog.Unlock u : shop.unlocks()) {
            Profile q = new Profile();
            assertFalse(ShopCatalog.requirementMet(u, q) && u.requirementValue > 0,
                u.id + " is met by a fresh profile");
            q.deepestFloor = 5;
            q.wins = 1;
            q.runs = 99;
            for (int i = 0; i < 30; i++) {
                q.bestiary.add("enemy" + i);
            }
            assertTrue(ShopCatalog.requirementMet(u, q), u.id + " is not met by a veteran profile");
        }
    }

    @Test
    void aWeaponUnlockGoesIntoTheWeaponSet() {
        ShopCatalog.Unlock kunai = shop.unlock("kunai");
        p.runs = kunai.requirementValue;
        p.gold = kunai.cost;
        assertTrue(ShopCatalog.buy(kunai, p));
        assertTrue(p.unlockedWeapons.contains("kunai"));
        assertFalse(p.unlockedCharacters.contains("kunai"));
    }

    @Test
    void everyLockedCharacterCarriesAPerkAndTheStarterHasNone() {
        for (String ch : Assets.Actor.CHARACTERS) {
            if (ch.equals(Assets.Actor.DEFAULT_CHARACTER)) {
                assertNull(shop.character(ch));
            } else {
                assertTrue(shop.character(ch).effect != null, ch);
            }
        }
    }

    /**
     * No two of them do the same thing.
     *
     * <p>The point of the roster, and the thing it failed at last time it was
     * six entries long: five perks were invented so that five recolours could
     * be priced, and what a player was choosing between was a hue. Five
     * different effects is the cheapest possible check that the choice is a
     * real one.
     */
    @Test
    void noTwoCharactersShareAPerk() {
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (String ch : Assets.Actor.CHARACTERS) {
            ShopCatalog.Unlock u = shop.character(ch);
            if (u != null) {
                assertTrue(seen.add(u.effect), ch + " repeats the perk " + u.effect);
            }
        }
    }

    /**
     * The end screens bank through the shop so there is one way to write a run
     * into a profile, and that way still keeps every coin and counts every run.
     */
    @Test
    void bankingThroughTheShopKeepsEveryCoinAndCountsEveryRun() {
        for (int i = 0; i < 5; i++) {
            shop.bank(p, new RunSummary(2, 30, 100, 0, 400f, false));
        }
        assertEquals(500, p.gold);
        assertEquals(5, p.runs);
    }
}
