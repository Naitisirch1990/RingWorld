package dev.ringworld.world;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class RingChunkWindowTest {
    private static final RingWorldLayouts.RingLayout OVERWORLD = RingWorldLayouts.OVERWORLD;

    @Test
    void seamWindowContainsChunksFromBothCanonicalEdges() {
        RingChunkWindow window = new RingChunkWindow(3_839, 0, 6, OVERWORLD);
        assertTrue(window.contains(3_839, 0, false));
        assertTrue(window.contains(0, 0, false));
        assertTrue(window.contains(1, 0, false));
    }

    @Test
    void finiteBandRejectsExteriorChunkRows() {
        RingChunkWindow window = new RingChunkWindow(0, 62, 8, OVERWORLD);
        assertTrue(window.contains(0, 62, false));
        assertFalse(window.contains(0, 63, true));
        assertFalse(window.contains(0, -63, true));
        assertTrue(window.chunks().stream()
                .allMatch(chunk -> chunk.z() >= -62 && chunk.z() <= 62));
    }

    @Test
    void naturalSeamStepDoesNotRequireFullClientRekey() {
        RingChunkWindow before = new RingChunkWindow(3_839, 0, 12, OVERWORLD);
        RingChunkWindow after = new RingChunkWindow(3_840, 0, 12, OVERWORLD);
        assertFalse(after.requiresFullRekeyFrom(before));
    }

    @Test
    void longTeleportRequiresFullClientRekey() {
        RingChunkWindow before = new RingChunkWindow(100, 0, 6, OVERWORLD);
        RingChunkWindow after = new RingChunkWindow(1_000, 0, 6, OVERWORLD);
        assertTrue(after.requiresFullRekeyFrom(before));
    }
}
