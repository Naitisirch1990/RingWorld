package dev.ringworld.platform.forge1201.mixin;

import dev.ringworld.platform.forge1201.Forge1201RingRuntime;
import dev.ringworld.world.RingChunkCoordinates;
import dev.ringworld.world.RingGeometry;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Keeps ChunkMap's holder maps on one finite canonical X plane.
 *
 * <p>This code deliberately uses bootstrap-safe geometry. ChunkMap can begin
 * scheduling holders before the Forge level-load event has attached persisted
 * settings to the live generator, and letting those first requests retain
 * X=-1 or X=C creates a second physical copy of the seam on disk.</p>
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

    /** Canonicalise the key before pending-unload lookup and updating-map insertion. */
    @ModifyVariable(method = "updateChunkScheduling", at = @At("HEAD"), argsOnly = true)
    private long ringworld1201$canonicalScheduledHolder(long packedPos) {
        return ringworld1201$canonicalPackedPos(packedPos);
    }

    /**
     * Belt-and-suspenders write-side gate: the ChunkPos stored inside a newly
     * created holder must match the already-canonical map key. This catches any
     * pre-attachment scheduling path that bypassed a public chunk request.
     */
    @ModifyArg(
            method = "updateChunkScheduling",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ChunkHolder;<init>(Lnet/minecraft/world/level/ChunkPos;ILnet/minecraft/world/level/LevelHeightAccessor;Lnet/minecraft/world/level/lighting/LevelLightEngine;Lnet/minecraft/server/level/ChunkHolder$LevelChangeListener;Lnet/minecraft/server/level/ChunkHolder$PlayerProvider;)V"),
            index = 0)
    private ChunkPos ringworld1201$canonicalNewHolderPosition(ChunkPos pos) {
        RingGeometry geometry = Forge1201RingRuntime.geometryForStorage(level);
        if (geometry == null) return pos;
        int canonicalX = RingChunkCoordinates.wrapChunkX(pos.x, geometry);
        return canonicalX == pos.x ? pos : new ChunkPos(canonicalX, pos.z);
    }

    private long ringworld1201$canonicalPackedPos(long packedPos) {
        RingGeometry geometry = Forge1201RingRuntime.geometryForStorage(level);
        if (geometry == null) return packedPos;
        ChunkPos pos = new ChunkPos(packedPos);
        int canonicalX = RingChunkCoordinates.wrapChunkX(pos.x, geometry);
        return canonicalX == pos.x ? packedPos : ChunkPos.asLong(canonicalX, pos.z);
    }
}
