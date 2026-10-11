package dev.ringworld.platform.forge1201.mixin;

import dev.ringworld.platform.forge1201.Forge1201RingRuntime;
import dev.ringworld.world.RingGeometry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Routes server-side entity distance checks through the shortest ring image. */
@Mixin(Entity.class)
abstract class Forge1201EntityDistanceMixin {
    @Inject(method = "distanceToSqr(DDD)D", at = @At("HEAD"), cancellable = true)
    private void ringworld1201$squaredDistance(double x, double y, double z,
                                                CallbackInfoReturnable<Double> cir) {
        Entity self = (Entity)(Object)this;
        RingGeometry geometry = ringworld1201$geometry(self);
        if (geometry == null) return;
        double dx = geometry.shortestCircumferenceDelta(x, self.getX());
        double dy = self.getY() - y;
        double dz = self.getZ() - z;
        cir.setReturnValue(dx * dx + dy * dy + dz * dz);
    }

    @Inject(method = "distanceToSqr(Lnet/minecraft/world/phys/Vec3;)D", at = @At("HEAD"), cancellable = true)
    private void ringworld1201$squaredDistance(Vec3 position, CallbackInfoReturnable<Double> cir) {
        ringworld1201$squaredDistance(position.x, position.y, position.z, cir);
    }

    @Inject(method = "distanceTo", at = @At("HEAD"), cancellable = true)
    private void ringworld1201$distance(Entity other, CallbackInfoReturnable<Float> cir) {
        Entity self = (Entity)(Object)this;
        RingGeometry geometry = ringworld1201$geometry(self);
        if (geometry == null) return;
        double dx = geometry.shortestCircumferenceDelta(other.getX(), self.getX());
        double dy = self.getY() - other.getY();
        double dz = self.getZ() - other.getZ();
        cir.setReturnValue(Mth.sqrt((float)(dx * dx + dy * dy + dz * dz)));
    }

    private static RingGeometry ringworld1201$geometry(Entity entity) {
        return entity.level() instanceof ServerLevel level && level.dimension() == Level.OVERWORLD
                ? Forge1201RingRuntime.geometryFor(level) : null;
    }
}
