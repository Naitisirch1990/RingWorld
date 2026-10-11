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
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Minecraft 1.20.1 noise-generator bridge for the custom ring policy.
 * Exterior chunk rows are kept empty, while every terrain-critical horizontal
 * sampling path uses one cached annular router for the attached dimension.
 */
@Mixin(NoiseBasedChunkGenerator.class)
public abstract class NoiseBasedChunkGeneratorMixin implements RingWorldGeneratorAccess {
    @Unique
    private RingWorldLayouts.RingLayout ringworld$layout;

    @Unique
    private int ringworld$terrainNoiseMapping = RingTerrainNoiseMapping.CURRENT;

    @Unique
    private RandomState ringworld$cachedNoiseState;

    @Unique
    private NoiseRouter ringworld$cachedRouter;

    @Override
    public void ringworld$setLayout(RingWorldLayouts.RingLayout layout) {
        if (this.ringworld$layout == layout || (layout != null && layout.equals(this.ringworld$layout))) {
            return;
        }
        this.ringworld$layout = layout;
        ringworld$clearRouterCache();
    }

    @Override
    public RingWorldLayouts.RingLayout ringworld$getLayout() {
        return ringworld$layout;
    }

    @Override
    public void ringworld$setTerrainNoiseMapping(int mappingVersion) {
        int supported = RingTerrainNoiseMapping.requireSupported(mappingVersion);
        if (this.ringworld$terrainNoiseMapping == supported) return;
        this.ringworld$terrainNoiseMapping = supported;
        ringworld$clearRouterCache();
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

    /** Biome climate noise must use the same periodic graph as terrain density. */
    @Redirect(
            method = "doCreateBiomes",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/levelgen/RandomState;router()Lnet/minecraft/world/level/levelgen/NoiseRouter;"))
    private NoiseRouter ringworld$periodicBiomeRouter(RandomState randomState) {
        return ringworld$layout == null
                ? randomState.router()
                : ringworld$getOrCreatePeriodicRouter(randomState);
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
        NoiseRouter override = ringworld$layout == null
                ? null
                : ringworld$getOrCreatePeriodicRouter(randomState);
        return RingNoiseSamplingContext.withRouter(override,
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

    /** A cave crossing X=0 must retain the same random identity on both seam charts. */
    @Redirect(
            method = "applyCarvers",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/levelgen/WorldgenRandom;setLargeFeatureSeed(JII)V"))
    private void ringworld$periodicCarverSeed(
            WorldgenRandom random,
            long seed,
            int sourceChunkX,
            int sourceChunkZ) {
        int seedChunkX = ringworld$layout == null
                ? sourceChunkX
                : RingTerrainNoiseMapping.carverSeedChunkX(
                        ringworld$layout, ringworld$terrainNoiseMapping, sourceChunkX);
        random.setLargeFeatureSeed(seed, seedChunkX, sourceChunkZ);
    }

    /** Height queries used by structure anchors must share canonical X ownership. */
    @ModifyVariable(
            method = "iterateNoiseColumn",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0,
            require = 1)
    private int ringworld$canonicalizeHeightQueryX(int blockX) {
        return ringworld$layout == null ? blockX : ringworld$layout.wrapBlockX(blockX);
    }

    @Redirect(
            method = "iterateNoiseColumn",
            at = @At(
                    value = "NEW",
                    target = "(ILnet/minecraft/world/level/levelgen/RandomState;IILnet/minecraft/world/level/levelgen/NoiseSettings;Lnet/minecraft/world/level/levelgen/DensityFunctions$BeardifierOrMarker;Lnet/minecraft/world/level/levelgen/NoiseGeneratorSettings;Lnet/minecraft/world/level/levelgen/Aquifer$FluidPicker;Lnet/minecraft/world/level/levelgen/blending/Blender;)Lnet/minecraft/world/level/levelgen/NoiseChunk;"))
    private NoiseChunk ringworld$createPeriodicHeightSampler(
            int cellCountXZ,
            RandomState randomState,
            int firstBlockX,
            int firstBlockZ,
            NoiseSettings noiseSettings,
            DensityFunctions.BeardifierOrMarker beardifier,
            NoiseGeneratorSettings settings,
            Aquifer.FluidPicker fluidPicker,
            Blender blender) {
        NoiseRouter override = ringworld$layout == null
                ? null
                : ringworld$getOrCreatePeriodicRouter(randomState);
        return RingNoiseSamplingContext.withRouter(override,
                () -> new NoiseChunk(
                        cellCountXZ, randomState, firstBlockX, firstBlockZ,
                        noiseSettings, beardifier, settings, fluidPicker, blender));
    }

    @Unique
    private synchronized NoiseRouter ringworld$getOrCreatePeriodicRouter(RandomState randomState) {
        if (ringworld$cachedNoiseState != randomState || ringworld$cachedRouter == null) {
            ringworld$cachedNoiseState = randomState;
            ringworld$cachedRouter = RingNoiseRouter.wrap(
                    randomState.router(), ringworld$layout, ringworld$terrainNoiseMapping);
        }
        return ringworld$cachedRouter;
    }

    @Unique
    private synchronized void ringworld$clearRouterCache() {
        ringworld$cachedNoiseState = null;
        ringworld$cachedRouter = null;
    }

    @Unique
    private boolean ringworld$isExterior(ChunkAccess chunk) {
        return ringworld$layout != null && ringworld$layout.isExteriorChunkZ(chunk.getPos().z);
    }
}
