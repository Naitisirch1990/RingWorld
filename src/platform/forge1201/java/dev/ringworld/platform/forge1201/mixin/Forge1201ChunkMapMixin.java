package dev.ringworld.platform.forge1201.mixin;

import dev.ringworld.platform.forge1201.Forge1201RingRuntime;
import dev.ringworld.world.RingGeometry;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Minecraft 1.20.1 has no ChunkTrackingView abstraction. This adapter keeps
 * vanilla's 1.20.1 watch-window shape but measures X on RingWorld's periodic
 * chunk graph and folds watch requests onto the canonical server chunk plane.
 */
@Mixin(ChunkMap.class)
abstract class Forge1201ChunkMapMixin {
    @Shadow @Final private ServerLevel level;

    @Inject(method = "updatePlayerStatus", at = @At("HEAD"))
    private void ringworld1201$canonicalizeJoiningPlayer(ServerPlayer player, boolean adding,
                                                         CallbackInfo ci) {
        if (adding) ringworld1201$canonicalizePlayer(player);
    }

    @Inject(method = "move", at = @At("HEAD"))
    private void ringworld1201$canonicalizeMovingPlayer(ServerPlayer player, CallbackInfo ci) {
        ringworld1201$canonicalizePlayer(player);
    }

    @Redirect(
            method = {"setViewDistance", "updatePlayerStatus", "move", "getPlayers"},
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ChunkMap;isChunkInRange(IIIII)Z"))
    private boolean ringworld1201$periodicChunkRange(int chunkX, int chunkZ,
                                                     int centerX, int centerZ,
                                                     int viewDistance) {
        RingGeometry geometry = Forge1201RingRuntime.geometryFor(level);
        if (geometry == null) {
            return ringworld1201$vanillaRange(chunkX, chunkZ, centerX, centerZ, viewDistance);
        }
        if (chunkZ < geometry.minChunkZ() || chunkZ > geometry.maxChunkZ()) return false;
        int circumference = geometry.circumferenceChunks();
        int first = Math.floorMod(chunkX, circumference);
        int second = Math.floorMod(centerX, circumference);
        int raw = Math.abs(first - second);
        int periodicDx = Math.min(raw, circumference - raw);
        return ringworld1201$rangeFromDeltas(periodicDx, Math.abs(chunkZ - centerZ), viewDistance);
    }

    @Redirect(
            method = "getPlayers",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ChunkMap;isChunkOnRangeBorder(IIIII)Z"))
    private boolean ringworld1201$periodicRangeBorder(int chunkX, int chunkZ,
                                                      int centerX, int centerZ,
                                                      int viewDistance) {
        if (!ringworld1201$periodicChunkRange(chunkX, chunkZ, centerX, centerZ, viewDistance)) {
            return false;
        }
        return !ringworld1201$periodicChunkRange(chunkX + 1, chunkZ + 1, centerX, centerZ, viewDistance)
                || !ringworld1201$periodicChunkRange(chunkX - 1, chunkZ + 1, centerX, centerZ, viewDistance)
                || !ringworld1201$periodicChunkRange(chunkX + 1, chunkZ - 1, centerX, centerZ, viewDistance)
                || !ringworld1201$periodicChunkRange(chunkX - 1, chunkZ - 1, centerX, centerZ, viewDistance);
    }

    @ModifyArg(
            method = {"updatePlayerStatus", "move"},
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ChunkMap;updateChunkTracking(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/level/ChunkPos;Lorg/apache/commons/lang3/mutable/MutableObject;ZZ)V"),
            index = 1)
    private ChunkPos ringworld1201$canonicalWatchChunk(ChunkPos pos) {
        RingGeometry geometry = Forge1201RingRuntime.geometryFor(level);
        if (geometry == null) return pos;
        int canonicalX = Math.floorMod(pos.x, geometry.circumferenceChunks());
        return canonicalX == pos.x ? pos : new ChunkPos(canonicalX, pos.z);
    }

    private void ringworld1201$canonicalizePlayer(ServerPlayer player) {
        RingGeometry geometry = Forge1201RingRuntime.geometryFor(level);
        if (geometry == null || player.level() != level) return;
        double sourceX = player.getX();
        if (geometry.needsCanonicalWrap(sourceX)) {
            player.setPos(geometry.wrapX(sourceX), player.getY(), player.getZ());
        }
    }

    private static boolean ringworld1201$vanillaRange(int chunkX, int chunkZ,
                                                      int centerX, int centerZ,
                                                      int viewDistance) {
        return ringworld1201$rangeFromDeltas(
                Math.abs(chunkX - centerX), Math.abs(chunkZ - centerZ), viewDistance);
    }

    /** Exact Minecraft 1.20.1 ChunkMap.isChunkInRange shape with supplied deltas. */
    private static boolean ringworld1201$rangeFromDeltas(int deltaX, int deltaZ, int viewDistance) {
        int i = Math.max(0, deltaX - 1);
        int j = Math.max(0, deltaZ - 1);
        long outer = Math.max(0, Math.max(i, j) - 1);
        long inner = Math.min(i, j);
        return inner * inner + outer * outer < (long)viewDistance * viewDistance;
    }
}
