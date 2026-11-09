// SPDX-License-Identifier: MPL-2.0
package dev.ringworld.detail;

import java.io.IOException;
import java.util.Arrays;

/** Greedy exterior rectangles: air is never extruded into a support column. */
public final class VolumeMesher {
    private VolumeMesher() {}
    public static int index(int x, int y, int z) { return (y*16 + z)*16 + x; }
    public static DetailTile mesh(int cx, int cz, int[] blocks, int step) throws IOException {
        if (blocks.length != 16*384*16 || (step != 1 && step != 2 && step != 4)) throw new IOException("volume input");
        int[] size = {16/step, 384, 16/step}, scale = {step, 1, step};
        int[] cells = new int[size[0]*size[1]*size[2]];
        for (int y = 0; y < 384; y++) for (int z = 0; z < size[2]; z++) for (int x = 0; x < size[0]; x++) {
            // Bounded coarse fallback samples an existing block, never invents
            // a filled vertical interval or bridges vertically separated layers.
            cells[(y*size[2]+z)*size[0]+x] = blocks[index(x*step+step/2, y, z*step+step/2)];
        }
        int[] faces = new int[DetailTile.MAX_QUADS*7]; int count = 0;
        for (int d = 0; d < 3; d++) {
            int u = (d+1)%3, v = (d+2)%3;
            long[] mask = new long[size[u]*size[v]];
            int[] pos = new int[3];
            for (int plane = 0; plane <= size[d]; plane++) {
                for (int j = 0; j < size[v]; j++) for (int i = 0; i < size[u]; i++) {
                    pos[u] = i; pos[v] = j; pos[d] = plane-1;
                    int a = plane == 0 ? 0 : at(cells, size, pos);
                    pos[d] = plane;
                    int b = plane == size[d] ? 0 : at(cells, size, pos);
                    mask[j*size[u]+i] = a != 0 && b == 0 ? ((a & 0xffffffL)+1) :
                        b != 0 && a == 0 ? -((b & 0xffffffL)+1) : 0;
                }
                for (int j = 0; j < size[v]; j++) for (int i = 0; i < size[u];) {
                    long value = mask[j*size[u]+i];
                    if (value == 0) { i++; continue; }
                    int w = 1;
                    while (i+w < size[u] && mask[j*size[u]+i+w] == value) w++;
                    int h = 1;
                    outer: while (j+h < size[v]) {
                        for (int k = 0; k < w; k++) if (mask[(j+h)*size[u]+i+k] != value) break outer;
                        h++;
                    }
                    if (count == DetailTile.MAX_QUADS) throw new IOException("volume quad budget");
                    pos[d] = plane*scale[d]; pos[u] = i*scale[u]; pos[v] = j*scale[v];
                    int q = count++*7;
                    faces[q] = pos[0]; faces[q+1] = pos[1]-64; faces[q+2] = pos[2];
                    faces[q+3] = w*scale[u]; faces[q+4] = h*scale[v];
                    faces[q+5] = d*2+(value > 0 ? 1 : 0);
                    faces[q+6] = (int)(Math.abs(value)-1);
                    for (int yy = 0; yy < h; yy++) Arrays.fill(mask, (j+yy)*size[u]+i, (j+yy)*size[u]+i+w, 0);
                    i += w;
                }
            }
        }
        DetailTile tile = new DetailTile(cx, cz); tile.quads = Arrays.copyOf(faces, count*7); return tile;
    }
    private static int at(int[] data, int[] size, int[] p) { return data[(p[1]*size[2]+p[2])*size[0]+p[0]]; }
}
