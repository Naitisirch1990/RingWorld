package dev.ringworld.platform.forge1201.mixin;

import dev.ringworld.platform.forge1201.Forge1201ClientTopology;
import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingInteractionCoordinates;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Converts client presentation coordinates back to the canonical server chart
 * for the two ordinary survival block-interaction packet families.
 */
@Mixin(Connection.class)
abstract class Forge1201ClientConnectionMixin {
    @ModifyVariable(
            method = "send(Lnet/minecraft/network/protocol/Packet;)V",
            at = @At("HEAD"), argsOnly = true)
    private Packet<?> ringworld1201$canonicalizeOutboundPosition(Packet<?> packet) {
        RingGeometry geometry = Forge1201ClientTopology.activeGeometry();
        if (geometry == null) return packet;

        if (packet instanceof ServerboundPlayerActionPacket action) {
            BlockPos pos = action.getPos();
            BlockPos canonical = new BlockPos(
                    geometry.wrapBlockX(pos.getX()), pos.getY(), pos.getZ());
            if (canonical.equals(pos)) return packet;
            return new ServerboundPlayerActionPacket(
                    action.getAction(), canonical, action.getDirection(), action.getSequence());
        }

        if (packet instanceof ServerboundUseItemOnPacket interaction) {
            BlockHitResult hit = interaction.getHitResult();
            BlockPos pos = hit.getBlockPos();
            RingInteractionCoordinates.CanonicalBlockHit canonical =
                    RingInteractionCoordinates.canonicalizeBlockHit(
                            geometry, pos.getX(), hit.getLocation().x);
            if (canonical.blockX() == pos.getX()) return packet;
            Vec3 location = hit.getLocation();
            BlockHitResult canonicalHit = new BlockHitResult(
                    new Vec3(canonical.hitX(), location.y, location.z),
                    hit.getDirection(),
                    new BlockPos(canonical.blockX(), pos.getY(), pos.getZ()),
                    hit.isInside());
            return new ServerboundUseItemOnPacket(
                    interaction.getHand(), canonicalHit, interaction.getSequence());
        }

        return packet;
    }
}
