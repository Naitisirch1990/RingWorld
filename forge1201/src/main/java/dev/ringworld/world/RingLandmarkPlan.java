package dev.ringworld.world;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Deterministic high-level landmark schedule for the 61,440-block Overworld ring.
 * Generation code will later resolve each slot against terrain and biome suitability.
 */
public final class RingLandmarkPlan {
    public enum LandmarkType {
        GRAND_START_CITY,
        ABANDONED_CITY,
        MILITARY_BASE,
        GIANT_DAM,
        FORTIFIED_CASTLE,
        INDUSTRIAL_COMPLEX,
        DESTROYED_AIRPORT,
        PYRAMID_CITY,
        PORT_CITY,
        OPEN_PIT_MINE,
        UNDERGROUND_LAB,
        FLOODED_CITY,
        MOUNTAIN_OBSERVATORY,
        RINGWORLD_INSTALLATION,
        FINAL_CITY
    }

    public record Landmark(int slot, int canonicalX, LandmarkType type) {}

    private static final List<LandmarkType> OVERWORLD_TYPES = List.of(
            LandmarkType.GRAND_START_CITY,
            LandmarkType.ABANDONED_CITY,
            LandmarkType.MILITARY_BASE,
            LandmarkType.GIANT_DAM,
            LandmarkType.FORTIFIED_CASTLE,
            LandmarkType.INDUSTRIAL_COMPLEX,
            LandmarkType.DESTROYED_AIRPORT,
            LandmarkType.PYRAMID_CITY,
            LandmarkType.PORT_CITY,
            LandmarkType.OPEN_PIT_MINE,
            LandmarkType.UNDERGROUND_LAB,
            LandmarkType.FLOODED_CITY,
            LandmarkType.MOUNTAIN_OBSERVATORY,
            LandmarkType.RINGWORLD_INSTALLATION,
            LandmarkType.FINAL_CITY);

    private RingLandmarkPlan() {}

    public static List<Landmark> overworld(long seed) {
        int circumference = RingWorldLayouts.OVERWORLD.circumferenceBlocks();
        int spacing = RingWorldLayouts.MEGA_POI_SPACING_BLOCKS;
        if (circumference / spacing != OVERWORLD_TYPES.size()) {
            throw new IllegalStateException("Landmark schedule no longer matches Overworld circumference");
        }

        List<Landmark> result = new ArrayList<>(OVERWORLD_TYPES.size());
        for (int slot = 0; slot < OVERWORLD_TYPES.size(); slot++) {
            int base = slot * spacing;
            int jitter = slot == 0 ? 0 : deterministicJitter(seed, slot);
            int x = Math.floorMod(base + jitter, circumference);
            result.add(new Landmark(slot, x, OVERWORLD_TYPES.get(slot)));
        }
        return Collections.unmodifiableList(result);
    }

    private static int deterministicJitter(long seed, int slot) {
        long mixed = seed
                ^ (0x9E3779B97F4A7C15L * (slot + 1L))
                ^ 0x52494E47574F524CL;
        Random random = new Random(mixed);
        int radius = RingWorldLayouts.MEGA_POI_JITTER_BLOCKS;
        return random.nextInt(radius * 2 + 1) - radius;
    }
}
