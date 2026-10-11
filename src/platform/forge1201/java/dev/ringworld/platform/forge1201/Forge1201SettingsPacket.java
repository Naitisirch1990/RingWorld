package dev.ringworld.platform.forge1201;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/** Immutable world-layout packet for Forge 1.20.1 SimpleChannel. */
public record Forge1201SettingsPacket(int width, int circumference, long seed, int wallHeight,
                                      int surfaceReferenceY, int terrainNoiseMapping,
                                      int formatVersion, long fingerprint) {
    public static void encode(Forge1201SettingsPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.width());
        buffer.writeVarInt(packet.circumference());
        buffer.writeLong(packet.seed());
        buffer.writeVarInt(packet.wallHeight());
        buffer.writeVarInt(packet.surfaceReferenceY());
        buffer.writeVarInt(packet.terrainNoiseMapping());
        buffer.writeVarInt(packet.formatVersion());
        buffer.writeLong(packet.fingerprint());
    }

    public static Forge1201SettingsPacket decode(FriendlyByteBuf buffer) {
        return new Forge1201SettingsPacket(
                buffer.readVarInt(), buffer.readVarInt(), buffer.readLong(),
                buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(),
                buffer.readVarInt(), buffer.readLong());
    }

    public static void handle(Forge1201SettingsPacket packet,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> Forge1201ClientRingState.accept(packet));
        context.setPacketHandled(true);
    }
}
