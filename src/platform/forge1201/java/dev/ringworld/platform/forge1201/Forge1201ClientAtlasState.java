package dev.ringworld.platform.forge1201;

import dev.ringworld.RingWorldMod;
import dev.ringworld.world.RingTerrainAtlas;
import java.io.IOException;
import java.util.BitSet;

/** Client-session storage independent of ClientLevel, so respawn does not erase the map. */
public final class Forge1201ClientAtlasState {
    private static RingTerrainAtlas atlas;
    private static long session;
    private static final BitSet dirty = new BitSet();
    public static RingTerrainAtlas atlas() { return atlas; }
    public static int nextDirty() { int i = dirty.nextSetBit(0); if (i >= 0) dirty.clear(i); return i; }
    public static void clear() { atlas = null; session = 0; dirty.clear(); }
    public static void accept(Forge1201AtlasPacket p) {
        var settings = Forge1201ClientRingState.current();
        if (settings == null) return;
        // Negative X is an explicit reset, sent before tiles on the ordered play channel.
        if (p.tileX() == -1) {
            clear(); session = p.session();
            atlas = new RingTerrainAtlas(settings.overworldGeometry(), session);
            return;
        }
        if (atlas == null || session != p.session() || p.tileX() < 0 || p.tileZ() < 0
                || p.tileX() >= atlas.tileColumns() || p.tileZ() >= atlas.tileRows()) return;
        try {
            atlas.applyTile(p.tileX(), p.tileZ(), p.data());
            // GPU groups contain eight adjacent X tiles and the full width.
            dirty.set(p.tileX() / 8);
            if(p.tileX()%8==0 && p.tileX()>0) dirty.set(p.tileX()/8-1);
            if(p.tileX()%8==7 && p.tileX()+1<atlas.tileColumns()) dirty.set(p.tileX()/8+1);
        } catch (IOException | IllegalArgumentException e) {
            RingWorldMod.LOGGER.warn("Rejected invalid Forge terrain atlas tile", e);
        }
    }
}
