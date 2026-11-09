#!/usr/bin/env python3
"""JVM adapter, wire and scheduler tests with explicit non-runtime API doubles."""
from pathlib import Path
import sys
import build

root=Path(__file__).resolve().parent
out=root/'build/integration';src=out/'src'
stubs=dict(build.STUBS)
stubs.update({
'net/minecraft/world/phys/Vec3.java':'package net.minecraft.world.phys; public class Vec3 {}',
'net/minecraft/world/phys/AABB.java':'package net.minecraft.world.phys; public class AABB {}',
'io/netty/buffer/ByteBuf.java':'package io.netty.buffer; public class ByteBuf {}',
'net/minecraft/network/FriendlyByteBuf.java':'''package net.minecraft.network;
import java.io.*;
public class FriendlyByteBuf extends io.netty.buffer.ByteBuf {
 ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);DataInputStream in;
 public FriendlyByteBuf(){} public FriendlyByteBuf(byte[] b){in=new DataInputStream(new ByteArrayInputStream(b));}
 public byte[] bytes(){return bytes.toByteArray();}
 public io.netty.buffer.ByteBuf writeLong(long x){try{out.writeLong(x);return this;}catch(IOException e){throw new RuntimeException(e);}}
 public long readLong(){try{return in.readLong();}catch(IOException e){throw new RuntimeException(e);}}
 public FriendlyByteBuf m_130130_(int x){while((x & ~127)!=0){bytes.write(x & 127 | 128);x>>>=7;}bytes.write(x);return this;}
 public int m_130242_(){try{int n=0;for(int i=0;i<5;i++){int b=in.readUnsignedByte();n|=(b&127)<<(7*i);if((b&128)==0)return n;}throw new RuntimeException();}catch(IOException e){throw new RuntimeException(e);}}
 public FriendlyByteBuf m_130087_(byte[] b){m_130130_(b.length);bytes.writeBytes(b);return this;}
 public byte[] m_130101_(int limit){int length=m_130242_();if(length<0 || length>limit)throw new IllegalArgumentException("wire budget");try{byte[] b=in.readNBytes(length);if(b.length!=length)throw new IllegalArgumentException();return b;}catch(IOException e){throw new RuntimeException(e);}}
}''',
'com/mojang/blaze3d/vertex/BufferBuilder.java':'''package com.mojang.blaze3d.vertex;
import java.util.*;
public class BufferBuilder {
 public static final List<double[]> vertices=new ArrayList<>();
 public void m_166779_(VertexFormat.Mode m,VertexFormat f){vertices.clear();}
 public RenderedBuffer m_231175_(){return new RenderedBuffer();}
 public static class RenderedBuffer { public void m_231200_(){} }
}''',
'com/mojang/blaze3d/vertex/Tesselator.java':'''package com.mojang.blaze3d.vertex;
public class Tesselator {static Tesselator t=new Tesselator();static BufferBuilder b=new BufferBuilder();
 public static Tesselator m_85913_(){return t;} public BufferBuilder m_85915_(){return b;} }''',
'com/mojang/blaze3d/vertex/VertexBuffer.java':'''package com.mojang.blaze3d.vertex;
public class VertexBuffer {public enum Usage{STATIC} public static boolean failUpload;public boolean closed;
 public VertexBuffer(Usage u){} public void close(){closed=true;}public void m_85921_(){}
 public void m_231221_(BufferBuilder.RenderedBuffer b){if(failUpload)throw new IllegalStateException("test upload failure");}
 public static void m_85931_(){} }''',
'dev/ringworld/detail/DetailClient.java':'''package dev.ringworld.detail;
import java.util.*;import com.mojang.blaze3d.vertex.*;
public class DetailClient {
 static final Map<Long,DetailTile> tiles=new HashMap<>();static final Map<Long,VertexBuffer> meshes=new HashMap<>();
 static final Set<Long> covered=new HashSet<>();
 static void coverage(int x,int z,boolean on){if(on)covered.add(DetailTile.key(x,z));else covered.remove(DetailTile.key(x,z));}
 static void v(BufferBuilder b,double x,double y,double z,int rgb,double shade){BufferBuilder.vertices.add(new double[]{x,y,z});}
}''',
'dev/ringworld/platform/forge1201/Forge1201RingNetwork.java':'''package dev.ringworld.platform.forge1201;
import java.util.*;
public class Forge1201RingNetwork {
 public static final List<Object[]> sent=new ArrayList<>();
 public static void sendAtlas(Object player,Forge1201AtlasPacket packet){sent.add(new Object[]{player,packet});}
}''',
})
for name,content in stubs.items():
    p=src/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(content)
sources=list(src.rglob('*.java'))+[root/'AdapterTest.java']
classpath=str(Path(sys.argv[1]).resolve())
build.javac(out/'classes',sources,classpath)
build.run('java','-Xverify:all','-cp',f'{out}/classes:{classpath}',
          'dev.ringworld.detail.AdapterTest',Path(sys.argv[1]).resolve())
