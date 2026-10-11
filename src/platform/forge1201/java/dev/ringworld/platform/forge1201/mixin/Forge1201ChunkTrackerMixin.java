package dev.ringworld.platform.forge1201.mixin;

import dev.ringworld.world.RingChunkLevelContext;
import dev.ringworld.world.RingGeometry;
import net.minecraft.server.level.ChunkTracker;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Joins the two X edges of Forge 1.20.1 chunk ticket/simulation propagation. */
@Mixin(ChunkTracker.class)
abstract class Forge1201ChunkTrackerMixin {
    @Redirect(
            method = {"checkNeighborsAfterUpdate", "getComputedLevel"},
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/ChunkPos;asLong(II)J"))
    private long ringworld1201$periodicNeighbor(int x, int z) {
        RingGeometry geometry = RingChunkLevelContext.activeGeometry();
        if (geometry == null) return ChunkPos.asLong(x, z);
        return ChunkPos.asLong(Math.floorMod(x, geometry.circumferenceChunks()), z);
    }
}
