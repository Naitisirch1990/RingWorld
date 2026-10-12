package dev.ringworld.platform.forge1201.mixin;

import dev.ringworld.platform.forge1201.Forge1201ClientTopology;
import dev.ringworld.world.RingGeometry;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Exterior chunks are intentionally absent and must not block the rim's mesh. */
@Mixin(ChunkRenderDispatcher.RenderChunk.class)
abstract class Forge1201RimChunkRenderMixin {
    @Shadow private boolean doesChunkExistAt(BlockPos pos) { throw new AssertionError(); }
    @Redirect(method = "hasAllNeighbors", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/chunk/ChunkRenderDispatcher$RenderChunk;doesChunkExistAt(Lnet/minecraft/core/BlockPos;)Z"))
    private boolean ringworld1201$allowExterior(ChunkRenderDispatcher.RenderChunk section, BlockPos pos) {
        RingGeometry geometry = Forge1201ClientTopology.activeGeometry();
        if (geometry != null && geometry.isExteriorChunkZ(Math.floorDiv(pos.getZ(), 16))) return true;
        return doesChunkExistAt(pos);
    }
}
