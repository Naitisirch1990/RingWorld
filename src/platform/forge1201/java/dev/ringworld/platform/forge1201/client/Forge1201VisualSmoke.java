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
    private static final String[] NAMES = {"01-road", "02-up", "03-rim", "04-seam", "05-far-build", "06-far-edit"};
    private static final double[][] VIEWS = {{80,73,0,-90,0},{80,200,0,-90,-65},
            {80,105,-980,180,35},{61430,200,0,-90,20},{80,200,0,-90,-90},{80,200,0,-90,-90}};
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
                server.overworld().setDayTime(stage >= 4 ? 3000 : 6000);
                player.connection.teleport(view[0], view[1], view[2], (float)view[3], (float)view[4]);
                if (stage == 4) {
                    // Actual blocks only, on the opposite side of this disposable world.
                    // Nothing here executes in ordinary play.
                    var level = server.overworld();
                    int center = 80 + dev.ringworld.platform.forge1201.Forge1201RingRuntime.geometryFor(level).circumferenceBlocks()/2;
                    var pos = new net.minecraft.core.BlockPos.MutableBlockPos();
                    for (int x=center-128;x<center+128;x++) for(int z=-128;z<128;z++) {
                        var chunk=level.getChunk(x>>4,z>>4);
                        chunk.setBlockState(pos.set(x,300,z),net.minecraft.world.level.block.Blocks.RED_CONCRETE.defaultBlockState(),false);
                        if(Math.abs(x-center)<16 && Math.abs(z)<16)
                            for(int y=180;y<300;y++) chunk.setBlockState(pos.set(x,y,z),net.minecraft.world.level.block.Blocks.RED_CONCRETE.defaultBlockState(),false);
                    }
                    for(int x=(center-128)>>4;x<=((center+127)>>4);x++) for(int z=-8;z<8;z++)
                        dev.ringworld.platform.forge1201.Forge1201AtlasServer.capture(level,level.getChunk(x,z));
                    var atlas=dev.ringworld.platform.forge1201.Forge1201AtlasServer.atlas(level);
                    int column=center/8,row=(4-atlas.geometry().minWidthZ())/8;
                    if(atlas.cellHeight(column,row)!=301) throw new IllegalStateException("Far build absent from server atlas");
                    try {
                        var file=level.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("ringworld/fixture-atlas.rwat.gz");
                        atlas.save(file);
                        var reopened=dev.ringworld.world.RingTerrainAtlas.load(file,atlas.geometry(),atlas.worldHash());
                        if(reopened.cellHeight(column,row)!=301) throw new IllegalStateException("Far build lost after atlas reopen");
                    } catch(java.io.IOException ex) { throw new IllegalStateException(ex); }
                    RingWorldMod.LOGGER.info("[forge-visual] ATLAS_PERSIST_PASS actual far-side build at {}",center);
                }
                if (stage == 5) {
                    var level=server.overworld();
                    int center=80+dev.ringworld.platform.forge1201.Forge1201RingRuntime.geometryFor(level).circumferenceBlocks()/2;
                    var pos=new net.minecraft.core.BlockPos.MutableBlockPos();
                    for(int x=center-128;x<center+128;x++) for(int z=-128;z<128;z++)
                        level.getChunk(x>>4,z>>4).setBlockState(pos.set(x,300,z),net.minecraft.world.level.block.Blocks.BLUE_CONCRETE.defaultBlockState(),false);
                    // No explicit capture: normal atlas recapture must notice the edit.
                }
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
        if (stage >= 4) {
            var atlas=dev.ringworld.platform.forge1201.Forge1201ClientAtlasState.atlas();
            int center=80+geometry.circumferenceBlocks()/2;
            int row=(4-geometry.minWidthZ())/8;
            int expected=(stage==4 ? net.minecraft.world.level.block.Blocks.RED_CONCRETE : net.minecraft.world.level.block.Blocks.BLUE_CONCRETE)
                    .defaultBlockState().getMapColor(mc.level,new net.minecraft.core.BlockPos(center+4,300,4)).col;
            if(atlas==null || atlas.cellHeight(center/8,row)!=301 || atlas.cellColor(center/8,row)!=expected) {
                if(System.nanoTime()-started>90_000_000_000L) { failed=true; RingWorldMod.LOGGER.error("[forge-visual] FAIL far build not streamed"); mc.stop(); }
                return;
            }
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
                RingWorldMod.LOGGER.info("[forge-visual] CAPTURE_PASS six real-world views, actual far build synchronized, persisted and live-edited; visual review still required");
                mc.stop();
            }
        }));
    }
}
