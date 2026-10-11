package dev.ringworld.platform.forge1201.mixin;

import dev.ringworld.platform.forge1201.Forge1201RingRuntime;
import dev.ringworld.world.RingChunkCoordinates;
import dev.ringworld.world.RingGeometry;
import javax.annotation.Nullable;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Makes ChunkMap's generation dependency square resolve through canonical
 * holders while preserving the square's original logical iteration order.
 */
@Mixin(ChunkMap.class)
abstract class Forge1201ChunkGenerationLookupMixin {
    @Shadow @Final private ServerLevel level;

    @Shadow
    @Nullable
    protected abstract ChunkHolder getUpdatingChunkIfPresent(long packedPos);

    @Redirect(
            method = "getChunkRangeFuture",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ChunkMap;getUpdatingChunkIfPresent(J)Lnet/minecraft/server/level/ChunkHolder;"))
    private ChunkHolder ringworld1201$periodicGenerationHolder(ChunkMap ignored, long packedPos) {
        RingGeometry geometry = Forge1201RingRuntime.geometryForStorage(level);
        if (geometry == null) return getUpdatingChunkIfPresent(packedPos);
        ChunkPos requested = new ChunkPos(packedPos);
        int canonicalX = RingChunkCoordinates.wrapChunkX(requested.x, geometry);
        return getUpdatingChunkIfPresent(ChunkPos.asLong(canonicalX, requested.z));
    }
}
