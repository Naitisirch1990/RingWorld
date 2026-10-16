package dev.ringworldbiomes.world;

public final class RingMapLayout {
    public static final int CIRCUMFERENCE_CHUNKS = 3840;
    private static final int[] SEGMENT_LENGTHS = {275, 275, 275, 275, 274, 274, 274, 274, 274, 274, 274, 274, 274, 274};
    private static final String[] BIOMES = {
        "minecraft:savanna", "minecraft:bamboo_jungle", "minecraft:cherry_grove", "minecraft:dark_forest",
        "minecraft:deep_ocean", "minecraft:desert", "minecraft:ice_spikes", "minecraft:mushroom_fields",
        "minecraft:taiga", "minecraft:meadow", "minecraft:mangrove_swamp", "minecraft:jungle",
        "minecraft:flower_forest", "minecraft:jagged_peaks"
    };
    private static final int[] STARTS = new int[SEGMENT_LENGTHS.length];

    static {
        int offset = 0;
        for (int i = 0; i < SEGMENT_LENGTHS.length; i++) {
            STARTS[i] = offset;
            offset += SEGMENT_LENGTHS[i];
        }
        if (offset != CIRCUMFERENCE_CHUNKS) throw new IllegalStateException("Invalid ring segment allocation");
    }

    private RingMapLayout() {}

    public static int segmentForChunk(int chunkX) {
        int x = Math.floorMod(chunkX, CIRCUMFERENCE_CHUNKS);
        for (int i = SEGMENT_LENGTHS.length - 1; i >= 0; i--) {
            if (x >= STARTS[i]) return i;
        }
        return 0;
    }

    public static String biomeIdForChunk(int chunkX) {
        return BIOMES[segmentForChunk(chunkX)];
    }

    public static int wallLocalX(int chunkX) {
        int x = Math.floorMod(chunkX, CIRCUMFERENCE_CHUNKS);
        for (int boundary : STARTS) {
            if (x == boundary) return 0;
        }
        return -1;
    }

    public static int wallColorIndex(int chunkX) {
        return segmentForChunk(chunkX);
    }
}
