package dev.ringworld.platform.forge1201.mixin;

import dev.ringworld.platform.forge1201.Forge1201RingRuntime;
import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingPortalDestinationBounds;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import net.minecraft.BlockUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.portal.PortalForcer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes vanilla Nether-portal lookup periodic in X and keeps creation away from
 * the finite Z rims for every RingWorld dimension handled by PortalForcer.
 */
@Mixin(PortalForcer.class)
abstract class Forge1201PortalForcerMixin {
    @Shadow @Final protected ServerLevel level;

    @Inject(method = "findPortalAround", at = @At("HEAD"), cancellable = true)
    private void ringworld1201$findPeriodicPortal(
            BlockPos approximateExitPos, boolean destinationIsNether,
            WorldBorder worldBorder,
            CallbackInfoReturnable<Optional<BlockUtil.FoundRectangle>> cir) {
        RingGeometry geometry = Forge1201RingRuntime.geometryFor(level);
        if (geometry == null) return;

        BlockPos anchor = RingPortalDestinationBounds.normalizeSearchAnchor(geometry, approximateExitPos);
        int radius = destinationIsNether ? 16 : 128;
        PoiManager pois = level.getPoiManager();
        Set<BlockPos> candidates = new LinkedHashSet<>();

        for (BlockPos query : RingPortalDestinationBounds.periodicQueryAnchors(geometry, anchor)) {
            pois.ensureLoadedAndValid(level, query, radius);
            pois.getInSquare(type -> type.is(PoiTypes.NETHER_PORTAL), query, radius,
                            PoiManager.Occupancy.ANY)
                    .map(PoiRecord::getPos)
                    .map(pos -> new BlockPos(
                            geometry.wrapBlockX(pos.getX()), pos.getY(), pos.getZ()))
                    .filter(worldBorder::isWithinBounds)
                    .filter(pos -> RingPortalDestinationBounds.isSafePortalBlock(geometry, pos))
                    .filter(pos -> level.getBlockState(pos)
                            .hasProperty(BlockStateProperties.HORIZONTAL_AXIS))
                    .forEach(candidates::add);
        }

        Optional<BlockPos> closest = candidates.stream()
                .min(Comparator.<BlockPos>comparingDouble(pos ->
                                RingPortalDestinationBounds.periodicDistanceSquared(
                                        geometry, anchor, pos))
                        .thenComparingInt(BlockPos::getY));

        cir.setReturnValue(closest.map(pos -> {
            level.getChunkSource().addRegionTicket(
                    TicketType.PORTAL, new ChunkPos(pos), 3, pos);
            BlockState state = level.getBlockState(pos);
            return BlockUtil.getLargestRectangleAround(
                    pos,
                    state.getValue(BlockStateProperties.HORIZONTAL_AXIS),
                    21,
                    Direction.Axis.Y,
                    21,
                    candidate -> level.getBlockState(candidate) == state);
        }));
    }

    @ModifyVariable(method = "createPortal", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private BlockPos ringworld1201$normalizePortalCreationAnchor(BlockPos origin) {
        RingGeometry geometry = Forge1201RingRuntime.geometryFor(level);
        return geometry == null
                ? origin
                : RingPortalDestinationBounds.normalizeSearchAnchor(geometry, origin);
    }
}
