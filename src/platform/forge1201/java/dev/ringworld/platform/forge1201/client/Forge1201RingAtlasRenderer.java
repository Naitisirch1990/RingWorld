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
 * chunk render distance. Gameplay remains the real 61,440-block circumference;
 * this distant mesh is compressed to sky scale so the complete ring can remain
 * visible without asking Minecraft to render tens of thousands of normal chunks.
 *
 * <p>The mesh progressively adopts real surface height and map colour from chunks
 * the client has actually loaded. Unsampled cells still use the synthetic fallback
 * until a server-backed full-ring terrain atlas is available.</p>
 */
@Mod.EventBusSubscriber(modid = RingWorldMod.MOD_ID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Forge1201RingAtlasRenderer {
    private static final double TAU = Math.PI * 2.0;
    private static final int ANGULAR_SEGMENTS = Forge1201RingTerrainAtlas.ANGULAR_SEGMENTS;
    private static final int WIDTH_SEGMENTS = Forge1201RingTerrainAtlas.WIDTH_SEGMENTS;

    /** Sky-scale radius; opposite surface is roughly twice this distance overhead. */
    private static final double VISUAL_RADIUS = 240.0;
    /** Compress the real 2,000-block width so both side walls fit comfortably in view. */
    private static final double WIDTH_SCALE = 0.18;
    /** Terrain vertical exaggeration inside the atlas. */
    private static final double HEIGHT_SCALE = 0.55;

    /**
     * Do not let low render-distance settings pull the sky mesh too close to the player.
     * Above this floor the fade follows the player's configured chunk render distance.
     */
    private static final double MIN_FADE_START_ANGLE = 0.04;
    private static final double FADE_START_RENDER_FACTOR = 0.65;
    private static final double FADE_END_RENDER_FACTOR = 1.20;
    private static final double MIN_FADE_SPAN_BLOCKS = 256.0;
    private static final int MAX_ATLAS_ALPHA = 238;

    private Forge1201RingAtlasRenderer() { }

    @SubscribeEvent
    public static void renderRingAtlas(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.level.dimension() != Level.OVERWORLD) return;
        RingGeometry geometry = Forge1201ClientTopology.activeGeometry();
        if (!isRenderableGeometry(geometry)) return;

        // Learn only from chunks Minecraft already has in memory. The cache is canonical,
        // so a sample remains useful after the player crosses the periodic X seam.
        Forge1201RingTerrainAtlas.refreshLoadedTerrain(minecraft.level, geometry);

        Vec3 camera = event.getCamera().getPosition();
        if (!isFinite(camera)) return;
        Matrix4f pose = event.getPoseStack().last().pose();
        FadeWindow fadeWindow = fadeWindow(minecraft, geometry);

        // The atlas shares Minecraft's global RenderSystem state. Keep every state change
        // tightly scoped so a failed/aborted atlas frame cannot tint or corrupt later terrain.
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        try {
            BufferBuilder builder = Tesselator.getInstance().getBuilder();
            builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            drawSurface(builder, pose, geometry, camera, fadeWindow);
            drawSideWalls(builder, pose, geometry, camera, fadeWindow);
            BufferUploader.drawWithShader(builder.end());
        } finally {
            // Terrain, entities and translucent passes render after this event. Always return
            // the shared renderer to a neutral state even if atlas tessellation throws.
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
        }
    }

    private static boolean isRenderableGeometry(RingGeometry geometry) {
        return geometry != null
                && geometry.circumferenceBlocks() > 0
                && geometry.minWidthZ() <= geometry.maxWidthZ();
    }

    private static boolean isFinite(Vec3 position) {
        return Double.isFinite(position.x)
                && Double.isFinite(position.y)
                && Double.isFinite(position.z);
    }

    private static void drawSurface(BufferBuilder builder, Matrix4f pose,
                                    RingGeometry geometry, Vec3 camera, FadeWindow fadeWindow) {
        double minZ = geometry.minWidthZ();
        double maxZ = geometry.maxWidthZ() + 1.0;

        for (int ai = 0; ai < ANGULAR_SEGMENTS; ai++) {
            double a0 = -Math.PI + TAU * ai / ANGULAR_SEGMENTS;
            double a1 = -Math.PI + TAU * (ai + 1) / ANGULAR_SEGMENTS;
            double midAngle = (a0 + a1) * 0.5;
            int alpha = fadeAlpha(midAngle, fadeWindow);
            if (alpha <= 0) continue;

            double worldX0 = geometry.wrapX(camera.x + a0 * geometry.circumferenceBlocks() / TAU);
            double worldX1 = geometry.wrapX(camera.x + a1 * geometry.circumferenceBlocks() / TAU);

            for (int zi = 0; zi < WIDTH_SEGMENTS; zi++) {
                double z0 = lerp(minZ, maxZ, zi / (double)WIDTH_SEGMENTS);
                double z1 = lerp(minZ, maxZ, (zi + 1.0) / WIDTH_SEGMENTS);
                double midX = geometry.wrapX(camera.x + midAngle * geometry.circumferenceBlocks() / TAU);
                double midZ = (z0 + z1) * 0.5;
                int rgb = atlasColor(midX, midZ, geometry);

                double h00 = atlasHeight(worldX0, z0, geometry);
                double h10 = atlasHeight(worldX1, z0, geometry);
                double h11 = atlasHeight(worldX1, z1, geometry);
                double h01 = atlasHeight(worldX0, z1, geometry);

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
                                      RingGeometry geometry, Vec3 camera, FadeWindow fadeWindow) {
        double[] edges = {geometry.minWidthZ(), geometry.maxWidthZ() + 1.0};
        final int wallRgb = 0x777B82;

        for (double edgeZ : edges) {
            for (int ai = 0; ai < ANGULAR_SEGMENTS; ai++) {
                double a0 = -Math.PI + TAU * ai / ANGULAR_SEGMENTS;
                double a1 = -Math.PI + TAU * (ai + 1) / ANGULAR_SEGMENTS;
                int alpha = fadeAlpha((a0 + a1) * 0.5, fadeWindow);
                if (alpha <= 0) continue;

                double x0 = geometry.wrapX(camera.x + a0 * geometry.circumferenceBlocks() / TAU);
                double x1 = geometry.wrapX(camera.x + a1 * geometry.circumferenceBlocks() / TAU);
                double h0 = atlasHeight(x0, edgeZ, geometry);
                double h1 = atlasHeight(x1, edgeZ, geometry);
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

    private static FadeWindow fadeWindow(Minecraft minecraft, RingGeometry geometry) {
        int renderDistanceChunks = Math.max(2, minecraft.options.getEffectiveRenderDistance());
        double renderDistanceBlocks = renderDistanceChunks * 16.0;
        double radiansPerBlock = TAU / geometry.circumferenceBlocks();

        double startAngle = Math.max(
                MIN_FADE_START_ANGLE,
                renderDistanceBlocks * FADE_START_RENDER_FACTOR * radiansPerBlock);
        double endAngle = Math.max(
                startAngle + MIN_FADE_SPAN_BLOCKS * radiansPerBlock,
                renderDistanceBlocks * FADE_END_RENDER_FACTOR * radiansPerBlock);
        return new FadeWindow(startAngle, endAngle);
    }

    private static int fadeAlpha(double angle, FadeWindow fadeWindow) {
        double abs = Math.abs(angle);
        if (abs <= fadeWindow.startAngle()) return 0;
        if (abs >= fadeWindow.endAngle()) return MAX_ATLAS_ALPHA;
        double t = (abs - fadeWindow.startAngle())
                / (fadeWindow.endAngle() - fadeWindow.startAngle());
        t = t * t * (3.0 - 2.0 * t);
        return (int)Math.round(MAX_ATLAS_ALPHA * t);
    }

    private static double atlasHeight(double x, double z, RingGeometry geometry) {
        double fallback = placeholderHeight(x, z);
        return Forge1201RingTerrainAtlas.height(x, z, geometry, fallback);
    }

    private static int atlasColor(double x, double z, RingGeometry geometry) {
        int fallback = placeholderColor(x, z);
        return Forge1201RingTerrainAtlas.color(x, z, geometry, fallback);
    }

    /** Synthetic fallback used only while a canonical atlas cell has not been observed. */
    private static double placeholderHeight(double x, double z) {
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

    /** Synthetic colour fallback; real sampled cells always override this value. */
    private static int placeholderColor(double x, double z) {
        double continental = Math.sin(x * TAU / 7900.0)
                + 0.55 * Math.cos((x + z * 1.8) * TAU / 3100.0);
        if (continental < -0.78) return 0x3E72B8;

        double temperature = Math.sin((x - 1800.0) * TAU / 13700.0)
                + 0.30 * Math.cos(z * TAU / 1900.0);
        double wetness = Math.cos((x + 700.0) * TAU / 6100.0)
                + 0.45 * Math.sin((x - z) * TAU / 2700.0);
        double height = placeholderHeight(x, z);

        if (height > 86.0 || temperature < -0.88) return 0xE8EEEE;
        if (temperature > 0.82 && wetness < -0.50) return 0xB9653F;
        if (temperature > 0.58 && wetness < 0.12) return 0xD8C783;
        if (wetness > 0.72) return 0x315F3A;
        if (wetness > 0.20) return 0x4E8145;
        return 0x6EA453;
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

    private record FadeWindow(double startAngle, double endAngle) { }

    private record AtlasPoint(double x, double y, double z) { }
}
