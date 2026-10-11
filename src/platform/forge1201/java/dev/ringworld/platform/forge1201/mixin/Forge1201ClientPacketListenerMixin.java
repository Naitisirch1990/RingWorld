package dev.ringworld.platform.forge1201.mixin;

import dev.ringworld.platform.forge1201.Forge1201ClientRingState;
import dev.ringworld.platform.forge1201.Forge1201ClientTopology;
import dev.ringworld.platform.forge1201.client.Forge1201ClientChunkMapAccess;
import dev.ringworld.platform.forge1201.client.Forge1201ClientChunkMaps;
import dev.ringworld.world.RingGeometry;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundAddPlayerPacket;
import net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundBlockEventPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundChunksBiomesPacket;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundLevelEventPacket;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundMoveVehiclePacket;
import net.minecraft.network.protocol.game.ClientboundOpenSignEditorPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheCenterPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Projects canonical server packets into the periodic image nearest the local player. */
@Mixin(ClientPacketListener.class)
abstract class Forge1201ClientPacketListenerMixin {
    @Inject(method = "handleLogin", at = @At("HEAD"))
    private void ringworld1201$clearPreviousSession(ClientboundLoginPacket packet, CallbackInfo ci) {
        Forge1201ClientRingState.clear();
    }

    @Redirect(method = "handleLevelChunkWithLight", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/protocol/game/ClientboundLevelChunkWithLightPacket;getX()I"))
    private int ringworld1201$mapChunkDataX(ClientboundLevelChunkWithLightPacket packet) {
        return Forge1201ClientTopology.mapChunkX(packet.getX());
    }

    @Redirect(method = "handleLightUpdatePacket", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/protocol/game/ClientboundLightUpdatePacket;getX()I"))
    private int ringworld1201$mapLightUpdateX(ClientboundLightUpdatePacket packet) {
        return Forge1201ClientTopology.mapChunkX(packet.getX());
    }

    @ModifyVariable(method = "handleChunksBiomes", at = @At("HEAD"), argsOnly = true)
    private ClientboundChunksBiomesPacket ringworld1201$mapChunkBiomeData(ClientboundChunksBiomesPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!minecraft.isSameThread() || Forge1201ClientTopology.activeGeometry() == null) return packet;
        List<ClientboundChunksBiomesPacket.ChunkBiomeData> mapped = packet.chunkBiomeData().stream()
                .map(data -> new ClientboundChunksBiomesPacket.ChunkBiomeData(
                        new ChunkPos(Forge1201ClientTopology.mapChunkX(data.pos().x), data.pos().z), data.buffer()))
                .toList();
        return new ClientboundChunksBiomesPacket(mapped);
    }

    /**
     * A command-scale teleport, respawn or portal can move the presentation
     * chart farther than the old client cache overlaps. Drop only that stale
     * cache; natural seam walking remains a small neighbouring move and never
     * takes this path.
     */
    @Inject(method = "handleSetChunkCacheCenter", at = @At("HEAD"))
    private void ringworld1201$rekeyChunkCenter(ClientboundSetChunkCacheCenterPacket packet,
                                                CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!minecraft.isSameThread() || minecraft.level == null || minecraft.player == null) return;
        if (Forge1201ClientTopology.activeGeometry() == null) return;
        rekeyClientChart(minecraft, Forge1201ClientTopology.mapChunkX(packet.getX()), packet.getZ());
    }

    @Redirect(method = "handleSetChunkCacheCenter", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/protocol/game/ClientboundSetChunkCacheCenterPacket;getX()I"))
    private int ringworld1201$mapChunkCenterX(ClientboundSetChunkCacheCenterPacket packet) {
        return Forge1201ClientTopology.mapChunkX(packet.getX());
    }

    @Redirect(method = "handleForgetLevelChunk", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/protocol/game/ClientboundForgetLevelChunkPacket;getX()I"))
    private int ringworld1201$mapUnloadChunkX(ClientboundForgetLevelChunkPacket packet) {
        return Forge1201ClientTopology.mapChunkX(packet.getX());
    }

    @Redirect(method = "handleChunkBlocksUpdate", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/protocol/game/ClientboundSectionBlocksUpdatePacket;runUpdates(Ljava/util/function/BiConsumer;)V"))
    private void ringworld1201$mapChunkDelta(ClientboundSectionBlocksUpdatePacket packet,
                                              BiConsumer<BlockPos, BlockState> consumer) {
        packet.runUpdates((pos, state) -> consumer.accept(Forge1201ClientTopology.mapBlockPos(pos), state));
    }

    @Redirect(method = "handleBlockUpdate", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/protocol/game/ClientboundBlockUpdatePacket;getPos()Lnet/minecraft/core/BlockPos;"))
    private BlockPos ringworld1201$mapBlockUpdate(ClientboundBlockUpdatePacket packet) {
        return Forge1201ClientTopology.mapBlockPos(packet.getPos());
    }

    @Redirect(method = "handleBlockEntityData", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/protocol/game/ClientboundBlockEntityDataPacket;getPos()Lnet/minecraft/core/BlockPos;"))
    private BlockPos ringworld1201$mapBlockEntityData(ClientboundBlockEntityDataPacket packet) {
        return Forge1201ClientTopology.mapBlockPos(packet.getPos());
    }

    @Redirect(method = "handleOpenSignEditor", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/protocol/game/ClientboundOpenSignEditorPacket;getPos()Lnet/minecraft/core/BlockPos;"))
    private BlockPos ringworld1201$mapOpenSignEditor(ClientboundOpenSignEditorPacket packet) {
        return Forge1201ClientTopology.mapBlockPos(packet.getPos());
    }

    @Redirect(method = "handleBlockDestruction", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/protocol/game/ClientboundBlockDestructionPacket;getPos()Lnet/minecraft/core/BlockPos;"))
    private BlockPos ringworld1201$mapBlockDestruction(ClientboundBlockDestructionPacket packet) {
        return Forge1201ClientTopology.mapBlockPos(packet.getPos());
    }

    @Redirect(method = "handleBlockEvent", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/protocol/game/ClientboundBlockEventPacket;getPos()Lnet/minecraft/core/BlockPos;"))
    private BlockPos ringworld1201$mapBlockEvent(ClientboundBlockEventPacket packet) {
        return Forge1201ClientTopology.mapBlockPos(packet.getPos());
    }

    @Redirect(method = "handleLevelEvent", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/protocol/game/ClientboundLevelEventPacket;getPos()Lnet/minecraft/core/BlockPos;"))
    private BlockPos ringworld1201$mapLevelEvent(ClientboundLevelEventPacket packet) {
        return Forge1201ClientTopology.mapBlockPos(packet.getPos());
    }

    @ModifyVariable(method = "handleAddEntity", at = @At("HEAD"), argsOnly = true)
    private ClientboundAddEntityPacket ringworld1201$projectEntitySpawn(ClientboundAddEntityPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!minecraft.isSameThread() || minecraft.player == null) return packet;
        RingGeometry geometry = Forge1201ClientTopology.activeGeometry();
        if (geometry == null) return packet;
        double logicalX = geometry.nearestImageX(packet.getX(), minecraft.player.getX());
        if (logicalX == packet.getX()) return packet;
        return new ClientboundAddEntityPacket(packet.getId(), packet.getUUID(), logicalX,
                packet.getY(), packet.getZ(), packet.getXRot(), packet.getYRot(), packet.getType(),
                packet.getData(), new Vec3(packet.getXa(), packet.getYa(), packet.getZa()), packet.getYHeadRot());
    }

    /** Minecraft 1.20.1 still has a dedicated player-spawn packet. */
    @Redirect(method = "handleAddPlayer", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/protocol/game/ClientboundAddPlayerPacket;getX()D"))
    private double ringworld1201$projectRemotePlayerSpawnX(ClientboundAddPlayerPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        RingGeometry geometry = Forge1201ClientTopology.activeGeometry();
        if (geometry == null || minecraft.player == null) return packet.getX();
        return geometry.nearestImageX(packet.getX(), minecraft.player.getX());
    }

    @Redirect(method = "handleTeleportEntity", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/protocol/game/ClientboundTeleportEntityPacket;getX()D"))
    private double ringworld1201$projectEntityTeleportX(ClientboundTeleportEntityPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        RingGeometry geometry = Forge1201ClientTopology.activeGeometry();
        if (geometry == null || minecraft.level == null || minecraft.player == null) return packet.getX();
        var entity = minecraft.level.getEntity(packet.getId());
        double referenceX = entity == null ? minecraft.player.getX() : entity.getX();
        return geometry.nearestImageX(packet.getX(), referenceX);
    }

    @Redirect(method = "handleMoveVehicle", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/protocol/game/ClientboundMoveVehiclePacket;getX()D"))
    private double ringworld1201$projectVehicleCorrectionX(ClientboundMoveVehiclePacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        RingGeometry geometry = Forge1201ClientTopology.activeGeometry();
        if (geometry == null || minecraft.player == null) return packet.getX();
        return geometry.nearestImageX(packet.getX(), minecraft.player.getRootVehicle().getX());
    }

    @ModifyVariable(method = "handleMovePlayer", at = @At("HEAD"), argsOnly = true)
    private ClientboundPlayerPositionPacket ringworld1201$projectPlayerTeleport(ClientboundPlayerPositionPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!minecraft.isSameThread()) return packet;
        LocalPlayer player = minecraft.player;
        RingGeometry geometry = Forge1201ClientTopology.activeGeometry();
        if (geometry == null || player == null) return packet;

        Set<RelativeMovement> relative = packet.getRelativeArguments();
        double targetX = relative.contains(RelativeMovement.X) ? player.getX() + packet.getX() : packet.getX();
        double targetZ = relative.contains(RelativeMovement.Z) ? player.getZ() + packet.getZ() : packet.getZ();
        double presentationX = geometry.nearestImageX(targetX, player.getX());

        rekeyClientChart(minecraft,
                Math.floorDiv((int)Math.floor(presentationX), 16),
                Math.floorDiv((int)Math.floor(targetZ), 16));

        if (presentationX == targetX) return packet;
        EnumSet<RelativeMovement> projected = relative.isEmpty()
                ? EnumSet.noneOf(RelativeMovement.class) : EnumSet.copyOf(relative);
        projected.remove(RelativeMovement.X);
        return new ClientboundPlayerPositionPacket(presentationX, packet.getY(), packet.getZ(),
                packet.getYRot(), packet.getXRot(), Set.copyOf(projected), packet.getId());
    }

    private static void rekeyClientChart(Minecraft minecraft, int nextChunkX, int nextChunkZ) {
        if (minecraft.level == null) return;
        Forge1201ClientChunkMapAccess access =
                Forge1201ClientChunkMaps.get(minecraft.level.getChunkSource());
        if (access == null) return;

        int viewDistance = minecraft.options.renderDistance().get();
        int overlapDiameter = viewDistance * 2 + 2;
        if (Math.abs(nextChunkX - access.ringworld1201$centerChunkX()) <= overlapDiameter
                && Math.abs(nextChunkZ - access.ringworld1201$centerChunkZ()) <= overlapDiameter) {
            return;
        }

        access.ringworld1201$clearAllChunks();
        minecraft.level.getChunkSource().updateViewCenter(nextChunkX, nextChunkZ);
        minecraft.levelRenderer.needsUpdate();
    }
}
