package dev.ringworld.platform.forge1201.mixin;

import dev.ringworld.platform.forge1201.Forge1201RingRuntime;
import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingPlayerMovementAccess;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Forge 1.20.1 movement seam bridge.
 *
 * <p>The client may continue walking in the nearest visual image while the
 * authoritative entity remains in the one canonical circumference. Vanilla
 * anti-cheat baselines are shifted with the same local chart change so a seam
 * crossing is validated as a normal short movement rather than a 61k-block
 * teleport.</p>
 */
@Mixin(ServerGamePacketListenerImpl.class)
abstract class Forge1201ServerGamePacketListenerMixin implements RingPlayerMovementAccess {
    @Shadow public ServerPlayer player;
    @Shadow private double firstGoodX;
    @Shadow private double lastGoodX;
    @Shadow private double vehicleFirstGoodX;
    @Shadow private double vehicleLastGoodX;

    @Override
    public void ringworld$resetPlayerMovementBaselines() {
        firstGoodX = player.getX();
        lastGoodX = player.getX();
    }

    @ModifyVariable(method = "handleMovePlayer", at = @At("HEAD"), argsOnly = true)
    private ServerboundMovePlayerPacket ringworld1201$projectPlayerMovement(ServerboundMovePlayerPacket packet) {
        ServerLevel level = player.serverLevel();
        if (!packet.hasPosition() || level.dimension() != Level.OVERWORLD) return packet;

        RingGeometry geometry = Forge1201RingRuntime.geometryFor(level);
        if (geometry == null) return packet;

        double presentationX = packet.getX(player.getX());
        double nearestX = geometry.nearestImageX(presentationX, player.getX());
        double canonicalTargetX = geometry.wrapX(nearestX);

        // A server-owned pose change can move the player to the adjacent
        // canonical image without touching movement packets. Realign vanilla's
        // remembered anti-cheat chart before it computes flat-space deltas.
        double alignedFirstGoodX = geometry.nearestImageX(firstGoodX, player.getX());
        double baselineShift = alignedFirstGoodX - firstGoodX;
        if (baselineShift != 0.0) {
            firstGoodX += baselineShift;
            lastGoodX += baselineShift;
        }

        // If this packet crosses the seam, move the source pose into the image
        // adjacent to the canonical destination first. Vanilla then performs
        // only the ordinary epsilon-sized final movement and collision check.
        if (canonicalTargetX != nearestX) {
            double localSourceX = geometry.nearestImageX(player.getX(), canonicalTargetX);
            double sourceShift = localSourceX - player.getX();
            if (sourceShift != 0.0) {
                player.setPos(localSourceX, player.getY(), player.getZ());
                firstGoodX += sourceShift;
                lastGoodX += sourceShift;
            }
        }

        if (canonicalTargetX == presentationX) return packet;
        return new ServerboundMovePlayerPacket.PosRot(
                canonicalTargetX,
                packet.getY(player.getY()),
                packet.getZ(player.getZ()),
                packet.getYRot(player.getYRot()),
                packet.getXRot(player.getXRot()),
                packet.isOnGround());
    }

    @Inject(method = "handleMovePlayer", at = @At("TAIL"))
    private void ringworld1201$foldPlayerAfterMovement(ServerboundMovePlayerPacket packet, CallbackInfo ci) {
        ServerLevel level = player.serverLevel();
        if (level.dimension() != Level.OVERWORLD) return;
        RingGeometry geometry = Forge1201RingRuntime.geometryFor(level);
        double shift = Forge1201RingRuntime.canonicalizeEntityPosition(player, geometry);
        if (shift == 0.0) return;
        firstGoodX += shift;
        lastGoodX += shift;
    }

    @Redirect(
            method = "handleMoveVehicle",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/network/protocol/game/ServerboundMoveVehiclePacket;getX()D"))
    private double ringworld1201$projectVehicleMovement(ServerboundMoveVehiclePacket packet) {
        ServerLevel level = player.serverLevel();
        Entity vehicle = player.getRootVehicle();
        if (level.dimension() != Level.OVERWORLD || vehicle == player) return packet.getX();
        RingGeometry geometry = Forge1201RingRuntime.geometryFor(level);
        return geometry == null ? packet.getX() : geometry.nearestImageX(packet.getX(), vehicle.getX());
    }

    @Inject(method = "handleMoveVehicle", at = @At("TAIL"))
    private void ringworld1201$foldVehicleAfterMovement(ServerboundMoveVehiclePacket packet, CallbackInfo ci) {
        ServerLevel level = player.serverLevel();
        Entity vehicle = player.getRootVehicle();
        if (level.dimension() != Level.OVERWORLD || vehicle == player) return;
        RingGeometry geometry = Forge1201RingRuntime.geometryFor(level);
        if (geometry == null) return;

        double vehicleShift = Forge1201RingRuntime.canonicalizeEntityPosition(vehicle, geometry);
        if (vehicleShift != 0.0) {
            vehicleFirstGoodX += vehicleShift;
            vehicleLastGoodX += vehicleShift;
        }

        vehicle.getSelfAndPassengers().skip(1).forEach(passenger -> {
            double passengerShift = Forge1201RingRuntime.canonicalizeEntityPosition(passenger, geometry);
            if (passenger == player && passengerShift != 0.0) {
                firstGoodX += passengerShift;
                lastGoodX += passengerShift;
            }
        });
    }
}
