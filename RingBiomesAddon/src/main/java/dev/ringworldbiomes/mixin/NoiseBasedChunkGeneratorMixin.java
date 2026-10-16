package dev.ringworldbiomes.mixin;

import dev.ringworldbiomes.world.RingMapLayout;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Replaces the biome samples when the noise generator has chosen the chunk's
 * biomes. Decoration-time replacement is too late for biome-specific blocks.
 */
@Mixin(NoiseBasedChunkGenerator.class)
abstract class NoiseBasedChunkGeneratorMixin {
    @Inject(method = "doCreateBiomes", at = @At("RETURN"), cancellable = true)
    private void ringworldBiomes$setFixedOverworldBiome(
            Blender blender,
            RandomState randomState,
            StructureManager structureManager,
            ChunkAccess chunk,
            CallbackInfo ci) {
        String biomeId = RingMapLayout.biomeIdForChunk(chunk.getPos().x);
        ResourceKey<Biome> biomeKey = ResourceKey.create(
                Registries.BIOME, new ResourceLocation(biomeId));

        // Nether and End biome sources do not contain any of these Overworld keys.
        Holder<Biome> target = ((ChunkGenerator)(Object)this).getBiomeSource().possibleBiomes().stream()
                .filter(holder -> holder.unwrapKey().filter(biomeKey::equals).isPresent())
                .findFirst()
                .orElse(null);
        if (target == null) return;

        BiomeResolver fixedBiome = (quartX, quartY, quartZ, sampler) -> target;
        chunk.fillBiomesFromNoise(fixedBiome, randomState.sampler());
    }
}
