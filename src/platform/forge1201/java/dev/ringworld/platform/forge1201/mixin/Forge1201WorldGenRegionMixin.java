package dev.ringworld.platform.forge1201.mixin;

import dev.ringworld.platform.forge1201.Forge1201RingRuntime;
import dev.ringworld.world.RingChunkCoordinates;
import dev.ringworld.world.RingGeometry;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Gives a 1.20.1 WorldGenRegion a continuous local X chart across the ring seam.
 *
 * <p>ChunkMap stores only canonical holders (0..C-1). A generation square around
 * chunk 0, however, is logically ordered ... C-2, C-1, 0, 1, 2. Vanilla
 * WorldGenRegion derives its array index from the physical first holder, which
 * is discontinuous at that point. This adapter indexes the already ordered
 * cache relative to the centre chunk and projects requested canonical X values
 * into the nearest local image before calculating the array slot.</p>
 */
@Mixin(WorldGenRegion.class)
abstract class Forge1201WorldGenRegionMixin {
    @Shadow @Final private List<ChunkAccess> cache;
    @Shadow @Final private ChunkAccess center;
    @Shadow @Final private int size;
    @Shadow @Final private ServerLevel level;

    @Inject(
            method = "getChunk(IILnet/minecraft/world/level/chunk/ChunkStatus;Z)Lnet/minecraft/world/level/chunk/ChunkAccess;",
            at = @At("HEAD"), cancellable = true)
    private void ringworld1201$periodicRegionChunk(int chunkX, int chunkZ,
                                                   ChunkStatus requiredStatus, boolean required,
                                                   CallbackInfoReturnable<ChunkAccess> cir) {
        RingGeometry geometry = Forge1201RingRuntime.geometryFor(level);
        if (geometry == null) return;

        int localX = RingChunkCoordinates.nearestImageChunkX(
                chunkX, center.getPos().x, geometry);
        int radius = size / 2;
        int firstX = center.getPos().x - radius;
        int firstZ = center.getPos().z - radius;
        int localZ = chunkZ - firstZ;
        int localOffsetX = localX - firstX;

        if (localOffsetX >= 0 && localOffsetX < size && localZ >= 0 && localZ < size) {
            ChunkAccess candidate = cache.get(localOffsetX + localZ * size);
            if (candidate.getStatus().isOrAfter(requiredStatus)) {
                cir.setReturnValue(candidate);
                return;
            }
            if (!required) {
                cir.setReturnValue(null);
                return;
            }
            throw new RuntimeException("RingWorld region chunk has status " + candidate.getStatus()
                    + " but " + requiredStatus + " was required at local chunk "
                    + localX + "," + chunkZ);
        }

        if (!required) {
            cir.setReturnValue(null);
            return;
        }
        throw new RuntimeException("RingWorld worldgen requested chunk outside local region: "
                + chunkX + "," + chunkZ + " projectedX=" + localX
                + " center=" + center.getPos() + " size=" + size);
    }

    @Inject(method = "hasChunk", at = @At("HEAD"), cancellable = true)
    private void ringworld1201$periodicHasChunk(int chunkX, int chunkZ,
                                                CallbackInfoReturnable<Boolean> cir) {
        RingGeometry geometry = Forge1201RingRuntime.geometryFor(level);
        if (geometry == null) return;
        int localX = RingChunkCoordinates.nearestImageChunkX(
                chunkX, center.getPos().x, geometry);
        int radius = size / 2;
        int firstX = center.getPos().x - radius;
        int firstZ = center.getPos().z - radius;
        cir.setReturnValue(localX >= firstX && localX < firstX + size
                && chunkZ >= firstZ && chunkZ < firstZ + size);
    }

    /** Canonical coordinates are required for block-entity maps inside the canonical holder. */
    @ModifyVariable(method = {"getBlockState", "getFluidState", "getBlockEntity"},
            at = @At("HEAD"), argsOnly = true)
    private BlockPos ringworld1201$canonicalReadPosition(BlockPos pos) {
        return ringworld1201$canonical(pos);
    }

    /**
     * Keep vanilla's local write-radius check in the seam-local chart, then
     * fold the committed position into the holder's canonical coordinate plane.
     */
    @ModifyVariable(
            method = "setBlock",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/level/WorldGenRegion;ensureCanWrite(Lnet/minecraft/core/BlockPos;)Z",
                    shift = At.Shift.AFTER),
            argsOnly = true)
    private BlockPos ringworld1201$canonicalWritePosition(BlockPos pos) {
        return ringworld1201$canonical(pos);
    }

    @ModifyVariable(method = "addFreshEntity", at = @At("HEAD"), argsOnly = true)
    private Entity ringworld1201$canonicalGeneratedEntity(Entity entity) {
        RingGeometry geometry = Forge1201RingRuntime.geometryFor(level);
        if (geometry != null) {
            entity.setPos(geometry.wrapX(entity.getX()), entity.getY(), entity.getZ());
        }
        return entity;
    }

    private BlockPos ringworld1201$canonical(BlockPos pos) {
        RingGeometry geometry = Forge1201RingRuntime.geometryFor(level);
        if (geometry == null) return pos;
        int x = geometry.wrapBlockX(pos.getX());
        return x == pos.getX() ? pos : new BlockPos(x, pos.getY(), pos.getZ());
    }
}
