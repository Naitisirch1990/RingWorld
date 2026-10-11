package dev.ringworld.world;

import java.util.function.Consumer;
import net.minecraft.world.level.ChunkPos;

/**
 * Loader/version-neutral chunk watch window used by RingWorld topology.
 *
 * <p>Minecraft 1.21.x exposes this shape as {@code ChunkTrackingView}, while
 * 1.20.1 does not. Keeping the periodic window contract here lets the 1.20.1
 * Forge adapter translate to its older chunk-tracking implementation without
 * leaking a version-specific server type into the shared topology code.</p>
 */
public interface RingChunkView {
    boolean contains(int x, int z, boolean includeEdge);

    default boolean contains(int x, int z) {
        return contains(x, z, true);
    }

    void forEach(Consumer<ChunkPos> consumer);
}
