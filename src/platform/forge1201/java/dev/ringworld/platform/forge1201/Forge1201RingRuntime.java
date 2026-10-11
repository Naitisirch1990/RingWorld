package dev.ringworld.platform.forge1201;

import dev.ringworld.RingWorldMod;
import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingWorldSettings;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
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

/** Forge 1.20.1 server-runtime bridge for the RingWorld backport. */
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

        RingGeometry geometry = geometryFor(level);
        List<Entity> toWrap = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            if (entity != null && !entity.isRemoved()
                    && geometry.needsCanonicalWrap(entity.getX())) {
                toWrap.add(entity);
            }
        }
        for (Entity entity : toWrap) {
            if (entity.isRemoved() || entity.level() != level) continue;
            canonicalizeEntityPosition(entity, geometry);
        }
    }

    public static double canonicalizeEntityPosition(Entity entity, RingGeometry geometry) {
        if (entity == null || geometry == null) return 0.0;
        double sourceX = entity.getX();
        if (!geometry.needsCanonicalWrap(sourceX)) return 0.0;
        double targetX = geometry.wrapX(sourceX);
        double shift = targetX - sourceX;
        entity.setPos(targetX, entity.getY(), entity.getZ());
        return shift;
    }

    public static RingGeometry geometryFor(ServerLevel level) {
        if (level.dimension() != Level.OVERWORLD) return null;
        RingGeometry geometry = GEOMETRY.get(level);
        return geometry != null ? geometry : attach(level);
    }

    private static RingGeometry attach(ServerLevel level) {
        RingWorldSettings settings = RingWorldSettings.get(level);
        RingGeometry geometry = settings.geometry();
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        if (generator instanceof Forge1201GeneratorAccess access) {
            access.ringworld1201$setGeometry(geometry);
            access.ringworld1201$setTerrainNoiseMapping(settings.terrainNoiseMapping());
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
