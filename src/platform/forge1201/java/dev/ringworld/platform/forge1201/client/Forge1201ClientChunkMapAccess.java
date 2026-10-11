package dev.ringworld.platform.forge1201.client;

/** Operations exposed by the private 1.20.1 ClientChunkCache.Storage object. */
public interface Forge1201ClientChunkMapAccess {
    int ringworld1201$centerChunkX();
    int ringworld1201$centerChunkZ();
    void ringworld1201$clearAllChunks();
}
