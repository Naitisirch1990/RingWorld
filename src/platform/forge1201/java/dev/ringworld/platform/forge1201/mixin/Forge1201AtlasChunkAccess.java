package dev.ringworld.platform.forge1201.mixin;

import java.util.concurrent.CompletableFuture;
import com.mojang.datafixers.util.Either;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** The public 1.20.1 getChunkFuture managed-blocks on the server thread. */
@Mixin(ServerChunkCache.class)
public interface Forge1201AtlasChunkAccess {
    @Invoker("getChunkFutureMainThread")
    CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> ringworld$atlasFuture(
            int x, int z, ChunkStatus status, boolean create);
}
