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
 * the client has actually loaded. Unsampled cells use a multi-scale synthetic
 * fallback until a server-backed full-ring terrain atlas is available. Vertex
 * colours are sampled independently so broad atlas cells blend instead of
 * appearing as the hard rectangular bands visible in the first alpha.</p>
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
    /** Match the physical ten-block rim closely instead of drawing an oversized grey lip. */
    private static final double VISUAL_WALL_HEIGHT = 11.0;
    private static final double WALL_ALPHA_SCALE = 0.72;

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

                double h00 = atlasHeight(worldX0, z0, geometry);
                double h10 = atlasHeight(worldX1, z0, geometry);
                double h11 = atlasHeight(worldX1, z1, geometry);
                double h01 = atlasHeight(worldX0, z1, geometry);

                AtlasPoint p00 = point(a0, h00, z0, camera);
                AtlasPoint p10 = point(a1, h10, z0, camera);
                AtlasPoint p11 = point(a1, h11, z1, camera);
                AtlasPoint p01 = point(a0, h01, z1, camera);

                // Independent corner colours let the GPU interpolate biome transitions across
                // the quad instead of magnifying one flat colour into a giant sky stripe.
                int c00 = atlasColor(worldX0, z0, geometry);
                int c10 = atlasColor(worldX1, z0, geometry);
                int c11 = atlasColor(worldX1, z1, geometry);
                int c01 = atlasColor(worldX0, z1, geometry);

                vertex(builder, pose, p00, c00, alpha);
                vertex(builder, pose, p10, c10, alpha);
                vertex(builder, pose, p11, c11, alpha);
                vertex(builder, pose, p01, c01, alpha);
            }
        }
    }

    /** Subtle curved lips make the finite 2,000-block band legible without outlining it heavily. */
    private static void drawSideWalls(BufferBuilder builder, Matrix4f pose,
                                      RingGeometry geometry, Vec3 camera, FadeWindow fadeWindow) {
        double[] edges = {geometry.minWidthZ(), geometry.maxWidthZ() + 1.0};
        final int wallRgb = 0x666B72;

        for (double edgeZ : edges) {
            for (int ai = 0; ai < ANGULAR_SEGMENTS; ai++) {
                double a0 = -Math.PI + TAU * ai / ANGULAR_SEGMENTS;
                double a1 = -Math.PI + TAU * (ai + 1) / ANGULAR_SEGMENTS;
                int alpha = fadeAlpha((a0 + a1) * 0.5, fadeWindow);
                alpha = (int)Math.round(alpha * WALL_ALPHA_SCALE);
                if (alpha <= 0) continue;

                double x0 = geometry.wrapX(camera.x + a0 * geometry.circumferenceBlocks() / TAU);
                double x1 = geometry.wrapX(camera.x + a1 * geometry.circumferenceBlocks() / TAU);
                double h0 = atlasHeight(x0, edgeZ, geometry);
                double h1 = atlasHeight(x1, edgeZ, geometry);
                double top0 = h0 + VISUAL_WALL_HEIGHT;
                double top1 = h1 + VISUAL_WALL_HEIGHT;

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
        t = smoothstep(t);
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

    /**
     * Multi-scale terrain used only for unseen atlas cells. The first alpha used
     * very long sine waves, which enlarged into obvious coloured ribbons. Mixing
     * several wavelengths and both X/Z directions keeps the fallback coherent at
     * ring scale while giving it mountain chains, coast variation and local relief.
     */
    private static double placeholderHeight(double x, double z) {
        double continental = 0.62 * Math.sin((x + z * 0.42) * TAU / 6800.0)
                + 0.38 * Math.cos((x - z * 1.25 + 900.0) * TAU / 3900.0)
                + 0.24 * Math.sin((x * 0.72 + z * 2.35 - 500.0) * TAU / 2150.0);

        double coast = smoothstep((continental + 0.46) / 0.34);
        if (coast <= 0.015) return 63.0;

        double rolling = 3.8 * Math.sin((x + z * 1.70) * TAU / 1050.0)
                + 2.4 * Math.cos((x * 1.35 - z * 1.10) * TAU / 620.0)
                + 1.2 * Math.sin((x * 2.25 + z * 0.65) * TAU / 330.0);

        double ridgeSignal = Math.sin((x + z * 0.35 + 1300.0) * TAU / 3100.0)
                + 0.72 * Math.cos((x * 0.82 - z * 1.55) * TAU / 1850.0)
                - 0.70;
        double ridge = Math.max(0.0, ridgeSignal);
        double mountains = ridge * ridge * 24.0;

        double landHeight = 67.0 + rolling + mountains;
        return lerp(63.0, landHeight, coast);
    }

    /** Synthetic colour fallback; real sampled cells always override this value. */
    private static int placeholderColor(double x, double z) {
        double height = placeholderHeight(x, z);
        double temperature = Math.sin((x - 1800.0 + z * 0.25) * TAU / 11800.0)
                + 0.28 * Math.cos((x + z * 1.65) * TAU / 2850.0);
        double wetness = Math.cos((x + 700.0 - z * 0.32) * TAU / 5450.0)
                + 0.40 * Math.sin((x - z * 1.30) * TAU / 1750.0);

        int base;
        if (height < 64.15) {
            base = 0x3D76B8;
        } else if (height > 101.0 || (height > 82.0 && temperature < -0.92)) {
            base = 0xE7ECEC;
        } else if (height > 90.0) {
            base = 0x82847F;
        } else if (temperature > 0.78 && wetness < -0.42) {
            base = 0xB96A45;
        } else if (temperature > 0.52 && wetness < 0.08) {
            base = 0xD6C786;
        } else if (wetness > 0.68) {
            base = 0x315E3B;
        } else if (wetness > 0.14) {
            base = 0x4E8148;
        } else {
            base = 0x70A357;
        }

        // Small deterministic tint variations stop large same-biome regions from looking painted.
        double detail = 0.55 * Math.sin((x + z * 1.8) * TAU / 520.0)
                + 0.45 * Math.cos((x * 1.7 - z) * TAU / 370.0);
        return tint(base, 1.0 + detail * 0.055);
    }

    private static int tint(int rgb, double factor) {
        int r = clampColor((int)Math.round(((rgb >> 16) & 0xFF) * factor));
        int g = clampColor((int)Math.round(((rgb >> 8) & 0xFF) * factor));
        int b = clampColor((int)Math.round((rgb & 0xFF) * factor));
        return (r << 16) | (g << 8) | b;
    }

    private static int clampColor(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private static double smoothstep(double t) {
        t = Math.max(0.0, Math.min(1.0, t));
        return t * t * (3.0 - 2.0 * t);
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
