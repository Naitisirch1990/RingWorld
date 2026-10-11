package dev.ringworld.world;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;

/**
 * First playable art pass for the project ring.
 *
 * <p>The decoration is deliberately coordinate-driven: every chunk can build
 * only its own piece of a road, city, or monument, so landmarks remain stable
 * across the periodic X seam and do not require neighbouring chunks to be
 * loaded. It is intentionally conservative outside the central travel spine;
 * vanilla terrain, villages and third-party structures remain free to occupy
 * the rest of the 2,000-block-wide band.</p>
 */
public final class RingArtisticPrototype {
    public static final int TARGET_LANDMARK_SPACING = 4_096;
    public static final int ROAD_Y = (int)RingGeometry.SURFACE_Y + 1;
    public static final int ROAD_HALF_WIDTH = 3;
    public static final int LANDMARK_OFFSET_Z = 144;

    private static final int ROAD_HEADROOM = 5;
    private static final int PYRAMID_RADIUS = 32;
    private static final int CITY_RADIUS = 80;

    private RingArtisticPrototype() { }

    /** Adds the prototype roads and monuments after vanilla biome decoration. */
    public static void decorate(WorldGenLevel world, ChunkAccess chunk, RingGeometry geometry) {
        if (RingGenerationBoundary.isExterior(chunk, geometry)) return;

        DimensionStyle style = DimensionStyle.forDimension(world.getLevel().dimension());
        if (style == null) return;

        int landmarkCount = Math.max(1,
                Math.round(geometry.circumferenceBlocks() / (float)TARGET_LANDMARK_SPACING));
        int landmarkSpacing = geometry.circumferenceBlocks() / landmarkCount;

        int minZ = Math.max(chunk.getPos().getMinBlockZ(), geometry.minWidthZ());
        int maxZ = Math.min(chunk.getPos().getMaxBlockZ(), geometry.maxWidthZ());
        if (minZ > maxZ) return;

        for (int rawX = chunk.getPos().getMinBlockX(); rawX <= chunk.getPos().getMaxBlockX(); rawX++) {
            int canonicalX = geometry.wrapBlockX(rawX);
            Landmark landmark = nearestLandmark(canonicalX, geometry.circumferenceBlocks(),
                    landmarkCount, landmarkSpacing);

            for (int z = minZ; z <= maxZ; z++) {
                // One continuous, seven-block-wide causeway follows the complete ring.
                if (Math.abs(z) <= ROAD_HALF_WIDTH) {
                    buildRoadColumn(chunk, rawX, z, canonicalX, style, true);
                }

                // Every landmark gets a short north/south spur from the ring road.
                int landmarkZ = ((landmark.index() & 1) == 0 ? 1 : -1) * LANDMARK_OFFSET_Z;
                if (Math.abs(landmark.deltaX()) <= 2 && betweenInclusive(z, 0, landmarkZ)) {
                    buildRoadColumn(chunk, rawX, z, canonicalX, style, false);
                }

                int dz = z - landmarkZ;
                if (style == DimensionStyle.OVERWORLD && landmark.index() == 0) {
                    buildPyramidCapitalColumn(chunk, rawX, z, canonicalX,
                            landmark.deltaX(), dz, style);
                } else {
                    buildMonumentColumn(chunk, rawX, z, canonicalX,
                            landmark.deltaX(), dz, style, landmark.index());
                }
            }
        }
    }

    private static void buildRoadColumn(ChunkAccess chunk, int x, int z, int canonicalX,
                                        DimensionStyle style, boolean mainRoad) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int transverse = mainRoad ? Math.abs(z) : 0;
        BlockState pavement = transverse == ROAD_HALF_WIDTH ? style.roadEdge : style.road;

        clearVertical(chunk, pos, x, z, ROAD_Y + 1, ROAD_Y + ROAD_HEADROOM);
        set(chunk, pos.set(x, ROAD_Y, z), pavement);

        // Small luminous studs make the road readable at night without rows of entities.
        if (mainRoad && Math.floorMod(canonicalX, 32) == 0 && Math.abs(z) == ROAD_HALF_WIDTH - 1) {
            set(chunk, pos.set(x, ROAD_Y, z), style.light);
        }
    }

    /**
     * The seam landmark is the prototype desert capital: one monumental pyramid,
     * a walled city, gates, streets and repeated low houses. The pyramid is the
     * sole pyramid in the whole Overworld layout.
     */
    private static void buildPyramidCapitalColumn(ChunkAccess chunk, int x, int z, int canonicalX,
                                                   int dx, int dz, DimensionStyle style) {
        int ax = Math.abs(dx);
        int az = Math.abs(dz);
        int chebyshev = Math.max(ax, az);
        if (chebyshev > CITY_RADIUS + 2) return;

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        // Sandstone street grid and broad ceremonial plaza around the pyramid.
        int streetX = centeredModulo(dx, 24);
        int streetZ = centeredModulo(dz, 24);
        boolean plaza = chebyshev <= PYRAMID_RADIUS + 8;
        boolean street = (Math.abs(streetX) <= 2 || Math.abs(streetZ) <= 2)
                && chebyshev < CITY_RADIUS - 3;
        if (plaza || street) {
            clearVertical(chunk, pos, x, z, ROAD_Y + 1, ROAD_Y + 5);
            set(chunk, pos.set(x, ROAD_Y, z), plaza
                    ? Blocks.SMOOTH_SANDSTONE.defaultBlockState()
                    : Blocks.CUT_SANDSTONE.defaultBlockState());
        }

        // Two-block-thick city wall with four generous gates.
        if (chebyshev >= CITY_RADIUS - 1 && chebyshev <= CITY_RADIUS) {
            boolean northSouthGate = az >= CITY_RADIUS - 1 && ax <= 4;
            boolean eastWestGate = ax >= CITY_RADIUS - 1 && az <= 4;
            if (!northSouthGate && !eastWestGate) {
                for (int y = ROAD_Y + 1; y <= ROAD_Y + 7; y++) {
                    set(chunk, pos.set(x, y, z), Blocks.CUT_SANDSTONE.defaultBlockState());
                }
                if (((ax + az) & 7) == 0) {
                    set(chunk, pos.set(x, ROAD_Y + 8, z), Blocks.SMOOTH_SANDSTONE.defaultBlockState());
                }
            } else {
                clearVertical(chunk, pos, x, z, ROAD_Y + 1, ROAD_Y + 8);
            }
        }

        // Four taller corner bastions anchor the city silhouette.
        if (ax >= CITY_RADIUS - 7 && az >= CITY_RADIUS - 7
                && ax <= CITY_RADIUS && az <= CITY_RADIUS) {
            for (int y = ROAD_Y + 1; y <= ROAD_Y + 13; y++) {
                boolean shell = ax >= CITY_RADIUS - 2 || az >= CITY_RADIUS - 2
                        || ax <= CITY_RADIUS - 5 || az <= CITY_RADIUS - 5;
                set(chunk, pos.set(x, y, z), shell
                        ? Blocks.CHISELED_SANDSTONE.defaultBlockState()
                        : Blocks.AIR.defaultBlockState());
            }
            set(chunk, pos.set(x, ROAD_Y + 14, z), Blocks.SMOOTH_SANDSTONE.defaultBlockState());
        }

        // Repeated houses create a readable city without erasing the whole biome.
        if (chebyshev > PYRAMID_RADIUS + 10 && chebyshev < CITY_RADIUS - 6
                && Math.abs(streetX) >= 5 && Math.abs(streetX) <= 10
                && Math.abs(streetZ) >= 5 && Math.abs(streetZ) <= 10) {
            int hx = Math.abs(streetX);
            int hz = Math.abs(streetZ);
            boolean wall = hx >= 9 || hz >= 9;
            boolean doorway = hz >= 9 && streetZ < 0 && Math.abs(streetX) <= 6;
            for (int y = ROAD_Y + 1; y <= ROAD_Y + 5; y++) {
                BlockState state = wall && !(doorway && y <= ROAD_Y + 3)
                        ? Blocks.SANDSTONE.defaultBlockState()
                        : Blocks.AIR.defaultBlockState();
                set(chunk, pos.set(x, y, z), state);
            }
            set(chunk, pos.set(x, ROAD_Y + 6, z), Blocks.SMOOTH_SANDSTONE.defaultBlockState());
        }

        // One stepped 65x65 pyramid at the heart of the capital.
        if (chebyshev <= PYRAMID_RADIUS) {
            int topY = ROAD_Y + 1 + (PYRAMID_RADIUS - chebyshev);
            for (int y = ROAD_Y + 1; y <= topY; y++) {
                set(chunk, pos.set(x, y, z), Blocks.SANDSTONE.defaultBlockState());
            }

            // South entrance corridor and a small central chamber.
            if (Math.abs(dx) <= 2 && dz <= -20 && dz >= -PYRAMID_RADIUS) {
                clearVertical(chunk, pos, x, z, ROAD_Y + 1, ROAD_Y + 5);
            }
            if (Math.abs(dx) <= 5 && Math.abs(dz) <= 5) {
                clearVertical(chunk, pos, x, z, ROAD_Y + 1, ROAD_Y + 7);
            }

            if (dx == 0 && dz == 0) {
                set(chunk, pos.set(x, ROAD_Y + PYRAMID_RADIUS + 2, z),
                        Blocks.GOLD_BLOCK.defaultBlockState());
                set(chunk, pos.set(x, ROAD_Y + PYRAMID_RADIUS + 3, z), style.light);
            }
        }
    }

    /** A different monumental tower/plaza is repeated roughly every four kilometres. */
    private static void buildMonumentColumn(ChunkAccess chunk, int x, int z, int canonicalX,
                                            int dx, int dz, DimensionStyle style, int landmarkIndex) {
        int ax = Math.abs(dx);
        int az = Math.abs(dz);
        if (ax > 24 || az > 24) return;

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int chebyshev = Math.max(ax, az);

        if (chebyshev <= 18) {
            clearVertical(chunk, pos, x, z, ROAD_Y + 1, ROAD_Y + 4);
            BlockState floor = ((ax + az + landmarkIndex) & 3) == 0 ? style.roadEdge : style.road;
            set(chunk, pos.set(x, ROAD_Y, z), floor);
        }

        // Four corner pylons.
        if (ax >= 13 && ax <= 15 && az >= 13 && az <= 15) {
            for (int y = ROAD_Y + 1; y <= ROAD_Y + 14; y++) {
                set(chunk, pos.set(x, y, z), style.accent);
            }
            if (ax == 14 && az == 14) {
                set(chunk, pos.set(x, ROAD_Y + 15, z), style.light);
            }
        }

        // Hollow 9x9 central tower with an entrance facing the ring road.
        if (ax <= 4 && az <= 4) {
            for (int y = ROAD_Y + 1; y <= ROAD_Y + 27; y++) {
                boolean shell = ax == 4 || az == 4 || y >= ROAD_Y + 25;
                boolean doorway = dz == (LANDMARK_OFFSET_Z > 0 ? -4 : 4)
                        && ax <= 1 && y <= ROAD_Y + 4;
                BlockState state = shell && !doorway ? style.accent : Blocks.AIR.defaultBlockState();
                set(chunk, pos.set(x, y, z), state);
            }
        }

        // Tapered beacon crown, visible from the road before the plaza is reached.
        for (int crown = 0; crown < 8; crown++) {
            int radius = Math.max(0, 3 - crown / 2);
            if (ax <= radius && az <= radius) {
                set(chunk, pos.set(x, ROAD_Y + 28 + crown, z),
                        crown == 7 ? style.light : style.crown);
            }
        }
    }

    private static Landmark nearestLandmark(int canonicalX, int circumference,
                                            int landmarkCount, int spacing) {
        int rounded = Math.floorDiv(canonicalX + spacing / 2, spacing);
        int index = Math.floorMod(rounded, landmarkCount);
        int centerX = index * spacing;
        int delta = canonicalX - centerX;
        if (delta > circumference / 2) delta -= circumference;
        if (delta < -circumference / 2) delta += circumference;
        return new Landmark(index, centerX, delta);
    }

    private static int centeredModulo(int value, int period) {
        return Math.floorMod(value + period / 2, period) - period / 2;
    }

    private static boolean betweenInclusive(int value, int a, int b) {
        return value >= Math.min(a, b) && value <= Math.max(a, b);
    }

    private static void clearVertical(ChunkAccess chunk, BlockPos.MutableBlockPos pos,
                                      int x, int z, int minY, int maxY) {
        int top = chunk.getMinBuildHeight() + chunk.getHeight() - 1;
        int from = Math.max(minY, chunk.getMinBuildHeight());
        int to = Math.min(maxY, top);
        for (int y = from; y <= to; y++) {
            set(chunk, pos.set(x, y, z), Blocks.AIR.defaultBlockState());
        }
    }

    private static void set(ChunkAccess chunk, BlockPos pos, BlockState state) {
        if (chunk.getBlockState(pos).equals(state)) return;
        chunk.removeBlockEntity(pos);
        chunk.setBlockState(pos, state, false);
    }

    private record Landmark(int index, int centerX, int deltaX) { }

    private enum DimensionStyle {
        OVERWORLD(
                Blocks.STONE_BRICKS.defaultBlockState(),
                Blocks.SMOOTH_STONE.defaultBlockState(),
                Blocks.CHISELED_STONE_BRICKS.defaultBlockState(),
                Blocks.COPPER_BLOCK.defaultBlockState(),
                Blocks.SEA_LANTERN.defaultBlockState()),
        NETHER(
                Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(),
                Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState(),
                Blocks.GILDED_BLACKSTONE.defaultBlockState(),
                Blocks.RED_NETHER_BRICKS.defaultBlockState(),
                Blocks.SHROOMLIGHT.defaultBlockState()),
        END(
                Blocks.END_STONE_BRICKS.defaultBlockState(),
                Blocks.PURPUR_BLOCK.defaultBlockState(),
                Blocks.PURPUR_PILLAR.defaultBlockState(),
                Blocks.OBSIDIAN.defaultBlockState(),
                Blocks.SEA_LANTERN.defaultBlockState());

        private final BlockState road;
        private final BlockState roadEdge;
        private final BlockState accent;
        private final BlockState crown;
        private final BlockState light;

        DimensionStyle(BlockState road, BlockState roadEdge, BlockState accent,
                       BlockState crown, BlockState light) {
            this.road = road;
            this.roadEdge = roadEdge;
            this.accent = accent;
            this.crown = crown;
            this.light = light;
        }

        private static DimensionStyle forDimension(ResourceKey<Level> dimension) {
            if (Level.OVERWORLD.equals(dimension)) return OVERWORLD;
            if (Level.NETHER.equals(dimension)) return NETHER;
            if (Level.END.equals(dimension)) return END;
            return null;
        }
    }
}
