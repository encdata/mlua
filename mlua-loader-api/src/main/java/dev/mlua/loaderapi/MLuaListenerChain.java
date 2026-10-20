package dev.mlua.loaderapi;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Ordered listener composition that isolates a failing lifecycle listener. */
public final class MLuaListenerChain {
    private static final Logger LOGGER = Logger.getLogger("MLua");

    private MLuaListenerChain() { }

    public static Runnable append(Runnable current, Runnable next) {
        Runnable guardedNext = guarded(next);
        return current == null ? guardedNext : () -> {
            current.run();
            guardedNext.run();
        };
    }

    public static <T> Consumer<T> append(Consumer<T> current, Consumer<T> next) {
        Consumer<T> guardedNext = guarded(next);
        return current == null ? guardedNext : value -> {
            current.accept(value);
            guardedNext.accept(value);
        };
    }

    public static DoubleConsumer append(DoubleConsumer current, DoubleConsumer next) {
        DoubleConsumer guardedNext = guarded(next);
        return current == null ? guardedNext : value -> {
            current.accept(value);
            guardedNext.accept(value);
        };
    }

    private static Runnable guarded(Runnable listener) {
        Objects.requireNonNull(listener, "listener");
        AtomicReference<Runnable> active = new AtomicReference<>(listener);
        return () -> {
            Runnable current = active.get();
            if (current == null) return;
            try {
                current.run();
            } catch (VirtualMachineError | ThreadDeath fatal) {
                throw fatal;
            } catch (Throwable failure) {
                if (active.compareAndSet(current, null)) report(failure);
            }
        };
    }

    private static <T> Consumer<T> guarded(Consumer<T> listener) {
        Objects.requireNonNull(listener, "listener");
        AtomicReference<Consumer<T>> active = new AtomicReference<>(listener);
        return value -> {
            Consumer<T> current = active.get();
            if (current == null) return;
            try {
                current.accept(value);
            } catch (VirtualMachineError | ThreadDeath fatal) {
                throw fatal;
            } catch (Throwable failure) {
                if (active.compareAndSet(current, null)) report(failure);
            }
        };
    }

    private static DoubleConsumer guarded(DoubleConsumer listener) {
        Objects.requireNonNull(listener, "listener");
        AtomicReference<DoubleConsumer> active = new AtomicReference<>(listener);
        return value -> {
            DoubleConsumer current = active.get();
            if (current == null) return;
            try {
                current.accept(value);
            } catch (VirtualMachineError | ThreadDeath fatal) {
                throw fatal;
            } catch (Throwable failure) {
                if (active.compareAndSet(current, null)) report(failure);
            }
        };
    }

    private static void report(Throwable failure) {
        LOGGER.log(Level.SEVERE,
            "MLua lifecycle listener failed and was disabled; later listeners will continue", failure);
    }
}
