package dev.ringworld.platform.forge1201.client;

import dev.ringworld.RingWorldMod;
import dev.ringworld.platform.forge1201.Forge1201ClientTopology;
import java.io.File;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Opt-in, disposable-world visual fixture. Never enabled for ordinary play. */
@Mod.EventBusSubscriber(modid = RingWorldMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Forge1201VisualSmoke {
    private static final boolean ENABLED = Boolean.getBoolean("ringworld.visualSmoke");
    private static final String[] NAMES = {"01-road", "02-up", "03-rim", "04-seam"};
    private static final double[][] VIEWS = {{80,73,0,-90,0},{80,200,0,-90,-65},
            {80,105,-980,180,35},{61430,200,0,-90,20}};
    private static int stage, frames;
    private static long started;
    private static boolean positioned, waiting;
    private static volatile boolean serverPositioned;
    private static long arrived;
    private static boolean failed;
    private Forge1201VisualSmoke() { }
    @SubscribeEvent
    public static void frame(RenderLevelStageEvent event) {
        if (!ENABLED || failed || event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.getSingleplayerServer() == null || mc.screen != null) return;
        if (stage >= VIEWS.length) return;
        if (!positioned) {
            positioned = true; started = System.nanoTime(); frames = 0; arrived = 0; serverPositioned = false;
            double[] view = VIEWS[stage];
            var id = mc.player.getUUID();
            var server = mc.getSingleplayerServer();
            server.execute(() -> {
                ServerPlayer player = server.getPlayerList().getPlayer(id);
                if (player == null) return;
                player.setGameMode(GameType.CREATIVE);
                player.getAbilities().flying = true; player.onUpdateAbilities();
                server.overworld().setDayTime(6000);
                player.connection.teleport(view[0], view[1], view[2], (float)view[3], (float)view[4]);
                serverPositioned = true;
            });
            RingWorldMod.LOGGER.info("[forge-visual] preparing {}", NAMES[stage]);
        }
        double[] target = VIEWS[stage];
        var geometry = Forge1201ClientTopology.activeGeometry();
        boolean atTarget = serverPositioned && geometry != null
                && Math.abs(geometry.shortestCircumferenceDelta(mc.player.getX(), target[0])) < 0.5
                && Math.abs(mc.player.getY() - target[1]) < 0.5
                && Math.abs(mc.player.getZ() - target[2]) < 0.5
                && Math.abs(net.minecraft.util.Mth.wrapDegrees(mc.player.getYRot() - target[3])) < 2
                && Math.abs(mc.player.getXRot() - target[4]) < 2;
        if (!atTarget) {
            frames = 0; arrived = 0;
            if (System.nanoTime() - started > 60_000_000_000L) {
                failed = true;
                RingWorldMod.LOGGER.error("[forge-visual] FAIL camera did not reach {}: position={} yaw={} pitch={} serverAck={}",
                        NAMES[stage], mc.player.position(), mc.player.getYRot(), mc.player.getXRot(), serverPositioned);
                Thread.getAllStackTraces().forEach((thread, stack) -> {
                    if (thread.getName().equals("Server thread")) {
                        RingWorldMod.LOGGER.error("[forge-visual] server thread state={}", thread.getState());
                        for (StackTraceElement element : stack) RingWorldMod.LOGGER.error("  at {}", element);
                    }
                });
                mc.stop();
            }
            return;
        }
        if (arrived == 0) {
            arrived = System.nanoTime();
            RingWorldMod.LOGGER.info("[forge-visual] arrived {} position={}", NAMES[stage], mc.player.position());
        }
        frames++;
        if (waiting || frames < 100 || System.nanoTime() - arrived < 15_000_000_000L) return;
        waiting = true;
        String name = NAMES[stage] + ".png";
        Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), message -> mc.execute(() -> {
            File file = new File(new File(mc.gameDirectory, "screenshots"), name);
            if (!file.isFile() || file.length() < 1024) {
                RingWorldMod.LOGGER.error("[forge-visual] FAIL missing {}", name);
                mc.stop(); return;
            }
            RingWorldMod.LOGGER.info("[forge-visual] captured {}", name);
            stage++; positioned = false; waiting = false;
            if (stage == VIEWS.length) {
                RingWorldMod.LOGGER.info("[forge-visual] CAPTURE_PASS four real-world views; visual review still required");
                mc.stop();
            }
        }));
    }
}
