package dev.ringworld.platform.forge1201;

import dev.ringworld.RingWorldMod;
import dev.ringworld.world.RingWorldSettings;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/** Forge 1.20.1 wire adapter for the three RingWorld dimension profiles. */
public final class Forge1201RingNetwork {
    private static final String PROTOCOL = "ringworld-settings-v4-forge1201";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(RingWorldMod.MOD_ID, "settings"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals);
    private static boolean registered;

    private Forge1201RingNetwork() { }

    public static synchronized void register() {
        if (registered) return;
        CHANNEL.registerMessage(
                0,
                Forge1201SettingsPacket.class,
                Forge1201SettingsPacket::encode,
                Forge1201SettingsPacket::decode,
                Forge1201SettingsPacket::handle);
        registered = true;
        RingWorldMod.LOGGER.info("Registered Forge 1.20.1 RingWorld settings channel {}", PROTOCOL);
    }

    public static void sendSettings(ServerPlayer player, RingWorldSettings settings) {
        register();
        Forge1201SettingsPacket packet = new Forge1201SettingsPacket(
                settings.widthBlocks(), settings.circumferenceBlocks(),
                Forge1201DimensionProfiles.WIDTH_BLOCKS, Forge1201DimensionProfiles.NETHER_CIRCUMFERENCE,
                Forge1201DimensionProfiles.WIDTH_BLOCKS, Forge1201DimensionProfiles.END_CIRCUMFERENCE,
                settings.generatorSeed(), settings.wallHeightBlocks(), settings.surfaceReferenceY(),
                settings.terrainNoiseMapping(), settings.formatVersion(), settings.layoutFingerprint());
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }
}
