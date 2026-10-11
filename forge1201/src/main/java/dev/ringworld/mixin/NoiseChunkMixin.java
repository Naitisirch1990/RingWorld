package dev.ringworld.mixin;

import dev.ringworld.world.RingNoiseSamplingContext;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.RandomState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Lets one NoiseChunk use the ring-wrapped router without mutating global RandomState. */
@Mixin(net.minecraft.world.level.levelgen.NoiseChunk.class)
public abstract class NoiseChunkMixin {
    @Redirect(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/levelgen/RandomState;router()Lnet/minecraft/world/level/levelgen/NoiseRouter;"))
    private NoiseRouter ringworld$usePeriodicRouter(RandomState randomState) {
        NoiseRouter override = RingNoiseSamplingContext.currentRouter();
        return override != null ? override : randomState.router();
    }
}
