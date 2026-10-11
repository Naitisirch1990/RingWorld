package dev.ringworld.platform.forge1201.mixin;

import dev.ringworld.platform.forge1201.Forge1201GeneratorAccess;
import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingNoiseRouter;
import dev.ringworld.world.RingNoiseSamplingContext;
import dev.ringworld.world.RingTerrainNoiseMapping;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Stores geometry and installs the saved annular noise mapping on Forge 1.20.1. */
@Mixin(NoiseBasedChunkGenerator.class)
abstract class Forge1201NoiseGeneratorMixin implements Forge1201GeneratorAccess {
    @Unique private RingGeometry ringworld1201$geometry;
    @Unique private int ringworld1201$terrainNoiseMapping = RingTerrainNoiseMapping.CURRENT;
    @Unique private int ringworld1201$wallHeight;
    @Unique private RandomState ringworld1201$cachedNoiseState;
    @Unique private NoiseRouter ringworld1201$cachedRouter;

    @Override
    public void ringworld1201$setGeometry(RingGeometry geometry) {
        this.ringworld1201$geometry = geometry;
        this.ringworld1201$cachedNoiseState = null;
        this.ringworld1201$cachedRouter = null;
    }

    @Override
    public RingGeometry ringworld1201$getGeometry() {
        return ringworld1201$geometry;
    }

    @Override
    public void ringworld1201$setTerrainNoiseMapping(int mappingVersion) {
        int supported = RingTerrainNoiseMapping.requireSupported(mappingVersion);
        if (supported == ringworld1201$terrainNoiseMapping) return;
        ringworld1201$terrainNoiseMapping = supported;
        ringworld1201$cachedNoiseState = null;
        ringworld1201$cachedRouter = null;
    }

    @Override
    public int ringworld1201$getTerrainNoiseMapping() {
        return ringworld1201$terrainNoiseMapping;
    }

    @Override
    public void ringworld1201$setWallHeight(int wallHeightBlocks) {
        this.ringworld1201$wallHeight = wallHeightBlocks;
    }

    @Override
    public int ringworld1201$getWallHeight() {
        return ringworld1201$wallHeight;
    }

    /** Biome climate uses the same annular coordinate embedding as terrain. */
    @Redirect(
            method = "doCreateBiomes",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/levelgen/RandomState;router()Lnet/minecraft/world/level/levelgen/NoiseRouter;"))
    private NoiseRouter ringworld1201$periodicBiomeRouter(RandomState randomState) {
        return ringworld1201$geometry == null
                ? randomState.router()
                : ringworld1201$periodicRouter(randomState);
    }

    /** Final terrain density must be constructed with that same periodic router. */
    @Redirect(
            method = "createNoiseChunk",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/levelgen/NoiseChunk;forChunk(Lnet/minecraft/world/level/chunk/ChunkAccess;Lnet/minecraft/world/level/levelgen/RandomState;Lnet/minecraft/world/level/levelgen/DensityFunctions$BeardifierOrMarker;Lnet/minecraft/world/level/levelgen/NoiseGeneratorSettings;Lnet/minecraft/world/level/levelgen/Aquifer$FluidPicker;Lnet/minecraft/world/level/levelgen/blending/Blender;)Lnet/minecraft/world/level/levelgen/NoiseChunk;"))
    private NoiseChunk ringworld1201$createPeriodicSampler(
            ChunkAccess chunk, RandomState randomState,
            DensityFunctions.BeardifierOrMarker beardifier,
            NoiseGeneratorSettings settings, Aquifer.FluidPicker fluidPicker,
            Blender blender) {
        NoiseRouter override = ringworld1201$geometry == null
                ? null : ringworld1201$periodicRouter(randomState);
        return RingNoiseSamplingContext.withRouter(override,
                () -> NoiseChunk.forChunk(chunk, randomState, beardifier, settings, fluidPicker, blender));
    }

    @Unique
    private synchronized NoiseRouter ringworld1201$periodicRouter(RandomState randomState) {
        if (ringworld1201$cachedNoiseState != randomState || ringworld1201$cachedRouter == null) {
            ringworld1201$cachedNoiseState = randomState;
            ringworld1201$cachedRouter = RingNoiseRouter.wrap(
                    randomState.router(), ringworld1201$geometry, ringworld1201$terrainNoiseMapping);
        }
        return ringworld1201$cachedRouter;
    }
}
