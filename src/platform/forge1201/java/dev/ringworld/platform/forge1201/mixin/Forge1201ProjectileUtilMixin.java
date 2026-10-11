package dev.ringworld.platform.forge1201.mixin;

import dev.ringworld.platform.forge1201.Forge1201RingRuntime;
import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingTopology;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes vanilla projectile/entity raycasts see the nearest periodic image of
 * seam-adjacent hitboxes on Forge 1.20.1 in every configured ring dimension.
 * This is the generic foundation used by arrows and by gun mods that ultimately
 * delegate entity collision to ProjectileUtil.
 */
@Mixin(ProjectileUtil.class)
abstract class Forge1201ProjectileUtilMixin {
    @Inject(
            method = "getEntityHitResult(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;F)Lnet/minecraft/world/phys/EntityHitResult;",
            at = @At("HEAD"), cancellable = true)
    private static void ringworld1201$periodicEntityCollision(
            Level world, Entity source, Vec3 start, Vec3 end, AABB query,
            Predicate<Entity> predicate, float margin,
            CallbackInfoReturnable<EntityHitResult> cir) {
        if (!(world instanceof ServerLevel serverLevel)) return;

        RingGeometry geometry = Forge1201RingRuntime.geometryFor(serverLevel);
        if (geometry == null) return;
        RingTopology topology = new RingTopology(geometry);

        double nearestDistance = Double.MAX_VALUE;
        Entity nearestEntity = null;
        Vec3 nearestHit = null;

        // A vanilla entity query only sees the canonical storage plane. When a
        // projectile ray crosses X=0/C its chart-space query box can live partly
        // in the neighbouring periodic image, so split that box into canonical
        // windows before asking the level for candidates. Identity de-duplication
        // prevents a very wide query from testing the same entity twice.
        Set<Entity> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        for (RingTopology.QueryWindow window : topology.canonicalWindows(query)) {
            for (Entity candidate : world.getEntities(source, window.canonicalBox(), predicate)) {
                if (!visited.add(candidate)) continue;
                AABB hitbox = topology.projectBoxNear(candidate.getBoundingBox(), source.getX()).inflate(margin);
                Optional<Vec3> hit = hitbox.clip(start, end);
                if (hit.isEmpty()) continue;
                double distance = start.distanceToSqr(hit.get());
                if (distance < nearestDistance) {
                    nearestDistance = distance;
                    nearestEntity = candidate;
                    nearestHit = hit.get();
                }
            }
        }
        cir.setReturnValue(nearestEntity == null ? null : new EntityHitResult(nearestEntity, nearestHit));
    }
}
