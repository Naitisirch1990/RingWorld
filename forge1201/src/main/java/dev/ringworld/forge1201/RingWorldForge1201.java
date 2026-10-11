package dev.ringworld.forge1201;

import com.mojang.logging.LogUtils;
import dev.ringworld.world.RingTerrainNoiseMapping;
import dev.ringworld.world.RingWorldLayouts;
import dev.ringworld.world.RingWorldSettings;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

@Mod(RingWorldForge1201.MOD_ID)
public final class RingWorldForge1201 {
    public static final String MOD_ID = "ringworld";
    private static final Logger LOGGER = LogUtils.getLogger();

    public RingWorldForge1201() {
        MinecraftForge.EVENT_BUS.register(this);
        LOGGER.info(
                "RingWorld Forge 1.20.1 bootstrap loaded: Overworld={}x{}, Nether={}x{}, End={}x{}",
                RingWorldLayouts.OVERWORLD.circumferenceBlocks(), RingWorldLayouts.OVERWORLD.widthBlocks(),
                RingWorldLayouts.NETHER.circumferenceBlocks(), RingWorldLayouts.NETHER.widthBlocks(),
                RingWorldLayouts.END.circumferenceBlocks(), RingWorldLayouts.END.widthBlocks());
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        RingWorldCommands.register(event);
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        RingWorldSettings settings = RingWorldSettings.get(event.getServer().overworld());
        RingTerrainNoiseMapping.requireSafeNewWorldGeometry(settings.overworld());
        RingTerrainNoiseMapping.requireSafeNewWorldGeometry(settings.nether());
        RingTerrainNoiseMapping.requireSafeNewWorldGeometry(settings.end());
        LOGGER.info(
                "RingWorld saved layout ready (seed={}): Overworld={}x{}, Nether={}x{}, End={}x{}, terrainMapping={}",
                settings.generatorSeed(),
                settings.overworld().circumferenceBlocks(), settings.overworld().widthBlocks(),
                settings.nether().circumferenceBlocks(), settings.nether().widthBlocks(),
                settings.end().circumferenceBlocks(), settings.end().widthBlocks(),
                RingTerrainNoiseMapping.diagnosticName(RingTerrainNoiseMapping.CURRENT));
    }
}
