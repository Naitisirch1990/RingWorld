package dev.ringworldbiomes.mixin;

import dev.ringworldbiomes.world.RingMapLayout;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkGenerator.class)
public abstract class ChunkGeneratorMixin {
    private static final int WALL_HEIGHT = 160;

    @Inject(method = "applyBiomeDecoration", at = @At("HEAD"))
    private void ringworldBiomes$prepareRing(WorldGenLevel level, ChunkAccess chunk,
            StructureManager structureManager, CallbackInfo ci) {
        if (level.getLevel().dimension() != Level.OVERWORLD) return;
        int localX = RingMapLayout.wallLocalX(chunk.getPos().x);
        if (localX < 0) return;
        var wall = wallMaterial(RingMapLayout.wallColorIndex(chunk.getPos().x));

        int minZ = chunk.getPos().getMinBlockZ();
        int maxZ = chunk.getPos().getMaxBlockZ();
        for (int z = minZ; z <= maxZ; z++) {
            if (z < -1000 || z > 999 || Math.abs(z) <= 6) continue; // 13-wide opening protects the 7-wide road.
            int localZ = z & 15;
            int baseY = chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, localX, localZ);
            int ceiling = Math.min(level.getMaxBuildHeight(), baseY + WALL_HEIGHT);
            for (int y = Math.max(level.getMinBuildHeight(), baseY); y < ceiling; y++) {
                chunk.setBlockState(new BlockPos(chunk.getPos().getMinBlockX() + localX, y, z), wall, false);
            }
        }
    }

    private static net.minecraft.world.level.block.state.BlockState wallMaterial(int segment) {
        return switch (segment) {
            case 0, 5, 12 -> Blocks.YELLOW_TERRACOTTA.defaultBlockState();
            case 1, 11 -> Blocks.LIME_TERRACOTTA.defaultBlockState();
            case 2 -> Blocks.PINK_TERRACOTTA.defaultBlockState();
            case 3, 8 -> Blocks.DARK_OAK_PLANKS.defaultBlockState();
            case 4 -> Blocks.PRISMARINE_BRICKS.defaultBlockState();
            case 6 -> Blocks.PACKED_ICE.defaultBlockState();
            case 7 -> Blocks.PURPLE_TERRACOTTA.defaultBlockState();
            case 9 -> Blocks.GREEN_TERRACOTTA.defaultBlockState();
            case 10 -> Blocks.MANGROVE_PLANKS.defaultBlockState();
            case 13 -> Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState();
            default -> Blocks.SMOOTH_SANDSTONE.defaultBlockState();
        };
    }
}
