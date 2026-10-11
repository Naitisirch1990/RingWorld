package dev.ringworld.platform.forge1201.mixin;

import dev.ringworld.platform.forge1201.Forge1201GeneratorAccess;
import dev.ringworld.world.RingGeometry;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Stores Forge-1.20.1 RingWorld geometry on the vanilla noise generator. */
@Mixin(NoiseBasedChunkGenerator.class)
abstract class Forge1201NoiseGeneratorMixin implements Forge1201GeneratorAccess {
    @Unique private RingGeometry ringworld1201$geometry;
    @Unique private int ringworld1201$wallHeight;

    @Override
    public void ringworld1201$setGeometry(RingGeometry geometry) {
        this.ringworld1201$geometry = geometry;
    }

    @Override
    public RingGeometry ringworld1201$getGeometry() {
        return ringworld1201$geometry;
    }

    @Override
    public void ringworld1201$setWallHeight(int wallHeightBlocks) {
        this.ringworld1201$wallHeight = wallHeightBlocks;
    }

    @Override
    public int ringworld1201$getWallHeight() {
        return ringworld1201$wallHeight;
    }
}
