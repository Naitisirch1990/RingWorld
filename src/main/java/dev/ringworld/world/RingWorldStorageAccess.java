package dev.ringworld.world;

import java.nio.file.Path;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Read-only access to Minecraft's authoritative per-dimension storage path.
 *
 * <p>Minecraft 26.1 keeps the {@code LevelStorageAccess} handle protected on
 * the server. Storage owners must use this bridge instead of reconstructing
 * dimension folder names or treating the world root as the Overworld.</p>
 */
public interface RingWorldStorageAccess {
    Path ringworld$getDimensionPath(ResourceKey<Level> dimension);

    static Path dimensionPath(ServerLevel level) {
        // Newer loader builds inject this bridge into MinecraftServer. The
        // Forge 1.20.1 bootstrap intentionally excludes that 1.21-specific
        // mixin; use vanilla's dimension folder mapping instead of casting
        // the dedicated server to an interface it does not implement.
        if (level.getServer() instanceof RingWorldStorageAccess access) {
            return access.ringworld$getDimensionPath(level.dimension());
        }
        return DimensionType.getStorageFolder(
                level.dimension(), level.getServer().getWorldPath(LevelResource.ROOT));
    }
}
