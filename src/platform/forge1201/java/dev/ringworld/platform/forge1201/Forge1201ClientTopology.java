package dev.ringworld.platform.forge1201;

import dev.ringworld.world.RingChunkCoordinates;
import dev.ringworld.world.RingGeometry;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;

/** Client-only projection helpers for the continuous RingWorld presentation chart. */
public final class Forge1201ClientTopology {
    private Forge1201ClientTopology() { }

    @Nullable
    public static RingGeometry activeGeometry() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return null;
        Forge1201ClientRingState.Settings settings = Forge1201ClientRingState.current();
        if (settings == null) return null;
        if (minecraft.level.dimension() == Level.OVERWORLD) return settings.overworldGeometry();
        if (minecraft.level.dimension() == Level.NETHER) return settings.netherGeometry();
        if (minecraft.level.dimension() == Level.END) return settings.endGeometry();
        return null;
    }

    public static int mapChunkX(int canonicalChunkX) {
        Minecraft minecraft = Minecraft.getInstance();
        RingGeometry geometry = activeGeometry();
        if (geometry == null || minecraft.player == null) return canonicalChunkX;
        int reference = Math.floorDiv((int)Math.floor(minecraft.player.getX()), 16);
        return RingChunkCoordinates.nearestImageChunkX(canonicalChunkX, reference, geometry);
    }

    public static double mapEntityX(double canonicalX, double referenceX) {
        RingGeometry geometry = activeGeometry();
        return geometry == null ? canonicalX : geometry.nearestImageX(canonicalX, referenceX);
    }

    public static BlockPos mapBlockPos(BlockPos canonical) {
        Minecraft minecraft = Minecraft.getInstance();
        RingGeometry geometry = activeGeometry();
        if (geometry == null || minecraft.player == null) return canonical;
        int x = (int)Math.floor(geometry.nearestImageX(canonical.getX(), minecraft.player.getX()));
        return x == canonical.getX() ? canonical : new BlockPos(x, canonical.getY(), canonical.getZ());
    }

    public static ChunkPos mapChunkPos(ChunkPos canonical) {
        int x = mapChunkX(canonical.x);
        return x == canonical.x ? canonical : new ChunkPos(x, canonical.z);
    }
}
