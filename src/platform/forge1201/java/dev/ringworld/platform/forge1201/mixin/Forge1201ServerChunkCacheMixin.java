package dev.ringworld.platform.forge1201.mixin;

import dev.ringworld.platform.forge1201.Forge1201RingRuntime;
import dev.ringworld.world.RingChunkCoordinates;
import dev.ringworld.world.RingGeometry;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Folds world-facing server chunk requests onto the finite canonical chunk
 * plane. This is deliberately below block/entity gameplay so a query that
 * crosses X=0/C reads the same chunks instead of generating a second world.
 */
@Mixin(ServerChunkCache.class)
abstract class Forge1201ServerChunkCacheMixin {
    @Shadow @Final public ServerLevel level;

    @ModifyVariable(
            method = "getChunk(IILnet/minecraft/world/level/chunk/ChunkStatus;Z)Lnet/minecraft/world/level/chunk/ChunkAccess;",
            at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int ringworld1201$canonicalGetChunkX(int chunkX) {
        return ringworld1201$canonicalChunkX(chunkX);
    }

    @ModifyVariable(
            method = "getChunkNow(II)Lnet/minecraft/world/level/chunk/LevelChunk;",
            at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int ringworld1201$canonicalGetChunkNowX(int chunkX) {
        return ringworld1201$canonicalChunkX(chunkX);
    }

    @ModifyVariable(
            method = "getChunkFuture(IILnet/minecraft/world/level/chunk/ChunkStatus;Z)Ljava/util/concurrent/CompletableFuture;",
            at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int ringworld1201$canonicalFutureX(int chunkX) {
        return ringworld1201$canonicalChunkX(chunkX);
    }

    @ModifyVariable(
            method = "hasChunk(II)Z",
            at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int ringworld1201$canonicalHasChunkX(int chunkX) {
        return ringworld1201$canonicalChunkX(chunkX);
    }

    @ModifyVariable(
            method = "getChunkForLighting(II)Lnet/minecraft/world/level/chunk/LightChunk;",
            at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int ringworld1201$canonicalLightingChunkX(int chunkX) {
        return ringworld1201$canonicalChunkX(chunkX);
    }

    @ModifyVariable(
            method = "addRegionTicket(Lnet/minecraft/server/level/TicketType;Lnet/minecraft/world/level/ChunkPos;ILjava/lang/Object;)V",
            at = @At("HEAD"), argsOnly = true)
    private ChunkPos ringworld1201$canonicalRegionTicket(ChunkPos pos) {
        return ringworld1201$canonicalChunkPos(pos);
    }

    // Forge adds this force-ticks overload itself, so it has no Mojang/SRG
    // obfuscation mapping. Keep the literal Forge runtime name/desriptor.
    @ModifyVariable(
            method = "addRegionTicket(Lnet/minecraft/server/level/TicketType;Lnet/minecraft/world/level/ChunkPos;ILjava/lang/Object;Z)V",
            at = @At("HEAD"), argsOnly = true, remap = false)
    private ChunkPos ringworld1201$canonicalForcedRegionTicket(ChunkPos pos) {
        return ringworld1201$canonicalChunkPos(pos);
    }

    @ModifyVariable(
            method = "removeRegionTicket(Lnet/minecraft/server/level/TicketType;Lnet/minecraft/world/level/ChunkPos;ILjava/lang/Object;)V",
            at = @At("HEAD"), argsOnly = true)
    private ChunkPos ringworld1201$canonicalRegionTicketRemoval(ChunkPos pos) {
        return ringworld1201$canonicalChunkPos(pos);
    }

    // Same Forge-only overload as above: the name is intentionally not remapped.
    @ModifyVariable(
            method = "removeRegionTicket(Lnet/minecraft/server/level/TicketType;Lnet/minecraft/world/level/ChunkPos;ILjava/lang/Object;Z)V",
            at = @At("HEAD"), argsOnly = true, remap = false)
    private ChunkPos ringworld1201$canonicalForcedRegionTicketRemoval(ChunkPos pos) {
        return ringworld1201$canonicalChunkPos(pos);
    }

    @ModifyVariable(
            method = "updateChunkForced(Lnet/minecraft/world/level/ChunkPos;Z)V",
            at = @At("HEAD"), argsOnly = true)
    private ChunkPos ringworld1201$canonicalForcedChunk(ChunkPos pos) {
        return ringworld1201$canonicalChunkPos(pos);
    }

    private int ringworld1201$canonicalChunkX(int chunkX) {
        RingGeometry geometry = Forge1201RingRuntime.geometryFor(level);
        return geometry == null ? chunkX : RingChunkCoordinates.wrapChunkX(chunkX, geometry);
    }

    private ChunkPos ringworld1201$canonicalChunkPos(ChunkPos pos) {
        RingGeometry geometry = Forge1201RingRuntime.geometryFor(level);
        if (geometry == null) return pos;
        int canonicalX = RingChunkCoordinates.wrapChunkX(pos.x, geometry);
        return canonicalX == pos.x ? pos : new ChunkPos(canonicalX, pos.z);
    }
}
