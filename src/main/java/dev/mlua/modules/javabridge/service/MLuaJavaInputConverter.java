package dev.mlua.modules.javabridge.service;

import dev.mlua.api.MLuaLuaCoercions;
import java.lang.reflect.Array;
import java.lang.reflect.Executable;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.jse.CoerceLuaToJava;

/** Converts script values and invocation arguments into the requested Java types. */
final class MLuaJavaInputConverter {
    private final MLuaJavaService service;
    private final MLuaJavaTypedValueConverter typedValues;

    MLuaJavaInputConverter(MLuaJavaService service, MLuaJavaTypedValueConverter typedValues) {
        this.service = service;
        this.typedValues = typedValues;
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

    void validateLuaConversionBudget(LuaValue value, MLuaJavaConversionBudget budget) {
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
            return typedValues.convertTypedValue(unwrapJavaValue(value), targetType);
        }
        if (MLuaLuaCoercions.hasAdapter(rawType)) {
            return MLuaLuaCoercions.javaValue(value, targetType);
        }
        if (!value.istable()) return typedValues.convertTypedValue(unwrapJavaValue(value), targetType);
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
            return typedValues.convertValue(luaTableValueContents(source, activeTables, depth), rawType);
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
            converted[i] = typedValues.convertTypedValue(rawArgs[i], genericTypes[i], new IdentityHashMap<>(), 0, budget);
        }
        return converted;
    }


    private Object[] convertVarArgs(Object[] rawArgs, Executable executable, MLuaJavaConversionBudget budget) {
        Class<?>[] parameterTypes = executable.getParameterTypes();
        Type[] genericTypes = executable.getGenericParameterTypes();
        int fixedCount = parameterTypes.length - 1;
        Object[] converted = new Object[parameterTypes.length];
        for (int i = 0; i < fixedCount; i++) {
            converted[i] = typedValues.convertTypedValue(rawArgs[i], genericTypes[i], new IdentityHashMap<>(), 0, budget);
        }

        Class<?> componentType = parameterTypes[fixedCount].getComponentType();
        Type componentGenericType = genericTypes[fixedCount] instanceof java.lang.reflect.GenericArrayType genericArray
            ? genericArray.getGenericComponentType() : componentType;
        Object varArray = Array.newInstance(componentType, rawArgs.length - fixedCount);
        for (int i = fixedCount; i < rawArgs.length; i++) {
            Array.set(varArray, i - fixedCount,
                typedValues.convertTypedValue(rawArgs[i], componentGenericType, new IdentityHashMap<>(), 0, budget));
        }
        converted[fixedCount] = varArray;
        return converted;
    }


}
