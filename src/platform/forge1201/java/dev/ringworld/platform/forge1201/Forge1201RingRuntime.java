package dev.ringworld.platform.forge1201;

import dev.ringworld.RingWorldMod;
import dev.ringworld.world.RingGenerationBoundary;
import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingNavigationAccess;
import dev.ringworld.world.RingTerrainNoiseMapping;
import dev.ringworld.world.RingWorldSettings;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.ChunkEvent;
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
        if (!(event.getLevel() instanceof ServerLevel level)
                || !Forge1201DimensionProfiles.isRingDimension(level)) return;
        RingGeometry geometry = attach(level);
        RingWorldMod.LOGGER.info(
                "Forge 1.20.1 RingWorld runtime attached to {}: circumference={} width={} chunks={}x{}",
                Forge1201DimensionProfiles.diagnosticName(level),
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
     * Edge style v3 is deliberately repaired when an already-generated chunk
     * is loaded. This lets existing RingWorld saves gain the level twenty-block
     * wall and quiet approach without deleting/regenerating the world.
     */
    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !(event.getChunk() instanceof LevelChunk chunk)
                || !Forge1201DimensionProfiles.isRingDimension(level)) return;
        RingGeometry geometry = geometryFor(level);
        if (geometry == null || !RingGenerationBoundary.intersectsBoundaryReserve(chunk, geometry)) return;
        if (RingGenerationBoundary.migrateLegacyRim(chunk, geometry, 0)) {
            RingWorldMod.LOGGER.debug(
                    "Updated RingWorld edge chunk {} in {} to rim style {}",
                    chunk.getPos(), Forge1201DimensionProfiles.diagnosticName(level),
                    RingGenerationBoundary.RIM_STYLE_VERSION);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        // The packet carries the persisted Overworld identity plus the fixed
        // Nether and End profiles, so client presentation can select the right
        // geometry immediately after a dimension change.
        ServerLevel overworld = player.getServer().getLevel(Level.OVERWORLD);
        if (overworld != null) {
            Forge1201RingNetwork.sendSettings(player, RingWorldSettings.get(overworld));
        }
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.level instanceof ServerLevel level)
                || !Forge1201DimensionProfiles.isRingDimension(level)) return;

        RingGeometry geometry = geometryFor(level);
        if (geometry == null) return;
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

        // Active AI paths live in the same transient coordinate chart as the
        // mob. Fold their nodes and stuck-detection caches by exactly the same
        // circumference shift or a mob crossing X=0/C immediately believes its
        // target jumped tens of thousands of blocks away.
        if (entity instanceof Mob mob && mob.getNavigation() instanceof RingNavigationAccess navigation) {
            navigation.ringworld$foldPath((int)Math.rint(shift));
        }
        return shift;
    }

    /** Full runtime geometry; attaches saved settings and generator state lazily if necessary. */
    public static RingGeometry geometryFor(ServerLevel level) {
        if (!Forge1201DimensionProfiles.isRingDimension(level)) return null;
        RingGeometry geometry = GEOMETRY.get(level);
        return geometry != null ? geometry : attach(level);
    }

    /**
     * Geometry safe to use from ChunkMap/DistanceManager while ServerLevel is
     * still under construction. It must not touch getChunkSource(), saved data,
     * or fire runtime attachment as those facilities can be incomplete at that
     * point. Once attached, the persisted geometry is returned instead.
     */
    public static RingGeometry geometryForStorage(ServerLevel level) {
        if (!Forge1201DimensionProfiles.isRingDimension(level)) return null;
        RingGeometry geometry = GEOMETRY.get(level);
        return geometry != null ? geometry : Forge1201DimensionProfiles.bootstrapGeometry(level);
    }

    private static RingGeometry attach(ServerLevel level) {
        final RingGeometry geometry;
        final int terrainNoiseMapping;
        final int wallHeight;

        if (level.dimension() == Level.OVERWORLD) {
            RingWorldSettings settings = RingWorldSettings.get(level);
            geometry = settings.geometry();
            terrainNoiseMapping = settings.terrainNoiseMapping();
            wallHeight = settings.wallHeightBlocks();
        } else {
            geometry = Forge1201DimensionProfiles.fixedGeometry(level);
            if (geometry == null) return null;
            terrainNoiseMapping = RingTerrainNoiseMapping.CURRENT;
            wallHeight = Forge1201DimensionProfiles.LEGACY_WALL_HEIGHT_BLOCKS;
        }

        ChunkGenerator generator = level.getChunkSource().getGenerator();
        if (generator instanceof Forge1201GeneratorAccess access) {
            access.ringworld1201$setGeometry(geometry);
            access.ringworld1201$setTerrainNoiseMapping(terrainNoiseMapping);
            access.ringworld1201$setWallHeight(wallHeight);
        } else {
            RingWorldMod.LOGGER.warn(
                    "Forge 1.20.1 {} generator does not expose RingWorld geometry: {}",
                    Forge1201DimensionProfiles.diagnosticName(level), generator.getClass().getName());
        }
        GEOMETRY.put(level, geometry);
        return geometry;
    }
}
