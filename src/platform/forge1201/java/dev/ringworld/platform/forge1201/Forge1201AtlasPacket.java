package dev.ringworld.platform.forge1201;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkDirection;

/** Server-authoritative, bounded atlas tile; no client-only classes on the server. */
public record Forge1201AtlasPacket(long session, int tileX, int tileZ, byte[] data) {
    public static void encode(Forge1201AtlasPacket p, FriendlyByteBuf b) {
        b.writeLong(p.session); b.writeVarInt(p.tileX); b.writeVarInt(p.tileZ); b.writeByteArray(p.data);
    }
    public static Forge1201AtlasPacket decode(FriendlyByteBuf b) {
        return new Forge1201AtlasPacket(b.readLong(), b.readVarInt(), b.readVarInt(), b.readByteArray(1794));
    }
    public static void handle(Forge1201AtlasPacket p, Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get();
        if (context.getDirection() == NetworkDirection.PLAY_TO_CLIENT)
            context.enqueueWork(() -> Forge1201ClientAtlasState.accept(p));
        context.setPacketHandled(true);
    }
}
