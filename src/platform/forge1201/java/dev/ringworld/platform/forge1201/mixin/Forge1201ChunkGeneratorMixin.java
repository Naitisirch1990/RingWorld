package dev.ringworld.platform.forge1201.mixin;

import dev.ringworld.platform.forge1201.Forge1201GeneratorAccess;
import dev.ringworld.world.RingArtisticPrototype;
import dev.ringworld.world.RingGenerationBoundary;
import dev.ringworld.world.RingGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Forge 1.20.1 finite-band boundary and first artistic world pass. */
@Mixin(ChunkGenerator.class)
abstract class Forge1201ChunkGeneratorMixin {
    private static final int PROJECT_VISIBLE_RIM_HEIGHT = 10;

    @Inject(method = "applyBiomeDecoration", at = @At("HEAD"), cancellable = true)
    private void ringworld1201$skipExteriorFeatures(WorldGenLevel world, ChunkAccess chunk,
                                                    StructureManager structures, CallbackInfo ci) {
        Forge1201GeneratorAccess access = ringworld1201$access();
        if (access == null) return;
        RingGeometry geometry = access.ringworld1201$getGeometry();
        if (geometry == null || !RingGenerationBoundary.isExterior(chunk, geometry)) return;
        RingGenerationBoundary.clearExterior(chunk);
        ci.cancel();
    }

    @Inject(method = "applyBiomeDecoration", at = @At("TAIL"))
    private void ringworld1201$finishRingGeneration(WorldGenLevel world, ChunkAccess chunk,
                                                     StructureManager structures, CallbackInfo ci) {
        Forge1201GeneratorAccess access = ringworld1201$access();
        if (access == null) return;
        RingGeometry geometry = access.ringworld1201$getGeometry();
        if (geometry == null) return;

        RingGenerationBoundary.installBedrockFloor(chunk, geometry);
        RingGenerationBoundary.installRim(chunk, geometry, access.ringworld1201$getWallHeight());
        ringworld1201$trimVisibleRimToProjectHeight(chunk, geometry);
        RingArtisticPrototype.decorate(world, chunk, geometry);
    }

    /**
     * The project brief uses ten visible bedrock blocks over the levelled edge
     * approach. The underlying boundary helper still carries a taller legacy
     * style for save migration, so this final generation pass turns only the
     * surplus visible rim into the same invisible barrier used above it.
     */
    private void ringworld1201$trimVisibleRimToProjectHeight(ChunkAccess chunk, RingGeometry geometry) {
        if (!RingGenerationBoundary.containsRim(chunk, geometry)) return;

        int firstBarrierY = (int)Math.floor(RingGeometry.SURFACE_Y) + 1 + PROJECT_VISIBLE_RIM_HEIGHT;
        int worldTopExclusive = chunk.getMinBuildHeight() + chunk.getHeight();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int[] rimZs = {geometry.minWidthZ(), geometry.maxWidthZ()};

        for (int rimZ : rimZs) {
            if (rimZ < chunk.getPos().getMinBlockZ() || rimZ > chunk.getPos().getMaxBlockZ()) continue;
            int inward = rimZ == geometry.minWidthZ() ? 1 : -1;
            for (int x = chunk.getPos().getMinBlockX(); x <= chunk.getPos().getMaxBlockX(); x++) {
                for (int depth = 0; depth < RingGenerationBoundary.RIM_THICKNESS; depth++) {
                    int z = rimZ + inward * depth;
                    for (int y = firstBarrierY; y < worldTopExclusive; y++) {
                        pos.set(x, y, z);
                        if (chunk.getBlockState(pos).is(Blocks.BARRIER)) continue;
                        chunk.removeBlockEntity(pos);
                        chunk.setBlockState(pos, Blocks.BARRIER.defaultBlockState(), false);
                    }
                }
            }
        }
    }

    private Forge1201GeneratorAccess ringworld1201$access() {
        return (Object)this instanceof Forge1201GeneratorAccess access ? access : null;
    }
}
