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
    private static final System.Logger LOGGER = System.getLogger(ScopedEventBridge.class.getName());
    private final EventBridge delegate;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final List<CallbackTarget> callbackTargets = new ArrayList<>();
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
        List<CallbackTarget> callbacks;
        synchronized (this.callbackTargets) {
            if (!this.closed.compareAndSet(false, true)) return;
            callbacks = List.copyOf(this.callbackTargets);
            this.callbackTargets.clear();
            callbacks.forEach(callback -> callback.target().set(null));
        }
        for (CallbackTarget callback : callbacks) {
            try {
                this.delegate.removeListener(callback.hook(), callback.proxy());
            } catch (RuntimeException failure) {
                LOGGER.log(System.Logger.Level.WARNING,
                    "Failed to remove loader callback for " + callback.hook(), failure);
            }
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
        Object[] forwarded = arguments == null ? null : arguments.clone();
        Class<?>[] parameterTypes = method.getParameterTypes();
        if (forwarded != null && forwarded.length > 0 && parameterTypes.length > 0
            && forwarded[0] != null && method.getName().startsWith("on")
            && parameterTypes[0].isInterface() && isFunctional(parameterTypes[0])) {
            synchronized (this.callbackTargets) {
                if (this.closed.get()) throw new IllegalStateException("Event bridge scope is closed");
                CallbackTarget callback = this.revocableCallback(
                    method.getName(), forwarded[0], parameterTypes[0]);
                forwarded[0] = callback.proxy();
                try {
                    return this.invokeDelegate(method, forwarded);
                } catch (Throwable failure) {
                    callback.target().set(null);
                    this.callbackTargets.remove(callback);
                    throw failure;
                }
            }
        }
        if (this.closed.get()) throw new IllegalStateException("Event bridge scope is closed");
        return this.invokeDelegate(method, forwarded);
    }

    private Object invokeDelegate(Method method, Object[] forwarded) throws Throwable {
        try {
            return method.invoke(this.delegate, forwarded);
        } catch (InvocationTargetException failure) {
            throw failure.getCause();
        }
    }

    private CallbackTarget revocableCallback(String hook, Object callback, Class<?> callbackType) {
        AtomicReference<Object> target = new AtomicReference<>(callback);
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
        Object proxy = Proxy.newProxyInstance(callbackType.getClassLoader(), new Class<?>[]{callbackType}, handler);
        CallbackTarget registeredCallback = new CallbackTarget(hook, proxy, target);
        synchronized (this.callbackTargets) {
            if (this.closed.get()) throw new IllegalStateException("Event bridge scope is closed");
            this.callbackTargets.add(registeredCallback);
        }
        return registeredCallback;
    }

    private static boolean isFunctional(Class<?> type) {
        int abstractMethods = 0;
        for (Method method : type.getMethods()) {
            if (method.isDefault() || method.getDeclaringClass() == Object.class) continue;
            if (++abstractMethods > 1) return false;
        }
        return abstractMethods == 1;
    }

    private record CallbackTarget(String hook, Object proxy, AtomicReference<Object> target) { }
}
