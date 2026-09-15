package com.kagebi.village;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.badlogic.gdx.utils.ObjectIntMap;
import com.kagebi.data.VillageCatalog;
import com.kagebi.save.VillageState;

/** What the workers make while nobody is watching, and what stops them. */
class WorkshopsTest {

    private VillageCatalog cat;
    private VillageState v;
    private VillageCatalog.Workshop forest;
    private VillageCatalog.Workshop ranch;

    @BeforeEach
    void setUp() {
        cat = TestCatalog.small();
        v = new VillageState();
        forest = cat.workshop("forest");
        ranch = cat.workshop("ranch");
    }

    @Test
    void aWorkerMakesOneUnitEveryPeriodFromWhenTheyAreFirstAskedAbout() {
        v.clock = 100;
        assertEquals(0, Workshops.ready(cat, v, forest), "nothing is made before anyone asks");
        v.clock = 125;
        assertEquals(2, Workshops.ready(cat, v, forest));
        assertEquals(5.0, Workshops.untilNext(cat, v, forest), 1e-9);
    }

    @Test
    void aFullWorkshopWaitsAndStartsAgainFromTheCollection() {
        Workshops.ready(cat, v, forest);
        v.clock = 10_000;
        assertEquals(5, Workshops.ready(cat, v, forest), "a long absence fills it, and no more");
        assertEquals(-1.0, Workshops.untilNext(cat, v, forest), 1e-9);

        ObjectIntMap<String> got = Workshops.collect(cat, v, forest);
        assertEquals(5, got.get("wood", 0));
        assertEquals(5, v.stock.get("wood", 0));
        assertEquals(0, Workshops.ready(cat, v, forest));

        v.clock = 10_009;
        assertEquals(0, Workshops.ready(cat, v, forest), "the next log is a whole period after collecting");
        v.clock = 10_010;
        assertEquals(1, Workshops.ready(cat, v, forest));
    }

    @Test
    void aToolMakesTheWorkerFasterAndLetsThemHoldMore() {
        Workshops.ready(cat, v, forest);
        v.tools.put("axe", 1);
        assertEquals(10 / 1.5, Workshops.period(cat, v, forest), 1e-9);
        assertEquals(7, Workshops.capacity(cat, v, forest));
        v.clock = 21;
        assertEquals(3, Workshops.ready(cat, v, forest), "21 seconds at six and two-thirds each");
        v.tools.put("axe", 9);
        assertEquals(7, Workshops.capacity(cat, v, forest), "a level past the tool's top counts as its top");
    }

    @Test
    void whatAUnitTurnsOutToBeFollowsTheWeightsAndIsTheSameEveryTime() {
        VillageState again = new VillageState();
        Workshops.ready(cat, v, ranch);
        Workshops.ready(cat, again, ranch);
        for (int i = 0; i < 4000; i++) {
            v.clock += 180;
            again.clock += 180;
            Workshops.collect(cat, v, ranch);
            Workshops.collect(cat, again, ranch);
        }
        int eggs = v.stock.get("egg", 0);
        int milk = v.stock.get("milk", 0);
        assertEquals(12_000, eggs + milk, "three units every three minutes");
        assertEquals(0.75, eggs / 12_000.0, 0.02, "eggs are weighed three to one against milk");
        assertEquals(eggs, again.stock.get("egg", 0), "the same save collects the same goods");
    }

    @Test
    void anOverfullShelfOrAStartInTheFutureIsBroughtBackToSense() {
        VillageState.Work work = Workshops.work(v, forest);
        work.held = 99;
        assertEquals(5, Workshops.ready(cat, v, forest));

        VillageState later = new VillageState();
        later.clock = 50;
        Workshops.work(later, forest).since = 400;
        assertEquals(0, Workshops.ready(cat, later, forest));
        later.clock = 60;
        assertEquals(1, Workshops.ready(cat, later, forest), "counted from the clock, not from 400");
    }
}
