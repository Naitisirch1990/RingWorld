package dev.ringworld.forge1201;

import com.mojang.logging.LogUtils;
import dev.ringworld.world.RingWorldLayouts;
import net.minecraftforge.common.MinecraftForge;
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
}
