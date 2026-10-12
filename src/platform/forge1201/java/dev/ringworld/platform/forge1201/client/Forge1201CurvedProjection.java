package dev.ringworld.platform.forge1201.client;

import dev.ringworld.world.RingGeometry;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Forge renderer-only curvature profile; canonical world coordinates stay unchanged. */
public final class Forge1201CurvedProjection {
    public static final double CURVATURE_GAIN = 0.65;
    private Forge1201CurvedProjection() { }

    public static double angle(RingGeometry geometry, double cameraX, double x) {
        double intrinsic = Math.PI * 2.0 * geometry.shortestCircumferenceDelta(cameraX, x)
                / geometry.circumferenceBlocks();
        return visualAngle(intrinsic);
    }

    public static Vec3 toCameraLocal(RingGeometry geometry, Vec3 point, Vec3 camera) {
        double angle = angle(geometry, camera.x, point.x);
        double radius = geometry.physicalRadiusAt(point.y);
        double cameraRadius = geometry.physicalRadiusAt(camera.y);
        return new Vec3(radius * Math.sin(angle), cameraRadius - radius * Math.cos(angle),
                point.z - camera.z);
    }

    public static AABB toCameraLocalBounds(RingGeometry geometry, AABB bounds, Vec3 camera) {
        double start = Math.PI * 2.0
                * geometry.shortestCircumferenceDelta(camera.x, bounds.minX)
                / geometry.circumferenceBlocks();
        double end = start + Math.PI * 2.0 * (bounds.maxX - bounds.minX)
                / geometry.circumferenceBlocks();
        double minAngle = visualAngle(start), maxAngle = visualAngle(end);
        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
        int firstCardinal = (int)Math.ceil(minAngle / (Math.PI / 2.0));
        int lastCardinal = (int)Math.floor(maxAngle / (Math.PI / 2.0));
        int samples = 2 + Math.max(0, lastCardinal - firstCardinal + 1);
        for (int i = 0; i < samples; i++) {
            double a;
            if (i == 0) a = minAngle;
            else if (i == 1) a = maxAngle;
            else {
                double target = (firstCardinal + i - 2) * (Math.PI / 2.0);
                a = visualAngle(invert(target, start, end));
            }
            for (double y : new double[]{bounds.minY, bounds.maxY}) {
                double radius = geometry.physicalRadiusAt(y);
                double x = radius * Math.sin(a);
                double vertical = geometry.physicalRadiusAt(camera.y) - radius * Math.cos(a);
                minX = Math.min(minX, x); maxX = Math.max(maxX, x);
                minY = Math.min(minY, vertical); maxY = Math.max(maxY, vertical);
            }
        }
        return new AABB(minX, minY, bounds.minZ - camera.z,
                maxX, maxY, bounds.maxZ - camera.z);
    }

    private static double visualAngle(double angle) {
        return angle + CURVATURE_GAIN * Math.sin(angle);
    }

    private static double invert(double target, double low, double high) {
        for (int i = 0; i < 48; i++) {
            double middle = (low + high) * 0.5;
            if (visualAngle(middle) < target) low = middle;
            else high = middle;
        }
        return (low + high) * 0.5;
    }
}
