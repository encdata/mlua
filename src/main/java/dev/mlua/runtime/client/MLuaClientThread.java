package dev.mlua.runtime.client;

import dev.mlua.core.MLuaLogger;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.Callable;
import java.util.function.Supplier;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaThread;
import net.minecraft.client.Minecraft;

/** Tracks the thread used by the loader's client tick for APIs that touch live client state. */
public final class MLuaClientThread {
    private static final AtomicReference<Thread> OWNER = new AtomicReference<>();
    private static final AtomicBoolean REPORTED_THREAD_CHANGE = new AtomicBoolean();
    private static final AtomicBoolean REPORTED_THREAD_MISMATCH = new AtomicBoolean();

    private MLuaClientThread() {
    }

    /** Called from the loader's client tick before MLua runs client Lua tasks. */
    public static void captureCurrent() {
        Thread current = Thread.currentThread();
        Thread owner = OWNER.getAndSet(current);
        if (owner != null && owner != current && REPORTED_THREAD_CHANGE.compareAndSet(false, true)) {
            MLuaLogger.LOGGER.warn("MLua client tick moved from '{}' to '{}'; using the active tick thread",
                owner.getName(), current.getName());
        }
    }

    public static boolean isClientThread() {
        Thread current = Thread.currentThread();
        Thread owner = OWNER.get();
        if (owner == current) return true;
        boolean minecraftThread = false;
        try {
            Minecraft minecraft = Minecraft.getInstance();
            minecraftThread = minecraft != null && minecraft.isSameThread();
        } catch (RuntimeException unavailable) {
            minecraftThread = false;
        }
        if (owner != null && !minecraftThread && !MLuaClientExecutionScope.isActive()
                && REPORTED_THREAD_MISMATCH.compareAndSet(false, true)) {
            MLuaLogger.LOGGER.error("MLua client thread mismatch: tick='{}' ({}), caller='{}' ({}), Minecraft.isSameThread()={}",
                owner.getName(), System.identityHashCode(owner), current.getName(),
                System.identityHashCode(current), minecraftThread);
        }
        return minecraftThread;
    }

    /**
     * Runs client Lua work while the client tick thread waits synchronously for its Lua coroutine.
     * The coroutine is serialized with the tick, but it is not the Minecraft thread; callers must
     * use this context only for client state APIs that are safe during that serialized callback.
     */
    public static void runSynchronousClientCallback(Runnable callback) {
        if (!isClientThread()) {
            throw new IllegalStateException("Client callback must be entered from Minecraft's client thread.");
        }
        try (MLuaClientExecutionScope ignored = MLuaClientExecutionScope.open()) {
            callback.run();
        }
    }

    /** True on Minecraft's client thread and on a child coroutine spawned by its synchronous callback. */
    public static boolean isClientExecutionContext() {
        return isClientThread() || MLuaClientExecutionScope.isActive();
    }

    /**
     * Keeps the client event loop processing tasks while a Lua coroutine is resumed.
     * This is the client-side counterpart of MinecraftServer.managedBlock.
     */
    public static <T> T runWithResumeWaitHook(Supplier<T> callback) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || !minecraft.isSameThread()) return callback.get();
        return LuaThread.withResumeWaitHook(minecraft::managedBlock, callback);
    }

    /** Runs a local-player mutation on the real client thread and returns its result. */
    public static <T> T callOnClientThread(Supplier<T> action) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) throw new LuaError("[MLua] Minecraft's client is not available.");
        if (minecraft.isSameThread()) return action.get();
        if (!isClientExecutionContext()) {
            throw new LuaError("[MLua] Client player actions must be called from a client Lua callback.");
        }

        try {
            return submitToClientThread(minecraft, action::get);
        } catch (RuntimeException | Error failure) {
            throw failure;
        } catch (Exception failure) {
            throw new LuaError("[MLua] Client player action failed: " + failure.getMessage());
        }
    }

    /** Reads client-owned Minecraft state on its owner thread, including from asynchronous Lua workers. */
    public static <T> T callForClientState(Callable<T> action) throws Exception {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) throw new LuaError("[MLua] Minecraft's client is not available.");
        if (minecraft.isSameThread()) return action.call();
        return submitToClientThread(minecraft, action);
    }

    /** Runs a client-owned operation on Minecraft's thread and waits for its result from any Lua worker. */
    public static <T> T runOnClientThread(Callable<T> action) {
        try {
            return callForClientState(action);
        } catch (LuaError failure) {
            throw failure;
        } catch (Exception failure) {
            throw new LuaError("[MLua] Client-thread operation failed: "
                + (failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage()));
        }
    }

    private static <T> T submitToClientThread(Minecraft minecraft, Callable<T> action) throws Exception {
        CompletableFuture<T> result = new CompletableFuture<>();
        minecraft.execute(() -> {
            try {
                result.complete(action.call());
            } catch (Throwable failure) {
                result.completeExceptionally(failure);
            }
        });
        try {
            return result.join();
        } catch (CompletionException failure) {
            Throwable cause = failure.getCause() == null ? failure : failure.getCause();
            if (cause instanceof Exception exception) throw exception;
            if (cause instanceof Error error) throw error;
            throw new IllegalStateException(cause);
        }
    }

}
