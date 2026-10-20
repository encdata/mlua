package dev.mlua.modules.tween.service;

import dev.mlua.core.MLuaCallbackBudget;
import dev.mlua.core.MLuaContext;
import dev.mlua.core.engine.MLuaLuaBindings;
import dev.mlua.core.signal.MLuaSignal;
import dev.mlua.common.enums.Side;
import dev.mlua.math.MLuaVector;
import java.util.ArrayList;
import java.util.List;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/** One property tween. Sampling and writes happen on the client render thread. */
final class MLuaTween {
    record Property(String name, LuaValue getter, LuaValue setter, LuaValue goal) { }

    private LuaTable target;
    private List<Property> properties;
    private MLuaContext context;
    private Globals globals;
    private Object executionLock;
    private Object ownerSession;
    private final String ownerModId;
    private final double duration;
    private final String style;
    private final String direction;
    private final MLuaSignal completed = new MLuaSignal();
    private volatile String playbackState = "Created";
    private List<LuaValue> starts = List.of();
    private long startedAtNanos;
    private long pausedElapsedNanos;
    private volatile String error;
    private boolean completionPending;
    private boolean disposed;

    MLuaTween(LuaTable target, List<Property> properties, MLuaContext context,
              double duration, String style, String direction) {
        this.target = target;
        this.properties = List.copyOf(properties);
        this.context = context;
        this.globals = context.globals();
        this.executionLock = context.executionLock();
        this.ownerSession = context.session();
        this.ownerModId = context.ownerModId();
        this.duration = duration;
        this.style = style;
        this.direction = direction;
    }

    synchronized void play() {
        requireNotDisposed();
        error = null;
        List<LuaValue> current = new ArrayList<>(properties.size());
        runBounded(() -> {
            for (Property property : properties) {
                LuaValue value = property.getter().invoke(LuaValue.varargsOf(new LuaValue[]{target})).arg1();
                if (!compatible(value, property.goal())) {
                    throw new LuaError("[MLua] Tween target property '" + property.name()
                        + "' changed to an unsupported value type.");
                }
                current.add(value);
            }
            return null;
        });
        starts = List.copyOf(current);
        startedAtNanos = System.nanoTime();
        pausedElapsedNanos = 0L;
        if (duration == 0.0) {
            apply(1.0);
            playbackState = "Completed";
            fireCompleted();
        } else {
            MLuaTweenService.activate(this);
            playbackState = "Playing";
        }
    }

    synchronized void pause() {
        if (!"Playing".equals(playbackState)) return;
        pausedElapsedNanos = Math.max(0L, System.nanoTime() - startedAtNanos);
        playbackState = "Paused";
        MLuaTweenService.deactivate(this);
    }

    synchronized void cancel() {
        if (disposed) return;
        if ("Completed".equals(playbackState) || "Cancelled".equals(playbackState)) return;
        boolean wasActive = "Playing".equals(playbackState) || "Paused".equals(playbackState);
        boolean wasPlaying = "Playing".equals(playbackState);
        playbackState = "Cancelled";
        if (wasPlaying) MLuaTweenService.deactivate(this);
        starts = List.of();
        pausedElapsedNanos = 0L;
        if (wasActive) fireCompleted();
    }

    synchronized void dispose() {
        if (disposed) return;
        if ("Playing".equals(playbackState)) MLuaTweenService.deactivate(this);
        disposed = true;
        playbackState = "Cancelled";
        starts = List.of();
        completionPending = false;
        completed.destroyPermanently();
        target = null;
        properties = List.of();
        context = null;
        globals = null;
        executionLock = null;
        ownerSession = null;
    }

    String playbackState() { return playbackState; }
    String error() { return error; }
    Object ownerSession() { return ownerSession; }
    String ownerModId() { return ownerModId; }
    LuaTable completedSignal() {
        return MLuaLuaBindings.signalInstance(completed, globals, context.scheduler(), ownerModId, Side.CLIENT);
    }
    void fireCompleted() { completed.fireOnSide(Side.CLIENT, LuaValue.valueOf(playbackState)); }
    void firePendingCompletion() {
        synchronized (this) {
            if (!completionPending) return;
            completionPending = false;
        }
        fireCompleted();
    }

    synchronized boolean sample(long nowNanos) {
        if (!"Playing".equals(playbackState)) return false;
        long elapsed = Math.max(0L, nowNanos - startedAtNanos);
        double alpha = Math.min(1.0, elapsed / (duration * 1_000_000_000.0));
        try {
            apply(ease(alpha));
        } catch (RuntimeException | Error failure) {
            error = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
            playbackState = "Cancelled";
            completionPending = true;
            return false;
        }
        if (alpha >= 1.0) {
            playbackState = "Completed";
            completionPending = true;
            return false;
        }
        return true;
    }

    synchronized void resume() {
        requireNotDisposed();
        if (!"Paused".equals(playbackState)) {
            play();
            return;
        }
        MLuaTweenService.activate(this);
        startedAtNanos = System.nanoTime() - pausedElapsedNanos;
        playbackState = "Playing";
    }

    private void requireNotDisposed() {
        if (disposed) throw new LuaError("[MLua] This Tween was disposed when its addon runtime unloaded.");
    }

    private void apply(double alpha) {
        runBounded(() -> {
            for (int index = 0; index < properties.size(); index++) {
                Property property = properties.get(index);
                LuaValue value = interpolate(starts.get(index), property.goal(), alpha);
                property.setter().invoke(LuaValue.varargsOf(new LuaValue[]{target, value}));
            }
            return null;
        });
    }

    private <T> T runBounded(java.util.function.Supplier<T> action) {
        synchronized (executionLock) {
            return MLuaCallbackBudget.run(context, globals, action);
        }
    }

    private double ease(double value) {
        double t = Math.max(0.0, Math.min(1.0, value));
        if ("Out".equals(direction)) return 1.0 - easeIn(1.0 - t);
        if ("InOut".equals(direction)) return t < 0.5
            ? easeIn(t * 2.0) * 0.5
            : 1.0 - easeIn((1.0 - t) * 2.0) * 0.5;
        return easeIn(t);
    }

    private double easeIn(double t) {
        return switch (style) {
            case "Sine" -> 1.0 - Math.cos(t * Math.PI / 2.0);
            case "Quad" -> t * t;
            case "Cubic" -> t * t * t;
            case "Quart" -> t * t * t * t;
            case "Quint" -> t * t * t * t * t;
            case "Expo" -> t == 0.0 ? 0.0 : Math.pow(2.0, 10.0 * t - 10.0);
            case "Circ" -> 1.0 - Math.sqrt(Math.max(0.0, 1.0 - t * t));
            case "Back" -> 2.70158 * t * t * t - 1.70158 * t * t;
            case "Elastic" -> t == 0.0 || t == 1.0 ? t
                : -Math.pow(2.0, 10.0 * t - 10.0) * Math.sin((t * 10.0 - 10.75) * (2.0 * Math.PI / 3.0));
            case "Bounce" -> 1.0 - easeOutBounce(1.0 - t);
            default -> t;
        };
    }

    private static double easeOutBounce(double t) {
        double n = 7.5625;
        double d = 2.75;
        if (t < 1.0 / d) return n * t * t;
        if (t < 2.0 / d) { t -= 1.5 / d; return n * t * t + 0.75; }
        if (t < 2.5 / d) { t -= 2.25 / d; return n * t * t + 0.9375; }
        t -= 2.625 / d;
        return n * t * t + 0.984375;
    }

    private static LuaValue interpolate(LuaValue from, LuaValue to, double alpha) {
        if (from.isnumber() && to.isnumber()) {
            double value = from.todouble() + (to.todouble() - from.todouble()) * alpha;
            if (!Double.isFinite(value)) throw new LuaError("[MLua] Tween produced a non-finite number.");
            return LuaValue.valueOf(value);
        }
        LuaValue lerp = from.get("Lerp");
        if (lerp.isfunction()) {
            return lerp.invoke(LuaValue.varargsOf(new LuaValue[]{from, to, LuaValue.valueOf(alpha)})).arg1();
        }
        throw new LuaError("[MLua] Tween supports numbers and native Vector2, Vector3, Color3, and CFrame values.");
    }

    private static boolean compatible(LuaValue from, LuaValue to) {
        if (from.isnumber() && to.isnumber()) return Double.isFinite(from.todouble()) && Double.isFinite(to.todouble());
        if (MLuaVector.isVector2(from)) return MLuaVector.isVector2(to);
        if (MLuaVector.isVector3(from)) return MLuaVector.isVector3(to);
        if (dev.mlua.math.MLuaCFrame.isCFrame(from)) return dev.mlua.math.MLuaCFrame.isCFrame(to);
        if (dev.mlua.core.engine.MLuaLuauTypes.isColor3(from)) return dev.mlua.core.engine.MLuaLuauTypes.isColor3(to);
        return false;
    }
}
