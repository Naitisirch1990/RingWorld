package dev.ringworldbiomes.mixin;

import dev.ringworldbiomes.world.RingMapLayout;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Replaces the generated biome samples before surface rules, carving and
 * biome decorations use them. Doing this during decoration is too late for
 * biome-specific ground blocks and terrain generation.
 */
@Mixin(NoiseBasedChunkGenerator.class)
abstract class NoiseBasedChunkGeneratorMixin {
    @Inject(method = "doCreateBiomes", at = @At("RETURN"), cancellable = true)
    private void ringworldBiomes$setFixedOverworldBiome(
            Executor executor,
            RandomState randomState,
            Blender blender,
            StructureManager structureManager,
            ChunkAccess chunk,
            CallbackInfoReturnable<CompletableFuture<ChunkAccess>> cir) {
        String biomeId = RingMapLayout.biomeIdForChunk(chunk.getPos().x);
        ResourceKey<Biome> biomeKey = ResourceKey.create(
                Registries.BIOME, new ResourceLocation(biomeId));

        // Nether and End biome sources do not contain any of these Overworld keys.
        Holder<Biome> target = ((ChunkGenerator)(Object)this).getBiomeSource().possibleBiomes().stream()
                .filter(holder -> holder.unwrapKey().filter(biomeKey::equals).isPresent())
                .findFirst()
                .orElse(null);
        if (target == null) return;

        cir.setReturnValue(cir.getReturnValue().thenApply(generated -> {
            BiomeResolver fixedBiome = (quartX, quartY, quartZ, sampler) -> target;
            generated.fillBiomesFromNoise(fixedBiome, randomState.sampler());
            return generated;
        }));
    }
}
