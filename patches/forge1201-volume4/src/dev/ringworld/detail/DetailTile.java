// SPDX-License-Identifier: MPL-2.0
package dev.ringworld.detail;

import java.io.*;
import java.util.zip.*;

/** Session-only, bounded surface mesh. No changes to saved world/Atlas formats. */
public final class DetailTile {
    public static final int MAGIC = 0x52445634, MAX_QUADS = 4096, MAX_PACKET = 24576;
    public final int x, z;
    // Retain the fields used by the unchanged client acceptance/helper bytecode.
    public final short[] top = new short[64], ground = new short[64];
    public final int[] color = new int[64];
    public final byte[] kind = new byte[64];
    // Each rectangle: x, y, z, extent along u, extent along v, direction, RGB.
    public int[] quads = new int[0];
    public DetailTile(int x, int z) { this.x = x; this.z = z; }
    public static long key(int x, int z) { return (long)x << 32 | (z & 0xffffffffL); }
    public long key() { return key(x, z); }

    public byte[] encode() throws IOException {
        validate();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(new DeflaterOutputStream(bytes))) {
            out.writeInt(MAGIC); out.writeInt(x); out.writeInt(z);
            out.writeShort(quads.length / 7);
            for (int i = 0; i < quads.length; i += 7) {
                out.writeByte(quads[i]); out.writeShort(quads[i+1]); out.writeByte(quads[i+2]);
                out.writeShort(quads[i+3]); out.writeShort(quads[i+4]); out.writeByte(quads[i+5]);
                out.writeByte(quads[i+6] >>> 16); out.writeShort(quads[i+6]);
            }
        }
        if (bytes.size() > MAX_PACKET) throw new IOException("volume packet budget");
        return bytes.toByteArray();
    }

    public static DetailTile decode(byte[] bytes) throws IOException {
        if (bytes.length < 8 || bytes.length > MAX_PACKET) throw new IOException("volume packet length");
        byte[] raw;
        try (InputStream in = new InflaterInputStream(new ByteArrayInputStream(bytes))) {
            raw = in.readNBytes(15 + MAX_QUADS * 12);
        }
        if (raw.length < 14 || raw.length > 14 + MAX_QUADS * 12) throw new IOException("volume inflate budget");
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(raw))) {
            if (in.readInt() != MAGIC) throw new IOException("volume protocol: update server and clients together");
            DetailTile tile = new DetailTile(in.readInt(), in.readInt());
            int count = in.readUnsignedShort();
            if (count > MAX_QUADS || raw.length != 14 + count * 12) throw new IOException("volume quad count");
            tile.quads = new int[count * 7];
            for (int i = 0; i < tile.quads.length; i += 7) {
                tile.quads[i] = in.readUnsignedByte(); tile.quads[i+1] = in.readShort();
                tile.quads[i+2] = in.readUnsignedByte(); tile.quads[i+3] = in.readUnsignedShort();
                tile.quads[i+4] = in.readUnsignedShort(); tile.quads[i+5] = in.readUnsignedByte();
                tile.quads[i+6] = (in.readUnsignedByte() << 16) | in.readUnsignedShort();
            }
            tile.validate(); return tile;
        }
    }

    private void validate() throws IOException {
        if (quads.length % 7 != 0 || quads.length > MAX_QUADS * 7) throw new IOException("volume quad budget");
        for (int i = 0; i < quads.length; i += 7) {
            int dir = quads[i+5];
            if (dir < 0 || dir > 5) throw new IOException("volume direction");
            int d = dir / 2, u = (d+1)%3, v = (d+2)%3;
            int[] start = {quads[i], quads[i+1], quads[i+2]};
            int[] low = {0, -64, 0}, high = {16, 320, 16};
            if (quads[i+3] < 1 || quads[i+4] < 1) throw new IOException("volume empty face");
            for (int a = 0; a < 3; a++) {
                int end = start[a] + (a == u ? quads[i+3] : a == v ? quads[i+4] : 0);
                if (start[a] < low[a] || start[a] > high[a] || end > high[a]) throw new IOException("volume coordinate");
            }
            if ((quads[i+6] & 0xff000000) != 0) throw new IOException("volume color");
        }
    }
}
