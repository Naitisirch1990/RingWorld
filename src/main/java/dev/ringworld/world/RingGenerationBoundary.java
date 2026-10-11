package dev.ringworld.world;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

/** Boundary operations performed during chunk generation, plus one-time repair on loaded legacy edge chunks. */
public final class RingGenerationBoundary {
    /** Five visible bedrock blocks from the playable side toward each outer edge. */
    public static final int RIM_THICKNESS = 5;
    /** Exactly twenty visible blocks rise above the levelled approach surface. */
    public static final int VISIBLE_WALL_HEIGHT = 20;
    /** Completely quiet ground immediately inside each wall. */
    public static final int APPROACH_CLEARANCE_BLOCKS = 64;
    /** Additional terrain blend that gently returns from the quiet ground to natural terrain. */
    public static final int APPROACH_TRANSITION_BLOCKS = 64;
    /** Increment when rim placement/material semantics change. */
    public static final int RIM_STYLE_VERSION = 3;

    private static final int BOUNDARY_RESERVE_BLOCKS =
            RIM_THICKNESS + APPROACH_CLEARANCE_BLOCKS + APPROACH_TRANSITION_BLOCKS;

    private RingGenerationBoundary() { }

    /**
     * Legacy fixed-height helper retained for old saved metadata and reports.
     * Project rims now use one fixed world elevation through {@link #localWallTopExclusive}.
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

    /** True for chunks that can contain the wall, quiet strip, or smoothing strip. */
    public static boolean intersectsBoundaryReserve(ChunkAccess chunk, RingGeometry geometry) {
        if (isExterior(chunk, geometry)) return false;
        ChunkPos pos = chunk.getPos();
        int firstZ = Math.max(pos.getMinBlockZ(), geometry.minWidthZ());
        int lastZ = Math.min(pos.getMaxBlockZ(), geometry.maxWidthZ());
        if (firstZ > lastZ) return false;
        return distanceFromNearestRim(firstZ, geometry) < BOUNDARY_RESERVE_BLOCKS
                || distanceFromNearestRim(lastZ, geometry) < BOUNDARY_RESERVE_BLOCKS;
    }

    private static boolean containsZ(ChunkPos pos, int z) {
        return z >= pos.getMinBlockZ() && z <= pos.getMaxBlockZ();
    }

    private static int distanceFromNearestRim(int z, RingGeometry geometry) {
        return Math.min(z - geometry.minWidthZ(), geometry.maxWidthZ() - z);
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
     * Finishes the boundary after features have run. The inner 64 blocks are
     * deliberately quiet and level, followed by a 64-block smooth return to
     * natural terrain. The five-block-thick rim has a perfectly horizontal
     * crest: twenty visible bedrock blocks above the approach surface, with an
     * invisible barrier continuing from that crest to the build ceiling.
     */
    public static void installRim(ChunkAccess chunk, RingGeometry geometry, int ignoredLegacyWallHeightBlocks) {
        if (!intersectsBoundaryReserve(chunk, geometry)) return;
        reshapeApproach(chunk, geometry, false);
        if (containsRim(chunk, geometry)) {
            installUniformWalls(chunk, geometry, false);
        }
    }

    /**
     * Returns the fixed exclusive bedrock top. The levelled approach occupies
     * Y=surfaceReferenceY, so the visible wall is the next twenty blocks.
     */
    static int localWallTopExclusive(ChunkAccess chunk, int x, int probeZ) {
        int worldMinY = chunk.getMinBuildHeight();
        int worldTopExclusive = worldMinY + chunk.getHeight();
        long desired = (long)Math.floor(RingGeometry.SURFACE_Y) + 1L + VISIBLE_WALL_HEIGHT;
        return (int)Math.max(worldMinY, Math.min(desired, worldTopExclusive));
    }

    private static boolean reshapeApproach(ChunkAccess chunk, RingGeometry geometry, boolean trackChanges) {
        ChunkPos pos = chunk.getPos();
        int firstZ = Math.max(pos.getMinBlockZ(), geometry.minWidthZ());
        int lastZ = Math.min(pos.getMaxBlockZ(), geometry.maxWidthZ());
        int plateauY = clamp((int)Math.floor(RingGeometry.SURFACE_Y),
                chunk.getMinBuildHeight() + 1,
                chunk.getMinBuildHeight() + chunk.getHeight() - 2);
        boolean changed = false;
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();

        for (int z = firstZ; z <= lastZ; z++) {
            int rimDistance = distanceFromNearestRim(z, geometry);
            if (rimDistance < RIM_THICKNESS || rimDistance >= BOUNDARY_RESERVE_BLOCKS) continue;
            int approachDistance = rimDistance - RIM_THICKNESS;

            for (int x = pos.getMinBlockX(); x <= pos.getMaxBlockX(); x++) {
                int originalSurfaceY = findSolidSurfaceY(chunk, x, z);
                if (originalSurfaceY == Integer.MIN_VALUE) originalSurfaceY = plateauY;

                int targetY;
                if (approachDistance < APPROACH_CLEARANCE_BLOCKS) {
                    targetY = plateauY;
                } else {
                    double linear = (approachDistance - APPROACH_CLEARANCE_BLOCKS + 1.0)
                            / APPROACH_TRANSITION_BLOCKS;
                    double t = smoothstep(Math.max(0.0, Math.min(1.0, linear)));
                    targetY = (int)Math.round(plateauY + (originalSurfaceY - plateauY) * t);
                }
                targetY = clamp(targetY, chunk.getMinBuildHeight() + 1,
                        chunk.getMinBuildHeight() + chunk.getHeight() - 2);

                BlockState support = findSupportMaterial(chunk, x, z,
                        Math.min(targetY, originalSurfaceY));
                if (support == null) support = Blocks.STONE.defaultBlockState();

                if (originalSurfaceY < targetY) {
                    for (int y = originalSurfaceY + 1; y <= targetY; y++) {
                        changed |= setBlock(chunk, mutable.set(x, y, z), support, trackChanges);
                    }
                } else {
                    for (int y = targetY + 1;
                         y < chunk.getMinBuildHeight() + chunk.getHeight(); y++) {
                        changed |= setBlock(chunk, mutable.set(x, y, z),
                                Blocks.AIR.defaultBlockState(), trackChanges);
                    }
                }
                changed |= setBlock(chunk, mutable.set(x, targetY, z), support, trackChanges);
            }
        }
        return changed;
    }

    private static boolean installUniformWalls(ChunkAccess chunk, RingGeometry geometry, boolean trackChanges) {
        ChunkPos pos = chunk.getPos();
        int worldMinY = chunk.getMinBuildHeight();
        int worldTopExclusive = worldMinY + chunk.getHeight();
        int wallTopExclusive = localWallTopExclusive(chunk, pos.getMinBlockX(), geometry.minWidthZ());
        boolean changed = false;
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        int[] rimZs = {geometry.minWidthZ(), geometry.maxWidthZ()};

        for (int rimZ : rimZs) {
            if (!containsZ(pos, rimZ)) continue;
            int inwardDirection = rimZ == geometry.minWidthZ() ? 1 : -1;
            for (int x = pos.getMinBlockX(); x <= pos.getMaxBlockX(); x++) {
                for (int depth = 0; depth < RIM_THICKNESS; depth++) {
                    int z = rimZ + inwardDirection * depth;
                    for (int y = worldMinY; y < wallTopExclusive; y++) {
                        changed |= setBlock(chunk, mutable.set(x, y, z),
                                Blocks.BEDROCK.defaultBlockState(), trackChanges);
                    }
                    for (int y = wallTopExclusive; y < worldTopExclusive; y++) {
                        changed |= setBlock(chunk, mutable.set(x, y, z),
                                Blocks.BARRIER.defaultBlockState(), trackChanges);
                    }
                }
            }
        }
        return changed;
    }

    private static int findSolidSurfaceY(ChunkAccess chunk, int x, int z) {
        int worldMinY = chunk.getMinBuildHeight();
        int worldTopExclusive = worldMinY + chunk.getHeight();
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        for (int y = worldTopExclusive - 1; y > worldMinY; y--) {
            BlockState state = chunk.getBlockState(mutable.set(x, y, z));
            if (!state.isAir() && state.getFluidState().isEmpty() && !isRimMaterial(state)) {
                return y;
            }
        }
        return Integer.MIN_VALUE;
    }

    private static BlockState findSupportMaterial(ChunkAccess chunk, int x, int z, int startY) {
        int minY = chunk.getMinBuildHeight() + 1;
        int y = Math.min(startY, chunk.getMinBuildHeight() + chunk.getHeight() - 2);
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        for (; y >= minY; y--) {
            BlockState state = chunk.getBlockState(mutable.set(x, y, z));
            if (!state.isAir() && state.getFluidState().isEmpty() && !isRimMaterial(state)) {
                return state;
            }
        }
        return null;
    }

    private static boolean setBlock(ChunkAccess chunk, BlockPos pos, BlockState state, boolean trackChanges) {
        if (chunk.getBlockState(pos).equals(state)) return false;
        chunk.removeBlockEntity(pos);
        chunk.setBlockState(pos, state, false);
        return trackChanges;
    }

    private static double smoothstep(double t) {
        return t * t * (3.0 - 2.0 * t);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * Repairs generated edge chunks from previous rim styles. This intentionally
     * covers the complete approach reserve, not only the five wall columns, so
     * existing saves gain the clean horizon without requiring a new world.
     */
    public static boolean migrateLegacyRim(LevelChunk chunk, RingGeometry geometry,
                                           int ignoredLegacyWallHeightBlocks) {
        if (!intersectsBoundaryReserve(chunk, geometry)) return false;
        boolean changed = reshapeApproach(chunk, geometry, true);
        if (containsRim(chunk, geometry)) {
            changed |= installUniformWalls(chunk, geometry, true);
        }
        if (changed) chunk.setUnsaved(true);
        return changed;
    }

    public static boolean isRimMaterial(BlockState state) {
        return state.is(Blocks.BEDROCK) || state.is(Blocks.BARRIER)
                || state.is(Blocks.COBBLESTONE) || state.is(Blocks.MOSSY_COBBLESTONE)
                || state.is(Blocks.STONE_BRICKS);
    }
}
