package dev.ringworld.world;

import java.util.function.Supplier;
import net.minecraft.world.level.levelgen.NoiseRouter;

/** Thread-confined bridge used only while vanilla constructs one NoiseChunk. */
public final class RingNoiseSamplingContext {
    private static final ThreadLocal<NoiseRouter> ROUTER = new ThreadLocal<>();

    private RingNoiseSamplingContext() {}

    public static NoiseRouter currentRouter() {
        return ROUTER.get();
    }

    public static <T> T withRouter(NoiseRouter router, Supplier<T> action) {
        NoiseRouter previous = ROUTER.get();
        if (router == null) ROUTER.remove();
        else ROUTER.set(router);
        try {
            return action.get();
        } finally {
            if (previous == null) ROUTER.remove();
            else ROUTER.set(previous);
        }
    }
}
