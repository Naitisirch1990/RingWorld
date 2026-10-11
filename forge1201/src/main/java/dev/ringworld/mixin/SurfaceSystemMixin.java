package dev.ringworld.mixin;

import dev.ringworld.world.RingSurfaceSamplingContext;
import net.minecraft.world.level.levelgen.SurfaceSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/** Redirects vanilla surface-only noise/random coordinates through the ring mapping. */
@Mixin(SurfaceSystem.class)
public abstract class SurfaceSystemMixin {
    @ModifyArgs(
            method = {"getSurfaceDepth", "getSurfaceSecondary", "getBand"},
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/levelgen/synth/NormalNoise;getValue(DDD)D"))
    private void ringworld$mapUnitNoise(Args args) {
        ringworld$mapNoise(args, 1.0);
    }

    @ModifyArgs(
            method = "erodedBadlandsExtension",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/levelgen/synth/NormalNoise;getValue(DDD)D",
                    ordinal = 0))
    private void ringworld$mapBadlandsSurface(Args args) {
        ringworld$mapNoise(args, 1.0);
    }

    @ModifyArgs(
            method = "erodedBadlandsExtension",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/levelgen/synth/NormalNoise;getValue(DDD)D",
                    ordinal = 1))
    private void ringworld$mapBadlandsPillar(Args args) {
        ringworld$mapNoise(args, 0.2);
    }

    @ModifyArgs(
            method = "erodedBadlandsExtension",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/levelgen/synth/NormalNoise;getValue(DDD)D",
                    ordinal = 2))
    private void ringworld$mapBadlandsRoof(Args args) {
        ringworld$mapNoise(args, 0.75);
    }

    @ModifyArgs(
            method = "frozenOceanExtension",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/levelgen/synth/NormalNoise;getValue(DDD)D",
                    ordinal = 0))
    private void ringworld$mapIcebergSurface(Args args) {
        ringworld$mapNoise(args, 1.0);
    }

    @ModifyArgs(
            method = "frozenOceanExtension",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/levelgen/synth/NormalNoise;getValue(DDD)D",
                    ordinal = 1))
    private void ringworld$mapIcebergPillar(Args args) {
        ringworld$mapNoise(args, 1.28);
    }

    @ModifyArgs(
            method = "frozenOceanExtension",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/levelgen/synth/NormalNoise;getValue(DDD)D",
                    ordinal = 2))
    private void ringworld$mapIcebergRoof(Args args) {
        ringworld$mapNoise(args, 1.17);
    }

    @ModifyArgs(
            method = {"getSurfaceDepth", "frozenOceanExtension"},
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/levelgen/PositionalRandomFactory;at(III)Lnet/minecraft/util/RandomSource;"))
    private void ringworld$mapSurfaceRandom(Args args) {
        RingSurfaceSamplingContext.BlockCoordinates mapped =
                RingSurfaceSamplingContext.mapBlock(args.get(0), args.get(2));
        args.set(0, mapped.x());
        args.set(2, mapped.z());
    }

    private static void ringworld$mapNoise(Args args, double scale) {
        RingSurfaceSamplingContext.Coordinates mapped =
                RingSurfaceSamplingContext.mapScaled(args.get(0), args.get(2), scale);
        args.set(0, mapped.x());
        args.set(2, mapped.z());
    }
}
