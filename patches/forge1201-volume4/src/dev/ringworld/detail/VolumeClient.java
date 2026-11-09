// SPDX-License-Identifier: MPL-2.0
package dev.ringworld.detail;

import com.mojang.blaze3d.vertex.*;
import dev.ringworld.world.RingGeometry;
import java.util.ArrayList;
import java.util.List;

/** Render-thread upload using the existing session, dirty queue and coverage map. */
public final class VolumeClient {
    public static void rebuild(long group, RingGeometry geometry) {
        int gx = (int)(group >> 32)*4, gz = (int)group*4;
        List<DetailTile> tiles = new ArrayList<>(); int count = 0;
        for (int z = gz; z < gz+4; z++) for (int x = gx; x < gx+4; x++) {
            DetailTile tile = DetailClient.tiles.get(DetailTile.key(x, z));
            if (tile != null) { tiles.add(tile); count += tile.quads.length/7; }
        }
        VertexBuffer old = DetailClient.meshes.get(group);
        if (tiles.isEmpty()) {
            if (old != null) { DetailClient.meshes.remove(group); old.close(); }
            return;
        }
        VertexBuffer fresh = null;
        if (count != 0) {
            BufferBuilder b = Tesselator.m_85913_().m_85915_();
            b.m_166779_(VertexFormat.Mode.QUADS, DefaultVertexFormat.f_85815_);
            for (DetailTile tile : tiles) for (int i = 0; i < tile.quads.length; i += 7) {
                int[] q = tile.quads; int d = q[i+5]/2, u = (d+1)%3, v = (d+2)%3;
                double[] p = {tile.x*16.0+q[i], q[i+1], tile.z*16.0+q[i+2]};
                // Exterior strip at finite Z is clipped to the saved width.
                double du=q[i+3], dv=q[i+4];
                double z0=p[2], z1=p[2]+(u == 2 ? du : v == 2 ? dv : 0);
                double min=geometry.minWidthZ(), max=geometry.maxWidthZ()+1;
                if (z1 < min || z0 > max) continue;
                if (u == 2 || v == 2) {
                    p[2]=Math.max(z0,min); double length=Math.min(z1,max)-p[2];
                    if(length<=0)continue;
                    if(u==2)du=length;else dv=length;
                }
                double shade = d == 1 ? (q[i+5]%2 == 1 ? 1.0 : .60) : d == 0 ? .72 : .85;
                DetailClient.v(b, p[0], p[1], p[2], q[i+6], shade);
                p[u] += du; DetailClient.v(b, p[0], p[1], p[2], q[i+6], shade);
                p[v] += dv; DetailClient.v(b, p[0], p[1], p[2], q[i+6], shade);
                p[u] -= du; DetailClient.v(b, p[0], p[1], p[2], q[i+6], shade);
            }
            BufferBuilder.RenderedBuffer built = b.m_231175_();
            boolean submitted=false;
            try {
                fresh = new VertexBuffer(VertexBuffer.Usage.STATIC);
                fresh.m_85921_(); submitted=true; fresh.m_231221_(built);
            }
            catch (RuntimeException | Error failure) {
                if(!submitted)built.m_231200_();
                if(fresh!=null)fresh.close();throw failure;
            }
            finally { VertexBuffer.m_85931_(); }
        }
        // Publish coverage only after a matching successful GPU upload. Empty
        // observed chunks also suppress the coarse surface that invented matter.
        if (fresh != null) DetailClient.meshes.put(group, fresh); else DetailClient.meshes.remove(group);
        if (old != null) old.close();
        for (DetailTile tile : tiles) DetailClient.coverage(tile.x, tile.z, true);
    }
}
