package dev.ringworld.platform.forge1201.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import dev.ringworld.RingWorldMod;
import java.io.IOException;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** The distant surface owns a shader so only its far depth is clamped. */
@Mod.EventBusSubscriber(modid = RingWorldMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class Forge1201RingShaders {
    private static ShaderInstance surface;
    private Forge1201RingShaders() { }
    public static ShaderInstance surface() { return surface; }
    @SubscribeEvent
    public static void register(RegisterShadersEvent event) throws IOException {
        event.registerShader(new ShaderInstance(event.getResourceProvider(),
                new ResourceLocation("ringworld", "ring_surface_1201"),
                DefaultVertexFormat.POSITION_COLOR), shader -> surface = shader);
    }
}
