package dev.mlua.minecraft.binding;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import org.luaj.vm2.LuaValue;

/** Selects the best Java overload for a set of Lua arguments using native-control conversions. */
final class MLuaNativeControlOverloadResolver {
    private MLuaNativeControlOverloadResolver() { }

    static Resolution resolve(List<Method> candidates, LuaValue[] values, boolean allowNil,
                              Predicate<Method> accepted) {
        Method selected = null;
        Object[] selectedArguments = null;
        int bestScore = Integer.MAX_VALUE;
        List<Method> ties = null;

        for (Method method : candidates) {
            if (!accepted.test(method) || method.getParameterCount() != values.length) continue;
            Object[] converted = new Object[values.length];
            int score = 0;
            boolean compatible = true;
            Class<?>[] parameterTypes = method.getParameterTypes();
            for (int index = 0; index < values.length; index++) {
                try {
                    MLuaNativeControlValueAdapter.Conversion conversion =
                        MLuaNativeControlValueAdapter.convertArgument(values[index], parameterTypes[index], allowNil);
                    converted[index] = conversion.value();
                    score += conversion.score();
                } catch (IllegalArgumentException unsupportedArgument) {
                    compatible = false;
                    break;
                }
            }
            if (!compatible) continue;
            if (score < bestScore) {
                selected = method;
                selectedArguments = converted;
                bestScore = score;
                ties = null;
            } else if (score == bestScore) {
                if (ties == null) {
                    ties = new ArrayList<>(2);
                    ties.add(selected);
                }
                ties.add(method);
            }
        }

        if (selected == null) return Resolution.NONE;
        if (ties != null) {
            selected = MLuaNativeControlReflection.mostSpecificMethod(ties);
            if (selected == null) {
                List<String> signatures = ties.stream().map(Method::toGenericString).sorted().toList();
                return new Resolution(null, null, signatures);
            }
            selectedArguments = new Object[values.length];
            Class<?>[] parameterTypes = selected.getParameterTypes();
            for (int index = 0; index < values.length; index++) {
                selectedArguments[index] = MLuaNativeControlValueAdapter.convertArgument(
                    values[index], parameterTypes[index], allowNil).value();
            }
        }
        return new Resolution(selected, selectedArguments, List.of());
    }

    record Resolution(Method method, Object[] arguments, List<String> ambiguousSignatures) {
        private static final Resolution NONE = new Resolution(null, null, List.of());

        Resolution {
            ambiguousSignatures = List.copyOf(ambiguousSignatures);
        }

        boolean found() { return this.method != null; }
        boolean ambiguous() { return !this.ambiguousSignatures.isEmpty(); }
    }
}
