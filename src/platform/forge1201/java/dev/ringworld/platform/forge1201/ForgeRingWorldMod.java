package dev.ringworld.platform.forge1201;

import dev.ringworld.RingWorldMod;
import dev.ringworld.world.RingWorldConfig;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;

/** Forge 1.20.1 bootstrap for the unofficial backport. */
@Mod(RingWorldMod.MOD_ID)
public final class ForgeRingWorldMod {
    public ForgeRingWorldMod() {
        RingWorldConfig.configureDirectory(FMLPaths.CONFIGDIR.get());
        RingWorldConfig.load();
        RingWorldMod.LOGGER.info("RingWorld Forge 1.20.1 bootstrap active");
    }
}
