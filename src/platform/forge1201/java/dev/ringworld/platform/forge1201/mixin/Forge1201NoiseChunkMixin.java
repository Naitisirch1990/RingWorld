package dev.ringworld.platform.forge1201.mixin;

import dev.ringworld.world.RingNoiseSamplingContext;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.RandomState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Installs the RingWorld router inside Forge 1.20.1 NoiseChunk construction. */
@Mixin(NoiseChunk.class)
abstract class Forge1201NoiseChunkMixin {
    @Redirect(
            method = "<init>",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/levelgen/RandomState;router()Lnet/minecraft/world/level/levelgen/NoiseRouter;"))
    private NoiseRouter ringworld1201$usePeriodicRouter(RandomState randomState) {
        NoiseRouter override = RingNoiseSamplingContext.currentRouter();
        return override != null ? override : randomState.router();
    }
}
