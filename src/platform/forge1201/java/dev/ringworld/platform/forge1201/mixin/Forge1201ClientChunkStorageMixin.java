package dev.ringworld.platform.forge1201.mixin;

import dev.ringworld.platform.forge1201.client.Forge1201ClientChunkMapAccess;
import dev.ringworld.platform.forge1201.client.Forge1201ClientChunkMaps;
import java.util.concurrent.atomic.AtomicReferenceArray;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Allows explicit long teleports to discard chunks stored in the previous presentation chart. */
@Mixin(targets = "net.minecraft.client.multiplayer.ClientChunkCache$Storage")
abstract class Forge1201ClientChunkStorageMixin implements Forge1201ClientChunkMapAccess {
    @Shadow @Final private AtomicReferenceArray<LevelChunk> chunks;
    @Shadow private volatile int viewCenterX;
    @Shadow private volatile int viewCenterZ;
    @Shadow protected abstract LevelChunk replace(int index, LevelChunk expected, LevelChunk replacement);

    /** The non-static 1.20.1 inner constructor exposes its synthetic owner as the first JVM argument. */
    @Inject(method = "<init>", at = @At("TAIL"))
    private void ringworld1201$register(ClientChunkCache owner, int radius, CallbackInfo ci) {
        Forge1201ClientChunkMaps.register(owner, this);
    }

    @Override
    public int ringworld1201$centerChunkX() {
        return viewCenterX;
    }

    @Override
    public int ringworld1201$centerChunkZ() {
        return viewCenterZ;
    }

    @Override
    public void ringworld1201$clearAllChunks() {
        for (int index = 0; index < chunks.length(); index++) {
            LevelChunk chunk = chunks.get(index);
            if (chunk != null) replace(index, chunk, null);
        }
    }
}
