package dev.ringworld.mixin;

import dev.ringworld.world.RingNoiseRouter;
import dev.ringworld.world.RingNoiseSamplingContext;
import dev.ringworld.world.RingTerrainNoiseMapping;
import dev.ringworld.world.RingWorldGeneratorAccess;
import dev.ringworld.world.RingWorldLayouts;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.Blender;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.RandomState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Minecraft 1.20.1 noise-generator bridge for the custom ring policy.
 * Exterior chunk rows are kept empty, while interior NoiseChunk construction
 * receives a ring-wrapped density router so X=0 and X=circumference sample one
 * continuous annular noise domain rather than two unrelated flat-world edges.
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

    @Redirect(
            method = "createNoiseChunk",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/levelgen/NoiseChunk;forChunk(Lnet/minecraft/world/level/chunk/ChunkAccess;Lnet/minecraft/world/level/levelgen/RandomState;Lnet/minecraft/world/level/levelgen/DensityFunctions$BeardifierOrMarker;Lnet/minecraft/world/level/levelgen/NoiseGeneratorSettings;Lnet/minecraft/world/level/levelgen/Aquifer$FluidPicker;Lnet/minecraft/world/level/levelgen/blending/Blender;)Lnet/minecraft/world/level/levelgen/NoiseChunk;"))
    private NoiseChunk ringworld$createPeriodicNoiseChunk(
            ChunkAccess chunk,
            RandomState randomState,
            DensityFunctions.BeardifierOrMarker beardifier,
            NoiseGeneratorSettings settings,
            Aquifer.FluidPicker fluidPicker,
            Blender blender) {
        if (ringworld$layout == null) {
            return NoiseChunk.forChunk(chunk, randomState, beardifier, settings, fluidPicker, blender);
        }
        NoiseRouter periodicRouter = RingNoiseRouter.wrap(
                randomState.router(), ringworld$layout, ringworld$terrainNoiseMapping);
        return RingNoiseSamplingContext.withRouter(periodicRouter,
                () -> NoiseChunk.forChunk(
                        chunk, randomState, beardifier, settings, fluidPicker, blender));
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
