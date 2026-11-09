// SPDX-License-Identifier: MPL-2.0
package dev.ringworld.detail;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import com.mojang.blaze3d.vertex.*;
import dev.ringworld.platform.forge1201.*;
import dev.ringworld.world.*;
import net.minecraft.network.FriendlyByteBuf;

public final class AdapterTest {
    static int checks;
    static void check(boolean b,String name){checks++;if(!b)throw new AssertionError(name);}
    public static final class Player {
        final UUID id=UUID.randomUUID();
        public UUID m_20148_(){return id;}public double m_20185_(){return 0;}public double m_20189_(){return 0;}
    }
    public static final class Level {
        public List<Player> players=new ArrayList<>();public List<Player> m_6907_(){return players;}
    }
    public static final class State {
        final Path path;final RingTerrainAtlas atlas;final long session=42;
        final Map<Object,Object> peers=new HashMap<>();
        State(Path p){path=p;atlas=new RingTerrainAtlas(new RingGeometry(128,2048),7);}
    }
    public static void main(String[] args)throws Exception {
        for(int length:new int[]{0,588,1794,1800,24000,24576}) {
            byte[] data=new byte[length];new Random(1).nextBytes(data);
            Forge1201AtlasPacket packet=new Forge1201AtlasPacket(42,-2,0,data);
            FriendlyByteBuf wire=new FriendlyByteBuf();Forge1201AtlasPacket.encode(packet,wire);
            Forge1201AtlasPacket decoded=Forge1201AtlasPacket.decode(new FriendlyByteBuf(wire.bytes()));
            check(decoded.session()==42 && decoded.tileX()==-2 && decoded.tileZ()==0 && Arrays.equals(data,decoded.data()),"actual patched packet roundtrip "+length);
        }
        FriendlyByteBuf tooBig=new FriendlyByteBuf();
        Forge1201AtlasPacket.encode(new Forge1201AtlasPacket(42,-2,0,new byte[24577]),tooBig);
        try{Forge1201AtlasPacket.decode(new FriendlyByteBuf(tooBig.bytes()));throw new AssertionError("packet cap");}
        catch(IllegalArgumentException expected){checks++;}
        byte[] network;
        try(java.util.zip.ZipFile jar=new java.util.zip.ZipFile(args[0])){network=jar.getInputStream(jar.getEntry("dev/ringworld/platform/forge1201/Forge1201RingNetwork.class")).readAllBytes();}
        String constants=new String(network,java.nio.charset.StandardCharsets.ISO_8859_1);
        check(constants.contains("ringworld-atlas-v7-volume4-forge1201") && !constants.contains("ringworld-atlas-v6-detail-forge1201"),"channel protocol fully upgraded");

        RingGeometry geometry=new RingGeometry(2000,61440);
        DetailTile tile=new DetailTile(0,0);tile.quads=new int[]{2,150,3,10,3,5,0xa12722};
        DetailClient.tiles.put(tile.key(),tile);VolumeClient.rebuild(0,geometry);
        check(BufferBuilder.vertices.size()==4,"volume adapter emits four vertices per quad");
        check(BufferBuilder.vertices.stream().allMatch(p->p[1]>=150 && p[1]<=153),"adapter never extrudes below hull");
        check(DetailClient.covered.contains(tile.key()),"coverage after upload");
        VertexBuffer original=DetailClient.meshes.get(0L);DetailClient.covered.clear();VertexBuffer.failUpload=true;
        try{VolumeClient.rebuild(0,geometry);throw new AssertionError("upload failure");}catch(IllegalStateException expected){checks++;}
        check(DetailClient.meshes.get(0L)==original && !original.closed && DetailClient.covered.isEmpty(),"failed upload preserves old mesh and coverage");
        VertexBuffer.failUpload=false;DetailClient.tiles.clear();VolumeClient.rebuild(0,geometry);
        check(original.closed && DetailClient.meshes.isEmpty(),"evicted group releases GPU mesh");
        DetailTile air=new DetailTile(0,0);DetailClient.tiles.put(0L,air);VolumeClient.rebuild(0,geometry);
        check(DetailClient.meshes.isEmpty() && DetailClient.covered.contains(0L),"empty saved chunks suppress invented coarse matter");
        DetailClient.tiles.clear();DetailTile rim=new DetailTile(0,-63);
        rim.quads=new int[]{0,150,0,16,16,3,0xffffff};DetailClient.tiles.put(rim.key(),rim);
        long group=DetailTile.key(0,Math.floorDiv(-63,4));VolumeClient.rebuild(group,geometry);
        check(BufferBuilder.vertices.size()==4,"partial finite rim face retained");
        check(BufferBuilder.vertices.stream().allMatch(p->p[2]>=-1000 && p[2]<=1000),"rim clipped to saved width");
        check(BufferBuilder.vertices.stream().anyMatch(p->p[2]==-1000),"rim boundary exact");

        Level level=new Level();State state=new State(Files.createTempDirectory("volume-scheduler").resolve("data/atlas.bin"));
        for(int i=0;i<3;i++){Player p=new Player();level.players.add(p);state.peers.put(p,new Object());}
        DetailServer.Context context=new DetailServer.Context(state);DetailServer.current=context;context.revision=2;
        long a=DetailTile.key(0,0),b=DetailTile.key(1,0);
        byte[] first=new byte[16000],second=new byte[8000];long now=System.currentTimeMillis();
        context.cache.put(a,new DetailServer.Cached(first,now,1));context.cache.put(b,new DetailServer.Cached(second,now,2));context.cacheBytes=24000;
        DetailServer.run(level,state);int total=0;Map<Object,Integer> perPlayer=new HashMap<>();Map<Object,Integer> counts=new HashMap<>();
        for(Object[] s:Forge1201RingNetwork.sent){int n=((Forge1201AtlasPacket)s[1]).data().length;total+=n;perPlayer.merge(s[0],n,Integer::sum);counts.merge(s[0],1,Integer::sum);}
        check(total<=65536 && total>0,"global detail send budget");
        check(perPlayer.values().stream().allMatch(n->n<=24576),"per player byte budget");
        check(counts.values().stream().allMatch(n->n<=2),"per player tile budget");
        Forge1201RingNetwork.sent.clear();DetailServer.run(level,state);
        check(Forge1201RingNetwork.sent.size()<=1,"already sent revisions not repeated");
        context.pending=CompletableFuture.completedFuture(List.of(new DetailServer.Result(a,first.clone())));
        Forge1201RingNetwork.sent.clear();DetailServer.run(level,state);
        check(context.cache.get(a).revision()==1 && Forge1201RingNetwork.sent.isEmpty(),"unchanged resample keeps revision and avoids network resend");
        level.players.remove(0);DetailServer.run(level,state);check(context.peers.size()==2,"disconnected peer state removed");
        DetailServer.stop();check(DetailServer.current==null && context.worker.isShutdown(),"worker stopped on session teardown");
        System.out.println("PASS "+checks+" JVM adapter/wire/scheduler checks with API doubles; not a Minecraft multiplayer run");
    }
}
