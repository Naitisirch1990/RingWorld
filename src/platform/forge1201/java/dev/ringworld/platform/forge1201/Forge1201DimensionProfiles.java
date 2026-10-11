package dev.ringworld.platform.forge1201;

import dev.ringworld.world.RingGeometry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/** Fixed project geometry for the three vanilla dimensions in the 1.20.1 port. */
public final class Forge1201DimensionProfiles {
    public static final int WIDTH_BLOCKS = 2_000;
    public static final int OVERWORLD_CIRCUMFERENCE = 61_440;
    public static final int NETHER_CIRCUMFERENCE = 30_720;
    public static final int END_CIRCUMFERENCE = 61_440;
    public static final int LEGACY_WALL_HEIGHT_BLOCKS = 160;

    private static final RingGeometry NETHER_GEOMETRY =
            new RingGeometry(WIDTH_BLOCKS, NETHER_CIRCUMFERENCE);
    private static final RingGeometry END_GEOMETRY =
            new RingGeometry(WIDTH_BLOCKS, END_CIRCUMFERENCE);

    private Forge1201DimensionProfiles() { }

    public static boolean isRingDimension(ServerLevel level) {
        return level.dimension() == Level.OVERWORLD
                || level.dimension() == Level.NETHER
                || level.dimension() == Level.END;
    }

    /** Overworld geometry is persisted in RingWorldSettings; the other two are fixed project profiles. */
    public static RingGeometry fixedGeometry(ServerLevel level) {
        if (level.dimension() == Level.NETHER) return NETHER_GEOMETRY;
        if (level.dimension() == Level.END) return END_GEOMETRY;
        return null;
    }

    public static String diagnosticName(ServerLevel level) {
        if (level.dimension() == Level.OVERWORLD) return "overworld";
        if (level.dimension() == Level.NETHER) return "nether";
        if (level.dimension() == Level.END) return "end";
        return level.dimension().location().toString();
    }
}
