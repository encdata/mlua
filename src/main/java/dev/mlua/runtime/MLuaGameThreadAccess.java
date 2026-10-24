package dev.mlua.runtime;

import dev.mlua.api.MLuaLuaErrors;
import dev.mlua.common.enums.Side;
import dev.mlua.core.service.MLuaService;
import dev.mlua.runtime.client.MLuaClientThread;
import dev.mlua.runtime.server.MLuaServerThread;
import java.util.concurrent.Callable;
import java.util.function.Supplier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.luaj.vm2.LuaError;

/** Dispatches synchronous live-world work to the Minecraft thread that owns that world. */
public final class MLuaGameThreadAccess {
    private MLuaGameThreadAccess() { }

    public static <T> T call(MLuaService.Context context, Callable<T> action) throws Exception {
        if (context.mlua().side() == Side.SERVER) {
            var server = context.mlua().server();
            if (server == null || server.isSameThread()) return action.call();
            return MLuaServerThread.callChecked(server, action::call);
        }
        return MLuaClientThread.callForClientState(action);
    }

    public static <T> T call(Level level, Callable<T> action) throws Exception {
        if (level instanceof ServerLevel serverLevel) {
            return MLuaServerThread.callChecked(serverLevel.getServer(), action::call);
        }
        if (level != null && isClientSide(level)) {
            return MLuaClientThread.callForClientState(action);
        }
        return action.call();
    }

    public static <T> T callUnchecked(MLuaService.Context context, Supplier<T> action) {
        try {
            return call(context, action::get);
        } catch (LuaError failure) {
            throw failure;
        } catch (Exception failure) {
            throw MLuaLuaErrors.javaFailure("Minecraft game-thread operation", failure);
        }
    }

    public static <T> T callUnchecked(Level level, Supplier<T> action) {
        try {
            return call(level, action::get);
        } catch (LuaError failure) {
            throw failure;
        } catch (Exception failure) {
            throw MLuaLuaErrors.javaFailure("Minecraft game-thread operation", failure);
        }
    }

    private static boolean isClientSide(Level level) {
        //--version 26.1
        return level.isClientSide();
        //--endversion
        //--version 1.20.1,1.21.1
        return level.isClientSide;
        //--endversion
    }
}
