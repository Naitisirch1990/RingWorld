package dev.ringworld.platform.forge1201.mixin;

import dev.ringworld.platform.forge1201.Forge1201RingRuntime;
import dev.ringworld.world.RingGeometry;
import java.util.Optional;
import net.minecraft.BlockUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.portal.PortalInfo;
import net.minecraft.world.level.portal.PortalShape;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Replaces vanilla's 8:1 Nether coordinate scaling with the project's 2:1
 * circumferential mapping while preserving the common 2,000-block Z width.
 */
@Mixin(Entity.class)
abstract class Forge1201EntityPortalScaleMixin {
    @Shadow protected BlockPos portalEntrancePos;

    @Shadow
    protected abstract Vec3 getRelativePortalPosition(
            Direction.Axis portalAxis, BlockUtil.FoundRectangle portalRectangle);

    @Shadow
    protected abstract Optional<BlockUtil.FoundRectangle> getExitPortal(
            ServerLevel destination, BlockPos destinationPos,
            boolean destinationIsNether, WorldBorder worldBorder);

    @Inject(method = "findDimensionEntryPoint", at = @At("HEAD"), cancellable = true)
    private void ringworld1201$mapNetherPortalAtTwoToOne(
            ServerLevel destination, CallbackInfoReturnable<PortalInfo> cir) {
        Entity self = (Entity)(Object)this;
        if (!(self.level() instanceof ServerLevel source)) return;

        boolean overworldToNether = source.dimension() == Level.OVERWORLD
                && destination.dimension() == Level.NETHER;
        boolean netherToOverworld = source.dimension() == Level.NETHER
                && destination.dimension() == Level.OVERWORLD;
        if (!overworldToNether && !netherToOverworld) return;

        RingGeometry sourceGeometry = Forge1201RingRuntime.geometryFor(source);
        RingGeometry destinationGeometry = Forge1201RingRuntime.geometryFor(destination);
        if (sourceGeometry == null || destinationGeometry == null) return;

        double circumferenceScale = overworldToNether ? 0.5D : 2.0D;
        double mappedX = destinationGeometry.wrapX(self.getX() * circumferenceScale);
        double mappedZ = self.getZ();
        WorldBorder worldBorder = destination.getWorldBorder();
        BlockPos destinationAnchor = worldBorder.clampToBounds(
                mappedX, self.getY(), mappedZ);
        boolean destinationIsNether = destination.dimension() == Level.NETHER;

        PortalInfo info = getExitPortal(
                destination, destinationAnchor, destinationIsNether, worldBorder)
                .map(exitRectangle -> {
                    BlockState entranceState = self.level().getBlockState(portalEntrancePos);
                    Direction.Axis entranceAxis;
                    Vec3 relativePosition;
                    if (entranceState.hasProperty(BlockStateProperties.HORIZONTAL_AXIS)) {
                        entranceAxis = entranceState.getValue(BlockStateProperties.HORIZONTAL_AXIS);
                        BlockUtil.FoundRectangle entranceRectangle =
                                BlockUtil.getLargestRectangleAround(
                                        portalEntrancePos,
                                        entranceAxis,
                                        21,
                                        Direction.Axis.Y,
                                        21,
                                        pos -> self.level().getBlockState(pos) == entranceState);
                        relativePosition = getRelativePortalPosition(
                                entranceAxis, entranceRectangle);
                    } else {
                        entranceAxis = Direction.Axis.X;
                        relativePosition = new Vec3(0.5D, 0.0D, 0.0D);
                    }

                    return PortalShape.createPortalInfo(
                            destination,
                            exitRectangle,
                            entranceAxis,
                            relativePosition,
                            self,
                            self.getDeltaMovement(),
                            self.getYRot(),
                            self.getXRot());
                })
                .orElse(null);
        cir.setReturnValue(info);
    }
}
