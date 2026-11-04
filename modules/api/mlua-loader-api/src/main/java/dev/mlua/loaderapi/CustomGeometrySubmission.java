package dev.mlua.loaderapi;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;

/** Reflection boundary for Minecraft versions that defer geometry through SubmitNodeCollector. */
final class CustomGeometrySubmission {
    private static final ClassValue<List<Method>> SUBMISSION_METHODS = new ClassValue<>() {
        @Override protected List<Method> computeValue(Class<?> type) {
            return java.util.Arrays.stream(type.getMethods())
                .filter(CustomGeometrySubmission::isSubmissionMethod)
                .sorted(Comparator.comparing(Method::toGenericString))
                .toList();
        }
    };
    private static final ClassValue<Optional<Method>> CALLBACK_METHODS = new ClassValue<>() {
        @Override protected Optional<Method> computeValue(Class<?> type) {
            return Optional.ofNullable(resolveGeometryCallback(type));
        }
    };

    private CustomGeometrySubmission() { }

    static boolean supports(Object source) {
        return source != null && !SUBMISSION_METHODS.get(source.getClass()).isEmpty();
    }

    static boolean submit(Object source, Object poseStack, Object renderType,
                          BiConsumer<Object, Object> geometry) {
        Objects.requireNonNull(geometry, "geometry");
        if (source == null || poseStack == null || renderType == null) return false;
        List<Method> candidates = new ArrayList<>();
        for (Method method : SUBMISSION_METHODS.get(source.getClass())) {
            if (method.getParameterTypes()[0].isInstance(poseStack)
                    && method.getParameterTypes()[1].isInstance(renderType)) {
                candidates.add(method);
            }
        }
        Method submit = candidates.stream()
            .min(Comparator.comparingInt((Method method) ->
                    typeDistance(poseStack.getClass(), method.getParameterTypes()[0])
                        + typeDistance(renderType.getClass(), method.getParameterTypes()[1]))
                .thenComparing(Method::toGenericString))
            .orElse(null);
        if (submit == null) return false;

        Class<?> rendererType = submit.getParameterTypes()[2];
        Method callbackMethod = findGeometryCallback(rendererType);
        if (callbackMethod == null) return false;
        Object renderer = Proxy.newProxyInstance(rendererType.getClassLoader(), new Class<?>[] {rendererType},
            (proxy, method, arguments) -> {
                if (method.getDeclaringClass() == Object.class) {
                    return switch (method.getName()) {
                        case "toString" -> "MLuaDeferredGeometryRenderer";
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == (arguments == null ? null : arguments[0]);
                        default -> null;
                    };
                }
                if (method.isDefault()) {
                    return InvocationHandler.invokeDefault(proxy, method, arguments);
                }
                if (!sameCallbackSignature(method, callbackMethod)) {
                    throw new IllegalStateException("Unexpected custom geometry callback method: " + method);
                }
                if (arguments == null || arguments.length != 2) {
                    throw new IllegalStateException("Unexpected custom geometry callback arguments: " + method);
                }
                geometry.accept(arguments[0], arguments[1]);
                return null;
            });
        try {
            submit.invoke(source, poseStack, renderType, renderer);
            return true;
        } catch (IllegalAccessException failure) {
            throw new IllegalStateException("Cannot access Minecraft custom geometry submission", failure);
        } catch (InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof RuntimeException runtime) throw runtime;
            if (cause instanceof Error error) throw error;
            throw new IllegalStateException("Minecraft custom geometry submission failed", cause);
        }
    }

    private static boolean isSubmissionMethod(Method method) {
        return method.getName().equals("submitCustomGeometry")
            && method.getParameterCount() == 3
            && findGeometryCallback(method.getParameterTypes()[2]) != null
            && !java.lang.reflect.Modifier.isStatic(method.getModifiers());
    }

    /** Accepts only a functional renderer callback matching Minecraft's pose/consumer contract. */
    private static Method findGeometryCallback(Class<?> callbackType) {
        return CALLBACK_METHODS.get(callbackType).orElse(null);
    }

    private static Method resolveGeometryCallback(Class<?> callbackType) {
        if (!callbackType.isInterface()) return null;
        List<Method> abstractMethods = new ArrayList<>();
        Set<String> signatures = new HashSet<>();
        for (Method method : callbackType.getMethods()) {
            if (!java.lang.reflect.Modifier.isAbstract(method.getModifiers())
                    || java.lang.reflect.Modifier.isStatic(method.getModifiers())
                    || method.getDeclaringClass() == Object.class) continue;
            String signature = callbackSignature(method);
            if (signatures.add(signature)) abstractMethods.add(method);
        }
        if (abstractMethods.size() != 1) return null;
        Method callback = abstractMethods.get(0);
        return callback.getParameterCount() == 2 && callback.getReturnType() == void.class ? callback : null;
    }

    private static boolean sameCallbackSignature(Method left, Method right) {
        return left.getName().equals(right.getName())
            && java.util.Arrays.equals(left.getParameterTypes(), right.getParameterTypes())
            && left.getReturnType() == right.getReturnType();
    }

    private static String callbackSignature(Method method) {
        return method.getName() + java.util.Arrays.toString(method.getParameterTypes())
            + "->" + method.getReturnType().getName();
    }

    /** Returns the shortest class/interface path from an actual value to a parameter type. */
    private static int typeDistance(Class<?> actual, Class<?> target) {
        if (actual == target) return 0;
        ArrayDeque<Class<?>> pending = new ArrayDeque<>();
        ArrayDeque<Integer> distances = new ArrayDeque<>();
        Set<Class<?>> visited = new HashSet<>();
        pending.add(actual);
        distances.add(0);
        while (!pending.isEmpty()) {
            Class<?> current = pending.removeFirst();
            int distance = distances.removeFirst();
            if (!visited.add(current)) continue;
            if (current == target) return distance;
            Class<?> parent = current.getSuperclass();
            if (parent != null) {
                pending.addLast(parent);
                distances.addLast(distance + 1);
            }
            for (Class<?> implemented : current.getInterfaces()) {
                pending.addLast(implemented);
                distances.addLast(distance + 1);
            }
        }
        return Integer.MAX_VALUE;
    }
}
