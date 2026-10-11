package dev.ringworld.platform.forge1201.mixin;

import dev.ringworld.platform.forge1201.Forge1201RingRuntime;
import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingTopology;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.entity.LevelEntityGetter;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Splits server entity lookup boxes that cross the periodic X seam.
 *
 * <p>Distance hooks alone are not enough: vanilla first discovers candidate
 * entities through a flat AABB query. Without this adapter, AI, collisions,
 * targeting and many modded systems can miss an entity only a few blocks away
 * on the opposite side of X=0/C.</p>
 */
@Mixin(Level.class)
abstract class Forge1201WorldEntityLookupMixin {
    @Shadow protected abstract LevelEntityGetter<Entity> getEntities();

    @Inject(method = "getEntities", at = @At("HEAD"), cancellable = true)
    private void ringworld1201$periodicEntities(
            Entity except, AABB box, Predicate<? super Entity> predicate,
            CallbackInfoReturnable<List<Entity>> cir) {
        RingTopology topology = ringworld1201$topologyFor(box);
        if (topology == null) return;

        List<Entity> result = new ArrayList<>();
        Set<Entity> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (RingTopology.QueryWindow window : topology.canonicalWindows(box)) {
            getEntities().get(window.canonicalBox(), entity -> {
                if (entity != except && seen.add(entity) && predicate.test(entity)) {
                    result.add(entity);
                }
            });
        }
        cir.setReturnValue(result);
    }

    @Inject(
            method = "getEntities(Lnet/minecraft/world/level/entity/EntityTypeTest;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;Ljava/util/List;I)V",
            at = @At("HEAD"), cancellable = true)
    private <T extends Entity> void ringworld1201$periodicTypedEntities(
            EntityTypeTest<Entity, T> filter, AABB box,
            Predicate<? super T> predicate, List<? super T> result, int limit,
            CallbackInfo ci) {
        RingTopology topology = ringworld1201$topologyFor(box);
        if (topology == null) return;

        Set<T> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (RingTopology.QueryWindow window : topology.canonicalWindows(box)) {
            getEntities().get(filter, window.canonicalBox(), entity -> {
                if (seen.add(entity) && predicate.test(entity)) result.add(entity);
                return result.size() >= limit
                        ? AbortableIterationConsumer.Continuation.ABORT
                        : AbortableIterationConsumer.Continuation.CONTINUE;
            });
            if (result.size() >= limit) break;
        }
        ci.cancel();
    }

    private RingTopology ringworld1201$topologyFor(AABB box) {
        if (!((Object)this instanceof ServerLevel level)) return null;
        RingGeometry geometry = Forge1201RingRuntime.geometryFor(level);
        if (geometry == null) return null;
        if (box.minX >= 0.0 && box.maxX <= geometry.circumferenceBlocks()) return null;
        return new RingTopology(geometry);
    }
}
