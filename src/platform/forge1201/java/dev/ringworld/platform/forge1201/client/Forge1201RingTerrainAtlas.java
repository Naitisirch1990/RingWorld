package dev.ringworld.platform.forge1201.client;

import dev.ringworld.world.RingGeometry;
import java.util.Arrays;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Session-local coarse terrain cache for the full-ring renderer.
 *
 * <p>The cache never asks Minecraft to load or generate a chunk. It only samples
 * cells whose presentation-chart position is already resident on the client,
 * then stores the result in canonical ring coordinates. This makes observations
 * survive seam crossings and lets the distant ring progressively replace the
 * synthetic placeholder with terrain that actually exists in the world.</p>
 *
 * <p>Reads are bilinearly filtered between neighbouring atlas cells. That matters
 * a lot on the sky-scale ring: without filtering, each coarse sample becomes a
 * visibly rectangular colour/height band when enlarged overhead.</p>
 */
public final class Forge1201RingTerrainAtlas {
    public static final int ANGULAR_SEGMENTS = 512;
    public static final int WIDTH_SEGMENTS = 32;

    private static final long REFRESH_INTERVAL_TICKS = 20L;
    private static final double[] HEIGHTS = new double[ANGULAR_SEGMENTS * WIDTH_SEGMENTS];
    private static final int[] COLORS = new int[ANGULAR_SEGMENTS * WIDTH_SEGMENTS];
    private static final boolean[] KNOWN = new boolean[ANGULAR_SEGMENTS * WIDTH_SEGMENTS];

    private static int cachedCircumference = -1;
    private static int cachedWidth = -1;
    private static long lastRefreshTick = Long.MIN_VALUE;

    private Forge1201RingTerrainAtlas() { }

    /** Samples only atlas points backed by chunks that are already loaded client-side. */
    public static void refreshLoadedTerrain(ClientLevel level, RingGeometry geometry) {
        ensureGeometry(geometry);
        long gameTime = level.getGameTime();
        if (lastRefreshTick != Long.MIN_VALUE
                && gameTime >= lastRefreshTick
                && gameTime - lastRefreshTick < REFRESH_INTERVAL_TICKS) {
            return;
        }
        lastRefreshTick = gameTime;

        Minecraft minecraft = Minecraft.getInstance();
        double referenceX = minecraft.player != null ? minecraft.player.getX() : 0.0;
        int minZ = geometry.minWidthZ();
        int width = geometry.widthBlocks();

        for (int ai = 0; ai < ANGULAR_SEGMENTS; ai++) {
            double canonicalX = (ai + 0.5) * geometry.circumferenceBlocks() / ANGULAR_SEGMENTS;
            int presentationX = (int)Math.floor(geometry.nearestImageX(canonicalX, referenceX));

            for (int zi = 0; zi < WIDTH_SEGMENTS; zi++) {
                int worldZ = (int)Math.floor(minZ + (zi + 0.5) * width / WIDTH_SEGMENTS);
                BlockPos probe = new BlockPos(presentationX,
                        RingGeometry.SURFACE_Y > level.getMinBuildHeight()
                                ? (int)RingGeometry.SURFACE_Y : level.getMinBuildHeight(),
                        worldZ);
                if (!level.hasChunkAt(probe)) continue;

                int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE, presentationX, worldZ);
                if (surfaceY <= level.getMinBuildHeight()) continue;

                BlockPos surface = new BlockPos(presentationX, surfaceY - 1, worldZ);
                BlockState state = level.getBlockState(surface);
                int index = index(ai, zi);
                HEIGHTS[index] = surfaceY;
                COLORS[index] = state.getMapColor(level, surface).col;
                KNOWN[index] = true;
            }
        }
    }

    public static boolean hasSample(double canonicalX, double worldZ, RingGeometry geometry) {
        ensureGeometry(geometry);
        GridPosition grid = gridPosition(canonicalX, worldZ, geometry);
        return KNOWN[index(grid.x0(), grid.z0())]
                || KNOWN[index(grid.x1(), grid.z0())]
                || KNOWN[index(grid.x0(), grid.z1())]
                || KNOWN[index(grid.x1(), grid.z1())];
    }

    public static double height(double canonicalX, double worldZ, RingGeometry geometry,
                                double fallback) {
        ensureGeometry(geometry);
        GridPosition grid = gridPosition(canonicalX, worldZ, geometry);

        double h00 = sampleHeight(grid.x0(), grid.z0(), fallback);
        double h10 = sampleHeight(grid.x1(), grid.z0(), fallback);
        double h01 = sampleHeight(grid.x0(), grid.z1(), fallback);
        double h11 = sampleHeight(grid.x1(), grid.z1(), fallback);

        double low = lerp(h00, h10, grid.tx());
        double high = lerp(h01, h11, grid.tx());
        return lerp(low, high, grid.tz());
    }

    public static int color(double canonicalX, double worldZ, RingGeometry geometry,
                            int fallback) {
        ensureGeometry(geometry);
        GridPosition grid = gridPosition(canonicalX, worldZ, geometry);

        int c00 = sampleColor(grid.x0(), grid.z0(), fallback);
        int c10 = sampleColor(grid.x1(), grid.z0(), fallback);
        int c01 = sampleColor(grid.x0(), grid.z1(), fallback);
        int c11 = sampleColor(grid.x1(), grid.z1(), fallback);
        return bilerpColor(c00, c10, c01, c11, grid.tx(), grid.tz());
    }

    private static GridPosition gridPosition(double canonicalX, double worldZ,
                                             RingGeometry geometry) {
        // Atlas values represent cell centres, hence the half-cell offset before interpolation.
        double gx = geometry.wrapX(canonicalX)
                * ANGULAR_SEGMENTS / geometry.circumferenceBlocks() - 0.5;
        int rawX0 = (int)Math.floor(gx);
        double tx = gx - Math.floor(gx);
        int x0 = Math.floorMod(rawX0, ANGULAR_SEGMENTS);
        int x1 = Math.floorMod(rawX0 + 1, ANGULAR_SEGMENTS);

        double gz = (worldZ - geometry.minWidthZ())
                * WIDTH_SEGMENTS / geometry.widthBlocks() - 0.5;
        gz = Math.max(0.0, Math.min(WIDTH_SEGMENTS - 1.0, gz));
        int z0 = (int)Math.floor(gz);
        int z1 = Math.min(WIDTH_SEGMENTS - 1, z0 + 1);
        double tz = gz - z0;

        return new GridPosition(x0, x1, z0, z1, tx, tz);
    }

    private static double sampleHeight(int ai, int zi, double fallback) {
        int index = index(ai, zi);
        return KNOWN[index] ? HEIGHTS[index] : fallback;
    }

    private static int sampleColor(int ai, int zi, int fallback) {
        int index = index(ai, zi);
        return KNOWN[index] ? COLORS[index] : fallback;
    }

    private static int bilerpColor(int c00, int c10, int c01, int c11, double tx, double tz) {
        int r = (int)Math.round(lerp(
                lerp((c00 >> 16) & 0xFF, (c10 >> 16) & 0xFF, tx),
                lerp((c01 >> 16) & 0xFF, (c11 >> 16) & 0xFF, tx), tz));
        int g = (int)Math.round(lerp(
                lerp((c00 >> 8) & 0xFF, (c10 >> 8) & 0xFF, tx),
                lerp((c01 >> 8) & 0xFF, (c11 >> 8) & 0xFF, tx), tz));
        int b = (int)Math.round(lerp(
                lerp(c00 & 0xFF, c10 & 0xFF, tx),
                lerp(c01 & 0xFF, c11 & 0xFF, tx), tz));
        return (r << 16) | (g << 8) | b;
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private static int index(int ai, int zi) {
        return ai * WIDTH_SEGMENTS + zi;
    }

    private static void ensureGeometry(RingGeometry geometry) {
        if (cachedCircumference == geometry.circumferenceBlocks()
                && cachedWidth == geometry.widthBlocks()) {
            return;
        }
        Arrays.fill(KNOWN, false);
        cachedCircumference = geometry.circumferenceBlocks();
        cachedWidth = geometry.widthBlocks();
        lastRefreshTick = Long.MIN_VALUE;
    }

    private record GridPosition(int x0, int x1, int z0, int z1, double tx, double tz) { }
}
