package dev.ringworld.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class RingWorldLayoutsTest {
    @Test
    void overworldMatchesAcceptedProjectDimensions() {
        assertEquals(61_440, RingWorldLayouts.OVERWORLD.circumferenceBlocks());
        assertEquals(2_000, RingWorldLayouts.OVERWORLD.widthBlocks());
        assertEquals(3_840, RingWorldLayouts.OVERWORLD.circumferenceChunks());
        assertEquals(125, RingWorldLayouts.OVERWORLD.widthChunks());
        assertEquals(-62, RingWorldLayouts.OVERWORLD.minChunkZ());
        assertEquals(62, RingWorldLayouts.OVERWORLD.maxChunkZ());
        assertEquals(-992, RingWorldLayouts.OVERWORLD.minWidthZ());
        assertEquals(1_007, RingWorldLayouts.OVERWORLD.maxWidthZ());
        assertEquals(2_000,
                RingWorldLayouts.OVERWORLD.maxWidthZ() - RingWorldLayouts.OVERWORLD.minWidthZ() + 1);
    }

    @Test
    void finiteBandUsesExactlyOneHundredTwentyFiveCompleteChunkRows() {
        assertFalse(RingWorldLayouts.OVERWORLD.isExteriorChunkZ(-62));
        assertFalse(RingWorldLayouts.OVERWORLD.isExteriorChunkZ(62));
        assertTrue(RingWorldLayouts.OVERWORLD.isExteriorChunkZ(-63));
        assertTrue(RingWorldLayouts.OVERWORLD.isExteriorChunkZ(63));
    }

    @Test
    void netherUsesHalfOverworldCircumference() {
        assertEquals(30_720, RingWorldLayouts.NETHER.circumferenceBlocks());
        assertEquals(1_920, RingWorldLayouts.NETHER.circumferenceChunks());
        assertEquals(125, RingWorldLayouts.NETHER.widthChunks());
    }

    @Test
    void endMatchesOverworldCircumference() {
        assertEquals(61_440, RingWorldLayouts.END.circumferenceBlocks());
        assertEquals(3_840, RingWorldLayouts.END.circumferenceChunks());
    }

    @Test
    void blockCoordinatesWrapCleanlyAcrossTheSeam() {
        assertEquals(61_439, RingWorldLayouts.OVERWORLD.wrapBlockX(-1));
        assertEquals(0, RingWorldLayouts.OVERWORLD.wrapBlockX(61_440));
        assertEquals(1, RingWorldLayouts.OVERWORLD.wrapBlockX(61_441));
    }

    @Test
    void chunkCoordinatesWrapCleanlyAcrossTheSeam() {
        assertEquals(3_839, RingWorldLayouts.OVERWORLD.wrapChunkX(-1));
        assertEquals(0, RingWorldLayouts.OVERWORLD.wrapChunkX(3_840));
        assertEquals(1, RingWorldLayouts.OVERWORLD.wrapChunkX(3_841));
    }

    @Test
    void nearestImageMakesSeamNeighborsLocal() {
        double image = RingWorldLayouts.OVERWORLD.nearestImageX(10.0, 61_435.0);
        assertEquals(61_450.0, image, 1.0e-9);
        assertEquals(15.0,
                Math.abs(RingWorldLayouts.OVERWORLD.shortestCircumferenceDelta(61_435.0, 10.0)),
                1.0e-9);
    }
}
