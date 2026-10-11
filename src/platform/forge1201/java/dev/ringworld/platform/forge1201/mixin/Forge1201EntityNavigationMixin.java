package dev.ringworld.platform.forge1201.mixin;

import dev.ringworld.platform.forge1201.Forge1201RingRuntime;
import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingNavigationAccess;
import dev.ringworld.world.RingTopology;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Projects AI path targets into the circumference image nearest the mob. */
@Mixin(PathNavigation.class)
abstract class Forge1201EntityNavigationMixin implements RingNavigationAccess {
    @Shadow @Final protected Mob mob;
    @Shadow @Final protected Level level;
    @Shadow protected Path path;
    @Shadow protected double speedModifier;
    @Shadow protected int tick;
    @Shadow protected int lastStuckCheck;
    @Shadow protected Vec3 lastStuckCheckPos;
    @Shadow protected Vec3i timeoutCachedNode;
    @Shadow protected long timeoutTimer;
    @Shadow protected long lastTimeoutCheck;
    @Shadow protected double timeoutLimit;
    @Shadow private BlockPos targetPos;
    @Shadow private boolean isStuck;

    @Override
    public void ringworld$foldPath(int deltaX) {
        if (deltaX == 0) return;
        if (targetPos != null) targetPos = targetPos.offset(deltaX, 0, 0);
        if (path != null) {
            List<Node> shiftedNodes = new ArrayList<>(path.getNodeCount());
            for (int index = 0; index < path.getNodeCount(); index++) {
                Node node = path.getNode(index);
                shiftedNodes.add(node.cloneAndMove(node.x + deltaX, node.y, node.z));
            }
            Path shiftedPath = new Path(shiftedNodes,
                    path.getTarget().offset(deltaX, 0, 0), path.canReach());
            shiftedPath.setNextNodeIndex(path.getNextNodeIndex());
            path = shiftedPath;
            if (!path.isDone()) {
                Vec3 next = path.getNextEntityPos(mob);
                mob.getMoveControl().setWantedPosition(next.x, next.y, next.z, speedModifier);
            }
        }

        // A chart fold is not physical movement. Reset raw-coordinate stuck
        // caches so the AI cannot compare positions from opposite ring images.
        lastStuckCheck = tick;
        lastStuckCheckPos = mob.position();
        timeoutCachedNode = Vec3i.ZERO;
        timeoutTimer = 0L;
        lastTimeoutCheck = 0L;
        timeoutLimit = 0.0;
        isStuck = false;
    }

    @ModifyVariable(
            method = "createPath(Ljava/util/Set;IZIF)Lnet/minecraft/world/level/pathfinder/Path;",
            at = @At("HEAD"), argsOnly = true)
    private Set<BlockPos> ringworld1201$nearestPathTargets(Set<BlockPos> targets) {
        if (!(level instanceof ServerLevel serverLevel) || targets.isEmpty()) return targets;
        RingGeometry geometry = Forge1201RingRuntime.geometryFor(serverLevel);
        if (geometry == null) return targets;

        RingTopology topology = new RingTopology(geometry);
        Set<BlockPos> projected = new LinkedHashSet<>(targets.size());
        boolean changed = false;
        for (BlockPos target : targets) {
            int imageX = topology.imageBlockNear(target.getX(), mob.getX());
            changed |= imageX != target.getX();
            projected.add(imageX == target.getX() ? target
                    : new BlockPos(imageX, target.getY(), target.getZ()));
        }
        return changed ? Set.copyOf(projected) : targets;
    }
}
