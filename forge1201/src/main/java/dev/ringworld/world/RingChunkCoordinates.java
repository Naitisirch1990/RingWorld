package dev.ringworld.world;

/** Canonical chunk-index operations for the periodic circumference axis. */
public final class RingChunkCoordinates {
    private RingChunkCoordinates() {}

    public static int wrapChunkX(int chunkX, RingWorldLayouts.RingLayout layout) {
        return Math.floorMod(chunkX, layout.circumferenceChunks());
    }

    /**
     * Mirrors vanilla's square simulation-distance test while joining the two
     * circumference edges.
     */
    public static boolean isWithinSimulationDistance(
            int entityChunkX, int entityChunkZ,
            int playerChunkX, int playerChunkZ,
            int simulationDistance,
            RingWorldLayouts.RingLayout layout) {
        int circumference = layout.circumferenceChunks();
        int canonicalEntityX = Math.floorMod(entityChunkX, circumference);
        int canonicalPlayerX = Math.floorMod(playerChunkX, circumference);
        int directX = Math.abs(canonicalEntityX - canonicalPlayerX);
        int periodicX = Math.min(directX, circumference - directX);
        long deltaZ = Math.abs((long) entityChunkZ - playerChunkZ);
        return periodicX <= simulationDistance && deltaZ <= simulationDistance;
    }

    /**
     * Returns the periodic copy of a canonical chunk nearest a client's logical
     * chunk coordinate. Server storage stays finite while presentation remains
     * continuous through any number of seam crossings.
     */
    public static int nearestImageChunkX(
            int canonicalChunkX,
            int referenceChunkX,
            RingWorldLayouts.RingLayout layout) {
        int circumference = layout.circumferenceChunks();
        int wrapped = Math.floorMod(canonicalChunkX, circumference);
        long imageIndex = Math.round((referenceChunkX - (double) wrapped) / circumference);
        long result = wrapped + imageIndex * (long) circumference;
        if (result < Integer.MIN_VALUE || result > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("ring chunk image exceeds vanilla coordinate range");
        }
        return (int) result;
    }
}
