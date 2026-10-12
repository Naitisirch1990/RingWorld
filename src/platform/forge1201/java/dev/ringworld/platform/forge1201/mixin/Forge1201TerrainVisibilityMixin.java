package dev.ringworld.platform.forge1201.mixin;

import dev.ringworld.platform.forge1201.Forge1201ClientTopology;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** A flat six-face occlusion graph cannot decide visibility on the curved ring.
 * Render distance and the transformed frustum continue to bound rendering.
 */
@Mixin(LevelRenderer.class)
abstract class Forge1201TerrainVisibilityMixin {
    @Redirect(method = "setupRender", at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/Minecraft;smartCull:Z", opcode = 180))
    private boolean ringworld1201$disableFlatOcclusion(Minecraft minecraft) {
        return Forge1201ClientTopology.activeGeometry() == null && minecraft.smartCull;
    }
}
