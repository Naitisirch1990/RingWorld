package dev.ringworld.platform.forge1201.mixin;

import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingTickSchedulerAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.world.ticks.LevelTicks;
import net.minecraft.world.ticks.ScheduledTick;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Stores block/fluid scheduled ticks under the same canonical X as chunk data. */
@Mixin(LevelTicks.class)
abstract class Forge1201LevelTicksMixin<T> implements RingTickSchedulerAccess {
    @Unique private RingGeometry ringworld1201$geometry;

    @Override
    public void ringworld$setGeometry(RingGeometry geometry) {
        ringworld1201$geometry = geometry;
    }

    @ModifyVariable(method = "schedule", at = @At("HEAD"), argsOnly = true)
    private ScheduledTick<T> ringworld1201$canonicalScheduledTick(ScheduledTick<T> tick) {
        RingGeometry geometry = ringworld1201$geometry;
        if (geometry == null) return tick;
        BlockPos pos = tick.pos();
        int canonicalX = geometry.wrapBlockX(pos.getX());
        if (canonicalX == pos.getX()) return tick;
        return new ScheduledTick<>(tick.type(),
                new BlockPos(canonicalX, pos.getY(), pos.getZ()),
                tick.triggerTick(), tick.priority(), tick.subTickOrder());
    }
}
