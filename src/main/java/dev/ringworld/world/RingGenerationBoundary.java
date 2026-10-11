package dev.ringworld.world;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

/** Boundary operations performed during chunk generation, never live ticks. */
public final class RingGenerationBoundary {
    /** Five visible bedrock blocks from the playable side toward each outer edge. */
    public static final int RIM_THICKNESS = 5;
    /** Bedrock rises this many blocks above the local world-surface sample. */
    public static final int WALL_CLEARANCE_ABOVE_TERRAIN = 10;
    /** Increment when rim placement/material semantics change. */
    public static final int RIM_STYLE_VERSION = 2;

    private RingGenerationBoundary() { }

    /**
     * Legacy fixed-height helper retained for old saved metadata and reports.
     * New project rims use local terrain height through {@link #localWallTopExclusive}.
     */
    public static int wallTopExclusive(int worldMinY, int worldHeight, int wallHeightBlocks) {
        if (worldHeight <= 0) throw new IllegalArgumentException("world height must be positive");
        if (wallHeightBlocks < 0) throw new IllegalArgumentException("wall height must be non-negative");
        long worldTopExclusive = (long)worldMinY + worldHeight;
        long requestedTopExclusive = (long)worldMinY + wallHeightBlocks;
        return (int)Math.min(requestedTopExclusive, worldTopExclusive);
    }

    public static boolean isExterior(ChunkAccess chunk, RingGeometry geometry) {
        ChunkPos pos = chunk.getPos();
        return pos.getMaxBlockZ() < geometry.minWidthZ() || pos.getMinBlockZ() > geometry.maxWidthZ();
    }

    public static boolean containsRim(ChunkAccess chunk, RingGeometry geometry) {
        ChunkPos pos = chunk.getPos();
        return containsZ(pos, geometry.minWidthZ()) || containsZ(pos, geometry.maxWidthZ());
    }

    private static boolean containsZ(ChunkPos pos, int z) {
        return z >= pos.getMinBlockZ() && z <= pos.getMaxBlockZ();
    }

    /** Removes only cross-chunk feature spillover; noise generation is skipped entirely. */
    public static void clearExterior(ChunkAccess chunk) {
        ChunkPos pos = chunk.getPos();
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        LevelChunkSection[] sections = chunk.getSections();
        for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
            LevelChunkSection section = sections[sectionIndex];
            if (section.hasOnlyAir()) continue;
            int sectionBottomY = chunk.getMinBuildHeight() + sectionIndex * 16;
            for (int localY = 0; localY < 16; localY++) {
                for (int localZ = 0; localZ < 16; localZ++) {
                    for (int localX = 0; localX < 16; localX++) {
                        if (section.getBlockState(localX, localY, localZ).isAir()) continue;
                        mutable.set(pos.getMinBlockX() + localX, sectionBottomY + localY, pos.getMinBlockZ() + localZ);
                        chunk.removeBlockEntity(mutable);
                        chunk.setBlockState(mutable, Blocks.AIR.defaultBlockState(), false);
                    }
                }
            }
        }
    }

    /** Ensures the complete playable band has a continuous one-block bedrock floor. */
    public static void installBedrockFloor(ChunkAccess chunk, RingGeometry geometry) {
        if (isExterior(chunk, geometry)) return;
        ChunkPos pos = chunk.getPos();
        int floorY = chunk.getMinBuildHeight();
        int firstZ = Math.max(pos.getMinBlockZ(), geometry.minWidthZ());
        int lastZ = Math.min(pos.getMaxBlockZ(), geometry.maxWidthZ());
        if (firstZ > lastZ) return;

        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        for (int x = pos.getMinBlockX(); x <= pos.getMaxBlockX(); x++) {
            for (int z = firstZ; z <= lastZ; z++) {
                mutable.set(x, floorY, z);
                chunk.removeBlockEntity(mutable);
                chunk.setBlockState(mutable, Blocks.BEDROCK.defaultBlockState(), false);
            }
        }
    }

    /**
     * Installs the project boundary after features have finished: five blocks
     * of bedrock rising ten blocks above local terrain plus a hidden full-height
     * barrier immediately outside the wall. For chunk-aligned legacy layouts,
     * where the exterior barrier plane belongs to the next chunk, the barrier
     * falls back to the outer bedrock face so escape is still impossible.
     */
    public static void installRim(ChunkAccess chunk, RingGeometry geometry, int ignoredLegacyWallHeightBlocks) {
        if (!containsRim(chunk, geometry)) return;
        ChunkPos pos = chunk.getPos();
        int worldTopExclusive = chunk.getMinBuildHeight() + chunk.getHeight();
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        int[] rimZs = {geometry.minWidthZ(), geometry.maxWidthZ()};

        for (int rimZ : rimZs) {
            if (!containsZ(pos, rimZ)) continue;
            int inwardDirection = rimZ == geometry.minWidthZ() ? 1 : -1;
            int probeZ = rimZ + inwardDirection * RIM_THICKNESS;
            int requestedBarrierZ = rimZ - inwardDirection;
            int barrierZ = containsZ(pos, requestedBarrierZ) ? requestedBarrierZ : rimZ;

            for (int x = pos.getMinBlockX(); x <= pos.getMaxBlockX(); x++) {
                int wallTopExclusive = localWallTopExclusive(chunk, x, probeZ);
                for (int depth = 0; depth < RIM_THICKNESS; depth++) {
                    int z = rimZ + inwardDirection * depth;
                    for (int y = chunk.getMinBuildHeight(); y < wallTopExclusive; y++) {
                        mutable.set(x, y, z);
                        chunk.removeBlockEntity(mutable);
                        chunk.setBlockState(mutable, Blocks.BEDROCK.defaultBlockState(), false);
                    }
                }

                // Place last so a fallback barrier on the outer wall face is not
                // overwritten by the visible bedrock pass above.
                for (int y = chunk.getMinBuildHeight(); y < worldTopExclusive; y++) {
                    mutable.set(x, y, barrierZ);
                    chunk.removeBlockEntity(mutable);
                    chunk.setBlockState(mutable, Blocks.BARRIER.defaultBlockState(), false);
                }
            }
        }
    }

    /** Returns the exclusive wall top for one X column, ten blocks above the local surface. */
    static int localWallTopExclusive(ChunkAccess chunk, int x, int probeZ) {
        int worldMinY = chunk.getMinBuildHeight();
        int worldTopExclusive = worldMinY + chunk.getHeight();
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();

        int surfaceY = Integer.MIN_VALUE;
        if (probeZ >= chunk.getPos().getMinBlockZ() && probeZ <= chunk.getPos().getMaxBlockZ()) {
            for (int y = worldTopExclusive - 1; y >= worldMinY; y--) {
                if (!chunk.getBlockState(mutable.set(x, y, probeZ)).isAir()) {
                    surfaceY = y;
                    break;
                }
            }
        }
        if (surfaceY == Integer.MIN_VALUE) {
            surfaceY = (int)Math.floor(RingGeometry.SURFACE_Y);
        }
        long desired = (long)surfaceY + 1L + WALL_CLEARANCE_ABOVE_TERRAIN;
        return (int)Math.max(worldMinY, Math.min(desired, worldTopExclusive));
    }

    /**
     * One-time conversion for legacy stone/cobble rims. Converted columns use
     * the same bedrock-plus-barrier semantics as newly generated chunks.
     */
    public static boolean migrateLegacyRim(LevelChunk chunk, RingGeometry geometry,
                                           int ignoredLegacyWallHeightBlocks) {
        if (!containsRim(chunk, geometry)) return false;
        ChunkPos pos = chunk.getPos();
        int chunkTopExclusive = chunk.getMinBuildHeight() + chunk.getHeight();
        int[] rimZs = {geometry.minWidthZ(), geometry.maxWidthZ()};
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();

        boolean legacy = false;
        for (int rimZ : rimZs) {
            if (!containsZ(pos, rimZ)) continue;
            for (int x = pos.getMinBlockX(); x <= pos.getMaxBlockX() && !legacy; x++) {
                for (int y = chunk.getMinBuildHeight(); y < chunkTopExclusive; y++) {
                    var state = chunk.getBlockState(mutable.set(x, y, rimZ));
                    if (state.is(Blocks.STONE_BRICKS) || state.is(Blocks.COBBLESTONE)
                            || state.is(Blocks.MOSSY_COBBLESTONE)) {
                        legacy = true;
                        break;
                    }
                }
            }
        }
        if (!legacy) return false;

        for (int rimZ : rimZs) {
            if (!containsZ(pos, rimZ)) continue;
            int inwardDirection = rimZ == geometry.minWidthZ() ? 1 : -1;
            int probeZ = rimZ + inwardDirection * RIM_THICKNESS;
            int requestedBarrierZ = rimZ - inwardDirection;
            int barrierZ = containsZ(pos, requestedBarrierZ) ? requestedBarrierZ : rimZ;

            for (int x = pos.getMinBlockX(); x <= pos.getMaxBlockX(); x++) {
                int wallTopExclusive = localWallTopExclusive(chunk, x, probeZ);
                for (int depth = 0; depth < RIM_THICKNESS; depth++) {
                    int z = rimZ + inwardDirection * depth;
                    for (int y = chunk.getMinBuildHeight(); y < chunkTopExclusive; y++) {
                        mutable.set(x, y, z);
                        if (y < wallTopExclusive) {
                            chunk.removeBlockEntity(mutable);
                            chunk.setBlockState(mutable, Blocks.BEDROCK.defaultBlockState(), false);
                        } else {
                            var state = chunk.getBlockState(mutable);
                            if (state.is(Blocks.STONE_BRICKS) || state.is(Blocks.COBBLESTONE)
                                    || state.is(Blocks.MOSSY_COBBLESTONE)) {
                                chunk.removeBlockEntity(mutable);
                                chunk.setBlockState(mutable, Blocks.AIR.defaultBlockState(), false);
                            }
                        }
                    }
                }
                for (int y = chunk.getMinBuildHeight(); y < chunkTopExclusive; y++) {
                    mutable.set(x, y, barrierZ);
                    chunk.removeBlockEntity(mutable);
                    chunk.setBlockState(mutable, Blocks.BARRIER.defaultBlockState(), false);
                }
            }
        }
        chunk.setUnsaved(true);
        return true;
    }

    public static boolean isRimMaterial(net.minecraft.world.level.block.state.BlockState state) {
        return state.is(Blocks.BEDROCK) || state.is(Blocks.BARRIER)
                || state.is(Blocks.COBBLESTONE) || state.is(Blocks.MOSSY_COBBLESTONE)
                || state.is(Blocks.STONE_BRICKS);
    }
}
