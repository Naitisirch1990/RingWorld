// SPDX-License-Identifier: MPL-2.0
package dev.ringworld.detail;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

public final class VolumeTest {
    static int checks;
    static void check(boolean value,String name) { checks++;if(!value)throw new AssertionError(name); }
    static void fails(Throwing r,String name)throws Exception {
        try {r.run();}catch(IOException expected){checks++;return;}throw new AssertionError(name);
    }
    interface Throwing {void run()throws Exception;}
    static int[] blocks(){return new int[16*384*16];}
    static void put(int[] a,int x,int y,int z,int rgb){a[VolumeMesher.index(x,y+64,z)]=0xff000000|rgb;}
    static void box(int[] a,int x0,int y0,int z0,int x1,int y1,int z1,int rgb) {
        for(int y=y0;y<y1;y++)for(int z=z0;z<z1;z++)for(int x=x0;x<x1;x++)put(a,x,y,z,rgb);
    }
    static DetailTile mesh(int[] a)throws Exception{return VolumeMesher.mesh(0,0,a,1);}
    static boolean front(DetailTile tile,double x,double y) {
        for(int i=0;i<tile.quads.length;i+=7){int[] q=tile.quads;
            if(q[i+5]/2==2 && x>q[i] && x<q[i]+q[i+3] && y>q[i+1] && y<q[i+1]+q[i+4])return true;
        }return false;
    }
    static void roundtrip(DetailTile tile)throws Exception {
        byte[] b=tile.encode();DetailTile back=DetailTile.decode(b);
        check(back.x==tile.x && back.z==tile.z && Arrays.equals(tile.quads,back.quads),"codec roundtrip");
        check(b.length<=DetailTile.MAX_PACKET,"wire budget");
    }
    static void tag(DataOutputStream out,int type,String name)throws Exception {out.writeByte(type);out.writeUTF(name);}
    static byte[] nbt(int cx,int cz,int[] a)throws Exception {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream o=new DataOutputStream(bytes);
        tag(o,10,"");tag(o,3,"xPos");o.writeInt(cx);tag(o,3,"zPos");o.writeInt(cz);
        tag(o,8,"Status");o.writeUTF("minecraft:full");tag(o,9,"sections");o.writeByte(10);o.writeInt(24);
        for(int sy=-4;sy<20;sy++) {
            tag(o,1,"Y");o.writeByte(sy);tag(o,10,"block_states");
            tag(o,9,"palette");o.writeByte(10);o.writeInt(3);
            for(String name:new String[]{"minecraft:air","minecraft:red_wool","minecraft:spruce_planks"}) {
                tag(o,8,"Name");o.writeUTF(name);o.writeByte(0);
            }
            tag(o,12,"data");o.writeInt(256);
            for(int group=0;group<256;group++) {
                long packed=0;
                for(int k=0;k<16;k++) {
                    int cell=group*16+k,x=cell%16,z=(cell/16)%16,y=sy*16+cell/256;
                    int rgb=a[VolumeMesher.index(x,y+64,z)];
                    int index=rgb==0?0:(rgb&0xffffff)==0xa12722?1:2;
                    packed|=(long)index<<(k*4);
                }o.writeLong(packed);
            }
            o.writeByte(0);o.writeByte(0);
        }
        o.writeByte(0);return bytes.toByteArray();
    }
    static void region(Path dir,int cx,int cz,byte[] nbt,int compression)throws Exception {
        byte[] data=nbt;
        if(compression!=3) {
            ByteArrayOutputStream b=new ByteArrayOutputStream();
            try(OutputStream out=compression==1?new GZIPOutputStream(b):new DeflaterOutputStream(b)){out.write(nbt);}
            data=b.toByteArray();
        }
        Path p=dir.resolve("r."+Math.floorDiv(cx,32)+"."+Math.floorDiv(cz,32)+".mca");
        try(RandomAccessFile f=new RandomAccessFile(p.toFile(),"rw")) {
            int sectors=(data.length+5+4095)/4096;
            f.setLength((2L+sectors)*4096);f.seek(4*(Math.floorMod(cx,32)+32*Math.floorMod(cz,32)));
            f.writeInt(2<<8|sectors);f.seek(8192);f.writeInt(data.length+1);f.writeByte(compression);f.write(data);
        }
    }
    public static void main(String[] args)throws Exception {
        int[] empty=blocks();DetailTile tile=mesh(empty);
        check(tile.quads.length==0,"empty air stays empty");roundtrip(tile);
        int[] solid=blocks();box(solid,0,64,0,16,80,16,0x123456);tile=mesh(solid);
        check(tile.quads.length==42,"solid box merges to six faces");roundtrip(tile);
        int[] floating=blocks();box(floating,2,150,3,14,153,13,0xa12722);
        tile=mesh(floating);check(tile.quads.length==42,"floating hull six faces");
        for(int i=0;i<tile.quads.length;i+=7)check(tile.quads[i+1]>=150,"no fictitious hanging support");
        check(front(tile,5.5,151.5),"hull visible");check(!front(tile,5.5,149.5),"air under hull retained");roundtrip(tile);
        int[] layers=blocks();box(layers,2,90,3,14,91,13,0x735435);box(layers,2,150,3,14,153,13,0xa12722);
        tile=mesh(layers);check(!front(tile,5.5,120.5),"stacked layers preserve vertical gap");roundtrip(tile);
        int[] arch=blocks();box(arch,2,100,6,4,112,8,0x735435);box(arch,10,100,6,12,112,8,0x735435);
        box(arch,2,110,6,12,112,8,0xa12722);tile=mesh(arch);
        check(front(tile,2.5,105.5),"arch pillar retained");check(front(tile,6.5,111.5),"arch lintel retained");
        check(!front(tile,6.5,105.5),"arch opening retained");roundtrip(tile);
        for(int step:new int[]{2,4}) {
            tile=VolumeMesher.mesh(0,0,layers,step);
            check(!front(tile,5.5,120.5),"fallback keeps vertical gaps step "+step);roundtrip(tile);
        }
        fails(()->DetailTile.decode(new byte[24577]),"oversized wire packet rejected");
        fails(()->DetailTile.decode(new byte[588]),"legacy payload rejected");
        DetailTile invalid=new DetailTile(0,0);invalid.quads=new int[]{0,-65,0,1,1,1,0};
        fails(invalid::encode,"out of world face rejected");
        invalid.quads=new int[]{0,0,0,0,1,1,0};fails(invalid::encode,"empty face rejected");
        ByteArrayOutputStream bomb=new ByteArrayOutputStream();
        try(OutputStream out=new DeflaterOutputStream(bomb)){out.write(new byte[100000]);}
        fails(()->DetailTile.decode(bomb.toByteArray()),"inflate budget enforced");
        int[] noisy=blocks();Random random=new Random(72);
        for(int y=0;y<64;y++)for(int z=0;z<16;z++)for(int x=0;x<16;x++)if((x+y+z)%2==0)put(noisy,x,y,z,random.nextInt(0xffffff));
        fails(()->mesh(noisy),"complex mesh quad budget enforced");
        Path dir=Files.createTempDirectory("ringworld-volume-region-");
        for(int compression=1;compression<=3;compression++) {
            int cx=compression==1?-33:compression,cz=-2;
            Path fixture=dir.resolve("compression"+compression);Files.createDirectories(fixture);
            region(fixture,cx,cz,nbt(cx,cz,floating),compression);
            tile=RegionSurface.read(fixture,cx,cz,0x64824b);
            check(tile!=null && tile.x==cx && tile.z==cz,"saved region coordinates compression "+compression);
            check(!front(tile,5.5,120.5) && front(tile,5.5,151.5),"saved floating ship keeps gap compression "+compression);
            check(tile.quads[6]==0xa12722,"colored wool palette retained");roundtrip(tile);
        }
        check(RegionSurface.read(dir,999,999,0)==null,"missing region never generates a chunk");
        region(dir,0,0,nbt(1,0,floating),2);
        check(RegionSurface.read(dir,0,0,0)==null,"mismatched saved chunk rejected");
        RegionSurface.Section one=new RegionSurface.Section(0,new String[]{"air"},new long[0]);
        check(one.paletteIndex(15,15,15)==0,"single entry palette without storage");
        RegionSurface.Section broken=new RegionSurface.Section(0,new String[]{"air","stone"},new long[0]);
        fails(()->broken.paletteIndex(0,0,0),"truncated palette rejected");
        check(DetailServer.distance(DetailTile.key(0,0),127,0,128)==1,"nearest chunk across seam");
        check(DetailServer.distance(DetailTile.key(127,-2),0,-2,128)==1,"reverse seam distance");
        // Retain a deterministic mesh fixture for optional GPU/silhouette inspection.
        Path out=Path.of(args[0]);Files.write(out,mesh(layers).encode());
        long start=System.nanoTime();for(int i=0;i<32;i++){if(RegionSurface.read(dir.resolve("compression2"),2,-2,0x64824b)==null)throw new AssertionError("benchmark fixture unavailable");}
        System.out.println("PASS "+checks+" Java checks; 32 saved chunk reads/meshes: "+((System.nanoTime()-start)/1_000_000)+" ms (fixture only)");
    }
}
