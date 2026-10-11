package dev.ringworld.mixin;

import dev.ringworld.world.RingTerrainNoiseMapping;
import dev.ringworld.world.RingWorldGeneratorAccess;
import dev.ringworld.world.RingWorldLayouts;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Blender;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * First Minecraft-ABI hook of the 1.20.1 backport. Besides owning the saved
 * ring policy, it keeps vanilla noise/surface/carver generation out of chunk
 * rows beyond the finite Z band. Guard rows remain empty for the later barrier
 * pass instead of becoming ordinary terrain outside the ring.
 */
@Mixin(NoiseBasedChunkGenerator.class)
public abstract class NoiseBasedChunkGeneratorMixin implements RingWorldGeneratorAccess {
    @Unique
    private RingWorldLayouts.RingLayout ringworld$layout;

    @Unique
    private int ringworld$terrainNoiseMapping = RingTerrainNoiseMapping.CURRENT;

    @Override
    public void ringworld$setLayout(RingWorldLayouts.RingLayout layout) {
        this.ringworld$layout = layout;
    }

    @Override
    public RingWorldLayouts.RingLayout ringworld$getLayout() {
        return ringworld$layout;
    }

    @Override
    public void ringworld$setTerrainNoiseMapping(int mappingVersion) {
        this.ringworld$terrainNoiseMapping = RingTerrainNoiseMapping.requireSupported(mappingVersion);
    }

    @Override
    public int ringworld$getTerrainNoiseMapping() {
        return ringworld$terrainNoiseMapping;
    }

    @Inject(method = "fillFromNoise", at = @At("HEAD"), cancellable = true)
    private void ringworld$skipExteriorNoise(
            Executor executor,
            Blender blender,
            RandomState randomState,
            StructureManager structures,
            ChunkAccess chunk,
            CallbackInfoReturnable<CompletableFuture<ChunkAccess>> cir) {
        if (ringworld$isExterior(chunk)) {
            cir.setReturnValue(CompletableFuture.completedFuture(chunk));
        }
    }

    @Inject(method = "buildSurface", at = @At("HEAD"), cancellable = true)
    private void ringworld$skipExteriorSurface(
            WorldGenRegion region,
            StructureManager structures,
            RandomState randomState,
            ChunkAccess chunk,
            CallbackInfo ci) {
        if (ringworld$isExterior(chunk)) ci.cancel();
    }

    @Inject(method = "applyCarvers", at = @At("HEAD"), cancellable = true)
    private void ringworld$skipExteriorCarvers(
            WorldGenRegion region,
            long seed,
            RandomState randomState,
            BiomeManager biomeManager,
            StructureManager structures,
            ChunkAccess chunk,
            GenerationStep.Carving carving,
            CallbackInfo ci) {
        if (ringworld$isExterior(chunk)) ci.cancel();
    }

    @Unique
    private boolean ringworld$isExterior(ChunkAccess chunk) {
        return ringworld$layout != null && ringworld$layout.isExteriorChunkZ(chunk.getPos().z);
    }
}
