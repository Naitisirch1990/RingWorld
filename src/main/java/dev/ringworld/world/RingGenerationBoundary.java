package dev.ringworld.world;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

/** Boundary operations performed during chunk generation, plus repair of older edge chunks. */
public final class RingGenerationBoundary {
    /** Five solid columns form each physical side wall. */
    public static final int RIM_THICKNESS = 5;
    /** Project brief: ten visible bedrock blocks above the reference surface. */
    public static final int VISIBLE_WALL_HEIGHT = 10;
    /** Shallow island coast immediately inside each wall. */
    public static final int COAST_WIDTH_BLOCKS = 20;
    /** Compatibility aliases kept for older diagnostics/config readers. */
    public static final int APPROACH_CLEARANCE_BLOCKS = COAST_WIDTH_BLOCKS;
    public static final int APPROACH_TRANSITION_BLOCKS = 0;
    /** Increment when rim/coast placement semantics change. */
    public static final int RIM_STYLE_VERSION = 4;

    public static final int SEA_LEVEL_Y = (int)RingGeometry.SURFACE_Y;
    private static final int OUTER_SEABED_Y = SEA_LEVEL_Y - 4;
    private static final int INNER_SEABED_Y = SEA_LEVEL_Y - 1;
    private static final int BOUNDARY_RESERVE_BLOCKS = RIM_THICKNESS + COAST_WIDTH_BLOCKS;

    private RingGenerationBoundary() { }

    /** Legacy helper retained for saved metadata and reports. */
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

    /** True for chunks that can contain a wall or the twenty-block coast. */
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

    /** Removes a chunk that is completely beyond the finite band. */
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
                        mutable.set(pos.getMinBlockX() + localX, sectionBottomY + localY,
                                pos.getMinBlockZ() + localZ);
                        chunk.removeBlockEntity(mutable);
                        chunk.setBlockState(mutable, Blocks.AIR.defaultBlockState(), false);
                    }
                }
            }
        }
    }

    /**
     * The width is chunk-count aligned but centred on Z=0, so both edge chunks
     * contain a few columns beyond the legal band. Those columns must be empty;
     * otherwise terrain remains visibly suspended behind the barrier.
     */
    public static boolean clearOutsidePlayableColumns(ChunkAccess chunk, RingGeometry geometry,
                                                       boolean trackChanges) {
        if (isExterior(chunk, geometry)) {
            if (!trackChanges) clearExterior(chunk);
            return false;
        }
        ChunkPos pos = chunk.getPos();
        int worldMinY = chunk.getMinBuildHeight();
        int worldTopExclusive = worldMinY + chunk.getHeight();
        boolean changed = false;
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();

        for (int z = pos.getMinBlockZ(); z <= pos.getMaxBlockZ(); z++) {
            if (z >= geometry.minWidthZ() && z <= geometry.maxWidthZ()) continue;
            for (int x = pos.getMinBlockX(); x <= pos.getMaxBlockX(); x++) {
                for (int y = worldMinY; y < worldTopExclusive; y++) {
                    mutable.set(x, y, z);
                    if (chunk.getBlockState(mutable).isAir()) continue;
                    chunk.removeBlockEntity(mutable);
                    chunk.setBlockState(mutable, Blocks.AIR.defaultBlockState(), false);
                    changed = true;
                }
            }
        }
        return trackChanges && changed;
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

    /** Backwards-compatible overload; Overworld-style island coast is the default. */
    public static void installRim(ChunkAccess chunk, RingGeometry geometry, int ignoredLegacyWallHeightBlocks) {
        installRim(chunk, geometry, ignoredLegacyWallHeightBlocks, true);
    }

    /**
     * Finishes the finite band after features have run. In the Overworld the
     * old 64+64 flattened approach is replaced by a twenty-block shallow sea:
     * a sand seabed slopes from Y=63 to Y=60 while the water surface remains
     * perfectly level at Y=64. The five-column bedrock wall then rises ten
     * visible blocks, with invisible barrier from its crest to build height.
     */
    public static void installRim(ChunkAccess chunk, RingGeometry geometry,
                                  int ignoredLegacyWallHeightBlocks, boolean islandCoast) {
        if (isExterior(chunk, geometry)) {
            clearExterior(chunk);
            return;
        }
        clearOutsidePlayableColumns(chunk, geometry, false);
        if (!intersectsBoundaryReserve(chunk, geometry)) return;
        if (islandCoast) reshapeIslandCoast(chunk, geometry, false);
        if (containsRim(chunk, geometry)) installUniformWalls(chunk, geometry, false);
    }

    /** Fixed exclusive bedrock top: surface Y + ten visible blocks. */
    static int localWallTopExclusive(ChunkAccess chunk, int x, int probeZ) {
        int worldMinY = chunk.getMinBuildHeight();
        int worldTopExclusive = worldMinY + chunk.getHeight();
        long desired = (long)Math.floor(RingGeometry.SURFACE_Y) + 1L + VISIBLE_WALL_HEIGHT;
        return (int)Math.max(worldMinY, Math.min(desired, worldTopExclusive));
    }

    private static boolean reshapeIslandCoast(ChunkAccess chunk, RingGeometry geometry,
                                              boolean trackChanges) {
        ChunkPos pos = chunk.getPos();
        int firstZ = Math.max(pos.getMinBlockZ(), geometry.minWidthZ());
        int lastZ = Math.min(pos.getMaxBlockZ(), geometry.maxWidthZ());
        int worldMinY = chunk.getMinBuildHeight();
        int worldTopExclusive = worldMinY + chunk.getHeight();
        boolean changed = false;
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();

        for (int z = firstZ; z <= lastZ; z++) {
            int rimDistance = distanceFromNearestRim(z, geometry);
            if (rimDistance < RIM_THICKNESS || rimDistance >= BOUNDARY_RESERVE_BLOCKS) continue;
            int coastIndex = rimDistance - RIM_THICKNESS;
            double t = COAST_WIDTH_BLOCKS <= 1 ? 1.0
                    : coastIndex / (double)(COAST_WIDTH_BLOCKS - 1);
            int seabedY = (int)Math.round(OUTER_SEABED_Y
                    + (INNER_SEABED_Y - OUTER_SEABED_Y) * smoothstep(t));
            seabedY = clamp(seabedY, worldMinY + 3, Math.min(SEA_LEVEL_Y - 1, worldTopExclusive - 2));

            for (int x = pos.getMinBlockX(); x <= pos.getMaxBlockX(); x++) {
                // Remove the old terrain completely above the new seabed.
                for (int y = seabedY + 1; y < worldTopExclusive; y++) {
                    changed |= setBlock(chunk, mutable.set(x, y, z),
                            Blocks.AIR.defaultBlockState(), trackChanges);
                }

                // Give the sand a stable shallow foundation even over caves/water.
                for (int y = Math.max(worldMinY + 1, seabedY - 2); y < seabedY; y++) {
                    BlockState current = chunk.getBlockState(mutable.set(x, y, z));
                    if (current.isAir() || !current.getFluidState().isEmpty()) {
                        changed |= setBlock(chunk, mutable,
                                Blocks.SANDSTONE.defaultBlockState(), trackChanges);
                    }
                }
                changed |= setBlock(chunk, mutable.set(x, seabedY, z),
                        Blocks.SAND.defaultBlockState(), trackChanges);

                // One common water level on both sides makes the band read as an island.
                for (int y = seabedY + 1; y <= SEA_LEVEL_Y && y < worldTopExclusive; y++) {
                    changed |= setBlock(chunk, mutable.set(x, y, z),
                            Blocks.WATER.defaultBlockState(), trackChanges);
                }
            }
        }
        return changed;
    }

    private static boolean installUniformWalls(ChunkAccess chunk, RingGeometry geometry,
                                               boolean trackChanges) {
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

    private static boolean setBlock(ChunkAccess chunk, BlockPos pos, BlockState state,
                                    boolean trackChanges) {
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

    /** Repairs generated edge chunks from previous rim styles. */
    public static boolean migrateLegacyRim(LevelChunk chunk, RingGeometry geometry,
                                           int ignoredLegacyWallHeightBlocks) {
        if (isExterior(chunk, geometry)) return false;
        boolean changed = clearOutsidePlayableColumns(chunk, geometry, true);
        if (intersectsBoundaryReserve(chunk, geometry)) {
            boolean overworld = chunk.getLevel().dimension() == net.minecraft.world.level.Level.OVERWORLD;
            if (overworld) changed |= reshapeIslandCoast(chunk, geometry, true);
            if (containsRim(chunk, geometry)) changed |= installUniformWalls(chunk, geometry, true);
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
