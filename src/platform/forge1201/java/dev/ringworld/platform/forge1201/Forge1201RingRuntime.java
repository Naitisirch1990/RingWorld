package dev.ringworld.platform.forge1201;

import dev.ringworld.RingWorldMod;
import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingWorldSettings;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * First Forge 1.20.1 server-runtime bridge.
 *
 * <p>This deliberately uses Forge lifecycle/tick events instead of the newer
 * 1.21 server hooks. It establishes saved geometry ownership and the canonical
 * X plane while the older chunk-tracking and rendering adapters are being
 * ported. Client presentation smoothing is added in a later backport stage.</p>
 */
@Mod.EventBusSubscriber(modid = RingWorldMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Forge1201RingRuntime {
    private static final Map<ServerLevel, RingGeometry> GEOMETRY = new IdentityHashMap<>();

    private Forge1201RingRuntime() { }

    @SubscribeEvent
    public static void onLevelLoad(LevelEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) return;
        RingGeometry geometry = attach(level);
        RingWorldMod.LOGGER.info(
                "Forge 1.20.1 RingWorld runtime attached: circumference={} width={} chunks={}x{}",
                geometry.circumferenceBlocks(), geometry.widthBlocks(),
                geometry.circumferenceChunks(), geometry.widthChunks());
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            GEOMETRY.remove(level);
        }
    }

    /**
     * Baseline 1.20.1 settings synchronization. A later login-order mixin will
     * move this packet ahead of initial chunk presentation, matching the
     * original RingWorld ordering contract exactly.
     */
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(player.level() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) return;
        Forge1201RingNetwork.sendSettings(player, RingWorldSettings.get(level));
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.level instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) return;

        RingGeometry geometry = GEOMETRY.computeIfAbsent(level, Forge1201RingRuntime::attach);
        for (Entity entity : level.getAllEntities()) {
            double sourceX = entity.getX();
            if (!geometry.needsCanonicalWrap(sourceX)) continue;
            entity.setPos(geometry.wrapX(sourceX), entity.getY(), entity.getZ());
        }
    }

    private static RingGeometry attach(ServerLevel level) {
        RingWorldSettings settings = RingWorldSettings.get(level);
        RingGeometry geometry = settings.geometry();
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        if (generator instanceof Forge1201GeneratorAccess access) {
            access.ringworld1201$setGeometry(geometry);
            access.ringworld1201$setWallHeight(settings.wallHeightBlocks());
        } else {
            RingWorldMod.LOGGER.warn(
                    "Forge 1.20.1 Overworld generator does not expose RingWorld geometry yet: {}",
                    generator.getClass().getName());
        }
        GEOMETRY.put(level, geometry);
        return geometry;
    }
}
