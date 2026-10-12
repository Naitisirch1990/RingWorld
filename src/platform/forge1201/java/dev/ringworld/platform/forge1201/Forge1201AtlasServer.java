package dev.ringworld.platform.forge1201;

import dev.ringworld.RingWorldMod;
import dev.ringworld.world.RingTerrainAtlas;
import dev.ringworld.world.RingWorldSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Single server-thread atlas writer. FULL chunks, durable cache, bounded streaming and generation. */
@Mod.EventBusSubscriber(modid = RingWorldMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Forge1201AtlasServer {
    private static final Map<ServerLevel, State> WORLDS = new IdentityHashMap<>();
    private static final TicketType<ChunkPos> TICKET = TicketType.create("ringworld_atlas_1201", Comparator.comparingLong(ChunkPos::toLong));
    private static final class Peer {
        final BitSet pending = new BitSet();
        Peer(BitSet populated) { pending.or(populated); }
    }
    private static final class State {
        final RingTerrainAtlas atlas;
        final Path path;
        final long session = UUID.randomUUID().getMostSignificantBits();
        final Map<Long, LevelChunk> loaded = new LinkedHashMap<>();
        final ArrayDeque<Long> sweep = new ArrayDeque<>();
        final Map<ServerPlayer, Peer> peers = new IdentityHashMap<>();
        final BitSet populated = new BitSet();
        boolean dirty, running = true;
        int cursor, ticks;
        ChunkPos request;
        CompletableFuture<?> future;
        CompletableFuture<Void> saving = CompletableFuture.completedFuture(null);
        State(ServerLevel level) {
            var settings = RingWorldSettings.get(level);
            path = level.getServer().getWorldPath(LevelResource.ROOT).resolve("ringworld/forge1201-terrain-atlas.rwat.gz");
            RingTerrainAtlas found = null;
            if (Files.isRegularFile(path)) try {
                found = RingTerrainAtlas.load(path, settings.geometry(), RingTerrainAtlas.worldHash(settings));
            } catch (Exception e) { RingWorldMod.LOGGER.warn("Cannot reuse Forge atlas cache; rebuilding", e); }
            atlas = found != null ? found : new RingTerrainAtlas(settings.geometry(), RingTerrainAtlas.worldHash(settings));
            for (int x=0; x<atlas.columns(); x++) for (int z=0; z<atlas.rows(); z++)
                if (atlas.hasCell(x,z)) populated.set((x / 16) * atlas.tileRows() + z / 16);
        }
    }
    private static State state(ServerLevel level) { return WORLDS.computeIfAbsent(level, State::new); }
    public static RingTerrainAtlas atlas(ServerLevel level) { return state(level).atlas; }
    @SubscribeEvent public static void loaded(ChunkEvent.Load e) {
        if (e.getLevel() instanceof ServerLevel level && level.dimension() == Level.OVERWORLD
                && e.getChunk() instanceof LevelChunk chunk) {
            // Queue only. No world mutation or FULL wait inside Forge's pre-promotion callback.
            state(level).loaded.put(chunk.getPos().toLong(), chunk);
        }
    }
    @SubscribeEvent public static void unloaded(ChunkEvent.Unload e) {
        if (e.getLevel() instanceof ServerLevel level && WORLDS.containsKey(level)) {
            State s = WORLDS.get(level);
            if (e.getChunk() instanceof LevelChunk chunk) capture(level, s, chunk);
            s.loaded.remove(e.getChunk().getPos().toLong());
        }
    }
    @SubscribeEvent public static void unload(LevelEvent.Unload e) {
        if (!(e.getLevel() instanceof ServerLevel level)) return;
        State s = WORLDS.remove(level); if (s == null) return;
        if (s.request != null) level.getChunkSource().removeRegionTicket(TICKET, s.request, 0, s.request);
        s.saving.join();
        try { s.atlas.save(s.path); } catch (Exception error) { RingWorldMod.LOGGER.error("Failed to save Forge terrain atlas", error); }
    }
    /** Also used by the disposable integration fixture after its block edits. */
    public static void capture(ServerLevel level, LevelChunk chunk) { capture(level, state(level), chunk); }
    private static void capture(ServerLevel level, State s, LevelChunk chunk) {
        var g = s.atlas.geometry(); int cx = chunk.getPos().x, cz = chunk.getPos().z;
        if (cx < 0 || cx >= g.circumferenceChunks() || g.isExteriorChunkZ(cz)) return;
        var pos = new BlockPos.MutableBlockPos();
        for (int dx=4; dx<16; dx+=8) for (int dz=4; dz<16; dz+=8) {
            int x=cx*16+dx, z=cz*16+dz;
            if (z < g.minWidthZ() || z > g.maxWidthZ()) continue;
            int y=chunk.getHeight(Heightmap.Types.WORLD_SURFACE, dx, dz);
            var block=chunk.getBlockState(pos.set(x,y,z));
            while (y>level.getMinBuildHeight() && (block.isAir() || block.is(Blocks.BARRIER)))
                block=chunk.getBlockState(pos.set(x,--y,z));
            // Empty End/void columns still have a known sample. Overworld only for now.
            int rgb=block.isAir() ? 0x333333 : block.getMapColor(level,pos).col;
            if (s.atlas.putBlockSample(x,z,y+1,rgb)) {
                int id=(x/128)*s.atlas.tileRows()+(z-g.minWidthZ())/128;
                s.populated.set(id); s.dirty=true;
                for (Peer p:s.peers.values()) p.pending.set(id);
            }
        }
    }
    @SubscribeEvent public static void tick(TickEvent.LevelTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.level instanceof ServerLevel level) || level.dimension()!=Level.OVERWORLD) return;
        State s=state(level); s.ticks++;
        if (s.sweep.isEmpty()) s.sweep.addAll(s.loaded.keySet());
        for (int i=0;i<16 && !s.sweep.isEmpty();i++) {
            LevelChunk chunk=s.loaded.get(s.sweep.removeFirst());
            if (chunk!=null && level.getChunkSource().getChunkNow(chunk.getPos().x,chunk.getPos().z)==chunk) capture(level,s,chunk);
        }
        // Keep observations fresh even after /fill, explosions and edits by other mods.
        // Ordered tile snapshots coalesce every change before publication, with no per-block packets.
        s.peers.keySet().removeIf(p -> p.hasDisconnected() || p.serverLevel()!=level);
        for (ServerPlayer player:level.players()) {
            Peer peer=s.peers.get(player);
            if (peer==null) {
                Forge1201RingNetwork.sendAtlas(player,new Forge1201AtlasPacket(s.session,-1,0,new byte[0]));
                peer=new Peer(s.populated); s.peers.put(player,peer);
            }
            for(int budget=0;budget<8;budget++) {
                int id=peer.pending.nextSetBit(0); if(id<0) break; peer.pending.clear(id);
                int tx=id/s.atlas.tileRows(),tz=id%s.atlas.tileRows();
                Forge1201RingNetwork.sendAtlas(player,new Forge1201AtlasPacket(s.session,tx,tz,s.atlas.encodeTile(tx,tz)));
            }
            if (s.ticks%200==0 && !s.atlas.isComplete()) {
                String text=String.format(Locale.ROOT,"Mapa lejano: %.2f%% — %s (/ringworld atlas)",s.atlas.completion()*100,s.running?"generando":"pausado");
                player.displayClientMessage(Component.literal(text),true);
            }
        }
        if (s.ticks%600==0 && s.dirty && s.saving.isDone()) {
            var snapshot=s.atlas.snapshot(); s.dirty=false;
            s.saving=CompletableFuture.runAsync(() -> {
                try { snapshot.save(s.path); }
                catch(Exception error) { RingWorldMod.LOGGER.error("Failed to checkpoint Forge atlas",error); }
            });
        }
        // One outstanding FULL future; never join an unfinished chunk request on the server thread.
        if (s.request!=null && s.future.isDone()) {
            LevelChunk chunk=level.getChunkSource().getChunkNow(s.request.x,s.request.z);
            if(chunk!=null) capture(level,s,chunk);
            level.getChunkSource().removeRegionTicket(TICKET,s.request,0,s.request);
            if(chunk==null) { s.running=false; RingWorldMod.LOGGER.error("Atlas generation paused after unavailable chunk {}",s.request); }
            s.request=null; s.future=null;
        }
        if (!s.running || s.request!=null || s.atlas.isComplete() || level.players().isEmpty()
                || level.getChunkSource().getPendingTasksCount()>64) return;
        var g=s.atlas.geometry(); int nz=Math.floorDiv(g.maxWidthZ(),16)-Math.floorDiv(g.minWidthZ(),16)+1, total=g.circumferenceChunks()*nz;
        // Resume from durable cells. Work is bounded even for a mostly complete map.
        for(int work=0;work<256;work++) {
            int n=s.cursor++%total, cx=n/nz, cz=Math.floorDiv(g.minWidthZ(),16)+n%nz;
            int row=(cz*16+4-g.minWidthZ())/8;
            boolean complete=true;
            for(int dz=4;dz<16;dz+=8) {
                int z=cz*16+dz; if(z<g.minWidthZ() || z>g.maxWidthZ()) continue;
                int r=(z-g.minWidthZ())/8;
                complete &= s.atlas.hasCell(cx*2,r) && s.atlas.hasCell(cx*2+1,r);
            }
            if(complete) continue;
            s.request=new ChunkPos(cx,cz);
            level.getChunkSource().addRegionTicket(TICKET,s.request,0,s.request);
            s.future=level.getChunkSource().getChunkFuture(cx,cz,ChunkStatus.FULL,true);
            break;
        }
    }
    @SubscribeEvent public static void commands(RegisterCommandsEvent e) {
        var atlas=Commands.literal("atlas").executes(c -> status(c.getSource().getLevel(),c.getSource()));
        for(String action:new String[]{"pause","resume","start"}) atlas.then(Commands.literal(action).requires(s->s.hasPermission(2)).executes(c -> {
            State s=state(c.getSource().getServer().overworld()); s.running=!action.equals("pause");
            return status(c.getSource().getServer().overworld(),c.getSource());
        }));
        atlas.then(Commands.literal("status").executes(c -> status(c.getSource().getServer().overworld(),c.getSource())));
        e.getDispatcher().register(Commands.literal("ringworld").then(atlas));
    }
    private static int status(ServerLevel ignored, net.minecraft.commands.CommandSourceStack source) {
        State s=state(source.getServer().overworld());
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,"Atlas: %d/%d (%.2f%%), %s. Datos reales; guarda y reanuda el progreso.",s.atlas.presentCount(),s.atlas.cellCount(),100*s.atlas.completion(),s.running?"generando":"pausado")),false); return 1;
    }
}
