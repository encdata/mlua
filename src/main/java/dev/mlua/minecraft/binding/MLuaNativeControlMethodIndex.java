package dev.mlua.minecraft.binding;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Caches the deterministic public-method view used by native widget bindings. */
final class MLuaNativeControlMethodIndex {
    private static final ClassValue<WidgetMethods> WIDGET_METHODS = new ClassValue<>() {
        @Override protected WidgetMethods computeValue(Class<?> type) {
            return new WidgetMethods(type);
        }
    };

    private MLuaNativeControlMethodIndex() { }

    static List<Method> methods(Object widget) {
        return WIDGET_METHODS.get(widget.getClass()).methods;
    }

    static List<Method> methodsNamed(Object widget, String name) {
        return WIDGET_METHODS.get(widget.getClass()).methodsNamed(name);
    }

    static Method propertyMethod(Object widget, String methodName, int parameterCount) {
        return WIDGET_METHODS.get(widget.getClass()).propertyMethod(methodName, parameterCount);
    }

    private static final class WidgetMethods {
        private final List<Method> methods;
        private final Map<String, List<Method>> methodsByName;
        private final Map<MethodKey, Method> properties;

        private WidgetMethods(Class<?> type) {
            Method[] discovered = type.getMethods();
            Arrays.sort(discovered, Comparator.comparing(Method::toGenericString));
            this.methods = List.of(discovered);
            Map<MethodKey, Method> indexed = new HashMap<>();
            Map<String, List<Method>> named = new HashMap<>();
            for (Method method : discovered) {
                named.computeIfAbsent(method.getName(), ignored -> new ArrayList<>()).add(method);
                if (Modifier.isStatic(method.getModifiers()) || method.getDeclaringClass() == Object.class
                    || method.isBridge() || method.isSynthetic()) continue;
                MethodKey key = new MethodKey(method.getName(), method.getParameterCount());
                Method previous = indexed.get(key);
                if (previous == null || betterPropertyMethod(type, method, previous)) indexed.put(key, method);
            }
            named.replaceAll((name, matches) -> List.copyOf(matches));
            this.methodsByName = Map.copyOf(named);
            this.properties = Map.copyOf(indexed);
        }

        private List<Method> methodsNamed(String name) {
            return this.methodsByName.getOrDefault(name, List.of());
        }

        private Method propertyMethod(String name, int parameterCount) {
            return this.properties.get(new MethodKey(name, parameterCount));
        }

        private static boolean betterPropertyMethod(Class<?> actual, Method candidate, Method previous) {
            int candidateDistance = inheritanceDistance(actual, candidate.getDeclaringClass());
            int previousDistance = inheritanceDistance(actual, previous.getDeclaringClass());
            return candidateDistance < previousDistance || (candidateDistance == previousDistance
                && candidate.toGenericString().compareTo(previous.toGenericString()) < 0);
        }

        private static int inheritanceDistance(Class<?> actual, Class<?> declaring) {
            int distance = 0;
            for (Class<?> current = actual; current != null; current = current.getSuperclass()) {
                if (current == declaring) return distance;
                distance++;
            }
            return 64;
        }
    }

    private record MethodKey(String name, int parameterCount) { }
}
