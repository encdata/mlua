package dev.mlua.runtime.server;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;
import net.minecraft.server.MinecraftServer;

/** Runs synchronous Minecraft server work on its owner thread. */
public final class MLuaServerThread {
    private MLuaServerThread() { }

    public static <T> T call(MinecraftServer server, Supplier<T> action) {
        try {
            return callChecked(server, action::get);
        } catch (RuntimeException | Error failure) {
            throw failure;
        } catch (Exception failure) {
            throw new IllegalStateException("Server-thread operation failed", failure);
        }
    }

    /** Runs server-owned work while preserving checked failures for reflective dispatchers. */
    public static <T> T callChecked(MinecraftServer server, CheckedSupplier<T> action) throws Exception {
        if (server == null) throw new IllegalStateException("Minecraft server is not available");
        if (server.isSameThread()) return action.get();

        CompletableFuture<T> result = new CompletableFuture<>();
        server.execute(() -> {
            try {
                result.complete(action.get());
            } catch (Throwable failure) {
                result.completeExceptionally(failure);
            }
        });
        try {
            return result.join();
        } catch (CompletionException failure) {
            Throwable cause = failure.getCause() == null ? failure : failure.getCause();
            if (cause instanceof Exception checked) throw checked;
            if (cause instanceof Error error) throw error;
            throw new IllegalStateException("Server-thread operation failed", cause);
        }
    }

    @FunctionalInterface
    public interface CheckedSupplier<T> {
        T get() throws Exception;
    }
}
