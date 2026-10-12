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
 * old synthetic placeholder with terrain that actually exists in the world.</p>
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
                BlockPos probe = new BlockPos(presentationX, RingGeometry.SURFACE_Y > level.getMinBuildHeight()
                        ? (int)RingGeometry.SURFACE_Y : level.getMinBuildHeight(), worldZ);
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
        return KNOWN[indexFor(canonicalX, worldZ, geometry)];
    }

    public static double height(double canonicalX, double worldZ, RingGeometry geometry,
                                double fallback) {
        ensureGeometry(geometry);
        int index = indexFor(canonicalX, worldZ, geometry);
        return KNOWN[index] ? HEIGHTS[index] : fallback;
    }

    public static int color(double canonicalX, double worldZ, RingGeometry geometry,
                            int fallback) {
        ensureGeometry(geometry);
        int index = indexFor(canonicalX, worldZ, geometry);
        return KNOWN[index] ? COLORS[index] : fallback;
    }

    private static int indexFor(double canonicalX, double worldZ, RingGeometry geometry) {
        int ai = (int)Math.floor(geometry.wrapX(canonicalX)
                * ANGULAR_SEGMENTS / geometry.circumferenceBlocks());
        if (ai >= ANGULAR_SEGMENTS) ai = ANGULAR_SEGMENTS - 1;
        if (ai < 0) ai = 0;

        double normalizedZ = (worldZ - geometry.minWidthZ()) / geometry.widthBlocks();
        int zi = (int)Math.floor(normalizedZ * WIDTH_SEGMENTS);
        if (zi >= WIDTH_SEGMENTS) zi = WIDTH_SEGMENTS - 1;
        if (zi < 0) zi = 0;
        return index(ai, zi);
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
}
