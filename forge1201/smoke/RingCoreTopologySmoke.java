package dev.ringworld.platform.forge1201.smoke;

import dev.ringworld.world.RingChunkTopology;

/** Minimal Java 17 checks for periodic chunk arithmetic at the agreed dimensions. */
public final class RingCoreTopologySmoke {
    private RingCoreTopologySmoke() {}

    public static void main(String[] args) {
        verify("Overworld", 61_440, 2_000);
        verify("Nether", 30_720, 2_000);
        verify("End", 61_440, 2_000);
        System.out.println("RingWorld periodic chunk arithmetic passed for all three dimensions.");
    }

    private static void verify(String dimension, int circumference, int width) {
        check(circumference % 16 == 0 && width % 16 == 0, dimension + " alignment");
        int n = circumference / 16;
        RingChunkTopology t = new RingChunkTopology(n);
        check(t.canonicalX(-1) == n - 1, dimension + " negative seam");
        check(t.canonicalX(n) == 0, dimension + " positive seam");
        check(t.canonicalX(n + 1) == 1, dimension + " seam successor");
        check(t.canonicalX(n * 3 + 17) == 17, dimension + " repeated lap");
        check(t.distanceX(n - 1, 0) == 1, dimension + " seam distance");
        check(t.distanceX(-1, n + 1) == 2, dimension + " image distance");
        check(t.distanceX(0, n / 2) == n / 2, dimension + " opposite arc");
        check(t.isWithinVanillaDistance(n - 1, 0, 6, 0, 0, true), dimension + " seam watch");
        check(!t.isWithinVanillaDistance(0, 0, 6, n / 2, 0, true), dimension + " distant watch");
        check(!t.isWithinVanillaDistance(0, 0, 6, 0, 100, true), dimension + " finite Z");
    }

    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }
}
