package dev.ringworld.platform.forge1201.mixin;

import dev.ringworld.platform.forge1201.Forge1201ClientTopology;
import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingInteractionCoordinates;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Converts client presentation coordinates back to the canonical server chart. */
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
            BlockPos canonical = canonical(pos, geometry);
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

        if (packet instanceof ServerboundSignUpdatePacket sign) {
            BlockPos canonical = canonical(sign.getPos(), geometry);
            if (canonical.equals(sign.getPos())) return packet;
            String[] lines = sign.getLines();
            return new ServerboundSignUpdatePacket(canonical, sign.isFrontText(),
                    lines[0], lines[1], lines[2], lines[3]);
        }

        return packet;
    }

    private static BlockPos canonical(BlockPos pos, RingGeometry geometry) {
        int x = geometry.wrapBlockX(pos.getX());
        return x == pos.getX() ? pos : new BlockPos(x, pos.getY(), pos.getZ());
    }
}
