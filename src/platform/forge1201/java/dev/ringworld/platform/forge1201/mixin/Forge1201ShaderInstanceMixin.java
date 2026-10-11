package dev.ringworld.platform.forge1201.mixin;

import com.mojang.blaze3d.shaders.Uniform;
import dev.ringworld.platform.forge1201.Forge1201ClientTopology;
import dev.ringworld.world.RingGeometry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Supplies the custom uniforms declared only by RingWorld's terrain programs. */
@Mixin(ShaderInstance.class)
abstract class Forge1201ShaderInstanceMixin {
    @Inject(method = "apply", at = @At("HEAD"))
    private void ringworld1201$applyCurvatureUniforms(CallbackInfo ci) {
        ShaderInstance shader = (ShaderInstance)(Object)this;
        Uniform layout = shader.getUniform("RingWorldLayout");
        if (layout == null) return;

        RingGeometry geometry = Forge1201ClientTopology.activeGeometry();
        if (geometry == null) {
            layout.set(0, 0, 0, 0);
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        Vec3 camera = minecraft.gameRenderer.getMainCamera().getPosition();
        layout.set(1, geometry.circumferenceBlocks(), geometry.widthBlocks(), 0);

        Uniform vertical = shader.getUniform("RingWorldVertical");
        if (vertical != null) {
            vertical.set((float)geometry.surfaceReferenceY(),
                    (float)geometry.radius(), 0.0F, 0.0F);
        }
        Uniform cameraUniform = shader.getUniform("RingWorldCamera");
        if (cameraUniform != null) {
            cameraUniform.set((float)camera.x, (float)camera.y, (float)camera.z, 0.0F);
        }
    }
}
