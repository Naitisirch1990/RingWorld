package dev.ringworld.platform.forge1201;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;

/**
 * Session-only gate for the expensive RingWorld atlas service.
 *
 * <p>The secret itself is deliberately not stored in the JAR. Only its SHA-256
 * digest is embedded. Authorization is reset whenever the server starts, so a
 * copied mod does not inherit an unlocked state from a world save.</p>
 */
final class Forge1201AtlasAccess {
    private static final byte[] EXPECTED_SHA256 = HexFormat.of().parseHex(
            "fa1778a99fb2f2f9265b42d068c642d3569807788d867fd828244179fc5cf9cb");

    private static volatile boolean unlocked;

    private Forge1201AtlasAccess() {}

    static boolean isUnlocked() {
        return unlocked;
    }

    static boolean unlock(String candidate) {
        byte[] actual;
        try {
            actual = MessageDigest.getInstance("SHA-256")
                    .digest(candidate.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }

        boolean matches = MessageDigest.isEqual(EXPECTED_SHA256, actual);
        Arrays.fill(actual, (byte) 0);
        if (matches) unlocked = true;
        return matches;
    }

    static void reset() {
        unlocked = false;
    }
}
