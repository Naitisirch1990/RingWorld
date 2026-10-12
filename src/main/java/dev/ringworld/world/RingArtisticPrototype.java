package dev.ringworld.world;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;

/**
 * Minimal infrastructure pass for the project world.
 *
 * <p>The earlier artistic prototype placed cities, pyramids and repeated
 * monuments. Those are intentionally gone: the player owns the aesthetics.
 * This pass now builds only the useful continuous Overworld road.</p>
 */
public final class RingArtisticPrototype {
    private static final int ROAD_CENTER_Z = 0;
    private static final int ROAD_HALF_WIDTH = 3;
    private static final int ROAD_LIGHT_SPACING = 12;
    private static final int ROAD_HEADROOM = 4;

    private RingArtisticPrototype() { }

    public static void decorate(WorldGenLevel world, ChunkAccess chunk, RingGeometry geometry) {
        if (world.getLevel().dimension() != Level.OVERWORLD) return;
        if (RingGenerationBoundary.isExterior(chunk, geometry)) return;
        buildTerrainFollowingRoad(chunk, geometry);
    }

    /**
     * Builds a seven-block-wide causeway whose profile is always at or above
     * the natural surface. A local one-block-per-block slope envelope raises
     * low points around steep peaks instead of burying the road inside them.
     */
    private static void buildTerrainFollowingRoad(ChunkAccess chunk, RingGeometry geometry) {
        int chunkMinZ = chunk.getPos().getMinBlockZ();
        int chunkMaxZ = chunk.getPos().getMaxBlockZ();
        int firstRoadZ = Math.max(chunkMinZ, ROAD_CENTER_Z - ROAD_HALF_WIDTH);
        int lastRoadZ = Math.min(chunkMaxZ, ROAD_CENTER_Z + ROAD_HALF_WIDTH);
        if (firstRoadZ > lastRoadZ) return;

        int chunkMinX = chunk.getPos().getMinBlockX();
        int[] rawHeights = new int[16];
        int[] roadHeights = new int[16];

        for (int localX = 0; localX < 16; localX++) {
            int worldX = chunkMinX + localX;
            int highest = chunk.getMinBuildHeight() + 1;
            for (int z = firstRoadZ; z <= lastRoadZ; z++) {
                highest = Math.max(highest, findTravelSurfaceY(chunk, worldX, z));
            }
            rawHeights[localX] = highest;
        }

        // Smallest slope-1 profile that never drops below any sampled surface.
        for (int x = 0; x < 16; x++) {
            int target = rawHeights[x];
            for (int sample = 0; sample < 16; sample++) {
                target = Math.max(target, rawHeights[sample] - Math.abs(sample - x));
            }
            roadHeights[x] = target;
        }

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int worldTopExclusive = chunk.getMinBuildHeight() + chunk.getHeight();
        for (int localX = 0; localX < 16; localX++) {
            int worldX = chunkMinX + localX;
            int targetY = Math.min(roadHeights[localX], worldTopExclusive - ROAD_HEADROOM - 1);

            for (int z = firstRoadZ; z <= lastRoadZ; z++) {
                int naturalY = findTravelSurfaceY(chunk, worldX, z);

                // Raised sections become a solid engineered causeway rather than floating blocks.
                for (int y = naturalY + 1; y < targetY; y++) {
                    setBlock(chunk, pos.set(worldX, y, z), Blocks.STONE_BRICKS.defaultBlockState());
                }

                BlockState pavement = pavementFor(worldX, z);
                setBlock(chunk, pos.set(worldX, targetY, z), pavement);

                // Nothing can cover the road after this pass; trees/terrain get clear headroom.
                for (int y = targetY + 1;
                     y <= targetY + ROAD_HEADROOM && y < worldTopExclusive; y++) {
                    setBlock(chunk, pos.set(worldX, y, z), Blocks.AIR.defaultBlockState());
                }
            }
        }
    }

    private static BlockState pavementFor(int worldX, int z) {
        if (z == ROAD_CENTER_Z && Math.floorMod(worldX, ROAD_LIGHT_SPACING) == 0) {
            return Blocks.SEA_LANTERN.defaultBlockState();
        }
        if (Math.abs(z - ROAD_CENTER_Z) == ROAD_HALF_WIDTH) {
            return Blocks.STONE_BRICKS.defaultBlockState();
        }
        return Blocks.SMOOTH_STONE.defaultBlockState();
    }

    /** Highest useful travel surface, ignoring tree leaves but respecting water. */
    private static int findTravelSurfaceY(ChunkAccess chunk, int x, int z) {
        int worldMinY = chunk.getMinBuildHeight();
        int worldTopExclusive = worldMinY + chunk.getHeight();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int y = worldTopExclusive - 1; y > worldMinY; y--) {
            BlockState state = chunk.getBlockState(pos.set(x, y, z));
            if (state.isAir() || state.is(BlockTags.LEAVES)) continue;
            if (!state.getFluidState().isEmpty()) return Math.min(y + 1, worldTopExclusive - 2);
            if (state.blocksMotion()) return y;
        }
        return Math.max(worldMinY + 1, (int)Math.floor(RingGeometry.SURFACE_Y));
    }

    private static void setBlock(ChunkAccess chunk, BlockPos pos, BlockState state) {
        if (chunk.getBlockState(pos).equals(state)) return;
        chunk.removeBlockEntity(pos);
        chunk.setBlockState(pos, state, false);
    }
}
