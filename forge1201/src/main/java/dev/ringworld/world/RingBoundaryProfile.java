package dev.ringworld.world;

/**
 * Pure geometry for the custom finite-Z boundary.
 *
 * <p>The visible wall occupies five blocks inside each playable edge. An invisible
 * barrier column sits immediately outside the finite band and later generation code
 * will extend it through the dimension's complete build height. The bedrock floor is
 * handled separately because its Y coordinate is dimension-specific.</p>
 */
public final class RingBoundaryProfile {
    private RingBoundaryProfile() {}

    public static boolean isVisibleWallZ(int z, RingWorldLayouts.RingLayout layout) {
        int depth = RingWorldLayouts.SIDE_WALL_THICKNESS_BLOCKS;
        return (z >= layout.minWidthZ() && z < layout.minWidthZ() + depth)
                || (z <= layout.maxWidthZ() && z > layout.maxWidthZ() - depth);
    }

    public static boolean isBarrierZ(int z, RingWorldLayouts.RingLayout layout) {
        return z == lowerBarrierZ(layout) || z == upperBarrierZ(layout);
    }

    public static int lowerBarrierZ(RingWorldLayouts.RingLayout layout) {
        return layout.minWidthZ() - 1;
    }

    public static int upperBarrierZ(RingWorldLayouts.RingLayout layout) {
        return layout.maxWidthZ() + 1;
    }

    /**
     * Exclusive top of the visible bedrock wall for a terrain surface block Y.
     * A terrain top at Y=64 therefore produces visible wall through Y=74: exactly
     * ten blocks above terrain while keeping the surface-height wall block itself.
     */
    public static int visibleWallTopExclusive(int terrainSurfaceY, int maxBuildYExclusive) {
        long requested = (long) terrainSurfaceY
                + RingWorldLayouts.SIDE_WALL_ABOVE_TERRAIN_BLOCKS + 1L;
        return (int) Math.min(requested, maxBuildYExclusive);
    }
}
