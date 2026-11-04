package dev.mlua.loaderapi;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Gives one lifecycle owner a revocable view of loader callbacks. Loader event APIs
 * commonly cannot physically unregister callbacks; closing this scope clears the
 * addon callback references and makes any retained loader wrapper inert.
 */
public final class ScopedEventBridge implements AutoCloseable {
    private final EventBridge delegate;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final List<AtomicReference<Object>> callbackTargets = new ArrayList<>();
    private final EventBridge scoped;

    public ScopedEventBridge(EventBridge delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.scoped = (EventBridge) Proxy.newProxyInstance(
            EventBridge.class.getClassLoader(), new Class<?>[]{EventBridge.class}, this::invokeBridge);
    }

    public EventBridge bridge() {
        if (this.closed.get()) throw new IllegalStateException("Event bridge scope is closed");
        return this.scoped;
    }

    @Override
    public void close() {
        if (!this.closed.compareAndSet(false, true)) return;
        synchronized (this.callbackTargets) {
            for (AtomicReference<Object> target : this.callbackTargets) target.set(null);
            this.callbackTargets.clear();
        }
    }

    private Object invokeBridge(Object proxy, Method method, Object[] arguments) throws Throwable {
        if (method.getDeclaringClass() == Object.class) {
            return switch (method.getName()) {
                case "toString" -> "Scoped(" + this.delegate + ")";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == (arguments == null ? null : arguments[0]);
                default -> null;
            };
        }
        if (this.closed.get()) throw new IllegalStateException("Event bridge scope is closed");
        Object[] forwarded = arguments == null ? null : arguments.clone();
        Class<?>[] parameterTypes = method.getParameterTypes();
        if (forwarded != null && forwarded.length > 0 && parameterTypes.length > 0
            && forwarded[0] != null && method.getName().startsWith("on")
            && parameterTypes[0].isInterface() && isFunctional(parameterTypes[0])) {
            forwarded[0] = this.revocableCallback(forwarded[0], parameterTypes[0]);
        }
        try {
            return method.invoke(this.delegate, forwarded);
        } catch (InvocationTargetException failure) {
            throw failure.getCause();
        }
    }

    private Object revocableCallback(Object callback, Class<?> callbackType) {
        AtomicReference<Object> target = new AtomicReference<>(callback);
        synchronized (this.callbackTargets) {
            if (this.closed.get()) throw new IllegalStateException("Event bridge scope is closed");
            this.callbackTargets.add(target);
        }
        InvocationHandler handler = (proxy, method, arguments) -> {
            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "toString" -> "ScopedCallback(" + (target.get() == null ? "closed" : "active") + ")";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == (arguments == null ? null : arguments[0]);
                    default -> null;
                };
            }
            Object active = target.get();
            if (active == null) return null;
            try {
                return method.invoke(active, arguments);
            } catch (InvocationTargetException failure) {
                throw failure.getCause();
            }
        };
        return Proxy.newProxyInstance(callbackType.getClassLoader(), new Class<?>[]{callbackType}, handler);
    }

    private static boolean isFunctional(Class<?> type) {
        int abstractMethods = 0;
        for (Method method : type.getMethods()) {
            if (method.isDefault() || method.getDeclaringClass() == Object.class) continue;
            if (++abstractMethods > 1) return false;
        }
        return abstractMethods == 1;
    }
}
