package dev.ringworld.platform.forge1201.client;

import java.util.Map;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import net.minecraft.client.multiplayer.ClientChunkCache;

/** Weak association between each client chunk cache and its active private storage. */
public final class Forge1201ClientChunkMaps {
    private static final Map<ClientChunkCache, Forge1201ClientChunkMapAccess> MAPS = new WeakHashMap<>();

    private Forge1201ClientChunkMaps() { }

    public static synchronized void register(ClientChunkCache cache,
                                             Forge1201ClientChunkMapAccess storage) {
        MAPS.put(cache, storage);
    }

    @Nullable
    public static synchronized Forge1201ClientChunkMapAccess get(ClientChunkCache cache) {
        return MAPS.get(cache);
    }
}
