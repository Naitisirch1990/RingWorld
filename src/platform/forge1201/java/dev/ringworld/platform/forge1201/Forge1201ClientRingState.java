package dev.ringworld.platform.forge1201;

import dev.ringworld.world.RingGeometry;

/**
 * Client-visible immutable RingWorld layouts for the Forge 1.20.1 adapter.
 *
 * <p>The holder deliberately has no Minecraft client-class references so the
 * dedicated server can load the networking classes safely. Client rendering
 * can select the active profile after inspecting its current dimension.</p>
 */
public final class Forge1201ClientRingState {
    private static volatile Settings settings;

    private Forge1201ClientRingState() { }

    public static void accept(Forge1201SettingsPacket packet) {
        settings = new Settings(
                packet.overworldWidth(), packet.overworldCircumference(),
                packet.netherWidth(), packet.netherCircumference(),
                packet.endWidth(), packet.endCircumference(),
                packet.seed(), packet.wallHeight(), packet.surfaceReferenceY(),
                packet.terrainNoiseMapping(), packet.formatVersion(), packet.fingerprint());
    }

    public static Settings current() {
        return settings;
    }

    public static void clear() {
        settings = null;
        Forge1201ClientAtlasState.clear();
    }

    public enum Profile {
        OVERWORLD,
        NETHER,
        END
    }

    public record Settings(
            int overworldWidth, int overworldCircumference,
            int netherWidth, int netherCircumference,
            int endWidth, int endCircumference,
            long seed, int wallHeight, int surfaceReferenceY,
            int terrainNoiseMapping, int formatVersion, long fingerprint) {

        public RingGeometry geometry(Profile profile) {
            return switch (profile) {
                case OVERWORLD -> new RingGeometry(overworldWidth, overworldCircumference);
                case NETHER -> new RingGeometry(netherWidth, netherCircumference);
                case END -> new RingGeometry(endWidth, endCircumference);
            };
        }

        public RingGeometry overworldGeometry() {
            return geometry(Profile.OVERWORLD);
        }

        public RingGeometry netherGeometry() {
            return geometry(Profile.NETHER);
        }

        public RingGeometry endGeometry() {
            return geometry(Profile.END);
        }
    }
}
