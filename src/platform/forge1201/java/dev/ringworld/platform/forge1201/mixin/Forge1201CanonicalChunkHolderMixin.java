package dev.ringworld.platform.forge1201.mixin;

import dev.ringworld.platform.forge1201.Forge1201RingRuntime;
import dev.ringworld.world.RingChunkCoordinates;
import dev.ringworld.world.RingGeometry;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Keeps ChunkMap's holder maps on one finite canonical X plane.
 *
 * <p>The distance graph is allowed to reason about the logical neighbour on
 * either side of X=0, but the holder itself must still be stored exactly once
 * at X in [0,C). Without this bridge Forge 1.20.1 can create a second holder
 * at X=-1 while preparing spawn, which then leaks into a negative-X region
 * file even though every later lookup is periodic.</p>
 */
@Mixin(ChunkMap.class)
abstract class Forge1201CanonicalChunkHolderMixin {
    @Shadow @Final private ServerLevel level;

    /** Canonicalise every updating-holder map lookup, including DistanceManager. */
    @ModifyVariable(method = "getUpdatingChunkIfPresent", at = @At("HEAD"), argsOnly = true)
    private long ringworld1201$canonicalUpdatingHolderLookup(long packedPos) {
        return ringworld1201$canonicalPackedPos(packedPos);
    }

    /** Canonicalise every visible-holder map lookup used by world-facing requests. */
    @ModifyVariable(method = "getVisibleChunkIfPresent", at = @At("HEAD"), argsOnly = true)
    private long ringworld1201$canonicalVisibleHolderLookup(long packedPos) {
        return ringworld1201$canonicalPackedPos(packedPos);
    }

    /**
     * The holder key and the ChunkPos stored inside a newly-created holder must
     * agree on the same canonical image. This is the final write-side gate for
     * the finite chunk graph.
     */
    @ModifyVariable(method = "updateChunkScheduling", at = @At("HEAD"), argsOnly = true)
    private long ringworld1201$canonicalScheduledHolder(long packedPos) {
        return ringworld1201$canonicalPackedPos(packedPos);
    }

    private long ringworld1201$canonicalPackedPos(long packedPos) {
        RingGeometry geometry = Forge1201RingRuntime.geometryFor(level);
        if (geometry == null) return packedPos;
        ChunkPos pos = new ChunkPos(packedPos);
        int canonicalX = RingChunkCoordinates.wrapChunkX(pos.x, geometry);
        return canonicalX == pos.x ? packedPos : ChunkPos.asLong(canonicalX, pos.z);
    }
}
