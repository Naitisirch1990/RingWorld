package dev.ringworld.world;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Chunk-safe generation mutations for the custom finite Z band.
 *
 * <p>The playable band is made from complete chunk rows. One empty guard row
 * exists immediately beyond each side, where a full-height barrier column is
 * installed. This lets collision remain absolute without exposing an ordinary
 * terrain chunk outside the ring.</p>
 */
public final class RingGenerationBoundary {
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final BlockState BEDROCK = Blocks.BEDROCK.defaultBlockState();
    private static final BlockState BARRIER = Blocks.BARRIER.defaultBlockState();

    private RingGenerationBoundary() {}

    public static boolean isExterior(ChunkAccess chunk, RingWorldLayouts.RingLayout layout) {
        return layout.isExteriorChunkZ(chunk.getPos().z);
    }

    public static boolean isGuardChunk(ChunkAccess chunk, RingWorldLayouts.RingLayout layout) {
        int chunkZ = chunk.getPos().z;
        return chunkZ == layout.minChunkZ() - 1 || chunkZ == layout.maxChunkZ() + 1;
    }

    /** Removes any structure/feature spill that reached an exterior or guard chunk. */
    public static void clearExterior(ChunkAccess chunk) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int minY = chunk.getMinBuildHeight();
        int maxY = minY + chunk.getHeight();
        int minX = chunk.getPos().getMinBlockX();
        int minZ = chunk.getPos().getMinBlockZ();

        for (int y = minY; y < maxY; y++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                for (int localX = 0; localX < 16; localX++) {
                    cursor.set(minX + localX, y, minZ + localZ);
                    if (!chunk.getBlockState(cursor).isAir()) {
                        chunk.setBlockState(cursor, AIR, false);
                    }
                }
            }
        }
    }

    /**
     * Installs the invisible full-height stop in the two otherwise-empty guard rows.
     * Only one global Z column is barrier; the rest of the guard chunk stays air.
     */
    public static void installGuardBarrier(ChunkAccess chunk, RingWorldLayouts.RingLayout layout) {
        if (!isGuardChunk(chunk, layout)) return;
        int barrierZ = chunk.getPos().z < layout.minChunkZ()
                ? RingBoundaryProfile.lowerBarrierZ(layout)
                : RingBoundaryProfile.upperBarrierZ(layout);
        int minY = chunk.getMinBuildHeight();
        int maxY = minY + chunk.getHeight();
        int minX = chunk.getPos().getMinBlockX();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int localX = 0; localX < 16; localX++) {
            int x = minX + localX;
            for (int y = minY; y < maxY; y++) {
                cursor.set(x, y, barrierZ);
                chunk.setBlockState(cursor, BARRIER, false);
            }
        }
    }

    /**
     * Final interior pass: one unbreakable bedrock floor across the entire finite
     * band plus five-block-thick side walls whose tops follow local terrain and
     * rise exactly ten blocks above it.
     */
    public static void installInteriorBedrock(ChunkAccess chunk, RingWorldLayouts.RingLayout layout) {
        if (isExterior(chunk, layout)) return;
        int minY = chunk.getMinBuildHeight();
        int maxY = minY + chunk.getHeight();
        int minX = chunk.getPos().getMinBlockX();
        int minZ = chunk.getPos().getMinBlockZ();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        // Complete bottom floor.
        for (int localZ = 0; localZ < 16; localZ++) {
            for (int localX = 0; localX < 16; localX++) {
                cursor.set(minX + localX, minY, minZ + localZ);
                chunk.setBlockState(cursor, BEDROCK, false);
            }
        }

        // Only the two playable edge chunks can contain visible side-wall blocks.
        if (chunk.getPos().z != layout.minChunkZ() && chunk.getPos().z != layout.maxChunkZ()) {
            return;
        }

        for (int localZ = 0; localZ < 16; localZ++) {
            int z = minZ + localZ;
            if (!RingBoundaryProfile.isVisibleWallZ(z, layout)) continue;
            for (int localX = 0; localX < 16; localX++) {
                int x = minX + localX;
                int surfaceBlockY = Math.max(
                        minY,
                        chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, localX, localZ) - 1);
                int wallTopExclusive = RingBoundaryProfile.visibleWallTopExclusive(
                        surfaceBlockY, maxY);
                for (int y = minY; y < wallTopExclusive; y++) {
                    cursor.set(x, y, z);
                    chunk.setBlockState(cursor, BEDROCK, false);
                }
            }
        }
    }
}
