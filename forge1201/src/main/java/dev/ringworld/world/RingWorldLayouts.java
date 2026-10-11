package dev.ringworld.world;

/**
 * Immutable project defaults for the Forge 1.20.1 custom backport.
 * X is periodic circumference. Z is the finite band width.
 */
public final class RingWorldLayouts {
    public static final RingLayout OVERWORLD = new RingLayout("overworld", 61_440, 2_000);
    public static final RingLayout NETHER = new RingLayout("the_nether", 30_720, 2_000);
    public static final RingLayout END = new RingLayout("the_end", 61_440, 2_000);

    public static final int SIDE_WALL_THICKNESS_BLOCKS = 5;
    public static final int SIDE_WALL_ABOVE_TERRAIN_BLOCKS = 10;
    public static final int MEGA_POI_SPACING_BLOCKS = 4_096;
    public static final int MEGA_POI_JITTER_BLOCKS = 512;

    private RingWorldLayouts() {}

    public record RingLayout(String dimensionPath, int circumferenceBlocks, int widthBlocks) {
        public RingLayout {
            if (dimensionPath == null || dimensionPath.isBlank()) {
                throw new IllegalArgumentException("dimensionPath is required");
            }
            if (circumferenceBlocks < 1_024 || (circumferenceBlocks & 15) != 0) {
                throw new IllegalArgumentException("circumference must be a 16-block multiple >= 1024");
            }
            if (widthBlocks < 128 || (widthBlocks & 15) != 0) {
                throw new IllegalArgumentException("width must be a 16-block multiple >= 128");
            }
        }

        public double radius() {
            return circumferenceBlocks / (2.0 * Math.PI);
        }

        public int circumferenceChunks() {
            return circumferenceBlocks / 16;
        }

        public int widthChunks() {
            return widthBlocks / 16;
        }

        /**
         * The accepted width is exactly 2,000 blocks = 125 chunks. Because 125 is odd,
         * centering the block interval exactly on Z=0 would cut two edge chunks in half.
         * We instead center the 125 complete chunk rows on chunk Z=0. The playable band
         * is therefore -992..1007: still exactly 2,000 blocks, but generation and storage
         * never need partial boundary chunks.
         */
        public int minChunkZ() {
            return -(widthChunks() / 2);
        }

        public int maxChunkZ() {
            return minChunkZ() + widthChunks() - 1;
        }

        public int minWidthZ() {
            return minChunkZ() * 16;
        }

        public int maxWidthZ() {
            return (maxChunkZ() + 1) * 16 - 1;
        }

        public boolean isInsideWidth(double z) {
            return z >= minWidthZ() && z <= maxWidthZ() + 1.0;
        }

        public boolean isExteriorChunkZ(int chunkZ) {
            return chunkZ < minChunkZ() || chunkZ > maxChunkZ();
        }

        public int wrapBlockX(int x) {
            return Math.floorMod(x, circumferenceBlocks);
        }

        public int wrapChunkX(int chunkX) {
            return Math.floorMod(chunkX, circumferenceChunks());
        }

        public double wrapX(double x) {
            double result = x % circumferenceBlocks;
            return result < 0.0 ? result + circumferenceBlocks : result;
        }

        public double shortestCircumferenceDelta(double fromX, double toX) {
            double delta = wrapX(toX) - wrapX(fromX);
            if (delta > circumferenceBlocks / 2.0) delta -= circumferenceBlocks;
            if (delta < -circumferenceBlocks / 2.0) delta += circumferenceBlocks;
            return delta;
        }

        public double nearestImageX(double canonicalX, double referenceX) {
            double wrapped = wrapX(canonicalX);
            double imageIndex = Math.rint((referenceX - wrapped) / circumferenceBlocks);
            return wrapped + imageIndex * circumferenceBlocks;
        }
    }
}
