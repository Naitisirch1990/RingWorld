package dev.ringworld.platform.forge1201.client;

import dev.ringworld.world.RingGeometry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

/** Real eight-block surface samples, retained across chunk unloads in one world.
 * Never loads or generates chunks. Unknown cells are explicitly unsampled.
 */
public final class Forge1201RingTerrainAtlas {
    private static final int STEP = 8;
    private static ClientLevel sampledLevel;
    private static int columns, rows, circumference, width, minZ;
    private static float[] heights = new float[0];
    private static int[] colors = new int[0];
    private static boolean[] known = new boolean[0];
    private static long tick = Long.MIN_VALUE;
    private static int cursor;
    private Forge1201RingTerrainAtlas() { }

    public static void refreshLoadedTerrain(ClientLevel level, RingGeometry geometry) {
        if (sampledLevel != level || circumference != geometry.circumferenceBlocks()
                || width != geometry.widthBlocks()) {
            sampledLevel = level;
            circumference = geometry.circumferenceBlocks(); width = geometry.widthBlocks();
            minZ = geometry.minWidthZ(); columns = circumference / STEP; rows = width / STEP;
            heights = new float[columns * rows]; colors = new int[heights.length];
            known = new boolean[heights.length]; tick = Long.MIN_VALUE; cursor = 0;
        }
        if (tick == level.getGameTime()) return;
        tick = level.getGameTime();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        int radius = Math.min(32, Math.max(2, mc.options.getEffectiveRenderDistance()));
        int side = radius * 2 + 1, total = side * side;
        int centerX = mc.player.blockPosition().getX() >> 4;
        int centerZ = mc.player.blockPosition().getZ() >> 4;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        // Bounded work per tick, including at 32 chunks; a full loaded neighbourhood
        // is swept again so block changes do not remain permanently stale.
        for (int work = 0; work < Math.min(96, total); work++) {
            int n = cursor++ % total;
            int cx = centerX + n % side - radius, cz = centerZ + n / side - radius;
            if (geometry.isExteriorChunkZ(cz)) continue;
            LevelChunk chunk = level.getChunkSource().getChunk(cx, cz, false);
            if (chunk == null) continue;
            for (int dx = 4; dx < 16; dx += STEP) {
                int x = cx * 16 + dx;
                for (int dz = 4; dz < 16; dz += STEP) {
                    int z = cz * 16 + dz;
                    if (z < minZ || z >= minZ + width) continue;
                    int y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, dx, dz);
                    if (y < level.getMinBuildHeight()) continue;
                    // ChunkAccess.getHeight is the highest block, not first air.
                    BlockState state = chunk.getBlockState(pos.set(x, y, z));
                    // The invisible barrier above the physical rim is not a visible surface.
                    while (y > level.getMinBuildHeight() && (state.isAir() || state.is(Blocks.BARRIER))) {
                        state = chunk.getBlockState(pos.set(x, --y, z));
                    }
                    if (state.isAir()) continue;
                    int i = (geometry.wrapBlockX(x) / STEP) * rows + (z - minZ) / STEP;
                    heights[i] = y + 1; colors[i] = state.getMapColor(level, pos).col; known[i] = true;
                }
            }
        }
    }

    public static Sample sample(double x, double z, RingGeometry geometry) {
        if (sampledLevel == null || circumference != geometry.circumferenceBlocks()
                || width != geometry.widthBlocks()) return new Sample(64, 0xA6B7AC, 0);
        double gx = geometry.wrapX(x) / STEP - 0.5;
        double gz = Math.max(0, Math.min(rows - 1, (z - minZ) / STEP - 0.5));
        int x0 = (int)Math.floor(gx), z0 = (int)Math.floor(gz);
        double tx = gx - Math.floor(gx), tz = gz - z0;
        double h = 0, r = 0, g = 0, b = 0, confidence = 0;
        for (int a = 0; a < 2; a++) for (int c = 0; c < 2; c++) {
            int i = Math.floorMod(x0 + a, columns) * rows + Math.min(rows - 1, z0 + c);
            double weight = (a == 0 ? 1 - tx : tx) * (c == 0 ? 1 - tz : tz);
            // Unknown samples have no fabricated coast, mountains or biome colours.
            double y = known[i] ? heights[i] : 64;
            int color = known[i] ? colors[i] : 0xA6B7AC;
            confidence += known[i] ? weight : 0;
            h += y * weight; r += ((color >> 16) & 255) * weight;
            g += ((color >> 8) & 255) * weight; b += (color & 255) * weight;
        }
        return new Sample(h, ((int)r << 16) | ((int)g << 8) | (int)b, confidence);
    }
    public record Sample(double height, int color, double confidence) { }
}
