package dev.ringworld.forge1201;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import dev.ringworld.world.RingLandmarkPlan;
import dev.ringworld.world.RingWorldLayouts;
import dev.ringworld.world.RingWorldSettings;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;

/** Small operator-facing diagnostics used while the backport becomes playable. */
public final class RingWorldCommands {
    private RingWorldCommands() {}

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("ringworld")
                        .then(Commands.literal("status")
                                .executes(context -> status(context.getSource())))
                        .then(Commands.literal("wrap")
                                .then(Commands.argument("x", IntegerArgumentType.integer())
                                        .executes(context -> wrap(
                                                context.getSource(),
                                                IntegerArgumentType.getInteger(context, "x")))))
                        .then(Commands.literal("pyramid")
                                .executes(context -> pyramid(context.getSource()))));
    }

    private static int status(net.minecraft.commands.CommandSourceStack source) {
        RingWorldSettings settings = RingWorldSettings.get(source.getServer().overworld());
        source.sendSuccess(() -> Component.literal(
                "RingWorld 1.20.1 alpha | Overworld "
                        + settings.overworld().circumferenceBlocks() + "x" + settings.overworld().widthBlocks()
                        + " | Nether "
                        + settings.nether().circumferenceBlocks() + "x" + settings.nether().widthBlocks()
                        + " | End "
                        + settings.end().circumferenceBlocks() + "x" + settings.end().widthBlocks()), false);
        source.sendSuccess(() -> Component.literal(
                "Finite Z: " + settings.overworld().minWidthZ() + ".." + settings.overworld().maxWidthZ()
                        + " (chunks " + settings.overworld().minChunkZ() + ".."
                        + settings.overworld().maxChunkZ() + ")"), false);
        return 1;
    }

    private static int wrap(net.minecraft.commands.CommandSourceStack source, int x) {
        RingWorldLayouts.RingLayout layout = RingWorldLayouts.OVERWORLD;
        int canonical = layout.wrapBlockX(x);
        source.sendSuccess(() -> Component.literal(
                "Overworld X " + x + " -> canonical X " + canonical
                        + " / " + layout.circumferenceBlocks()), false);
        return canonical;
    }

    private static int pyramid(net.minecraft.commands.CommandSourceStack source) {
        long seed = source.getServer().overworld().getSeed();
        RingLandmarkPlan.Landmark pyramid = RingLandmarkPlan.overworld(seed).stream()
                .filter(landmark -> landmark.type() == RingLandmarkPlan.LandmarkType.PYRAMID_CITY)
                .findFirst()
                .orElseThrow();
        source.sendSuccess(() -> Component.literal(
                "Unique Pyramid City sector: canonical X=" + pyramid.canonicalX()
                        + " (slot " + pyramid.slot() + ")"), false);
        return pyramid.canonicalX();
    }
}
