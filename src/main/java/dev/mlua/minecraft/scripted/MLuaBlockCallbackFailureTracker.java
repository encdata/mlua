package dev.mlua.minecraft.scripted;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

/** Bounds repeated failures from scheduled callbacks without retaining unloaded worlds or chunks. */
public final class MLuaBlockCallbackFailureTracker {
    private MLuaBlockCallbackFailureTracker() {
    }

    public static void invoke(Level level, BlockPos pos, String behaviorKey, String event,
                              MLuaBehaviorRegistry.Snapshot behaviors, LuaValue callback, Varargs args) {
        MLuaBehaviorCallbackFailureTracker.invoke(level, pos, "block", behaviorKey, event,
            behaviors == null ? null : behaviors.identity(), callback,
            () -> MLuaBehaviorRegistry.invokeCallback(behaviors, callback, args));
    }
}
