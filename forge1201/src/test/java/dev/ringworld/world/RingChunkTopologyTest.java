package dev.ringworld.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class RingChunkTopologyTest {
    private static final RingWorldLayouts.RingLayout OVERWORLD = RingWorldLayouts.OVERWORLD;

    @Test
    void chunkSeamNeighborsHaveDistanceOne() {
        RingChunkTopology topology = new RingChunkTopology(OVERWORLD);
        assertEquals(1, topology.distanceX(3_839, 0));
        assertEquals(2, topology.distanceX(3_839, 1));
    }

    @Test
    void arbitraryLogicalLapsCanonicalizeToOneStorageChunk() {
        RingChunkTopology topology = new RingChunkTopology(OVERWORLD);
        assertEquals(17, topology.canonicalX(17));
        assertEquals(17, topology.canonicalX(3_840 + 17));
        assertEquals(17, topology.canonicalX(-3_840 + 17));
    }

    @Test
    void simulationDistanceCrossesTheSeam() {
        assertTrue(RingChunkCoordinates.isWithinSimulationDistance(
                3_839, 0, 0, 0, 1, OVERWORLD));
        assertFalse(RingChunkCoordinates.isWithinSimulationDistance(
                3_838, 0, 0, 0, 1, OVERWORLD));
    }

    @Test
    void nearestChunkImageKeepsStreamingContinuousAcrossMultipleLaps() {
        assertEquals(3_841,
                RingChunkCoordinates.nearestImageChunkX(1, 3_839, OVERWORLD));
        assertEquals(7_681,
                RingChunkCoordinates.nearestImageChunkX(1, 7_679, OVERWORLD));
        assertEquals(-3_839,
                RingChunkCoordinates.nearestImageChunkX(1, -3_839, OVERWORLD));
    }

    @Test
    void vanillaShapedViewDistanceUsesPeriodicX() {
        RingChunkTopology topology = new RingChunkTopology(OVERWORLD);
        assertTrue(topology.isWithinVanillaDistance(0, 0, 6, 3_839, 0, false));
        assertTrue(topology.isWithinVanillaDistance(3_839, 0, 6, 0, 0, false));
        assertFalse(topology.isWithinVanillaDistance(0, 0, 2, 3_835, 0, false));
    }
}
