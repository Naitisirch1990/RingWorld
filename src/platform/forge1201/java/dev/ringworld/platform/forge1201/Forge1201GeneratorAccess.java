package dev.ringworld.platform.forge1201;

import dev.ringworld.world.RingGeometry;

/** Narrow generator bridge for the Forge 1.20.1 backport. */
public interface Forge1201GeneratorAccess {
    void ringworld1201$setGeometry(RingGeometry geometry);
    RingGeometry ringworld1201$getGeometry();
    void ringworld1201$setTerrainNoiseMapping(int mappingVersion);
    int ringworld1201$getTerrainNoiseMapping();
    void ringworld1201$setWallHeight(int wallHeightBlocks);
    int ringworld1201$getWallHeight();
}
