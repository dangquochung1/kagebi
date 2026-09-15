package com.kagebi.gfx;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.kagebi.Cfg;

/**
 * The village camera's zoom and its clamp. Nothing here calls {@code apply},
 * which needs a GL context; what it pushes is {@link CameraController#snap}.
 */
class CameraControllerTest {

    /** The island as make_island.py builds it: 102 by 62 tiles. */
    private static final float MAP_W = 102 * 16;
    private static final float MAP_H = 62 * 16;
    private static final float FIT = CameraController.fitZoom(MAP_W, MAP_H);

    @Test
    void theWholeIslandFitsByItsTallerSide() {
        assertEquals(992f / Cfg.VIRT_H, FIT, 1e-4f);
        assertEquals(1f, CameraController.fitZoom(320, 176), "a room never zooms in to fit");
    }

    @Test
    void theLadderEndsAtTheFitAndDropsWhatIsPastIt() {
        assertArrayEquals(new float[] {0.5f, 1f, 2f, 3f, FIT}, CameraController.levels(FIT));
        assertArrayEquals(new float[] {0.5f, 1f, 2f, 2.5f}, CameraController.levels(2.5f));
        assertArrayEquals(new float[] {0.5f, 1f}, CameraController.levels(1f));
    }

    @Test
    void notchesClimbTheLadderAndStopAtEitherEnd() {
        CameraController c = new CameraController();
        assertTrue(c.zoomBy(1, FIT));
        assertEquals(2f, c.targetZoom());
        assertTrue(c.zoomBy(5, FIT));
        assertEquals(FIT, c.targetZoom());
        assertFalse(c.zoomBy(1, FIT), "nothing past the whole island");

        assertTrue(c.zoomBy(-10, FIT));
        assertEquals(0.5f, c.targetZoom());
        assertFalse(c.zoomBy(-1, FIT), "nothing closer than half");
        assertFalse(c.zoomBy(0, FIT));
    }

    @Test
    void aChangeOfZoomIsEasedAndArrivesOnTime() {
        CameraController c = new CameraController();
        c.zoomBy(2, FIT);
        c.stepZoom();
        assertTrue(c.zoom() > 1f && c.zoom() < 3f, "part way after one step: " + c.zoom());
        for (int i = 1; i < CameraController.ZOOM_STEPS; i++) {
            c.stepZoom();
        }
        assertEquals(3f, c.zoom());
    }

    @Test
    void zoomedOutTheClampKeepsTheWiderViewInsideTheMap() {
        CameraController c = new CameraController();
        c.zoomBy(1, FIT);
        c.snapZoom();
        c.follow(0f, 0f, MAP_W, MAP_H);
        assertEquals(Cfg.VIRT_W, c.x());         // half of 640
        assertEquals(Cfg.VIRT_H, c.y());         // half of 360
        c.follow(MAP_W, MAP_H, MAP_W, MAP_H);
        assertEquals(MAP_W - Cfg.VIRT_W, c.x());
        assertEquals(MAP_H - Cfg.VIRT_H, c.y());
    }

    @Test
    void atTheWholeIslandTheCameraHoldsStill() {
        CameraController c = new CameraController();
        c.zoomBy(10, FIT);
        c.snapZoom();
        c.follow(100f, 900f, MAP_W, MAP_H);
        float x = c.x();
        float y = c.y();
        c.follow(1500f, 40f, MAP_W, MAP_H);
        assertEquals(x, c.x());
        assertEquals(y, c.y());
        assertEquals(MAP_W / 2f, x);
    }

    @Test
    void thePositionIsRoundedToWholePixelsOfTheView() {
        CameraController c = new CameraController();
        assertEquals(101f, c.snap(100.6f));
        c.zoomBy(2, FIT);                      // 3x
        c.snapZoom();
        assertEquals(102f, c.snap(100.6f));
        c.zoomBy(-10, FIT);                    // half
        c.snapZoom();
        assertEquals(100.5f, c.snap(100.6f));
    }
}
