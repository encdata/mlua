package dev.mlua.core;

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;

/** Runs work immediately on its owning thread and queues it otherwise. */
public final class MLuaThreadHandoff {
    private MLuaThreadHandoff() {
    }

    public static void execute(BooleanSupplier isOwnerThread, Executor ownerExecutor, Runnable action) {
        Objects.requireNonNull(isOwnerThread, "isOwnerThread");
        Objects.requireNonNull(ownerExecutor, "ownerExecutor");
        Objects.requireNonNull(action, "action");
        if (isOwnerThread.getAsBoolean()) action.run();
        else ownerExecutor.execute(action);
    }
}
