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

    /**
     * The main route is intentionally artificial: it stays perfectly level all
     * the way around the ring instead of following hills and valleys. At Y=71 it
     * sits a few blocks above the nominal sea/surface reference while still
     * cutting clean tunnels through mountains.
     */
    private static final int ROAD_Y = 71;
    private static final int ROAD_LIGHT_SPACING = 3;
    private static final int ROAD_HEADROOM = 5;

    private RingArtisticPrototype() { }

    public static void decorate(WorldGenLevel world, ChunkAccess chunk, RingGeometry geometry) {
        if (world.getLevel().dimension() != Level.OVERWORLD) return;
        if (RingGenerationBoundary.isExterior(chunk, geometry)) return;
        buildLevelRoad(chunk, geometry);
    }

    /**
     * Builds a seven-block-wide, globally level causeway.
     *
     * <p>Low terrain receives a solid stone-brick support up to the road. High
     * terrain is cut through instead: the pavement is forced to {@link #ROAD_Y}
     * and five complete air blocks are cleared above it. This produces a true
     * straight tunnel through mountains rather than making the road climb over
     * them.</p>
     */
    private static void buildLevelRoad(ChunkAccess chunk, RingGeometry geometry) {
        int chunkMinZ = chunk.getPos().getMinBlockZ();
        int chunkMaxZ = chunk.getPos().getMaxBlockZ();
        int firstRoadZ = Math.max(chunkMinZ, ROAD_CENTER_Z - ROAD_HALF_WIDTH);
        int lastRoadZ = Math.min(chunkMaxZ, ROAD_CENTER_Z + ROAD_HALF_WIDTH);
        if (firstRoadZ > lastRoadZ) return;

        int worldMinY = chunk.getMinBuildHeight();
        int worldTopExclusive = worldMinY + chunk.getHeight();
        int targetY = Math.max(worldMinY + 1,
                Math.min(ROAD_Y, worldTopExclusive - ROAD_HEADROOM - 1));
        int chunkMinX = chunk.getPos().getMinBlockX();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int localX = 0; localX < 16; localX++) {
            int worldX = chunkMinX + localX;

            for (int z = firstRoadZ; z <= lastRoadZ; z++) {
                int naturalY = findTravelSurfaceY(chunk, worldX, z);

                // Valleys and water become an engineered causeway with a solid support.
                if (naturalY < targetY) {
                    for (int y = naturalY + 1; y < targetY; y++) {
                        setBlock(chunk, pos.set(worldX, y, z),
                                Blocks.STONE_BRICKS.defaultBlockState());
                    }
                }

                // Mountains are not followed: the road stays at one global Y level.
                setBlock(chunk, pos.set(worldX, targetY, z), pavementFor(worldX, z));

                // Five full blocks of headroom make every mountain crossing a clean tunnel.
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
