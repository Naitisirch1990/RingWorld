package dev.ringworld.platform.forge1201.mixin;

import dev.ringworld.platform.forge1201.Forge1201RingRuntime;
import dev.ringworld.world.RingChunkCoordinates;
import dev.ringworld.world.RingEntityManagerAccess;
import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingTickSchedulerAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.ticks.LevelTicks;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Installs one ring geometry into entity IO and scheduled-tick storage. */
@Mixin(ServerLevel.class)
abstract class Forge1201ServerLevelMixin {
    @Shadow @Final private LevelTicks<Block> blockTicks;
    @Shadow @Final private LevelTicks<Fluid> fluidTicks;
    @Shadow @Final private PersistentEntitySectionManager<Entity> entityManager;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void ringworld1201$attachPersistentTopology(CallbackInfo ci) {
        ServerLevel level = (ServerLevel) (Object) this;
        RingGeometry geometry = Forge1201RingRuntime.geometryFor(level);
        if (geometry == null) return;
        ((RingEntityManagerAccess) entityManager).ringworld$setGeometry(geometry);
        ((RingTickSchedulerAccess) blockTicks).ringworld$setGeometry(geometry);
        ((RingTickSchedulerAccess) fluidTicks).ringworld$setGeometry(geometry);
    }

    @ModifyVariable(method = "areEntitiesLoaded", at = @At("HEAD"), argsOnly = true)
    private long ringworld1201$canonicalLoadedChunkKey(long packedPos) {
        ServerLevel level = (ServerLevel) (Object) this;
        RingGeometry geometry = Forge1201RingRuntime.geometryFor(level);
        if (geometry == null) return packedPos;
        ChunkPos pos = new ChunkPos(packedPos);
        return ChunkPos.asLong(RingChunkCoordinates.wrapChunkX(pos.x, geometry), pos.z);
    }

    @ModifyVariable(
            method = "isNaturalSpawningAllowed(Lnet/minecraft/world/level/ChunkPos;)Z",
            at = @At("HEAD"), argsOnly = true)
    private ChunkPos ringworld1201$canonicalSpawnQuery(ChunkPos pos) {
        ServerLevel level = (ServerLevel) (Object) this;
        RingGeometry geometry = Forge1201RingRuntime.geometryFor(level);
        if (geometry == null) return pos;
        int x = RingChunkCoordinates.wrapChunkX(pos.x, geometry);
        return x == pos.x ? pos : new ChunkPos(x, pos.z);
    }

    @ModifyVariable(method = "isPositionEntityTicking", at = @At("HEAD"), argsOnly = true)
    private BlockPos ringworld1201$canonicalEntityTickQuery(BlockPos pos) {
        ServerLevel level = (ServerLevel) (Object) this;
        RingGeometry geometry = Forge1201RingRuntime.geometryFor(level);
        if (geometry == null) return pos;
        int x = geometry.wrapBlockX(pos.getX());
        return x == pos.getX() ? pos : new BlockPos(x, pos.getY(), pos.getZ());
    }
}
