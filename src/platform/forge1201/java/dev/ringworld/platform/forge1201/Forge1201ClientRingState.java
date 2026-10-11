package dev.ringworld.platform.forge1201;

import dev.ringworld.world.RingGeometry;

/**
 * Client-visible immutable RingWorld layout for the Forge 1.20.1 adapter.
 *
 * <p>The holder deliberately has no Minecraft client-class references so the
 * dedicated server can load the networking classes safely. The later 1.20.1
 * renderer port consumes this state after the settings packet arrives.</p>
 */
public final class Forge1201ClientRingState {
    private static volatile Settings settings;

    private Forge1201ClientRingState() { }

    public static void accept(Forge1201SettingsPacket packet) {
        settings = new Settings(
                packet.width(), packet.circumference(), packet.seed(), packet.wallHeight(),
                packet.surfaceReferenceY(), packet.terrainNoiseMapping(),
                packet.formatVersion(), packet.fingerprint());
    }

    public static Settings current() {
        return settings;
    }

    public static void clear() {
        settings = null;
    }

    public record Settings(int width, int circumference, long seed, int wallHeight,
                           int surfaceReferenceY, int terrainNoiseMapping,
                           int formatVersion, long fingerprint) {
        public RingGeometry geometry() {
            return new RingGeometry(width, circumference);
        }
    }
}
