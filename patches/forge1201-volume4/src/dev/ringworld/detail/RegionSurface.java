// SPDX-License-Identifier: MPL-2.0
// NBT reader adapted from the user-supplied RingWorld detail extension.
package dev.ringworld.detail;

import dev.ringworld.detail.DetailTile;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

public final class RegionSurface {
    static final int LIMIT = 0x800000;

    static Object tag(DataInputStream dataInputStream, int n, int n2) throws IOException {
        if (n2 > 32) {
            throw new IOException("NBT depth");
        }
        switch (n) {
            case 1: {
                return dataInputStream.readByte();
            }
            case 2: {
                return dataInputStream.readShort();
            }
            case 3: {
                return dataInputStream.readInt();
            }
            case 4: {
                return dataInputStream.readLong();
            }
            case 5: {
                return Float.valueOf(dataInputStream.readFloat());
            }
            case 6: {
                return dataInputStream.readDouble();
            }
            case 7: {
                int n3 = RegionSurface.len(dataInputStream);
                byte[] byArray = new byte[n3];
                dataInputStream.readFully(byArray);
                return byArray;
            }
            case 8: {
                return dataInputStream.readUTF();
            }
            case 9: {
                int n4 = dataInputStream.readUnsignedByte();
                int n5 = RegionSurface.len(dataInputStream);
                if (n5 > 65536) {
                    throw new IOException("NBT list");
                }
                ArrayList<Object> arrayList = new ArrayList<Object>(n5);
                int n6 = 0;
                while (n6 < n5) {
                    arrayList.add(RegionSurface.tag(dataInputStream, n4, n2 + 1));
                    ++n6;
                }
                return arrayList;
            }
            case 10: {
                int n7;
                HashMap<String, Object> hashMap = new HashMap<String, Object>();
                while ((n7 = dataInputStream.readUnsignedByte()) != 0) {
                    if (hashMap.size() > 4096) {
                        throw new IOException("NBT keys");
                    }
                    hashMap.put(dataInputStream.readUTF(), RegionSurface.tag(dataInputStream, n7, n2 + 1));
                }
                return hashMap;
            }
            case 11: {
                int n8 = RegionSurface.len(dataInputStream);
                if (n8 > 0x200000) {
                    throw new IOException("NBT ints");
                }
                int[] nArray = new int[n8];
                int n9 = 0;
                while (n9 < n8) {
                    nArray[n9] = dataInputStream.readInt();
                    ++n9;
                }
                return nArray;
            }
            case 12: {
                int n10 = RegionSurface.len(dataInputStream);
                if (n10 > 0x100000) {
                    throw new IOException("NBT longs");
                }
                long[] lArray = new long[n10];
                int n11 = 0;
                while (n11 < n10) {
                    lArray[n11] = dataInputStream.readLong();
                    ++n11;
                }
                return lArray;
            }
        }
        throw new IOException("NBT tag");
    }

    static int len(DataInputStream dataInputStream) throws IOException {
        int n = dataInputStream.readInt();
        if (n < 0 || n > 0x800000) {
            throw new IOException("NBT size");
        }
        return n;
    }

    static Map<?, ?> map(Object object) {
        return object instanceof Map ? (Map)object : Collections.emptyMap();
    }

    public static DetailTile read(Path region, int cx, int cz, int fallback) throws IOException {
        Path path = region.resolve("r."+Math.floorDiv(cx,32)+"."+Math.floorDiv(cz,32)+".mca");
        if (!Files.isRegularFile(path)) return null;
        byte[] compressed; int compression;
        try (RandomAccessFile file = new RandomAccessFile(path.toFile(), "r")) {
            int slot = 4*(Math.floorMod(cx,32)+32*Math.floorMod(cz,32));
            if (file.length() < 8192) return null;
            file.seek(slot); int location = file.readInt();
            int sector = location >>> 8, sectors = location & 255;
            long offset = (long)sector*4096;
            if (sector < 2 || sectors == 0 || offset+5 > file.length()) return null;
            file.seek(offset); int length = file.readInt(); compression = file.readUnsignedByte();
            if (length < 1 || length > LIMIT || length > sectors*4096-4 || offset+4+length > file.length()) return null;
            if ((compression & 128) != 0) return null;
            compressed = new byte[length-1]; file.readFully(compressed);
            file.seek(slot); if (file.readInt() != location) return null;
        }
        InputStream source = new ByteArrayInputStream(compressed);
        InputStream decoded = switch (compression) {
            case 1 -> new GZIPInputStream(source);
            case 2 -> new InflaterInputStream(source);
            case 3 -> source;
            default -> null;
        };
        if (decoded == null) return null;
        byte[] bytes;
        try (InputStream in = decoded) { bytes = in.readNBytes(LIMIT+1); }
        if (bytes.length > LIMIT) throw new IOException("chunk inflate limit");
        Map<?,?> root;
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (in.readUnsignedByte() != 10) return null;
            in.readUTF(); root = map(tag(in,10,0));
        }
        if (!Objects.equals(root.get("xPos"),cx) || !Objects.equals(root.get("zPos"),cz)) return null;
        if (!"minecraft:full".equals(root.get("Status")) && !"full".equals(root.get("Status"))) return null;
        if (!(root.get("sections") instanceof List<?> sections)) return null;
        int[] blocks = new int[16*384*16];
        for (Object raw : sections) {
            if (Thread.currentThread().isInterrupted()) throw new IOException("volume capture interrupted");
            Map<?,?> section = map(raw), states = map(section.get("block_states"));
            if (!(section.get("Y") instanceof Number yy) || !(states.get("palette") instanceof List<?> palette)) continue;
            int sectionY = yy.intValue();
            if (sectionY < -4 || sectionY > 19 || palette.isEmpty() || palette.size() > 4096) continue;
            String[] names = new String[palette.size()]; int[] colors = new int[names.length];
            for (int i=0; i<names.length; i++) {
                names[i] = String.valueOf(map(palette.get(i)).get("Name"));
                colors[i] = ignore(names[i]) ? 0 : 0xff000000 | color(names[i],fallback);
            }
            if (colors.length == 1 && colors[0] == 0) continue;
            Section values = new Section(sectionY,names,states.get("data") instanceof long[] data ? data : new long[0]);
            for (int y=0; y<16; y++) for (int z=0; z<16; z++) for (int x=0; x<16; x++)
                blocks[VolumeMesher.index(x,sectionY*16+y+64,z)] = colors[values.paletteIndex(x,y,z)];
        }
        // Try block-exact silhouettes first. Only complex geometry falls back
        // horizontally; vertical gaps/underhangs remain represented in all tiers.
        IOException last = null;
        for (int step : new int[]{1,2,4}) {
            try {
                DetailTile tile = VolumeMesher.mesh(cx,cz,blocks,step);
                tile.encode(); return tile;
            } catch (IOException budget) { last = budget; }
        }
        throw last;
    }

    static boolean ignore(String string) {
        return string.endsWith(":air") || string.endsWith(":cave_air") || string.endsWith(":void_air") || string.endsWith(":barrier") || string.contains("torch") || string.contains("_flower") || string.contains("_sapling") || string.endsWith(":grass") || string.contains("tall_grass") || string.contains("fern") || string.contains("vine");
    }

    static boolean vegetation(String string) {
        return string.contains("leaves") || string.contains("_log") || string.contains("_wood") || string.contains("bamboo") || string.contains("mangrove_roots");
    }

    static int color(String string, int n) {
        String local = string.substring(string.indexOf(':')+1);
        String[] dyes = {"white","orange","magenta","light_blue","yellow","lime","pink","gray","light_gray","cyan","purple","blue","brown","green","red","black"};
        int[] rgb = {0xe9ecec,0xf07613,0xbd44b3,0x3aaed8,0xf8c627,0x70b919,0xed8dac,0x474f52,0x9d9d97,0x158991,0x792aac,0x35399d,0x724728,0x546d1b,0xa12722,0x1d1c21};
        for (int i=0;i<dyes.length;i++) if (local.startsWith(dyes[i]+"_") &&
            (local.contains("wool") || local.contains("concrete") || local.contains("terracotta") || local.contains("glass") || local.contains("carpet"))) return rgb[i];
        if (local.contains("dark_oak") && !local.contains("leaves")) return 0x443020;
        if (local.contains("spruce") && !local.contains("leaves")) return 0x735435;
        if (local.contains("birch") && !local.contains("leaves")) return 0xc8b47a;
        if (local.contains("oak") && !local.contains("leaves")) return 0xa3834f;
        if (local.contains("deepslate") || local.contains("blackstone")) return 0x45454a;
        if (local.contains("chain") || local.contains("iron_bars")) return 0x909294;
        if (local.contains("lantern") || local.contains("glowstone") || local.contains("shroomlight")) return 0xe6b25a;

        if (string.contains("water")) {
            return 3234964;
        }
        if (string.contains("lava")) {
            return 14052123;
        }
        if (string.contains("snow") || string.contains("quartz")) {
            return 0xD9DFDF;
        }
        if (string.contains("ice")) {
            return 9747149;
        }
        if (string.contains("leaves") || string.contains("bamboo")) {
            if (string.contains("cherry")) {
                return 13206940;
            }
            int n2 = n >> 16 & 0xFF;
            int n3 = n >> 8 & 0xFF;
            int n4 = n & 0xFF;
            return n3 > n2 && n3 > n4 ? n : 4355124;
        }
        if (string.contains("grass") || string.contains("moss")) {
            return 6393145;
        }
        if (string.contains("sand")) {
            return string.contains("red") ? 12023609 : 13220483;
        }
        if (string.contains("dirt") || string.contains("mud")) {
            return 7954503;
        }
        if (string.contains("log") || string.contains("wood") || string.contains("planks")) {
            return 9072971;
        }
        if (string.contains("stone") || string.contains("cobble") || string.contains("andesite") || string.contains("gravel")) {
            return 8487293;
        }
        if (string.contains("glass")) {
            return 9614517;
        }
        if (string.contains("bricks")) {
            return 9792603;
        }
        return n;
    }

    static final class Section {
        final int y;
        final String[] palette;
        final long[] data;
        final int bits;
        final int per;

        Section(int n, String[] stringArray, long[] lArray) {
            this.y = n;
            this.palette = stringArray;
            this.data = lArray;
            this.bits = Math.max(4, 32 - Integer.numberOfLeadingZeros(stringArray.length - 1));
            this.per = 64 / this.bits;
        }

        int paletteIndex(int x, int y, int z) throws IOException {
            if (palette.length == 1) return 0;
            int cell = (y*16+z)*16+x;
            if (cell/per >= data.length) throw new IOException("palette storage");
            int value = (int)(data[cell/per] >>> ((cell%per)*bits) & ((1L<<bits)-1));
            if (value >= palette.length) throw new IOException("palette index");
            return value;
        }
    }
}
