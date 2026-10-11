package dev.ringworld.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class RingNoiseCoordinatesTest {
    private static final RingWorldLayouts.RingLayout OVERWORLD = RingWorldLayouts.OVERWORLD;

    @Test
    void acceptedWorldHasSafeAnnularNoiseRadius() {
        assertTrue(RingTerrainNoiseMapping.minimumSampledRadius(OVERWORLD)
                > RingTerrainNoiseMapping.MINIMUM_NOISE_RADIUS_BLOCKS);
    }

    @Test
    void oneFullCircumferenceProducesIdenticalNoiseCoordinates() {
        RingNoiseCoordinates coordinates = RingNoiseCoordinates.forLayout(OVERWORLD);
        int circumference = OVERWORLD.circumferenceBlocks();

        for (int sourceZ : new int[]{OVERWORLD.minWidthZ(), 0, OVERWORLD.maxWidthZ()}) {
            for (int sourceX : new int[]{0, 1, 4_096, 30_720, 61_439}) {
                assertEquals(coordinates.noiseX(sourceX, sourceZ),
                        coordinates.noiseX(sourceX + circumference, sourceZ));
                assertEquals(coordinates.noiseZ(sourceX, sourceZ),
                        coordinates.noiseZ(sourceX + circumference, sourceZ));
            }
        }
    }

    @Test
    void negativeAndPositiveLogicalLapsUseSameTerrainIdentity() {
        RingNoiseCoordinates coordinates = RingNoiseCoordinates.forLayout(OVERWORLD);
        int circumference = OVERWORLD.circumferenceBlocks();
        int x = 12_345;
        int z = 321;

        assertEquals(coordinates.noiseX(x, z), coordinates.noiseX(x - circumference * 3, z));
        assertEquals(coordinates.noiseZ(x, z), coordinates.noiseZ(x + circumference * 5, z));
    }

    @Test
    void annularMappingRetainsFiniteWidthInsteadOfCollapsingIt() {
        RingNoiseCoordinates coordinates = RingNoiseCoordinates.forLayout(OVERWORLD);
        int x = 15_360;
        assertNotEquals(coordinates.noiseX(x, -500), coordinates.noiseX(x, 500));
    }

    @Test
    void completeMappingCanonicalizesCarverSeedChunksAcrossSeamCharts() {
        int circumferenceChunks = OVERWORLD.circumferenceChunks();
        assertEquals(0, RingTerrainNoiseMapping.carverSeedChunkX(
                OVERWORLD, RingTerrainNoiseMapping.CURRENT, circumferenceChunks));
        assertEquals(circumferenceChunks - 1,
                RingTerrainNoiseMapping.carverSeedChunkX(
                        OVERWORLD, RingTerrainNoiseMapping.CURRENT, -1));
    }
}
