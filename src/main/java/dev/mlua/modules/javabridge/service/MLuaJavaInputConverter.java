package dev.mlua.modules.javabridge.service;

import dev.mlua.api.MLuaLuaCoercions;
import java.lang.reflect.Array;
import java.lang.reflect.Executable;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.jse.CoerceJavaToLua;
import org.luaj.vm2.lib.jse.CoerceLuaToJava;

/** Converts script values and invocation arguments into the requested Java types. */
final class MLuaJavaInputConverter {
    private final MLuaJavaService service;

    MLuaJavaInputConverter(MLuaJavaService service) {
        this.service = service;
    }

    Object unwrapJavaValue(LuaValue value) {
        validateLuaConversionBudget(value);
        return unwrapJavaValue(value, new IdentityHashMap<>(), 0);
    }

    Object unwrapJavaValue(LuaValue value, IdentityHashMap<LuaTable, Boolean> activeTables, int depth) {
        if (value.isnil()) {
            return null;
        }
        if (MLuaLuaCoercions.isJavaNull(value)) {
            return null;
        }
        if (value.isuserdata()) {
            Object userdata = value.touserdata();
            if (userdata instanceof dev.mlua.api.MLuaScopedJavaReference scoped) {
                try {
                    return scoped.unwrap();
                } catch (IllegalStateException invalidLifetime) {
                    throw new LuaError("[MLua] " + invalidLifetime.getMessage());
                }
            }
        }
        if (value.istable()) {
            LuaValue proxiedObject = service.javaProxyField(value, "__javaObject");
            if (!proxiedObject.isnil()) {
                Object userdata = proxiedObject.touserdata();
                if (userdata != null) {
                    return userdata;
                }
            }

            LuaValue proxiedClass = service.javaProxyField(value, "__javaClass");
            if (proxiedClass.isuserdata(Class.class)) {
                return proxiedClass.touserdata(Class.class);
            }
        }
        if (value.istable()) return luaTableValue(value.checktable(), activeTables, depth);
        return CoerceLuaToJava.coerce(value, Object.class);
    }

    private Object luaTableValue(LuaTable table, IdentityHashMap<LuaTable, Boolean> activeTables, int depth) {
        MLuaJavaService.requireConversionDepth(depth, "Lua-to-Java");
        if (activeTables.put(table, Boolean.TRUE) != null) {
            throw new LuaError("[MLua] Cyclic Lua tables cannot be passed as Java values");
        }
        try {
            return luaTableValueContents(table, activeTables, depth);
        } finally {
            activeTables.remove(table);
        }
    }

    private Object luaTableValueContents(LuaTable table, IdentityHashMap<LuaTable, Boolean> activeTables, int depth) {
        List<Object> list = new ArrayList<>();
        LuaValue key = LuaValue.NIL;
        int count = 0;
        int maximum = 0;
        boolean arrayKeys = true;
        while (true) {
            Varargs entry = table.next(key);
            key = entry.arg1();
            if (key.isnil()) break;
            count++;
            MLuaJavaService.requireConversionEntries(count, "Lua-to-Java");
            if (key.type() != LuaValue.TNUMBER || key.todouble() < 1 || key.todouble() > Integer.MAX_VALUE
                || key.todouble() != Math.rint(key.todouble())) {
                arrayKeys = false;
                continue;
            }
            maximum = Math.max(maximum, key.toint());
        }
        if (arrayKeys && maximum == count) {
            for (int index = 1; index <= maximum; index++) {
                list.add(unwrapJavaValue(table.rawget(index), activeTables, depth + 1));
            }
            return list;
        }
        Map<Object, Object> map = new LinkedHashMap<>();
        key = LuaValue.NIL;
        while (true) {
            Varargs entry = table.next(key);
            key = entry.arg1();
            if (key.isnil()) return map;
            MLuaJavaContainerFactory.putConvertedMapEntry(map, service.unwrapJavaMapKey(key),
                unwrapJavaValue(entry.arg(2), activeTables, depth + 1));
        }
    }

    Object convertLuaValue(LuaValue value, Type targetType) {
        return convertLuaValue(value, targetType, new MLuaJavaConversionBudget());
    }

    Object convertLuaValue(LuaValue value, Type targetType, MLuaJavaConversionBudget budget) {
        validateLuaConversionBudget(value, budget);
        return convertLuaValue(value, targetType, new IdentityHashMap<>(), 0);
    }

    private void validateLuaConversionBudget(LuaValue value, MLuaJavaConversionBudget budget) {
        if (!value.istable() || service.isJavaProxy(value)) return;
        long[] entries = {0};
        countLuaTableEntries(value.checktable(), 0, new IdentityHashMap<>(), entries);
        budget.add(entries[0], "Lua-to-Java");
    }

    private void validateLuaConversionBudget(LuaValue value) {
        validateLuaConversionBudget(value, new MLuaJavaConversionBudget());
    }

    private void countLuaTableEntries(LuaTable table, int depth,
            IdentityHashMap<LuaTable, Boolean> activeTables, long[] entries) {
        MLuaJavaService.requireConversionDepth(depth, "Lua-to-Java");
        if (activeTables.put(table, Boolean.TRUE) != null) {
            throw new LuaError("[MLua] Cyclic Lua tables cannot be converted to Java values");
        }
        try {
            LuaValue key = LuaValue.NIL;
            while (true) {
                Varargs entry = table.next(key);
                key = entry.arg1();
                if (key.isnil()) break;
                entries[0]++;
                MLuaJavaService.requireConversionEntries(entries[0], "Lua-to-Java");
                countNestedTable(key, depth + 1, activeTables, entries);
                countNestedTable(entry.arg(2), depth + 1, activeTables, entries);
            }
        } finally {
            activeTables.remove(table);
        }
    }

    private void countNestedTable(LuaValue value, int depth,
            IdentityHashMap<LuaTable, Boolean> activeTables, long[] entries) {
        if (value.istable() && !service.isJavaProxy(value)) {
            countLuaTableEntries(value.checktable(), depth, activeTables, entries);
        }
    }

    Object convertLuaValue(LuaValue value, Type targetType, IdentityHashMap<LuaTable, Boolean> activeTables, int depth) {
        Class<?> rawType = service.rawClass(targetType);
        if (service.isJavaProxy(value)) {
            return convertTypedValue(unwrapJavaValue(value), targetType);
        }
        if (MLuaLuaCoercions.hasAdapter(rawType)) {
            return MLuaLuaCoercions.javaValue(value, targetType);
        }
        if (!value.istable()) return convertTypedValue(unwrapJavaValue(value), targetType);
        LuaTable source = value.checktable();
        if (rawType.isInterface() && !Map.class.isAssignableFrom(rawType)
            && !Iterable.class.isAssignableFrom(rawType)) {
            return service.createLuaInterfaceProxy(new Class<?>[]{rawType}, source, service.activeProxyContext());
        }
        MLuaJavaService.requireConversionDepth(depth, "Lua-to-Java");
        if (activeTables.put(source, Boolean.TRUE) != null) {
            throw new LuaError("[MLua] Cyclic Lua tables cannot be assigned to Java fields");
        }
        try {
            if (rawType.isArray()) {
                Type componentType = targetType instanceof java.lang.reflect.GenericArrayType genericArray
                    ? genericArray.getGenericComponentType() : rawType.getComponentType();
                List<Object> elements = luaArrayValues(source, componentType, activeTables, depth);
                Object array = Array.newInstance(rawType.getComponentType(), elements.size());
                for (int i = 0; i < elements.size(); i++) Array.set(array, i, elements.get(i));
                return array;
            }
            if (Iterable.class.isAssignableFrom(rawType)) {
                List<Object> elements = luaArrayValues(source, service.typeArgument(targetType, 0), activeTables, depth);
                Collection<Object> collection = MLuaJavaContainerFactory.newCollection(rawType, elements);
                if (collection != null) return collection;
                throw new LuaError("[MLua] Cannot assign a Lua array to Java Iterable type " + rawType.getName());
            }
            if (Map.class.isAssignableFrom(rawType)) {
                Map<Object, Object> values = new LinkedHashMap<>();
                LuaValue key = LuaValue.NIL;
                while (true) {
                    Varargs entry = source.next(key);
                    key = entry.arg1();
                    if (key.isnil()) break;
                    MLuaJavaService.requireConversionEntries(values.size() + 1, "Lua-to-Java");
                    Object convertedKey = convertLuaValue(key, service.typeArgument(targetType, 0), activeTables, depth + 1);
                    Object convertedValue = convertLuaValue(entry.arg(2), service.typeArgument(targetType, 1), activeTables, depth + 1);
                    MLuaJavaContainerFactory.putConvertedMapEntry(values, convertedKey, convertedValue);
                }
                Map<Object, Object> converted = MLuaJavaContainerFactory.newMap(rawType, values);
                if (converted != null) return converted;
                throw new LuaError("[MLua] Cannot assign a Lua table to Java map type " + rawType.getName());
            }
            return convertValue(luaTableValueContents(source, activeTables, depth), rawType);
        } finally {
            activeTables.remove(source);
        }
    }

    private List<Object> luaArrayValues(LuaTable table, Type elementType,
                                        IdentityHashMap<LuaTable, Boolean> activeTables, int depth) {
        List<Object> values = new ArrayList<>();
        for (int i = 1; ; i++) {
            LuaValue value = table.rawget(i);
            if (value.isnil()) break;
            MLuaJavaService.requireConversionEntries(values.size() + 1, "Lua-to-Java");
            values.add(convertLuaValue(value, elementType, activeTables, depth + 1));
        }
        LuaValue key = LuaValue.NIL;
        int count = 0;
        while (true) {
            Varargs entry = table.next(key);
            key = entry.arg1();
            if (key.isnil()) break;
            MLuaJavaService.requireConversionEntries(count + 1, "Lua-to-Java");
            if (key.type() != LuaValue.TNUMBER || key.todouble() < 1 || key.todouble() > values.size()
                || key.todouble() != Math.rint(key.todouble())) {
                throw new LuaError("[MLua] Java collection fields require a dense Lua array table");
            }
            count++;
        }
        if (count != values.size()) throw new LuaError("[MLua] Java collection fields require a dense Lua array table");
        return values;
    }


    Object[] convertArguments(Object[] rawArgs, Executable executable) {
        Class<?>[] parameterTypes = executable.getParameterTypes();
        Type[] genericTypes = executable.getGenericParameterTypes();
        MLuaJavaConversionBudget budget = new MLuaJavaConversionBudget();
        if (executable.isVarArgs() && !service.hasPackedVarargs(parameterTypes, rawArgs)) {
            return convertVarArgs(rawArgs, executable, budget);
        }

        Object[] converted = new Object[rawArgs.length];
        for (int i = 0; i < rawArgs.length; i++) {
            converted[i] = convertTypedValue(rawArgs[i], genericTypes[i], new IdentityHashMap<>(), 0, budget);
        }
        return converted;
    }


    private Object[] convertVarArgs(Object[] rawArgs, Executable executable, MLuaJavaConversionBudget budget) {
        Class<?>[] parameterTypes = executable.getParameterTypes();
        Type[] genericTypes = executable.getGenericParameterTypes();
        int fixedCount = parameterTypes.length - 1;
        Object[] converted = new Object[parameterTypes.length];
        for (int i = 0; i < fixedCount; i++) {
            converted[i] = convertTypedValue(rawArgs[i], genericTypes[i], new IdentityHashMap<>(), 0, budget);
        }

        Class<?> componentType = parameterTypes[fixedCount].getComponentType();
        Type componentGenericType = genericTypes[fixedCount] instanceof java.lang.reflect.GenericArrayType genericArray
            ? genericArray.getGenericComponentType() : componentType;
        Object varArray = Array.newInstance(componentType, rawArgs.length - fixedCount);
        for (int i = fixedCount; i < rawArgs.length; i++) {
            Array.set(varArray, i - fixedCount,
                convertTypedValue(rawArgs[i], componentGenericType, new IdentityHashMap<>(), 0, budget));
        }
        converted[fixedCount] = varArray;
        return converted;
    }

    private Object convertTypedValue(Object rawValue, Type targetType) {
        return convertTypedValue(rawValue, targetType, new IdentityHashMap<>(), 0,
            new MLuaJavaConversionBudget());
    }

    private Object convertTypedValue(Object rawValue, Type targetType,
                                     IdentityHashMap<Object, Boolean> activeContainers, int depth,
                                     MLuaJavaConversionBudget budget) {
        if (rawValue instanceof MLuaJavaTableArgument tableArgument) {
            Class<?> targetClass = service.rawClass(targetType);
            if (MLuaLuaCoercions.hasAdapter(targetClass) || targetClass.isArray() || Iterable.class.isAssignableFrom(targetClass)
                || Map.class.isAssignableFrom(targetClass) || targetClass.isInterface()) {
                return convertLuaValue(tableArgument.value(), targetType, budget);
            }
            validateLuaConversionBudget(tableArgument.value(), budget);
            rawValue = tableArgument.defaultValue();
        }
        Class<?> rawType = service.rawClass(targetType);
        boolean recursiveTarget = targetType instanceof java.lang.reflect.GenericArrayType
            || rawType.isArray() || Iterable.class.isAssignableFrom(rawType)
            || Map.class.isAssignableFrom(rawType);
        boolean containerValue = rawValue != null && (rawValue.getClass().isArray()
            || rawValue instanceof Iterable<?> || rawValue instanceof Map<?, ?>);
        if (Optional.class.isAssignableFrom(rawType) && containerValue) {
            MLuaJavaService.requireConversionDepth(depth, "Java-to-Java");
        }
        if (recursiveTarget && containerValue) {
            MLuaJavaService.requireConversionDepth(depth, "Java-to-Java");
            if (activeContainers.put(rawValue, Boolean.TRUE) != null) {
                throw new LuaError("[MLua] Cyclic Java arrays and collections cannot be converted to typed Java values");
            }
            try {
                return convertTypedValueContents(rawValue, targetType, activeContainers, depth, budget);
            } finally {
                activeContainers.remove(rawValue);
            }
        }
        return convertTypedValueContents(rawValue, targetType, activeContainers, depth, budget);
    }

    private Object convertTypedValueContents(Object rawValue, Type targetType,
                                             IdentityHashMap<Object, Boolean> activeContainers, int depth,
                                             MLuaJavaConversionBudget budget) {
        if (targetType instanceof java.lang.reflect.GenericArrayType genericArray) {
            if (rawValue == null) return null;
            Type componentType = genericArray.getGenericComponentType();
            Class<?> componentClass = service.rawClass(componentType);
            List<?> source;
            if (rawValue instanceof Iterable<?> iterable) {
                source = MLuaJavaService.copyBoundedIterable(iterable, "Java-to-Java", budget);
            } else if (rawValue != null && rawValue.getClass().isArray()) {
                int length = Array.getLength(rawValue);
                budget.add(length, "Java-to-Java");
                List<Object> values = new ArrayList<>(length);
                for (int i = 0; i < length; i++) values.add(Array.get(rawValue, i));
                source = values;
            } else {
                throw new LuaError("[MLua] Cannot convert value to Java array type " + componentClass.getName() + "[]");
            }
            Object result = Array.newInstance(componentClass, source.size());
            for (int i = 0; i < source.size(); i++) {
                Array.set(result, i, convertTypedValue(source.get(i), componentType, activeContainers,
                    depth + 1, budget));
            }
            return result;
        }
        Class<?> rawType = service.rawClass(targetType);
        if (Optional.class.isAssignableFrom(rawType)) {
            if (rawValue instanceof Optional<?> source) {
                if (rawType.isInstance(source) && !(targetType instanceof ParameterizedType)) return source;
                return source.map(value -> convertTypedValue(value, service.typeArgument(targetType, 0),
                    activeContainers, depth + 1, budget));
            }
            return rawValue == null ? Optional.empty() : Optional.ofNullable(
                convertTypedValue(rawValue, service.typeArgument(targetType, 0), activeContainers, depth + 1, budget));
        }
        if (rawValue == null) return convertValue(null, rawType);
        if (rawType.isArray() && (rawValue.getClass().isArray() || rawValue instanceof Iterable<?>)) {
            List<Object> source = new ArrayList<>();
            if (rawValue instanceof Iterable<?> iterable) {
                source = MLuaJavaService.copyBoundedIterable(iterable, "Java-to-Java", budget);
            } else {
                int length = Array.getLength(rawValue);
                budget.add(length, "Java-to-Java");
                for (int index = 0; index < length; index++) {
                    source.add(Array.get(rawValue, index));
                }
            }
            Class<?> componentType = rawType.getComponentType();
            Object result = Array.newInstance(componentType, source.size());
            for (int index = 0; index < source.size(); index++) {
                Array.set(result, index, convertTypedValue(source.get(index), componentType, activeContainers,
                    depth + 1, budget));
            }
            return result;
        }
        if (rawType.isArray() && !(rawValue.getClass().isArray() || rawValue instanceof Iterable<?>)) {
            throw new LuaError("[MLua] Java array parameters require a dense Lua array table, Java array, or Java collection; got "
                + rawValue.getClass().getName());
        }
        if (Iterable.class.isAssignableFrom(rawType) && rawValue instanceof Map<?, ?>) {
            throw new LuaError("[MLua] Java Iterable parameters require a dense Lua array table");
        }
        if (Iterable.class.isAssignableFrom(rawType)
            && (rawValue instanceof Iterable<?> || rawValue.getClass().isArray())) {
            List<Object> source = new ArrayList<>();
            if (rawValue instanceof Iterable<?> iterable) {
                source = MLuaJavaService.copyBoundedIterable(iterable, "Java-to-Java", budget);
            } else {
                int length = Array.getLength(rawValue);
                budget.add(length, "Java-to-Java");
                for (int index = 0; index < length; index++) {
                    source.add(Array.get(rawValue, index));
                }
            }
            List<Object> converted = new ArrayList<>(source.size());
            Type elementType = service.typeArgument(targetType, 0);
            for (Object value : source) converted.add(convertTypedValue(value, elementType,
                activeContainers, depth + 1, budget));
            Collection<Object> collection = MLuaJavaContainerFactory.newCollection(rawType, converted);
            if (collection != null) return collection;
            throw new LuaError("[MLua] Cannot convert Java values to Iterable type " + rawType.getName());
        }
        if (Iterable.class.isAssignableFrom(rawType)) {
            throw new LuaError("[MLua] Java Iterable parameters require a dense Lua array table, Java array, or Java collection; got "
                + rawValue.getClass().getName());
        }
        if (Map.class.isAssignableFrom(rawType)
            && (rawValue instanceof Map<?, ?> || rawValue instanceof Collection<?> collection && collection.isEmpty())) {
            Map<?, ?> source = rawValue instanceof Map<?, ?> map ? map : Map.of();
            Map<Object, Object> converted = new LinkedHashMap<>();
            Type keyType = service.typeArgument(targetType, 0);
            Type valueType = service.typeArgument(targetType, 1);
            source.forEach((key, value) -> {
                budget.add(1, "Java-to-Java");
                MLuaJavaContainerFactory.putConvertedMapEntry(converted,
                    convertTypedValue(key, keyType, activeContainers, depth + 1, budget),
                    convertTypedValue(value, valueType, activeContainers, depth + 1, budget));
            });
            Map<Object, Object> result = MLuaJavaContainerFactory.newMap(rawType, converted);
            if (result != null) return result;
            throw new LuaError("[MLua] Cannot convert Java values to map type " + rawType.getName());
        }
        return convertValue(rawValue, rawType, budget);
    }

    Object convertValue(Object rawValue, Class<?> targetType) {
        return convertValue(rawValue, targetType, new MLuaJavaConversionBudget());
    }

    Object convertValue(Object rawValue, Class<?> targetType, MLuaJavaConversionBudget budget) {
        if (rawValue == null) {
            if (targetType.isPrimitive()) {
                throw new LuaError("[MLua] Cannot assign nil to Java primitive type " + targetType.getName());
            }
            return null;
        }
        if (targetType.isInstance(rawValue)) {
            return rawValue;
        }
        if (MLuaLuaCoercions.hasAdapter(targetType)) {
            try {
                return MLuaLuaCoercions.javaValue(MLuaLuaCoercions.toLuaValue(rawValue), targetType);
            } catch (RuntimeException failure) {
                throw new LuaError("[MLua] Registered Lua adapter could not convert a value to Java "
                    + targetType.getName() + ": " + failure.getMessage());
            }
        }
        if (targetType.isArray()) {
            return convertArrayValue(rawValue, targetType.getComponentType(), budget);
        }
        Class<?> boxedType = service.boxType(targetType);
        if (boxedType == String.class) {
            if (rawValue instanceof CharSequence || rawValue instanceof Character) {
                return String.valueOf(rawValue);
            }
            throw new LuaError("[MLua] Cannot coerce " + rawValue.getClass().getName() + " to String");
        }
        if (boxedType.isEnum() && rawValue instanceof String name) {
            Object[] constants = boxedType.getEnumConstants();
            for (Object constant : constants) {
                if (((Enum<?>) constant).name().equals(name)) {
                    return constant;
                }
            }
            List<String> names = new ArrayList<>(constants.length);
            for (Object constant : constants) names.add(((Enum<?>) constant).name());
            throw new LuaError("[MLua] Unknown " + boxedType.getName() + " value '" + name
                + "'. Expected one of: " + names);
        }
        if (boxedType == Character.class) {
            return convertCharacter(rawValue);
        }
        if (MLuaJavaConversionTypes.isNumericType(boxedType) && rawValue instanceof Number number) {
            return convertNumber(number, boxedType);
        }
        if (boxedType == Boolean.class && rawValue instanceof Boolean) {
            return rawValue;
        }
        return CoerceLuaToJava.coerce(CoerceJavaToLua.coerce(rawValue), targetType);
    }


    private Object convertArrayValue(Object rawValue, Class<?> componentType, MLuaJavaConversionBudget budget) {
        if (rawValue.getClass().isArray()) {
            int length = Array.getLength(rawValue);
            budget.add(length, "Java-to-Java");
            Object array = Array.newInstance(componentType, length);
            for (int i = 0; i < length; i++) {
                Array.set(array, i, convertValue(Array.get(rawValue, i), componentType, budget));
            }
            return array;
        }
        if (rawValue instanceof Iterable<?> iterable) {
            List<Object> values = MLuaJavaService.copyBoundedIterable(iterable, "Java-to-Java", budget);
            Object array = Array.newInstance(componentType, values.size());
            int index = 0;
            for (Object item : values) {
                Array.set(array, index++, convertValue(item, componentType, budget));
            }
            return array;
        }
        throw new LuaError("[MLua] Cannot coerce " + rawValue.getClass().getName() + " to array type " + componentType.getName() + "[]");
    }

    private Object convertCharacter(Object rawValue) {
        if (rawValue instanceof Character) {
            return rawValue;
        }
        if (rawValue instanceof Number number) {
            int codePoint = (Integer) convertNumber(number, Integer.class);
            if (codePoint < Character.MIN_VALUE || codePoint > Character.MAX_VALUE) {
                throw new LuaError("[MLua] Character value is outside the char range: " + codePoint);
            }
            return (char) codePoint;
        }
        String string = String.valueOf(rawValue);
        if (string.length() != 1) {
            throw new LuaError("[MLua] Cannot coerce string '" + string + "' to char");
        }
        return string.charAt(0);
    }

    Object convertNumber(Number number, Class<?> targetType) {
        if (targetType == Byte.class || targetType == Short.class
            || targetType == Integer.class || targetType == Long.class) {
            long integral;
            try {
                integral = new BigDecimal(number.toString()).longValueExact();
            } catch (NumberFormatException | ArithmeticException error) {
                throw new LuaError("[MLua] Cannot coerce " + number + " to " + targetType.getSimpleName());
            }
            if (targetType == Byte.class && integral >= Byte.MIN_VALUE && integral <= Byte.MAX_VALUE) return (byte) integral;
            if (targetType == Short.class && integral >= Short.MIN_VALUE && integral <= Short.MAX_VALUE) return (short) integral;
            if (targetType == Integer.class && integral >= Integer.MIN_VALUE && integral <= Integer.MAX_VALUE) return (int) integral;
            if (targetType == Long.class) return integral;
            throw new LuaError("[MLua] " + number + " is outside the range of " + targetType.getSimpleName());
        }
        if (targetType == Float.class) {
            float converted = number.floatValue();
            if (Float.isFinite(converted)) return converted;
            throw new LuaError("[MLua] Cannot coerce " + number + " to Float");
        }
        if (targetType == Double.class) {
            double converted = number.doubleValue();
            if (Double.isFinite(converted)) return converted;
            throw new LuaError("[MLua] Cannot coerce " + number + " to Double");
        }
        return number;
    }

}
