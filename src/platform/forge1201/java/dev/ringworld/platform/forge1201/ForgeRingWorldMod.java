package dev.ringworld.platform.forge1201;

import dev.ringworld.RingWorldMod;
import dev.ringworld.world.RingWorldConfig;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;

/** Forge 1.20.1 bootstrap for the unofficial backport. */
@Mod(RingWorldMod.MOD_ID)
public final class ForgeRingWorldMod {
    /** Project layout agreed for the 1.20.1 world; both values are chunk-aligned. */
    private static final int PROJECT_CIRCUMFERENCE = 61_440;
    private static final int PROJECT_WIDTH = 2_000;

    public ForgeRingWorldMod() {
        Path configDirectory = FMLPaths.CONFIGDIR.get();
        boolean freshInstall = Files.notExists(configDirectory.resolve("ringworld.properties"));
        RingWorldConfig.configureDirectory(configDirectory);
        if (freshInstall) {
            // The old fixed wall-height field is retained only as saved-format
            // compatibility. The Forge 1.20.1 generation boundary now raises
            // bedrock ten blocks above local terrain and adds a full-height
            // barrier behind it.
            RingWorldConfig.saveBootstrapLayout(
                    PROJECT_WIDTH, PROJECT_CIRCUMFERENCE, 160, false);
        } else {
            RingWorldConfig.load();
        }
        Forge1201RingNetwork.register();
        RingWorldMod.LOGGER.info(
                "RingWorld Forge 1.20.1 bootstrap active (project layout {}x{})",
                PROJECT_CIRCUMFERENCE, PROJECT_WIDTH);
    }
}
