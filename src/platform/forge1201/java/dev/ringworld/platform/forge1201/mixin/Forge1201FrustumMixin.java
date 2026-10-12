package dev.ringworld.platform.forge1201.mixin;

import dev.ringworld.platform.forge1201.Forge1201ClientTopology;
import dev.ringworld.platform.forge1201.client.Forge1201CurvedProjection;
import dev.ringworld.world.RingGeometry;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** CPU visibility must use the same cylindrical envelope as terrain and entity rendering. */
@Mixin(Frustum.class)
abstract class Forge1201FrustumMixin {
    @Shadow private double camX;
    @Shadow private double camY;
    @Shadow private double camZ;
    @ModifyVariable(method = "isVisible", at = @At("HEAD"), argsOnly = true)
    private AABB ringworld1201$curveBounds(AABB box) {
        RingGeometry geometry = Forge1201ClientTopology.activeGeometry();
        if (geometry == null || !Double.isFinite(box.minX) || !Double.isFinite(box.maxX)
                || !Double.isFinite(box.minY) || !Double.isFinite(box.maxY)
                || !Double.isFinite(box.minZ) || !Double.isFinite(box.maxZ)) return box;
        Vec3 camera = new Vec3(camX, camY, camZ);
        return Forge1201CurvedProjection.toCameraLocalBounds(geometry, box, camera).move(camera);
    }
}
