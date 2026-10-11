package dev.ringworld.world;

/**
 * Implemented by the Forge 1.20.1 noise-generator mixin once a vanilla
 * generator is attached to one of the custom ring dimensions.
 */
public interface RingWorldGeneratorAccess {
    void ringworld$setLayout(RingWorldLayouts.RingLayout layout);

    RingWorldLayouts.RingLayout ringworld$getLayout();

    void ringworld$setTerrainNoiseMapping(int mappingVersion);

    int ringworld$getTerrainNoiseMapping();
}
