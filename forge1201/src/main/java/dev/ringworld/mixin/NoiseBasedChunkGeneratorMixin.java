package dev.ringworld.mixin;

import dev.ringworld.world.RingTerrainNoiseMapping;
import dev.ringworld.world.RingWorldGeneratorAccess;
import dev.ringworld.world.RingWorldLayouts;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * First Minecraft-ABI hook of the 1.20.1 backport. This mixin deliberately
 * stores policy only; later mixins will consume it in density, surface,
 * carver, structure, and finite-boundary generation paths.
 */
@Mixin(NoiseBasedChunkGenerator.class)
public abstract class NoiseBasedChunkGeneratorMixin implements RingWorldGeneratorAccess {
    @Unique
    private RingWorldLayouts.RingLayout ringworld$layout;

    @Unique
    private int ringworld$terrainNoiseMapping = RingTerrainNoiseMapping.CURRENT;

    @Override
    public void ringworld$setLayout(RingWorldLayouts.RingLayout layout) {
        this.ringworld$layout = layout;
    }

    @Override
    public RingWorldLayouts.RingLayout ringworld$getLayout() {
        return ringworld$layout;
    }

    @Override
    public void ringworld$setTerrainNoiseMapping(int mappingVersion) {
        this.ringworld$terrainNoiseMapping = RingTerrainNoiseMapping.requireSupported(mappingVersion);
    }

    @Override
    public int ringworld$getTerrainNoiseMapping() {
        return ringworld$terrainNoiseMapping;
    }
}
