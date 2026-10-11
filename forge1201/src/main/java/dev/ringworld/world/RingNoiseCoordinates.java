package dev.ringworld.world;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Shared immutable cylindrical coordinate lookup used by periodic terrain samplers. */
public final class RingNoiseCoordinates {
    private static final int MAX_PRECOMPUTED_CIRCUMFERENCE = 1_048_576;
    private static final ConcurrentMap<CacheKey, RingNoiseCoordinates> CACHE = new ConcurrentHashMap<>();

    private final RingWorldLayouts.RingLayout layout;
    private final int circumference;
    private final double radius;
    private final int mappingVersion;
    private final int[] legacyRingX;
    private final int[] legacyRingZOffset;
    private final double[] sine;
    private final double[] cosine;

    public static RingNoiseCoordinates forLayout(RingWorldLayouts.RingLayout layout) {
        return forLayout(layout, RingTerrainNoiseMapping.CURRENT);
    }

    public static RingNoiseCoordinates forLayout(
            RingWorldLayouts.RingLayout layout, int mappingVersion) {
        int supported = RingTerrainNoiseMapping.requireSupported(mappingVersion);
        RingTerrainNoiseMapping.requireSafeNewWorldGeometry(layout);
        return CACHE.computeIfAbsent(new CacheKey(layout, supported),
                key -> new RingNoiseCoordinates(key.layout(), key.mappingVersion()));
    }

    public static void clearCache() {
        CACHE.clear();
    }

    private RingNoiseCoordinates(RingWorldLayouts.RingLayout layout, int mappingVersion) {
        this.layout = layout;
        this.circumference = layout.circumferenceBlocks();
        this.radius = layout.radius();
        this.mappingVersion = mappingVersion;

        if (circumference <= MAX_PRECOMPUTED_CIRCUMFERENCE) {
            legacyRingX = mappingVersion == RingTerrainNoiseMapping.LEGACY_AXIAL
                    ? new int[circumference] : null;
            legacyRingZOffset = mappingVersion == RingTerrainNoiseMapping.LEGACY_AXIAL
                    ? new int[circumference] : null;
            sine = mappingVersion != RingTerrainNoiseMapping.LEGACY_AXIAL
                    ? new double[circumference] : null;
            cosine = mappingVersion != RingTerrainNoiseMapping.LEGACY_AXIAL
                    ? new double[circumference] : null;

            for (int x = 0; x < circumference; x++) {
                double angle = Math.PI * 2.0 * x / circumference;
                if (mappingVersion == RingTerrainNoiseMapping.LEGACY_AXIAL) {
                    legacyRingX[x] = rounded(radius * Math.sin(angle));
                    legacyRingZOffset[x] = rounded(radius * Math.cos(angle));
                } else {
                    sine[x] = Math.sin(angle);
                    cosine[x] = Math.cos(angle);
                }
            }
        } else {
            legacyRingX = null;
            legacyRingZOffset = null;
            sine = null;
            cosine = null;
        }
    }

    public RingWorldLayouts.RingLayout layout() {
        return layout;
    }

    public int mappingVersion() {
        return mappingVersion;
    }

    public int noiseX(int sourceX, int sourceZ) {
        int canonicalX = Math.floorMod(sourceX, circumference);
        if (mappingVersion == RingTerrainNoiseMapping.LEGACY_AXIAL) {
            if (legacyRingX != null) return legacyRingX[canonicalX];
            return rounded(radius * Math.sin(angle(canonicalX)));
        }
        double sin = sine != null ? sine[canonicalX] : Math.sin(angle(canonicalX));
        return rounded((radius + sourceZ) * sin);
    }

    public int noiseZ(int sourceX, int sourceZ) {
        int canonicalX = Math.floorMod(sourceX, circumference);
        if (mappingVersion == RingTerrainNoiseMapping.LEGACY_AXIAL) {
            int offset = legacyRingZOffset != null
                    ? legacyRingZOffset[canonicalX]
                    : rounded(radius * Math.cos(angle(canonicalX)));
            return sourceZ + offset;
        }
        double cos = cosine != null ? cosine[canonicalX] : Math.cos(angle(canonicalX));
        return rounded((radius + sourceZ) * cos);
    }

    public int ringX(int sourceX) {
        return noiseX(sourceX, 0);
    }

    public int ringZ(int sourceX, int sourceZ) {
        return noiseZ(sourceX, sourceZ);
    }

    private double angle(int canonicalX) {
        return Math.PI * 2.0 * canonicalX / circumference;
    }

    private static int rounded(double value) {
        if (value > Integer.MAX_VALUE || value < Integer.MIN_VALUE) {
            throw new IllegalArgumentException("Ring dimensions exceed vanilla noise coordinate range");
        }
        return (int) Math.round(value);
    }

    private record CacheKey(RingWorldLayouts.RingLayout layout, int mappingVersion) {}
}
