package dev.mlua.modules.javabridge.service;

import dev.mlua.api.MLuaLuaCoercions;

import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

/** Scores Java parameter compatibility without constructing conversion results. */
final class MLuaJavaCoercionScorer {
    private final MLuaJavaService service;

    MLuaJavaCoercionScorer(MLuaJavaService service) {
        this.service = service;
    }

    int score(Object value, Class<?> parameterType, Type targetType) {
        if (value instanceof MLuaJavaTableArgument tableArgument) {
            return luaTableCoercionScore(tableArgument, parameterType, targetType);
        }
        if (value instanceof LuaValue luaValue) {
            return luaValueCoercionScore(luaValue, parameterType, targetType);
        }
        if (value == null) {
            return parameterType.isPrimitive() ? -1 : 4;
        }

        if (parameterType.isArray()) {
            if (value.getClass().isArray()) {
                return parameterType.isInstance(value) ? 0 : 2;
            }
            if (value instanceof Iterable<?>) {
                return 3;
            }
        }

        if (Iterable.class.isAssignableFrom(parameterType)
            && (value instanceof Iterable<?> || value.getClass().isArray())) {
            if (MLuaJavaContainerFactory.supportsCollectionType(parameterType)) return 3;
        }

        if (Map.class.isAssignableFrom(parameterType)
            && (value instanceof Map<?, ?> || value instanceof Collection<?> collection && collection.isEmpty())) {
            if (MLuaJavaContainerFactory.supportsMapType(parameterType)) return 3;
        }

        Class<?> boxedType = service.boxType(parameterType);
        if (boxedType.isInstance(value)) return 0;
        if (MLuaLuaCoercions.hasAdapter(boxedType)) return 3;
        if (Optional.class.isAssignableFrom(boxedType)) {
            Type elementType = service.typeArgument(targetType, 0);
            if (value instanceof Optional<?> source) {
                if (source.isEmpty()) return 0;
                int sourceScore = score(source.get(), service.rawClass(elementType), elementType);
                return sourceScore < 0 ? -1 : sourceScore;
            }
            int elementScore = score(value, service.rawClass(elementType), elementType);
            // Optional is a wrapping conversion. Prefer direct Java values when
            // both the Optional element type and another overload can accept one.
            return elementScore < 0 ? -1 : elementScore + 3;
        }
        if (boxedType.isInstance(value)) {
            return 0;
        }
        if (value instanceof Boolean && MLuaJavaConversionTypes.isNumericType(boxedType)) {
            return -1;
        }
        if (boxedType == String.class) {
            return value instanceof CharSequence || value instanceof Character ? 2 : -1;
        }
        if (boxedType.isEnum() && value instanceof String) {
            return 2;
        }
        if (MLuaJavaConversionTypes.isNumericType(boxedType) && value instanceof Number number) {
            return numericCoercionScore(number, boxedType);
        }
        if (boxedType == Character.class && (value instanceof Character || value instanceof String || value instanceof Number)) {
            return 2;
        }
        if (boxedType == Boolean.class && value instanceof Boolean) {
            return 0;
        }
        if (parameterType == Object.class) {
            return 10;
        }
        if (boxedType.isAssignableFrom(value.getClass())) {
            return 1 + inheritanceDistance(value.getClass(), boxedType);
        }

        try {
            service.convertValue(value, parameterType);
            if (Number.class.isAssignableFrom(boxedType) && value instanceof Number) {
                return 2;
            }
            return 3;
        } catch (RuntimeException ex) {
            return -1;
        }
    }

    /** Checks table compatibility without allocating a proxy or invoking a user collection constructor. */
    private int luaTableCoercionScore(LuaValue value, Class<?> parameterType, Type targetType) {
        return luaTableCoercionScore(value, parameterType, targetType, 0, new MLuaJavaConversionBudget(), null);
    }

    private int luaTableCoercionScore(MLuaJavaTableArgument argument, Class<?> parameterType, Type targetType) {
        return luaTableCoercionScore(argument.value(), parameterType, targetType, 0,
            new MLuaJavaConversionBudget(), argument);
    }

    private int luaTableCoercionScore(LuaValue value, Class<?> parameterType, Type targetType,
                                      int depth, MLuaJavaConversionBudget budget) {
        return luaTableCoercionScore(value, parameterType, targetType, depth, budget, null);
    }

    private int luaTableCoercionScore(LuaValue value, Class<?> parameterType, Type targetType,
                                      int depth, MLuaJavaConversionBudget budget, MLuaJavaTableArgument genericValue) {
        if (value == null || value.isnil()) return parameterType.isPrimitive() ? -1 : 4;
        if (!value.istable() || service.isJavaProxy(value)) {
            return score(service.unwrapJavaValue(value), parameterType, targetType);
        }
        if (MLuaLuaCoercions.hasAdapter(parameterType)) return 3;
        MLuaJavaService.requireConversionDepth(depth, "Lua-to-Java overload resolution");
        Class<?> rawType = service.rawClass(targetType);
        LuaTable table = value.checktable();
        if (Optional.class.isAssignableFrom(rawType)) {
            Type elementType = service.typeArgument(targetType, 0);
            int elementScore = luaTableCoercionScore(value, service.rawClass(elementType), elementType,
                depth, budget, genericValue);
            return elementScore < 0 ? -1 : elementScore + 3;
        }
        if (rawType.isArray() || Iterable.class.isAssignableFrom(rawType)) {
            Type elementType = rawType.isArray()
                ? (targetType instanceof java.lang.reflect.GenericArrayType genericArray
                    ? genericArray.getGenericComponentType() : rawType.getComponentType())
                : service.typeArgument(targetType, 0);
            if (!supportsDenseArray(table)) return -1;
            long score = 3;
            for (int index = 1; ; index++) {
                LuaValue item = table.rawget(index);
                if (item.isnil()) break;
                budget.add(1, "Lua-to-Java overload resolution");
                int itemScore = luaValueCoercionScore(item, service.rawClass(elementType), elementType,
                    depth + 1, budget);
                if (itemScore < 0) return -1;
                score += itemScore;
                if (score >= Integer.MAX_VALUE) return Integer.MAX_VALUE - 1;
            }
            if (rawType.isArray() || MLuaJavaContainerFactory.supportsCollectionType(rawType)) return (int) score;
            return -1;
        }
        if (Map.class.isAssignableFrom(rawType)) {
            if (!MLuaJavaContainerFactory.supportsMapType(rawType)) return -1;
            Type keyType = service.typeArgument(targetType, 0);
            Type valueType = service.typeArgument(targetType, 1);
            long score = 3;
            LuaValue key = LuaValue.NIL;
            while (true) {
                Varargs entry = table.next(key);
                key = entry.arg1();
                if (key.isnil()) break;
                budget.add(1, "Lua-to-Java overload resolution");
                int keyScore = luaValueCoercionScore(key, service.rawClass(keyType), keyType, depth + 1, budget);
                int itemScore = luaValueCoercionScore(entry.arg(2), service.rawClass(valueType), valueType,
                    depth + 1, budget);
                if (keyScore < 0 || itemScore < 0) return -1;
                score += keyScore + itemScore;
                if (score >= Integer.MAX_VALUE) return Integer.MAX_VALUE - 1;
            }
            return (int) score;
        }
        if (rawType.isInterface()) {
            try {
                MLuaJavaInterfaceProxyFactory.validateInterfaceImplementation(new Class<?>[]{rawType}, table);
                ClassLoader loader = MLuaJavaInterfaceProxyFactory.proxyClassLoader(service, new Class<?>[]{rawType});
                if (loader == null && rawType.getClassLoader() != null) return -1;
                return 3;
            } catch (RuntimeException invalidImplementation) {
                return -1;
            }
        }
        return score(genericValue == null ? service.unwrapJavaValue(value) : genericValue.defaultValue(),
            parameterType, targetType);
    }

    private int luaValueCoercionScore(LuaValue value, Class<?> parameterType, Type targetType) {
        return luaValueCoercionScore(value, parameterType, targetType, 0, new MLuaJavaConversionBudget());
    }

    private int luaValueCoercionScore(LuaValue value, Class<?> parameterType, Type targetType,
                                      int depth, MLuaJavaConversionBudget budget) {
        if (value == null || value.isnil()) return parameterType.isPrimitive() ? -1 : 4;
        if (value.istable() && !service.isJavaProxy(value)) {
            return luaTableCoercionScore(value, parameterType, targetType, depth, budget);
        }
        return score(service.unwrapJavaValue(value), parameterType, targetType);
    }

    private boolean supportsDenseArray(LuaTable table) {
        int entryCount = 0;
        int largestIndex = 0;
        LuaValue key = LuaValue.NIL;
        while (true) {
            Varargs entry = table.next(key);
            key = entry.arg1();
            if (key.isnil()) return entryCount == largestIndex;
            if (key.type() != LuaValue.TNUMBER || !Double.isFinite(key.todouble())
                || key.todouble() < 1 || key.todouble() > Integer.MAX_VALUE
                || key.todouble() != Math.rint(key.todouble())) return false;
            entryCount++;
            if (entryCount > MLuaJavaService.MAX_CONVERSION_ENTRIES) return false;
            largestIndex = Math.max(largestIndex, key.toint());
        }
    }

    private int inheritanceDistance(Class<?> valueType, Class<?> targetType) {
        if (valueType == targetType) {
            return 0;
        }
        int distance = 0;
        for (Class<?> current = valueType; current != null; current = current.getSuperclass()) {
            if (current == targetType) {
                return distance;
            }
            distance++;
        }
        return 4;
    }

    private int numericCoercionScore(Number value, Class<?> targetType) {
        try {
            service.convertNumber(value, targetType);
        } catch (RuntimeException error) {
            return -1;
        }
        if (targetType.isInstance(value)) {
            return 0;
        }
        if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) {
            return targetType == Float.class || targetType == Double.class ? 1 : 2;
        }
        return 2;
    }

}
