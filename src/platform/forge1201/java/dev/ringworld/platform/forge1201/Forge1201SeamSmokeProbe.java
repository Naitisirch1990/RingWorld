package dev.ringworld.platform.forge1201;

import dev.ringworld.RingWorldMod;
import dev.ringworld.world.RingGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Opt-in dedicated-server smoke probe for actual Forge chunk lookup and block
 * reads across the canonical X seam. Pure coordinate tests cannot detect
 * duplicate ChunkHolders or mismatched region storage.
 *
 * <p>Only runs in CI when RINGWORLD_CI_SEAM_PROBE=1. Never runs on a normal
 * player server, so it cannot force chunk loads during ordinary gameplay.</p>
 */
@Mod.EventBusSubscriber(modid = RingWorldMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Forge1201SeamSmokeProbe {
    private Forge1201SeamSmokeProbe() { }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        if (!"1".equals(System.getenv("RINGWORLD_CI_SEAM_PROBE"))) return;
        ServerLevel level = event.getServer().getLevel(Level.OVERWORLD);
        if (level == null) throw new IllegalStateException("RingWorld seam smoke: Overworld unavailable");
        RingGeometry geometry = Forge1201RingRuntime.geometryFor(level);
        if (geometry == null) throw new IllegalStateException("RingWorld seam smoke: geometry unavailable");

        int lastChunkX = geometry.circumferenceChunks() - 1;
        LevelChunk first = level.getChunk(0, 0);
        LevelChunk firstAlias = level.getChunk(geometry.circumferenceChunks(), 0);
        LevelChunk last = level.getChunk(lastChunkX, 0);
        LevelChunk lastAlias = level.getChunk(-1, 0);

        require(first == firstAlias, "positive seam created a duplicate first chunk");
        require(last == lastAlias, "negative seam created a duplicate last chunk");
        require(first.getPos().x == 0, "first chunk has noncanonical stored position");
        require(last.getPos().x == lastChunkX, "last chunk has noncanonical stored position");

        int y = 64;
        int z = 0;
        require(level.getBlockState(new BlockPos(-1, y, z)).equals(
                        level.getBlockState(new BlockPos(geometry.circumferenceBlocks() - 1, y, z))),
                "negative seam block read differs from canonical storage");
        require(level.getBlockState(new BlockPos(geometry.circumferenceBlocks(), y, z)).equals(
                        level.getBlockState(new BlockPos(0, y, z))),
                "positive seam block read differs from canonical storage");

        RingWorldMod.LOGGER.info(
                "RingWorld seam chunk aliases passed for overworld: chunks 0 and {}, block reads at Y={}",
                lastChunkX, y);
    }

    private static void require(boolean valid, String message) {
        if (!valid) throw new IllegalStateException("RingWorld seam smoke: " + message);
    }
}
