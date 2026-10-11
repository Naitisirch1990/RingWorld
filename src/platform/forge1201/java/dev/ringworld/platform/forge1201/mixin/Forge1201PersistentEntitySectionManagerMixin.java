package dev.ringworld.platform.forge1201.mixin;

import dev.ringworld.platform.forge1201.Forge1201RingRuntime;
import dev.ringworld.world.RingEntityManagerAccess;
import dev.ringworld.world.RingGeometry;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import net.minecraft.world.level.entity.Visibility;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Stores entities and entity-file IO in the one canonical circumference plane. */
@Mixin(PersistentEntitySectionManager.class)
abstract class Forge1201PersistentEntitySectionManagerMixin<T extends EntityAccess>
        implements RingEntityManagerAccess {
    @Shadow @Final private Long2ObjectMap<?> chunkLoadStatuses;

    @Invoker("ensureChunkQueuedForLoad")
    protected abstract void ringworld1201$queueChunkEntityLoad(long packedPos);

    private RingGeometry ringworld1201$geometry;

    @Override
    public void ringworld$setGeometry(RingGeometry geometry) {
        ringworld1201$geometry = geometry;
    }

    @Override
    public void ringworld$ensureLoaded(ChunkPos pos) {
        RingGeometry geometry = ringworld1201$geometry;
        if (geometry == null) return;
        int x = Math.floorMod(pos.x, geometry.circumferenceChunks());
        long key = ChunkPos.asLong(x, pos.z);
        // The map's default is FRESH. Absence therefore means entity IO has not
        // yet been queued for this canonical terrain holder.
        if (!chunkLoadStatuses.containsKey(key)) {
            ringworld1201$queueChunkEntityLoad(key);
        }
    }

    /** Forge moves the vanilla indexing body into addEntityWithoutEvent. */
    @Inject(
            method = "addEntityWithoutEvent(Lnet/minecraft/world/level/entity/EntityAccess;Z)Z",
            at = @At("HEAD"))
    private void ringworld1201$canonicalEntityPosition(T entity, boolean existing,
                                                        CallbackInfoReturnable<Boolean> cir) {
        RingGeometry geometry = ringworld1201$geometry;
        if (geometry != null && entity instanceof Entity minecraftEntity) {
            Forge1201RingRuntime.canonicalizeEntityPosition(minecraftEntity, geometry);
        }
    }

    @ModifyVariable(
            method = "updateChunkStatus(Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/world/level/entity/Visibility;)V",
            at = @At("HEAD"), argsOnly = true)
    private ChunkPos ringworld1201$canonicalTrackingStatus(ChunkPos pos) {
        return ringworld1201$canonical(pos);
    }

    @ModifyVariable(method = "areEntitiesLoaded", at = @At("HEAD"), argsOnly = true)
    private long ringworld1201$canonicalLoadedStatus(long packedPos) {
        RingGeometry geometry = ringworld1201$geometry;
        if (geometry == null) return packedPos;
        ChunkPos pos = new ChunkPos(packedPos);
        int x = Math.floorMod(pos.x, geometry.circumferenceChunks());
        return x == pos.x ? packedPos : ChunkPos.asLong(x, pos.z);
    }

    @ModifyVariable(
            method = "canPositionTick(Lnet/minecraft/world/level/ChunkPos;)Z",
            at = @At("HEAD"), argsOnly = true)
    private ChunkPos ringworld1201$canonicalTickStatus(ChunkPos pos) {
        return ringworld1201$canonical(pos);
    }

    @Redirect(
            method = "addEntityWithoutEvent(Lnet/minecraft/world/level/entity/EntityAccess;Z)Z",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/core/SectionPos;asLong(Lnet/minecraft/core/BlockPos;)J"))
    private long ringworld1201$canonicalInitialSection(BlockPos pos) {
        RingGeometry geometry = ringworld1201$geometry;
        if (geometry == null) return SectionPos.asLong(pos);
        return SectionPos.asLong(
                Math.floorDiv(geometry.wrapBlockX(pos.getX()), 16),
                SectionPos.blockToSectionCoord(pos.getY()),
                SectionPos.blockToSectionCoord(pos.getZ()));
    }

    private ChunkPos ringworld1201$canonical(ChunkPos pos) {
        RingGeometry geometry = ringworld1201$geometry;
        if (geometry == null) return pos;
        int x = Math.floorMod(pos.x, geometry.circumferenceChunks());
        return x == pos.x ? pos : new ChunkPos(x, pos.z);
    }
}
