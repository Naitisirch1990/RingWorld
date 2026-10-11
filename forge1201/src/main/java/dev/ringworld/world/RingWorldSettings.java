package dev.ringworld.world;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Forge 1.20.1 saved-data owner for the custom ring layout.
 * The Overworld data storage owns the immutable layout for all three dimensions.
 */
public final class RingWorldSettings extends SavedData {
    public static final String STORAGE_KEY = "ringworld_settings";
    public static final int FORMAT_VERSION = 1;

    private final long generatorSeed;
    private final RingWorldLayouts.RingLayout overworld;
    private final RingWorldLayouts.RingLayout nether;
    private final RingWorldLayouts.RingLayout end;

    private RingWorldSettings(long generatorSeed) {
        this(generatorSeed, RingWorldLayouts.OVERWORLD, RingWorldLayouts.NETHER, RingWorldLayouts.END);
        setDirty();
    }

    private RingWorldSettings(long generatorSeed,
                              RingWorldLayouts.RingLayout overworld,
                              RingWorldLayouts.RingLayout nether,
                              RingWorldLayouts.RingLayout end) {
        this.generatorSeed = generatorSeed;
        this.overworld = overworld;
        this.nether = nether;
        this.end = end;
        validateProjectLayout(overworld, nether, end);
    }

    public static RingWorldSettings get(ServerLevel level) {
        ServerLevel owner = level.getServer().overworld();
        return owner.getDataStorage().computeIfAbsent(
                RingWorldSettings::load,
                () -> new RingWorldSettings(owner.getSeed()),
                STORAGE_KEY);
    }

    static RingWorldSettings load(CompoundTag tag) {
        int format = tag.getInt("format");
        if (format != FORMAT_VERSION) {
            throw new IllegalStateException("Unsupported RingWorld 1.20.1 settings format " + format);
        }
        long seed = tag.getLong("seed");
        RingWorldLayouts.RingLayout overworld = readLayout(tag, "overworld", "overworld");
        RingWorldLayouts.RingLayout nether = readLayout(tag, "nether", "the_nether");
        RingWorldLayouts.RingLayout end = readLayout(tag, "end", "the_end");
        return new RingWorldSettings(seed, overworld, nether, end);
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("format", FORMAT_VERSION);
        tag.putLong("seed", generatorSeed);
        writeLayout(tag, "overworld", overworld);
        writeLayout(tag, "nether", nether);
        writeLayout(tag, "end", end);
        return tag;
    }

    public long generatorSeed() {
        return generatorSeed;
    }

    public RingWorldLayouts.RingLayout overworld() {
        return overworld;
    }

    public RingWorldLayouts.RingLayout nether() {
        return nether;
    }

    public RingWorldLayouts.RingLayout end() {
        return end;
    }

    private static void writeLayout(CompoundTag tag, String key,
                                    RingWorldLayouts.RingLayout layout) {
        CompoundTag value = new CompoundTag();
        value.putInt("circumference", layout.circumferenceBlocks());
        value.putInt("width", layout.widthBlocks());
        tag.put(key, value);
    }

    private static RingWorldLayouts.RingLayout readLayout(
            CompoundTag tag, String key, String dimensionPath) {
        CompoundTag value = tag.getCompound(key);
        return new RingWorldLayouts.RingLayout(
                dimensionPath,
                value.getInt("circumference"),
                value.getInt("width"));
    }

    private static void validateProjectLayout(
            RingWorldLayouts.RingLayout overworld,
            RingWorldLayouts.RingLayout nether,
            RingWorldLayouts.RingLayout end) {
        requireExact("Overworld", overworld, RingWorldLayouts.OVERWORLD);
        requireExact("Nether", nether, RingWorldLayouts.NETHER);
        requireExact("End", end, RingWorldLayouts.END);
    }

    private static void requireExact(String label,
                                     RingWorldLayouts.RingLayout actual,
                                     RingWorldLayouts.RingLayout expected) {
        if (actual.circumferenceBlocks() != expected.circumferenceBlocks()
                || actual.widthBlocks() != expected.widthBlocks()) {
            throw new IllegalStateException(label + " RingWorld dimensions are immutable: saved="
                    + actual.circumferenceBlocks() + "x" + actual.widthBlocks()
                    + ", expected=" + expected.circumferenceBlocks() + "x" + expected.widthBlocks());
        }
    }
}
