package dev.ringworld.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class RingBoundaryProfileTest {
    private static final RingWorldLayouts.RingLayout OVERWORLD = RingWorldLayouts.OVERWORLD;

    @Test
    void visibleWallsAreFiveBlocksThickInsideBothEdges() {
        int min = OVERWORLD.minWidthZ();
        int max = OVERWORLD.maxWidthZ();

        for (int depth = 0; depth < 5; depth++) {
            assertTrue(RingBoundaryProfile.isVisibleWallZ(min + depth, OVERWORLD));
            assertTrue(RingBoundaryProfile.isVisibleWallZ(max - depth, OVERWORLD));
        }
        assertFalse(RingBoundaryProfile.isVisibleWallZ(min + 5, OVERWORLD));
        assertFalse(RingBoundaryProfile.isVisibleWallZ(max - 5, OVERWORLD));
    }

    @Test
    void barriersSitImmediatelyOutsideThePlayableBand() {
        assertEquals(-993, RingBoundaryProfile.lowerBarrierZ(OVERWORLD));
        assertEquals(1_008, RingBoundaryProfile.upperBarrierZ(OVERWORLD));
        assertTrue(RingBoundaryProfile.isBarrierZ(-993, OVERWORLD));
        assertTrue(RingBoundaryProfile.isBarrierZ(1_008, OVERWORLD));
        assertFalse(RingBoundaryProfile.isBarrierZ(-992, OVERWORLD));
        assertFalse(RingBoundaryProfile.isBarrierZ(1_007, OVERWORLD));
    }

    @Test
    void visibleWallRisesExactlyTenBlocksAboveTerrain() {
        assertEquals(75, RingBoundaryProfile.visibleWallTopExclusive(64, 320));
        assertEquals(320, RingBoundaryProfile.visibleWallTopExclusive(318, 320));
    }
}
