package dev.ringworld.mixin;

import dev.ringworld.world.RingGenerationBoundary;
import dev.ringworld.world.RingWorldGeneratorAccess;
import dev.ringworld.world.RingWorldLayouts;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps finite-band mutations inside Minecraft's asynchronous generation pipeline. */
@Mixin(ChunkGenerator.class)
public abstract class ChunkGeneratorMixin {
    @Inject(method = "applyBiomeDecoration", at = @At("HEAD"), cancellable = true)
    private void ringworld$prepareExteriorBoundary(
            WorldGenLevel level,
            ChunkAccess chunk,
            StructureManager structures,
            CallbackInfo ci) {
        RingWorldLayouts.RingLayout layout = ringworld$layout();
        if (layout == null || !RingGenerationBoundary.isExterior(chunk, layout)) return;

        // Interior features can spill across a chunk edge before this chunk reaches
        // its own feature stage. Erase that spill and leave only the guard barrier.
        RingGenerationBoundary.clearExterior(chunk);
        RingGenerationBoundary.installGuardBarrier(chunk, layout);
        ci.cancel();
    }

    @Inject(method = "applyBiomeDecoration", at = @At("TAIL"))
    private void ringworld$finishInteriorBoundary(
            WorldGenLevel level,
            ChunkAccess chunk,
            StructureManager structures,
            CallbackInfo ci) {
        RingWorldLayouts.RingLayout layout = ringworld$layout();
        if (layout != null) {
            RingGenerationBoundary.installInteriorBedrock(chunk, layout);
        }
    }

    private RingWorldLayouts.RingLayout ringworld$layout() {
        if ((Object) this instanceof RingWorldGeneratorAccess access) {
            return access.ringworld$getLayout();
        }
        return null;
    }
}
