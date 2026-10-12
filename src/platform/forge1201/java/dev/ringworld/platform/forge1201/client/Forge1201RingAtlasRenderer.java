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
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/** Distant surface in exactly the same physical cylinder as live terrain.
 * No miniature sky cylinder, exaggerated relief or synthetic biome ribbons.
 */
@Mod.EventBusSubscriber(modid = RingWorldMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Forge1201RingAtlasRenderer {
    private Forge1201RingAtlasRenderer() { }
    @SubscribeEvent
    public static void renderRingAtlas(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.level.dimension() != Level.OVERWORLD
                || Forge1201RingShaders.surface() == null) return;
        RingGeometry geometry = Forge1201ClientTopology.activeGeometry();
        if (geometry == null) return;
        Forge1201RingTerrainAtlas.refreshLoadedTerrain(mc.level, geometry);
        Vec3 camera = event.getCamera().getPosition();
        Matrix4f pose = event.getPoseStack().last().pose();
        // The atlas replaces terrain beyond vanilla's chunk draw distance.
        // Tying its fade to render distance made it vanish around seven chunks,
        // even though this curved mesh spans the full ring.
        double view = geometry.circumferenceBlocks() * 0.5;
        RenderSystem.enableDepthTest(); RenderSystem.depthMask(false);
        RenderSystem.disableCull(); RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1, 1, 1, 1);
        RenderSystem.setShader(Forge1201RingShaders::surface);
        try {
            BufferBuilder b = Tesselator.getInstance().getBuilder();
            b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            for (int sign : new int[]{-1, 1}) {
                double half = geometry.circumferenceBlocks() * 0.5;
                for (double d = 0; d < half;) {
                    double step = d < 512 ? 16 : d < 2048 ? 64 : 128;
                    double next = Math.min(half, d + step);
                    double x0 = camera.x + sign * d, x1 = camera.x + sign * next;
                    for (double z = geometry.minWidthZ(); z < geometry.maxWidthZ() + 1;) {
                        double zStep = d < 512 && Math.abs(z - camera.z) < 512 ? 16 : 64;
                        double z1 = Math.min(geometry.maxWidthZ() + 1, z + zStep);
                        surfaceVertex(b, pose, geometry, camera, x0, z, view);
                        surfaceVertex(b, pose, geometry, camera, x1, z, view);
                        surfaceVertex(b, pose, geometry, camera, x1, z1, view);
                        surfaceVertex(b, pose, geometry, camera, x0, z1, view);
                        z = z1;
                    }
                    // Double the apparent fascia from 5 to 10 blocks by placing
                    // its inner face five blocks farther into the ring.
                    for (double edge : new double[]{geometry.minWidthZ() + 10, geometry.maxWidthZ() - 9}) {
                        double base0 = Forge1201RingTerrainAtlas.sample(x0, edge, geometry).height();
                        double base1 = Forge1201RingTerrainAtlas.sample(x1, edge, geometry).height();
                        wallVertex(b, pose, geometry, camera, x0, base0, edge, view);
                        wallVertex(b, pose, geometry, camera, x1, base1, edge, view);
                        wallVertex(b, pose, geometry, camera, x1, base1 + 10, edge, view);
                        wallVertex(b, pose, geometry, camera, x0, base0 + 10, edge, view);
                    }
                    d = next;
                }
            }
            BufferUploader.drawWithShader(b.end());
        } finally {
            RenderSystem.setShaderColor(1, 1, 1, 1); RenderSystem.depthMask(true);
            RenderSystem.enableCull(); RenderSystem.disableBlend();
        }
    }
    private static void surfaceVertex(BufferBuilder b, Matrix4f pose, RingGeometry geometry,
                                      Vec3 camera, double x, double z, double view) {
        Forge1201RingTerrainAtlas.Sample sample = Forge1201RingTerrainAtlas.sample(x, z, geometry);
        int rgb = sample.color();
        // Unknown areas remain a pale, low-detail silhouette, explicitly distinct from real data.
        double haze = (1 - sample.confidence()) * 0.68;
        int r = (int)(((rgb >> 16) & 255) * (1 - haze) + 180 * haze);
        int g = (int)(((rgb >> 8) & 255) * (1 - haze) + 200 * haze);
        int blue = (int)((rgb & 255) * (1 - haze) + 212 * haze);
        vertex(b, pose, geometry, camera, x, sample.height(), z, (r << 16) | (g << 8) | blue, view);
    }
    private static void wallVertex(BufferBuilder b, Matrix4f pose, RingGeometry geometry,
                                   Vec3 camera, double x, double y, double z, double view) {
        vertex(b, pose, geometry, camera, x, y, z, 0x52545A, view);
    }
    private static void vertex(BufferBuilder b, Matrix4f pose, RingGeometry geometry, Vec3 camera,
                                double x, double y, double z, int rgb, double view) {
        Vec3 p = Forge1201CurvedProjection.toCameraLocal(geometry, new Vec3(x, y, z), camera);
        double dx = geometry.shortestCircumferenceDelta(camera.x, x);
        double distance = Math.sqrt(dx * dx + (z - camera.z) * (z - camera.z));
        double t = Math.max(0, Math.min(1, (distance - view * 0.55) / (view * 0.35)));
        int alpha = (int)Math.round(255 * t * t * (3 - 2 * t));
        b.vertex(pose, (float)p.x, (float)p.y, (float)p.z)
                .color((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, alpha).endVertex();
    }
}
