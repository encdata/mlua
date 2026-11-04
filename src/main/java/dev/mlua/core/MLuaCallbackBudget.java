package dev.mlua.core;

import dev.mlua.runtime.server.MLuaServerThread;
import java.util.Objects;
import java.util.function.Supplier;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaInstructionBudget;
import org.luaj.vm2.LuaThread;

/** Shared interpreted-Lua instruction limit for synchronous engine callbacks. */
public final class MLuaCallbackBudget {
    public static final long MAX_INSTRUCTIONS = 100_000L;
    public static final long MAX_SCRIPT_INSTRUCTIONS = 2_000_000L;
    private static volatile ClientWaitPump clientWaitPump;

    @FunctionalInterface
    public interface ClientWaitPump {
        Object run(Supplier<?> callback);
    }

    private MLuaCallbackBudget() { }

    /** Installs the client-only event-loop bridge without linking client classes on servers. */
    public static void installClientWaitPump(ClientWaitPump pump) {
        clientWaitPump = pump;
    }

    /** Runs a callback under its script environment's active budget, if one exists. */
    public static <T> T run(MLuaContext context, Supplier<T> callback) {
        return run(context, context == null ? null : context.globals(), callback);
    }

    /** Runs a callback with the supplied environment when no owning script context is available. */
    public static <T> T run(MLuaContext context, Globals globals, Supplier<T> callback) {
        return run(context, globals, MAX_INSTRUCTIONS, callback);
    }

    /** Runs synchronous script evaluation with its larger startup/workload allowance. */
    public static <T> T runScript(MLuaContext context, Globals globals, Supplier<T> callback) {
        return run(context, globals, MAX_SCRIPT_INSTRUCTIONS, callback);
    }

    private static <T> T run(MLuaContext context, Globals globals, long maximumInstructions,
                             Supplier<T> callback) {
        Objects.requireNonNull(callback, "callback");
        Globals environment = context != null && context.globals() != null ? context.globals() : globals;
        if (environment == null) return callback.get();
        try (LuaInstructionBudget.Scope ignored = environment.beginInstructionBudget(maximumInstructions)) {
            return runWithOwnerThreadResponsive(context, callback);
        }
    }

    /**
     * LuaJ runs resumed coroutines on child threads. While the owning Minecraft thread
     * waits, keep its event loop processing queued work until the coroutine yields or ends.
     */
    private static <T> T runWithOwnerThreadResponsive(MLuaContext context, Supplier<T> callback) {
        if (context == null || context.server() == null || !context.server().isSameThread()) {
            ClientWaitPump clientPump = context != null && context.isClient() ? clientWaitPump : null;
            if (clientPump != null) {
                @SuppressWarnings("unchecked") T result = (T) clientPump.run(callback);
                return result;
            }
            return callback.get();
        }
        var server = context.server();
        return LuaThread.withResumeWaitHook(completed -> MLuaServerThread.runOwnerThreadLuaWait(server, () -> {
            server.managedBlock(completed::getAsBoolean);
            return null;
        }), callback);
    }
}
