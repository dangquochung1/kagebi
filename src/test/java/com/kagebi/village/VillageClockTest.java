package com.kagebi.village;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.kagebi.save.VillageState;

/** The village's time: frame by frame, and never more than a stall's worth at once. */
class VillageClockTest {

    @Test
    void theClockMovesByEachFrameUpToAQuarterOfASecond() {
        VillageState v = new VillageState();
        VillageClock.advance(v, 0.016f);
        assertEquals(0.016, v.clock, 1e-6);
        VillageClock.advance(v, 30f);
        assertEquals(0.266, v.clock, 1e-6, "a stalled frame counts as a quarter of a second");
    }

    @Test
    void aFrameOfNoTimeOrOfNonsenseMovesNothing() {
        VillageState v = new VillageState();
        VillageClock.advance(v, 0f);
        VillageClock.advance(v, -1f);
        VillageClock.advance(v, Float.NaN);
        assertEquals(0.0, v.clock);
    }
}
