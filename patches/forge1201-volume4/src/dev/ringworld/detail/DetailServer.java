// SPDX-License-Identifier: MPL-2.0
package dev.ringworld.detail;

import dev.ringworld.platform.forge1201.Forge1201AtlasPacket;
import dev.ringworld.platform.forge1201.Forge1201RingNetwork;
import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingTerrainAtlas;
import java.lang.reflect.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;

/** Saved-region capture stays on one worker; only immutable packets cross threads. */
public final class DetailServer {
    static Context current;
    static long lastWarning;
    static Object field(Object owner, String name) throws ReflectiveOperationException {
        Field f = owner.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(owner);
    }
    static Object call(Object owner, String name) throws ReflectiveOperationException {
        return owner.getClass().getMethod(name).invoke(owner);
    }
    public static void stop() {
        if (current != null) { current.worker.shutdownNow(); current = null; }
    }
    public static void tick(Object level, Object state) {
        try { run(level,state); }
        catch (Exception failure) {
            long now = System.currentTimeMillis();
            if (now-lastWarning > 60000) {
                lastWarning = now;
                System.err.println("[RingWorld volume4] Detail fallback: "+failure.getClass().getSimpleName()+": "+failure.getMessage());
            }
        }
    }
    static void run(Object level, Object state) throws Exception {
        if (current == null || current.state != state) { stop(); current = new Context(state); }
        Context c = current;
        List<?> players = (List<?>)call(level,"m_6907_");
        Set<UUID> active = new HashSet<>();
        for (Object p : players) active.add((UUID)call(p,"m_20148_"));
        c.peers.keySet().retainAll(active);
        long now = System.currentTimeMillis();
        if (c.pending != null && c.pending.isDone()) {
            try {
                for (Result r : c.pending.get()) {
                    Cached old = c.cache.get(r.key);
                    // Stable array identity makes unchanged per-player checks O(1).
                    byte[] data = old != null && Arrays.equals(old.data,r.data) ? old.data : r.data;
                    if (old != null) c.cacheBytes -= bytes(old.data);
                    c.cache.put(r.key,new Cached(data,now,old != null && data == old.data ? old.revision : ++c.revision)); c.cacheBytes += bytes(data);
                }
                Iterator<Cached> evict = c.cache.values().iterator();
                while ((c.cache.size() > 4096 || c.cacheBytes > 64L*1024*1024) && evict.hasNext()) {
                    c.cacheBytes -= bytes(evict.next().data); evict.remove();
                }
            } finally { c.pending = null; }
        }
        if (players.isEmpty()) return;
        Map<?,?> ready = (Map<?,?>)field(state,"peers");
        RingGeometry g = c.atlas.geometry();
        int globalBytes = 0;
        int first = Math.floorMod(c.sendRound++,players.size());
        for (int pi=0; pi<players.size(); pi++) {
            Object player = players.get((first+pi)%players.size());
            UUID uuid = (UUID)call(player,"m_20148_");
            int x = Math.floorMod((int)Math.floor(((Number)call(player,"m_20185_")).doubleValue()/16),g.circumferenceChunks());
            int z = (int)Math.floor(((Number)call(player,"m_20189_")).doubleValue()/16);
            Peer peer = c.peers.computeIfAbsent(uuid,id -> new Peer());
            if (peer.keys == null || peer.x != x || peer.z != z) {
                peer.x=x; peer.z=z; peer.cursor=0;
                List<Long> keys = new ArrayList<>(); Set<Long> unique = new HashSet<>();
                for (int dz=-20;dz<=20;dz++) for (int dx=-20;dx<=20;dx++)
                    if (dx*dx+dz*dz <= 400 && !g.isExteriorChunkZ(z+dz)) {
                        long key = DetailTile.key(Math.floorMod(x+dx,g.circumferenceChunks()),z+dz);
                        if (unique.add(key)) keys.add(key);
                    }
                keys.sort(Comparator.comparingInt(k -> distance(k,x,z,g.circumferenceChunks())));
                peer.keys=keys; peer.sent.keySet().retainAll(unique);
            }
            if (!ready.containsKey(player)) continue;
            int sent=0, playerBytes=0;
            for (long key : peer.keys) {
                Cached cached = c.cache.get(key);
                if (cached == null || cached.data == null || Objects.equals(peer.sent.get(key),cached.revision)) continue;
                int length = cached.data.length;
                if (playerBytes+length > DetailTile.MAX_PACKET || globalBytes+length > 65536) continue;
                c.send.invoke(null,player,new Forge1201AtlasPacket(c.session,-2,0,cached.data));
                peer.sent.put(key,cached.revision); playerBytes+=length; globalBytes+=length;
                if (++sent == 2) break;
            }
        }
        if (c.pending != null) return;
        List<Peer> peers = new ArrayList<>(c.peers.values());
        List<Job> jobs = new ArrayList<>(); Set<Long> queued = new HashSet<>();
        for (int n=0; n<8 && !peers.isEmpty(); n++) {
            Peer peer=peers.get(Math.floorMod(c.round++,peers.size()));
            if (peer.keys == null || peer.keys.isEmpty()) continue;
            for (int scan=0; scan<peer.keys.size(); scan++) {
                long key=peer.keys.get(peer.cursor); peer.cursor=(peer.cursor+1)%peer.keys.size();
                Cached cached=c.cache.get(key);
                if (queued.contains(key) || cached != null && now-cached.time < 60000) continue;
                int x=(int)(key>>32), z=(int)key;
                int ax=Math.floorMod(x*2,c.atlas.columns()), az=(z*16+8-g.minWidthZ())/8;
                int color=az>=0 && az<c.atlas.rows() && c.atlas.hasCell(ax,az) ? c.atlas.cellColor(ax,az) : 0x64824b;
                jobs.add(new Job(key,x,z,color)); queued.add(key); break;
            }
        }
        if (!jobs.isEmpty()) c.pending=c.worker.submit(() -> capture(jobs,c.region));
    }
    static int bytes(byte[] b) { return b == null ? 0 : b.length; }
    static int distance(long key,int x,int z,int circumference) {
        int dx=Math.floorMod((int)(key>>32)-x+circumference/2,circumference)-circumference/2;
        int dz=(int)key-z; return dx*dx+dz*dz;
    }
    static List<Result> capture(List<Job> jobs,Path region) {
        List<Result> results=new ArrayList<>();
        for (Job job:jobs) {
            if (Thread.currentThread().isInterrupted()) break;
            byte[] data=null;
            try { DetailTile tile=RegionSurface.read(region,job.x,job.z,job.color); if(tile!=null)data=tile.encode(); }
            catch (Exception ignored) { /* Missing/concurrently saved regions retain coarse fallback. */ }
            results.add(new Result(job.key,data));
        }
        return results;
    }
    static final class Context {
        final Object state; final Path region; final RingTerrainAtlas atlas;
        final long session; final Method send;
        int round,sendRound; long cacheBytes,revision;
        final ExecutorService worker=Executors.newSingleThreadExecutor(r -> {
            Thread t=new Thread(r,"RingWorld-saved-volume4");t.setDaemon(true);return t;
        });
        Future<List<Result>> pending;
        final Map<UUID,Peer> peers=new LinkedHashMap<>();
        final LinkedHashMap<Long,Cached> cache=new LinkedHashMap<>(256,.75f,true);
        Context(Object state) throws Exception {
            this.state=state;
            region=((Path)field(state,"path")).getParent().getParent().resolve("region");
            atlas=(RingTerrainAtlas)field(state,"atlas"); session=(Long)field(state,"session");
            Method found=null;
            for(Method m:Forge1201RingNetwork.class.getMethods())if(m.getName().equals("sendAtlas"))found=m;
            if(found==null)throw new NoSuchMethodException("sendAtlas");send=found;
        }
    }
    static final class Peer {
        int x,z,cursor; List<Long> keys; final Map<Long,Long> sent=new HashMap<>();
    }
    record Job(long key,int x,int z,int color) {}
    record Result(long key,byte[] data) {}
    record Cached(byte[] data,long time,long revision) {}
}
