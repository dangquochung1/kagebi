package com.kagebi.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** The marks the village draws over its workers. */
class HubMarksTest {

    /**
     * The pack's green bar has seven pictures, empty to full. The full one is
     * kept for a full workshop, so a bar that looks full means come and collect,
     * never "nearly".
     */
    @Test
    void theBarIsFullOnlyWhenTheWorkshopIs() {
        assertEquals(0, HubScreen.barFrame(0f));
        assertEquals(0, HubScreen.barFrame(0.16f));
        assertEquals(1, HubScreen.barFrame(0.17f));
        assertEquals(3, HubScreen.barFrame(0.5f));
        assertEquals(5, HubScreen.barFrame(0.999f), "nearly is not full");
        assertEquals(6, HubScreen.barFrame(1f));
        assertEquals(0, HubScreen.barFrame(-1f), "out of range is clamped");
    }
}
