package dev.ringworld.platform.forge1201.mixin;

import dev.ringworld.platform.forge1201.Forge1201RingRuntime;
import dev.ringworld.world.RingGeometry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Uses the nearest ring image for explosion exposure rays and knockback. */
@Mixin(Explosion.class)
abstract class Forge1201ExplosionMixin {
    @Shadow @Final private Level level;
    @Shadow @Final private double x;

    @Redirect(
            method = "getSeenPercent",
            at = @At(value = "NEW",
                    target = "(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/level/ClipContext$Block;Lnet/minecraft/world/level/ClipContext$Fluid;Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/world/level/ClipContext;"))
    private static ClipContext ringworld1201$periodicExposureRay(
            Vec3 start, Vec3 end, ClipContext.Block shapeType,
            ClipContext.Fluid fluidHandling, Entity entity) {
        if (entity.level() instanceof ServerLevel serverLevel) {
            RingGeometry geometry = Forge1201RingRuntime.geometryFor(serverLevel);
            if (geometry != null) {
                end = new Vec3(geometry.nearestImageX(end.x, start.x), end.y, end.z);
            }
        }
        return new ClipContext(start, end, shapeType, fluidHandling, entity);
    }

    @Redirect(
            method = "explode",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;getX()D"))
    private double ringworld1201$periodicKnockbackX(Entity entity) {
        double entityX = entity.getX();
        if (!(level instanceof ServerLevel serverLevel)) return entityX;
        RingGeometry geometry = Forge1201RingRuntime.geometryFor(serverLevel);
        return geometry == null ? entityX : geometry.nearestImageX(entityX, x);
    }
}
