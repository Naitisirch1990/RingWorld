package dev.ringworld.platform.forge1201.client;

import dev.ringworld.world.RingGeometry;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Forge renderer-only curvature profile; canonical world coordinates stay unchanged. */
public final class Forge1201CurvedProjection {
    public static double gain(RingGeometry geometry) { return Math.max(1, Math.min(4, geometry.radius() / 1024.0)); }
    public static double radiusAt(RingGeometry geometry, double y) { return geometry.radius() / gain(geometry) + geometry.surfaceReferenceY() - y; }
    private static double widthScale(RingGeometry geometry, double a) {
        double t = Math.abs(a) / Math.PI;
        return 1.0 + 2.0 * Math.pow(Math.max(0.0, 1.0 - Math.abs(t - 1.0)), 4.0);
    }
    private Forge1201CurvedProjection() { }

    public static double angle(RingGeometry geometry, double cameraX, double x) {
        double intrinsic = Math.PI * 2.0 * geometry.shortestCircumferenceDelta(cameraX, x)
                / geometry.circumferenceBlocks();
        return visualAngle(intrinsic, gain(geometry));
    }

    public static Vec3 toCameraLocal(RingGeometry geometry, Vec3 point, Vec3 camera) {
        double angle = angle(geometry, camera.x, point.x);
        double radius = radiusAt(geometry, point.y);
        double cameraRadius = radiusAt(geometry, camera.y);
        return new Vec3(radius * Math.sin(angle), cameraRadius - radius * Math.cos(angle),
                (point.z - camera.z) * widthScale(geometry, angle));
    }

    public static AABB toCameraLocalBounds(RingGeometry geometry, AABB bounds, Vec3 camera) {
        double start = Math.PI * 2.0
                * geometry.shortestCircumferenceDelta(camera.x, bounds.minX)
                / geometry.circumferenceBlocks();
        double end = start + Math.PI * 2.0 * (bounds.maxX - bounds.minX)
                / geometry.circumferenceBlocks();
        double minAngle = visualAngle(start, gain(geometry)), maxAngle = visualAngle(end, gain(geometry));
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
                a = target;
            }
            for (double y : new double[]{bounds.minY, bounds.maxY}) {
                double radius = radiusAt(geometry, y);
                double x = radius * Math.sin(a);
                double vertical = radiusAt(geometry, camera.y) - radius * Math.cos(a);
                minX = Math.min(minX, x); maxX = Math.max(maxX, x);
                minY = Math.min(minY, vertical); maxY = Math.max(maxY, vertical);
            }
        }
        double z0 = bounds.minZ - camera.z, z1 = bounds.maxZ - camera.z;
        double factor = Math.max(widthScale(geometry, minAngle), widthScale(geometry, maxAngle));
        if (minAngle <= -Math.PI && maxAngle >= -Math.PI || minAngle <= Math.PI && maxAngle >= Math.PI)
            factor = 3.0;
        return new AABB(minX, minY, Math.min(Math.min(z0, z1), Math.min(z0 * factor, z1 * factor)),
                maxX, maxY, Math.max(Math.max(z0, z1), Math.max(z0 * factor, z1 * factor)));
    }

    private static double visualAngle(double angle, double gain) {
        // Derivative is gain at the camera and 1 at the opposite side. Unlike
        // the half-angle magnifier, this does not crush distant buildings.
        double turns = Math.floor((angle + Math.PI) / (2 * Math.PI));
        double local = angle - turns * 2 * Math.PI;
        double t = Math.abs(local), edge = 1 - t / Math.PI;
        double bend = Math.PI * .25 * Math.tanh(4 * (gain - 1) * t / Math.PI) * edge * edge;
        return local + Math.signum(local) * bend + turns * 2 * Math.PI;
    }
}
