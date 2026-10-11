package dev.ringworld.world;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Loader-neutral chunk window for a periodic X circumference and finite Z band.
 * This is kept independent from Minecraft's version-specific tracking classes so
 * the 1.20.1 adapter can delegate to one tested topology implementation.
 */
public record RingChunkWindow(
        int logicalCenterX,
        int centerZ,
        int viewDistance,
        RingWorldLayouts.RingLayout layout) {

    public RingChunkWindow {
        if (layout == null) throw new IllegalArgumentException("layout is required");
        if (viewDistance < 0) throw new IllegalArgumentException("viewDistance must be non-negative");
    }

    public int canonicalCenterX() {
        return layout.wrapChunkX(logicalCenterX);
    }

    public boolean contains(int chunkX, int chunkZ, boolean includeEdge) {
        if (layout.isExteriorChunkZ(chunkZ)) return false;
        return RingChunkTopology.isWithinVanillaDistance(
                layout.circumferenceChunks(),
                canonicalCenterX(), centerZ,
                viewDistance,
                chunkX, chunkZ,
                includeEdge);
    }

    public Set<ChunkCoordinate> chunks() {
        Set<ChunkCoordinate> emitted = new LinkedHashSet<>();
        int extent = viewDistance + 1;
        int firstZ = Math.max(layout.minChunkZ(), centerZ - extent);
        int lastZ = Math.min(layout.maxChunkZ(), centerZ + extent);

        for (int dx = -extent; dx <= extent; dx++) {
            int x = layout.wrapChunkX(canonicalCenterX() + dx);
            for (int z = firstZ; z <= lastZ; z++) {
                if (contains(x, z, false)) emitted.add(new ChunkCoordinate(x, z));
            }
        }
        return Set.copyOf(emitted);
    }

    public boolean requiresFullRekeyFrom(RingChunkWindow previous) {
        if (previous == null) return true;
        if (!previous.layout.equals(layout)) return true;
        int periodicDistance = new RingChunkTopology(layout)
                .distanceX(previous.logicalCenterX, logicalCenterX);
        int overlapDiameter = Math.max(previous.viewDistance, viewDistance) * 2 + 2;
        return periodicDistance > overlapDiameter;
    }

    public record ChunkCoordinate(int x, int z) {}
}
