package dev.ringworld.world;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class RingWorldLayoutsTest {
    @Test
    void overworldMatchesAcceptedProjectDimensions() {
        assertEquals(61_440, RingWorldLayouts.OVERWORLD.circumferenceBlocks());
        assertEquals(2_000, RingWorldLayouts.OVERWORLD.widthBlocks());
        assertEquals(3_840, RingWorldLayouts.OVERWORLD.circumferenceChunks());
        assertEquals(125, RingWorldLayouts.OVERWORLD.widthChunks());
        assertEquals(-1_000, RingWorldLayouts.OVERWORLD.minWidthZ());
        assertEquals(999, RingWorldLayouts.OVERWORLD.maxWidthZ());
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
    void nearestImageMakesSeamNeighborsLocal() {
        double image = RingWorldLayouts.OVERWORLD.nearestImageX(10.0, 61_435.0);
        assertEquals(61_450.0, image, 1.0e-9);
        assertEquals(15.0,
                Math.abs(RingWorldLayouts.OVERWORLD.shortestCircumferenceDelta(61_435.0, 10.0)),
                1.0e-9);
    }
}
