package dev.ringworld.world;

/** Versioned horizontal coordinate embedding used by periodic terrain noise. */
public final class RingTerrainNoiseMapping {
    public static final int LEGACY_AXIAL = 1;
    public static final int ANNULAR = 2;
    public static final int ANNULAR_COMPLETE = 3;
    public static final int ANNULAR_COMPLETE_V2 = 4;
    public static final int CURRENT = ANNULAR_COMPLETE_V2;

    public static final int QUERY_MARGIN_BLOCKS = 64;
    public static final double MINIMUM_NOISE_RADIUS_BLOCKS = 32.0;

    private RingTerrainNoiseMapping() {}

    public static int requireSupported(int mapping) {
        if (mapping != LEGACY_AXIAL && mapping != ANNULAR
                && mapping != ANNULAR_COMPLETE && mapping != ANNULAR_COMPLETE_V2) {
            throw new IllegalArgumentException("unsupported RingWorld terrain-noise mapping " + mapping);
        }
        return mapping;
    }

    public static String diagnosticName(int mapping) {
        return switch (requireSupported(mapping)) {
            case LEGACY_AXIAL -> "legacy-axial";
            case ANNULAR -> "annular-v1";
            case ANNULAR_COMPLETE -> "annular-complete";
            case ANNULAR_COMPLETE_V2 -> "annular-complete-v2";
            default -> throw new AssertionError("validated terrain-noise mapping " + mapping);
        };
    }

    public static double minimumSampledRadius(RingWorldLayouts.RingLayout layout) {
        return layout.radius() + layout.minWidthZ() - QUERY_MARGIN_BLOCKS;
    }

    public static void requireSafeNewWorldGeometry(RingWorldLayouts.RingLayout layout) {
        double minimumRadius = minimumSampledRadius(layout);
        if (minimumRadius < MINIMUM_NOISE_RADIUS_BLOCKS) {
            throw new IllegalArgumentException(
                    "circumference is too small for the selected finite width; minimum sampled radius="
                            + String.format(java.util.Locale.ROOT, "%.2f", minimumRadius));
        }
    }

    /** One deterministic carver identity no matter which logical seam chart asks for it. */
    public static int carverSeedChunkX(
            RingWorldLayouts.RingLayout layout, int mapping, int sourceChunkX) {
        requireSupported(mapping);
        return mapping >= ANNULAR_COMPLETE
                ? Math.floorMod(sourceChunkX, layout.circumferenceChunks())
                : sourceChunkX;
    }

    static ContinuousCoordinate continuousAnnular(
            RingWorldLayouts.RingLayout layout, double sourceX, double sourceZ) {
        double canonicalX = layout.wrapX(sourceX);
        double angle = Math.PI * 2.0 * canonicalX / layout.circumferenceBlocks();
        double radius = layout.radius() + sourceZ;
        return new ContinuousCoordinate(radius * Math.sin(angle), radius * Math.cos(angle));
    }

    record ContinuousCoordinate(double x, double z) {}
}
