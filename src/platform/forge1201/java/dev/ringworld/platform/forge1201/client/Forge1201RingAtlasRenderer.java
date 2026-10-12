package dev.ringworld.platform.forge1201.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.ringworld.RingWorldMod;
import dev.ringworld.platform.forge1201.Forge1201ClientTopology;
import dev.ringworld.world.RingGeometry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/**
 * Client-only low-poly representation of the complete RingWorld beyond normal
 * chunk render distance. It is deliberately independent of the physical ring
 * radius: gameplay remains 61,440 blocks around, while the atlas is compressed
 * into a sky-scale model so the ring is unmistakable from the ground.
 *
 * <p>This first pass is the same kind of placeholder described by the visual
 * reference: deterministic pseudo-biome colours and coarse relief. A later
 * cache can replace these samples with real generated terrain without changing
 * the projection/render architecture.</p>
 */
@Mod.EventBusSubscriber(modid = RingWorldMod.MOD_ID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Forge1201RingAtlasRenderer {
    private static final double TAU = Math.PI * 2.0;
    private static final int ANGULAR_SEGMENTS = 256;
    private static final int WIDTH_SEGMENTS = 20;

    /** Sky-scale radius; opposite surface is roughly twice this distance overhead. */
    private static final double VISUAL_RADIUS = 240.0;
    /** Compress the real 2,000-block width so both side walls fit comfortably in view. */
    private static final double WIDTH_SCALE = 0.18;
    /** Terrain vertical exaggeration inside the atlas. */
    private static final double HEIGHT_SCALE = 0.55;
    /** Real chunks win near the camera; the atlas softly fades in beyond this angle. */
    private static final double FADE_START_ANGLE = 0.12;
    private static final double FADE_END_ANGLE = 0.34;

    private Forge1201RingAtlasRenderer() { }

    @SubscribeEvent
    public static void renderRingAtlas(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.level.dimension() != Level.OVERWORLD) return;
        RingGeometry geometry = Forge1201ClientTopology.activeGeometry();
        if (geometry == null) return;

        Vec3 camera = event.getCamera().getPosition();
        Matrix4f pose = event.getPoseStack().last().pose();

        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        drawSurface(builder, pose, geometry, camera);
        drawSideWalls(builder, pose, geometry, camera);
        BufferUploader.drawWithShader(builder.end());

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static void drawSurface(BufferBuilder builder, Matrix4f pose,
                                    RingGeometry geometry, Vec3 camera) {
        double minZ = geometry.minWidthZ();
        double maxZ = geometry.maxWidthZ() + 1.0;

        for (int ai = 0; ai < ANGULAR_SEGMENTS; ai++) {
            double a0 = -Math.PI + TAU * ai / ANGULAR_SEGMENTS;
            double a1 = -Math.PI + TAU * (ai + 1) / ANGULAR_SEGMENTS;
            double midAngle = (a0 + a1) * 0.5;
            int alpha = fadeAlpha(midAngle);
            if (alpha <= 0) continue;

            double worldX0 = geometry.wrapX(camera.x + a0 * geometry.circumferenceBlocks() / TAU);
            double worldX1 = geometry.wrapX(camera.x + a1 * geometry.circumferenceBlocks() / TAU);

            for (int zi = 0; zi < WIDTH_SEGMENTS; zi++) {
                double z0 = lerp(minZ, maxZ, zi / (double)WIDTH_SEGMENTS);
                double z1 = lerp(minZ, maxZ, (zi + 1.0) / WIDTH_SEGMENTS);
                double midX = geometry.wrapX(camera.x + midAngle * geometry.circumferenceBlocks() / TAU);
                double midZ = (z0 + z1) * 0.5;
                int rgb = atlasColor(midX, midZ);

                double h00 = atlasHeight(worldX0, z0);
                double h10 = atlasHeight(worldX1, z0);
                double h11 = atlasHeight(worldX1, z1);
                double h01 = atlasHeight(worldX0, z1);

                AtlasPoint p00 = point(a0, h00, z0, camera);
                AtlasPoint p10 = point(a1, h10, z0, camera);
                AtlasPoint p11 = point(a1, h11, z1, camera);
                AtlasPoint p01 = point(a0, h01, z1, camera);

                vertex(builder, pose, p00, rgb, alpha);
                vertex(builder, pose, p10, rgb, alpha);
                vertex(builder, pose, p11, rgb, alpha);
                vertex(builder, pose, p01, rgb, alpha);
            }
        }
    }

    /** Grey curved lips make the finite 2,000-block band legible from inside. */
    private static void drawSideWalls(BufferBuilder builder, Matrix4f pose,
                                      RingGeometry geometry, Vec3 camera) {
        double[] edges = {geometry.minWidthZ(), geometry.maxWidthZ() + 1.0};
        final int wallRgb = 0x777B82;

        for (double edgeZ : edges) {
            for (int ai = 0; ai < ANGULAR_SEGMENTS; ai++) {
                double a0 = -Math.PI + TAU * ai / ANGULAR_SEGMENTS;
                double a1 = -Math.PI + TAU * (ai + 1) / ANGULAR_SEGMENTS;
                int alpha = fadeAlpha((a0 + a1) * 0.5);
                if (alpha <= 0) continue;

                double x0 = geometry.wrapX(camera.x + a0 * geometry.circumferenceBlocks() / TAU);
                double x1 = geometry.wrapX(camera.x + a1 * geometry.circumferenceBlocks() / TAU);
                double h0 = atlasHeight(x0, edgeZ);
                double h1 = atlasHeight(x1, edgeZ);
                // Visual lip is intentionally exaggerated; the physical wall remains ten blocks.
                double top0 = h0 + 22.0;
                double top1 = h1 + 22.0;

                AtlasPoint p00 = point(a0, h0, edgeZ, camera);
                AtlasPoint p10 = point(a1, h1, edgeZ, camera);
                AtlasPoint p11 = point(a1, top1, edgeZ, camera);
                AtlasPoint p01 = point(a0, top0, edgeZ, camera);

                vertex(builder, pose, p00, wallRgb, alpha);
                vertex(builder, pose, p10, wallRgb, alpha);
                vertex(builder, pose, p11, wallRgb, alpha);
                vertex(builder, pose, p01, wallRgb, alpha);
            }
        }
    }

    /** Same cylindrical mapping as the terrain shader, but with a sky-scale radius. */
    private static AtlasPoint point(double angle, double intrinsicY, double worldZ, Vec3 camera) {
        double cameraRadius = VISUAL_RADIUS
                + (RingGeometry.SURFACE_Y - camera.y) * HEIGHT_SCALE;
        double vertexRadius = VISUAL_RADIUS
                + (RingGeometry.SURFACE_Y - intrinsicY) * HEIGHT_SCALE;
        return new AtlasPoint(
                vertexRadius * Math.sin(angle),
                cameraRadius - vertexRadius * Math.cos(angle),
                (worldZ - camera.z) * WIDTH_SCALE);
    }

    private static int fadeAlpha(double angle) {
        double abs = Math.abs(angle);
        if (abs <= FADE_START_ANGLE) return 0;
        if (abs >= FADE_END_ANGLE) return 238;
        double t = (abs - FADE_START_ANGLE) / (FADE_END_ANGLE - FADE_START_ANGLE);
        t = t * t * (3.0 - 2.0 * t);
        return (int)Math.round(238.0 * t);
    }

    /** Coarse deterministic relief: enough silhouette now, replaceable by cached real heights later. */
    private static double atlasHeight(double x, double z) {
        double continental = Math.sin(x * TAU / 7900.0)
                + 0.55 * Math.cos((x + z * 1.8) * TAU / 3100.0);
        if (continental < -0.78) return 63.0;

        double hills = 7.0 * Math.sin((x + z * 0.7) * TAU / 2200.0)
                + 4.0 * Math.cos((x - z * 2.1) * TAU / 1250.0);
        double mountains = Math.max(0.0,
                Math.sin((x + 900.0) * TAU / 5400.0)
                        + Math.cos((z - 170.0) * TAU / 1650.0) - 0.65) * 22.0;
        return 66.0 + hills + mountains;
    }

    /** Placeholder biome map; large smooth patches make the full ring readable at sky scale. */
    private static int atlasColor(double x, double z) {
        double continental = Math.sin(x * TAU / 7900.0)
                + 0.55 * Math.cos((x + z * 1.8) * TAU / 3100.0);
        if (continental < -0.78) return 0x3E72B8; // ocean

        double temperature = Math.sin((x - 1800.0) * TAU / 13700.0)
                + 0.30 * Math.cos(z * TAU / 1900.0);
        double wetness = Math.cos((x + 700.0) * TAU / 6100.0)
                + 0.45 * Math.sin((x - z) * TAU / 2700.0);
        double height = atlasHeight(x, z);

        if (height > 86.0 || temperature < -0.88) return 0xE8EEEE; // snow/high mountains
        if (temperature > 0.82 && wetness < -0.50) return 0xB9653F; // badlands
        if (temperature > 0.58 && wetness < 0.12) return 0xD8C783; // desert
        if (wetness > 0.72) return 0x315F3A; // dark forest
        if (wetness > 0.20) return 0x4E8145; // forest
        return 0x6EA453; // plains
    }

    private static void vertex(BufferBuilder builder, Matrix4f pose, AtlasPoint p,
                               int rgb, int alpha) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        builder.vertex(pose, (float)p.x, (float)p.y, (float)p.z)
                .color(r, g, b, alpha).endVertex();
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private record AtlasPoint(double x, double y, double z) { }
}
