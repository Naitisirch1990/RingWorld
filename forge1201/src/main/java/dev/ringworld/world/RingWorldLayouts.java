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

        public int circumferenceChunks() {
            return circumferenceBlocks / 16;
        }

        public int widthChunks() {
            return widthBlocks / 16;
        }

        public int minWidthZ() {
            return -widthBlocks / 2;
        }

        public int maxWidthZ() {
            return minWidthZ() + widthBlocks - 1;
        }

        public int wrapBlockX(int x) {
            return Math.floorMod(x, circumferenceBlocks);
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
