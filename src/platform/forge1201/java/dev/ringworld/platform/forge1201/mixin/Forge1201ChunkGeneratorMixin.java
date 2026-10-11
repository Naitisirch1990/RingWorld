package dev.ringworld.platform.forge1201.mixin;

import dev.ringworld.platform.forge1201.Forge1201GeneratorAccess;
import dev.ringworld.world.RingGenerationBoundary;
import dev.ringworld.world.RingGeometry;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Forge 1.20.1 finite-band boundary stage. */
@Mixin(ChunkGenerator.class)
abstract class Forge1201ChunkGeneratorMixin {
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
    private void ringworld1201$buildBoundaryAfterFeatures(WorldGenLevel world, ChunkAccess chunk,
                                                           StructureManager structures, CallbackInfo ci) {
        Forge1201GeneratorAccess access = ringworld1201$access();
        if (access == null) return;
        RingGeometry geometry = access.ringworld1201$getGeometry();
        if (geometry == null) return;
        RingGenerationBoundary.installBedrockFloor(chunk, geometry);
        RingGenerationBoundary.installRim(chunk, geometry, access.ringworld1201$getWallHeight());
    }

    private Forge1201GeneratorAccess ringworld1201$access() {
        return (Object)this instanceof Forge1201GeneratorAccess access ? access : null;
    }
}
