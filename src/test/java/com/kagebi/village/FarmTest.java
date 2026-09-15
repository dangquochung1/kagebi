package com.kagebi.village;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.kagebi.data.VillageCatalog;
import com.kagebi.save.VillageState;

/** The field: sowing, growing, picking, and the level that opens more of it. */
class FarmTest {

    private VillageCatalog cat;
    private VillageState v;

    @BeforeEach
    void setUp() {
        cat = TestCatalog.small();
        v = new VillageState();
    }

    @Test
    void aNewFarmIsLevelOneWithItsFirstPlotsOpen() {
        assertEquals(1, Farm.level(cat, v));
        assertEquals(2, Farm.openPlots(cat, v));
        assertEquals(4, Farm.allPlots(cat));
        v.harvests = 3;
        assertEquals(2, Farm.level(cat, v));
        assertEquals(4, Farm.openPlots(cat, v));
    }

    @Test
    void sowingTakesASeedAndAnOpenBarePlot() {
        VillageCatalog.Crop carrot = cat.crop("carrot");
        assertFalse(Farm.sow(cat, v, 0, carrot), "no seed in hand");
        v.seeds.put("carrot", 2);
        assertTrue(Farm.sow(cat, v, 0, carrot));
        assertEquals(1, v.seeds.get("carrot", 0));
        assertFalse(Farm.sow(cat, v, 0, carrot), "the plot is already growing something");
        assertFalse(Farm.sow(cat, v, 2, carrot), "plot 2 opens at level two");
        assertTrue(Farm.sow(cat, v, 1, carrot));
        assertFalse(v.seeds.containsKey("carrot"), "a seed count that reaches nothing is gone");
    }

    @Test
    void aSeedFromAHigherLevelWaitsForTheFarm() {
        v.seeds.put("pumpkin", 1);
        assertFalse(Farm.sow(cat, v, 0, cat.crop("pumpkin")));
        v.harvests = 3;
        assertTrue(Farm.sow(cat, v, 0, cat.crop("pumpkin")));
    }

    @Test
    void aCropMovesAPictureEveryStageAndWaitsOnceRipe() {
        v.seeds.put("carrot", 1);
        v.clock = 1000;
        Farm.sow(cat, v, 0, cat.crop("carrot"));
        assertEquals(0, Farm.stage(cat, v, 0));
        assertEquals(500.0, Farm.untilRipe(cat, v, 0), 1e-9);
        v.clock = 1099;
        assertEquals(0, Farm.stage(cat, v, 0));
        v.clock = 1100;
        assertEquals(1, Farm.stage(cat, v, 0));
        v.clock = 1499;
        assertEquals(4, Farm.stage(cat, v, 0));
        assertFalse(Farm.ripe(cat, v, 0));
        v.clock = 1500;
        assertTrue(Farm.ripe(cat, v, 0));
        v.clock = 100_000;
        assertEquals(Farm.RIPE, Farm.stage(cat, v, 0), "nothing rots");
        assertEquals(0.0, Farm.untilRipe(cat, v, 0), 1e-9);
        assertEquals(-1, Farm.stage(cat, v, 1), "bare soil");
    }

    @Test
    void pickingARipeCropFillsTheStorehouseAndCountsTowardTheLevel() {
        v.seeds.put("carrot", 1);
        Farm.sow(cat, v, 0, cat.crop("carrot"));
        v.clock = 300;
        assertEquals(0, Farm.harvest(cat, v, 0), "not ripe yet");
        assertEquals(0, v.harvests);
        v.clock = 500;
        assertEquals(1, Farm.harvest(cat, v, 0));
        assertEquals(1, v.stock.get("carrot", 0));
        assertEquals(1, v.harvests);
        assertEquals(-1, Farm.stage(cat, v, 0), "the plot is bare again");
        assertEquals(0, Farm.harvest(cat, v, 0), "and cannot be picked twice");
    }

    @Test
    void theFirstVisitPlantsTheSceneOnceAtTheStagesItShows() {
        v.clock = 1000;
        Farm.plantScene(cat, v, new String[] {"carrot", null, "pumpkin", "turnip"}, new int[] {5, 0, 2, 3});
        assertEquals(Farm.RIPE, Farm.stage(cat, v, 0));
        assertEquals(-1, Farm.stage(cat, v, 1));
        assertEquals(2, Farm.stage(cat, v, 2), "a level-two crop the scene shows is there at level one");
        assertEquals(-1, Farm.stage(cat, v, 3), "a crop the catalog does not know leaves its plot bare");
        assertEquals(1, Farm.harvest(cat, v, 0), "and a ripe one can be picked at once");

        // Sown by the scene at 600, two stages in; ripe three stages of 200 later.
        v.clock = 1599;
        assertEquals(0, Farm.harvest(cat, v, 2));
        v.clock = 1600;
        assertEquals(2, Farm.harvest(cat, v, 2), "a plot the farm has not opened can still be picked");
        Farm.plantScene(cat, v, new String[] {"pumpkin"}, new int[] {5});
        assertEquals(-1, Farm.stage(cat, v, 0), "a second visit plants nothing");
    }
}
