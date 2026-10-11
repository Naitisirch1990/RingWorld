package dev.ringworld.platform.forge1201.mixin;

import dev.ringworld.world.RingCoordinateDensityFunction;
import org.spongepowered.asm.mixin.Mixin;

/** Tags vanilla density leaves that consume horizontal coordinates. */
@Mixin(targets = {
        "net.minecraft.world.level.levelgen.DensityFunctions$Noise",
        "net.minecraft.world.level.levelgen.DensityFunctions$Shift",
        "net.minecraft.world.level.levelgen.DensityFunctions$ShiftA",
        "net.minecraft.world.level.levelgen.DensityFunctions$ShiftB",
        "net.minecraft.world.level.levelgen.DensityFunctions$ShiftedNoise",
        "net.minecraft.world.level.levelgen.DensityFunctions$WeirdScaledSampler",
        "net.minecraft.world.level.levelgen.DensityFunctions$EndIslandDensityFunction"
})
abstract class Forge1201DensityCoordinateConsumerMixin implements RingCoordinateDensityFunction { }
