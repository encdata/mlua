package dev.mlua.modules.javabridge.Service;

import dev.mlua.core.service.MLuaService;
import dev.mlua.core.service.MLuaServiceBase;
import dev.mlua.common.service.MLuaServiceMetadata;
import dev.mlua.common.mixin.MLuaClassViewer;
import dev.mlua.loaderapi.LoaderApi;
import java.lang.reflect.Array;
import java.lang.reflect.Member;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.Deque;
import java.util.Queue;
import java.util.SortedMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.jse.CoerceJavaToLua;
import org.luaj.vm2.lib.jse.CoerceLuaToJava;

public final class MLuaJavaService extends MLuaServiceBase {
    private static final Map<String, String> SERVICE_METHOD_DESCRIPTIONS = createServiceMethodDescriptions();
    private final Object javaProxyMarker = new Object();
    private final Map<String, Class<?>> resolvedClasses = new ConcurrentHashMap<>();
    private final Map<MethodCacheKey, List<Method>> resolvedMethods = new ConcurrentHashMap<>();
    private final Map<Class<?>, List<Method>> collectedMethods = new ConcurrentHashMap<>();
    private final Map<Class<?>, List<Field>> collectedFields = new ConcurrentHashMap<>();
    private final Map<Class<?>, Constructor<?>[]> publicConstructors = new ConcurrentHashMap<>();

    public MLuaJavaService(MLuaService.Context context) {
        super(context);
    }

    public static Map<String, String> serviceMethodDescriptions() {
        return SERVICE_METHOD_DESCRIPTIONS;
    }

    private static Map<String, String> createServiceMethodDescriptions() {
        Map<String, String> descriptions = new LinkedHashMap<>();
        descriptions.put("LoadClass", "Loads a JVM class by fully qualified name.");
        descriptions.put("Import", "Loads a JVM class by fully qualified name; compatibility alias for LoadClass.");
        descriptions.put("import", "Loads a JVM class by fully qualified name; compatibility alias for LoadClass.");
        descriptions.put("bindClass", "Loads a JVM class by fully qualified name; compatibility alias for LoadClass.");
        descriptions.put("Package", "Returns a lazy proxy for navigating a Java package.");
        descriptions.put("package", "Returns a lazy proxy for navigating a Java package; alias for Package.");
        descriptions.put("New", "Constructs an object using a compatible public Java constructor.");
        descriptions.put("newInstance", "Constructs an object using a compatible public Java constructor; alias for New.");
        descriptions.put("Proxy", "Creates a Java interface implementation backed by Lua functions. Required abstract methods are checked at creation; default and static methods do not need Lua functions.");
        descriptions.put("createProxy", "Creates a Java interface implementation backed by Lua functions; alias for Proxy.");
        descriptions.put("IsInstance", "Checks whether a Java object matches a class or interface.");
        descriptions.put("Cast", "Checks a Java object's type and returns its live proxy.");
        descriptions.put("TypeOf", "Returns the runtime Java class name of a Java value, object proxy, or class proxy. Returns nil for nil.");
        descriptions.put("GetMethods", "Lists Java methods with structured parameter and return types, signatures, declaring classes, and abstract, default, varargs, and static status.");
        descriptions.put("GetFields", "Lists Java fields with raw and generic types, declaring classes, and whether they are static or final.");
        descriptions.put("GetField", "Reads a Java instance or static field by its exact Java name, including inherited superclass and interface fields.");
        descriptions.put("SetField", "Writes a non-final Java instance or static field and returns its new value.");
        descriptions.put("Inspect", "Returns structured metadata for a Java class.");
        descriptions.put("ViewClass", "Returns a Lua-oriented textual view of a Java class.");
        descriptions.put("ClassViewer", "Returns a Lua-oriented textual view of a Java class; alias for ViewClass.");
        descriptions.put("GetEnumConstants", "Returns the constants of a Java enum class.");
        descriptions.put("ToTable", "Copies a Java array, collection, or map into a Lua table. It raises an error for null entries, unsupported key types, or keys that collide after conversion.");
        descriptions.put("FromTable", "Converts a Lua table into a Java array, collection, or map and returns a live Java proxy. Optionally provide the element class for collections, or key and value classes for maps.");
        return java.util.Collections.unmodifiableMap(descriptions);
    }

    @Override
    public String name() {
        return "Java";
    }

    @Override
    public LuaTable toLua() {
        LuaTable table = new LuaTable();
        LuaValue loadClass = new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                String rootName = args.checkjstring(serviceArgumentIndex(args, table, 1));
                Class<?> direct = tryResolveClass(rootName);
                if (direct == null) {
                    throw new LuaError("[MLua] Could not resolve Java class '" + rootName + "'");
                }
                return wrapJavaValue(direct);
            }
        };
        table.set("LoadClass", loadClass);
        table.set("Import", loadClass);
        table.set("import", loadClass);
        table.set("bindClass", loadClass);

        LuaValue packageFn = new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                String rootName = args.checkjstring(serviceArgumentIndex(args, table, 1));
                return packageProxy(rootName);
            }
        };
        table.set("Package", packageFn);
        table.set("package", packageFn);

        LuaValue newFn = new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                int classIndex = serviceArgumentIndex(args, table, 1);
                Class<?> clazz = resolveClassArg(args.arg(classIndex));
                LuaValue constructor = wrapJavaValue(clazz).get("new");
                if (constructor.isnil()) {
                    throw new LuaError("[MLua] No public constructor found for " + clazz.getName());
                }
                return constructor.invoke(args.subargs(classIndex + 1));
            }
        };
        table.set("New", newFn);
        table.set("newInstance", newFn);

        LuaValue proxyFn = new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                int startIndex = serviceArgumentIndex(args, table, 1);
                int n = args.narg() - startIndex + 1;
                if (n < 2) {
                    throw new LuaError("[MLua] Java:Proxy requires at least one interface and a Lua table");
                }

                LuaTable implementation = args.checktable(startIndex + n - 1);
                Class<?>[] interfaces = new Class<?>[n - 1];
                for (int i = 0; i < interfaces.length; i++) {
                    Class<?> iface = resolveClassArg(args.arg(startIndex + i));
                    if (!iface.isInterface()) {
                        throw new LuaError("[MLua] Java:Proxy only supports interfaces in v1: " + iface.getName());
                    }
                    interfaces[i] = iface;
                }

                return wrapJavaValue(createLuaInterfaceProxy(interfaces, implementation));
            }
        };
        table.set("Proxy", proxyFn);
        table.set("createProxy", proxyFn);
        table.set("IsInstance", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                int valueIndex = serviceArgumentIndex(args, table, 1);
                Object value = unwrapJavaValue(args.arg(valueIndex));
                Class<?> clazz = resolveClassArg(args.arg(valueIndex + 1));
                return LuaValue.valueOf(value != null && clazz.isInstance(value));
            }
        });
        table.set("Cast", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                int valueIndex = serviceArgumentIndex(args, table, 1);
                Object value = unwrapJavaValue(args.arg(valueIndex));
                if (value == null) {
                    return LuaValue.NIL;
                }
                Class<?> clazz = resolveClassArg(args.arg(valueIndex + 1));
                if (!clazz.isInstance(value)) {
                    throw new LuaError("[MLua] Cannot cast " + value.getClass().getName() + " to " + clazz.getName());
                }
                return wrapJavaValue(value);
            }
        });
        table.set("TypeOf", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                Object value = unwrapJavaValue(args.arg(serviceArgumentIndex(args, table, 1)));
                if (value == null) {
                    return LuaValue.NIL;
                }
                Class<?> type = value instanceof Class<?> clazz ? clazz : value.getClass();
                return LuaValue.valueOf(type.getName());
            }
        });
        table.set("GetMethods", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                Class<?> clazz = resolveTargetClass(args.arg(serviceArgumentIndex(args, table, 1)));
                LuaTable methods = new LuaTable();
                int index = 1;
                for (Method method : collectMethods(clazz)) {
                    LuaTable entry = new LuaTable();
                    entry.set("Name", method.getName());
                    entry.set("DeclaringClass", method.getDeclaringClass().getName());
                    entry.set("Static", LuaValue.valueOf(Modifier.isStatic(method.getModifiers())));
                    entry.set("Abstract", LuaValue.valueOf(Modifier.isAbstract(method.getModifiers())));
                    entry.set("Default", LuaValue.valueOf(method.isDefault()));
                    entry.set("VarArgs", LuaValue.valueOf(method.isVarArgs()));
                    entry.set("ParameterCount", method.getParameterCount());
                    LuaTable parameters = new LuaTable();
                    java.lang.reflect.Parameter[] reflectedParameters = method.getParameters();
                    Type[] genericParameterTypes = method.getGenericParameterTypes();
                    for (int parameterIndex = 0; parameterIndex < reflectedParameters.length; parameterIndex++) {
                        java.lang.reflect.Parameter reflectedParameter = reflectedParameters[parameterIndex];
                        LuaTable parameter = new LuaTable();
                        parameter.set("Name", reflectedParameter.isNamePresent()
                            ? reflectedParameter.getName() : "arg" + (parameterIndex + 1));
                        parameter.set("NamePresent", LuaValue.valueOf(reflectedParameter.isNamePresent()));
                        parameter.set("Type", reflectedParameter.getType().getName());
                        parameter.set("GenericType", genericParameterTypes[parameterIndex].getTypeName());
                        parameters.set(parameterIndex + 1, parameter);
                    }
                    entry.set("Parameters", parameters);
                    entry.set("ReturnType", method.getReturnType().getName());
                    entry.set("GenericReturnType", method.getGenericReturnType().getTypeName());
                    entry.set("Descriptor", methodSignature(method));
                    methods.set(index++, entry);
                }
                return methods;
            }
        });
        table.set("GetFields", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                Class<?> clazz = resolveTargetClass(args.arg(serviceArgumentIndex(args, table, 1)));
                LuaTable fields = new LuaTable();
                int index = 1;
                for (Field field : collectFields(clazz)) {
                    LuaTable entry = new LuaTable();
                    entry.set("Name", field.getName());
                    entry.set("DeclaringClass", field.getDeclaringClass().getName());
                    entry.set("Static", LuaValue.valueOf(Modifier.isStatic(field.getModifiers())));
                    entry.set("Final", LuaValue.valueOf(Modifier.isFinal(field.getModifiers())));
                    entry.set("Type", field.getType().getName());
                    entry.set("GenericType", field.getGenericType().getTypeName());
                    fields.set(index++, entry);
                }
                return fields;
            }
        });
        table.set("GetField", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                int targetIndex = serviceArgumentIndex(args, table, 1);
                Object target = unwrapJavaValue(args.arg(targetIndex));
                if (target == null) throw new LuaError("[MLua] Java:GetField target cannot be nil");
                String fieldName = args.checkjstring(targetIndex + 1);
                boolean staticTarget = target instanceof Class<?>;
                Class<?> targetClass = staticTarget ? (Class<?>) target : target.getClass();
                Field field = resolveField(targetClass, fieldName, staticTarget);
                if (field == null) {
                    throw new LuaError("[MLua] No " + (staticTarget ? "static " : "instance ")
                        + "field " + targetClass.getName() + "." + fieldName);
                }
                try {
                    return wrapJavaValue(field.get(staticTarget ? null : target), field.getGenericType());
                } catch (IllegalAccessException | IllegalArgumentException error) {
                    throw new LuaError("[MLua] Failed to read field " + targetClass.getName() + "."
                        + fieldName + ": " + rootMessage(error));
                }
            }
        });
        table.set("SetField", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                int targetIndex = serviceArgumentIndex(args, table, 1);
                Object target = unwrapJavaValue(args.arg(targetIndex));
                if (target == null) throw new LuaError("[MLua] Java:SetField target cannot be nil");
                String fieldName = args.checkjstring(targetIndex + 1);
                boolean staticTarget = target instanceof Class<?>;
                Class<?> targetClass = staticTarget ? (Class<?>) target : target.getClass();
                Field field = resolveField(targetClass, fieldName, staticTarget);
                if (field == null) {
                    throw new LuaError("[MLua] No " + (staticTarget ? "static " : "instance ")
                        + "field " + targetClass.getName() + "." + fieldName);
                }
                if (Modifier.isFinal(field.getModifiers())) {
                    throw new LuaError("[MLua] Field " + targetClass.getName() + "." + fieldName + " is final");
                }
                try {
                    field.set(staticTarget ? null : target, convertLuaValue(args.arg(targetIndex + 2), field.getGenericType()));
                    return wrapJavaValue(field.get(staticTarget ? null : target), field.getGenericType());
                } catch (IllegalAccessException | IllegalArgumentException error) {
                    throw new LuaError("[MLua] Failed to write field " + targetClass.getName() + "."
                        + fieldName + ": " + rootMessage(error));
                }
            }
        });
        table.set("Inspect", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                Class<?> clazz = resolveTargetClass(args.arg(serviceArgumentIndex(args, table, 1)));
                return MLuaClassViewer.inspect(clazz);
            }
        });
        table.set("ViewClass", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                Class<?> clazz = resolveTargetClass(args.arg(serviceArgumentIndex(args, table, 1)));
                return LuaValue.valueOf(MLuaClassViewer.renderLua(clazz));
            }
        });
        table.set("ClassViewer", table.get("ViewClass"));
        table.set("GetEnumConstants", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                Class<?> clazz = resolveClassArg(args.arg(serviceArgumentIndex(args, table, 1)));
                if (!clazz.isEnum()) {
                    throw new LuaError("[MLua] " + clazz.getName() + " is not an enum type");
                }
                Object[] constants = clazz.getEnumConstants();
                LuaTable values = new LuaTable();
                for (int i = 0; i < constants.length; i++) {
                    values.set(i + 1, wrapJavaValue(constants[i]));
                }
                return values;
            }
        });
        table.set("ToTable", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return toLuaTable(unwrapJavaValue(args.arg(serviceArgumentIndex(args, table, 1))));
            }
        });
        table.set("FromTable", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                int valueIndex = serviceArgumentIndex(args, table, 1);
                LuaValue source = args.arg(valueIndex);
                if (!source.istable()) {
                    throw new LuaError("[MLua] Java:FromTable expects a Lua table as its first argument");
                }
                Class<?> targetType = resolveClassArg(args.arg(valueIndex + 1));
                if (!targetType.isArray() && !Iterable.class.isAssignableFrom(targetType)
                    && !Map.class.isAssignableFrom(targetType)) {
                    throw new LuaError("[MLua] Java:FromTable target must be an array, Iterable, or Map type: "
                        + targetType.getName());
                }
                Type conversionType = targetType;
                int typeArgumentCount = args.narg() - valueIndex - 1;
                if (Map.class.isAssignableFrom(targetType)) {
                    if (typeArgumentCount > 2) {
                        throw new LuaError("[MLua] Java:FromTable map conversion accepts optional key and value classes");
                    }
                    if (typeArgumentCount > 0) {
                        Type keyType = resolveClassArg(args.arg(valueIndex + 2));
                        Type valueType = typeArgumentCount == 2
                            ? resolveClassArg(args.arg(valueIndex + 3)) : Object.class;
                        conversionType = new LuaParameterizedType(targetType, keyType, valueType);
                    }
                } else if (Iterable.class.isAssignableFrom(targetType)) {
                    if (typeArgumentCount > 1) {
                        throw new LuaError("[MLua] Java:FromTable iterable conversion accepts one optional element class");
                    }
                    if (typeArgumentCount == 1) {
                        conversionType = new LuaParameterizedType(targetType,
                            resolveClassArg(args.arg(valueIndex + 2)));
                    }
                } else if (typeArgumentCount != 0) {
                    throw new LuaError("[MLua] Java:FromTable array conversion uses the component type declared by the array class");
                }
                return wrapJavaValue(convertLuaValue(source, conversionType), conversionType);
            }
        });
        MLuaServiceMetadata.attachMethodDescriptions(table, SERVICE_METHOD_DESCRIPTIONS);
        return table;
    }


    private LuaValue packageProxy(String path) {
        LuaTable proxy = new LuaTable();
        LuaTable meta = new LuaTable();
        meta.set("__index", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                LuaValue key = args.arg(2);
                if (!key.isstring()) {
                    return LuaValue.NIL;
                }

                String nextPath = path + "." + key.tojstring();
                Class<?> resolved = tryResolveClass(nextPath);
                return resolved != null ? wrapJavaValue(resolved) : packageProxy(nextPath);
            }
        });
        meta.set("__call", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                throw new LuaError("[MLua] Unresolved Java package path '" + path + "' cannot be called. Resolve a class first.");
            }
        });
        meta.set("__tostring", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return LuaValue.valueOf("JavaPackage(" + path + ")");
            }
        });
        proxy.setmetatable(meta);
        return proxy;
    }

    private Class<?> resolveClassArg(LuaValue value) {
        if (value.isstring()) {
            Class<?> resolved = tryResolveClass(value.tojstring());
            if (resolved == null) {
                throw new LuaError("[MLua] Could not resolve Java class '" + value.tojstring() + "'");
            }
            return resolved;
        }

        if (value.istable()) {
            LuaValue proxiedClass = javaProxyField(value, "__javaClass");
            if (proxiedClass.isuserdata(Class.class)) {
                return (Class<?>) proxiedClass.touserdata(Class.class);
            }
        }

        Object obj = value.touserdata();
        if (obj instanceof Class<?> clazz) {
            return clazz;
        }

        throw new LuaError("[MLua] Expected a Java class or class name");
    }

    private Class<?> resolveTargetClass(LuaValue value) {
        Object raw = unwrapJavaValue(value);
        if (raw == null) {
            throw new LuaError("[MLua] Expected a Java class or object, got nil");
        }
        return raw instanceof Class<?> clazz ? clazz : raw.getClass();
    }

    private Object createLuaInterfaceProxy(Class<?>[] interfaces, LuaTable implementation) {
        validateInterfaceImplementation(interfaces, implementation);
        for (Class<?> iface : interfaces) {
            if (!iface.isInterface()) {
                throw new LuaError("[MLua] Java:Proxy only supports interfaces in v1: " + iface.getName());
            }
        }
        ClassLoader loader = proxyClassLoader(interfaces);
        if (loader == null) {
            throw new LuaError("[MLua] Could not create Java interface proxy: no class loader can see all requested interfaces");
        }
        try {
            return Proxy.newProxyInstance(loader, interfaces, new LuaInvocationHandler(implementation));
        } catch (IllegalArgumentException error) {
            throw new LuaError("[MLua] Could not create Java interface proxy: " + error.getMessage());
        }
    }

    private void validateInterfaceImplementation(Class<?>[] interfaces, LuaTable implementation) {
        Map<String, Method> requiredMethods = new LinkedHashMap<>();
        for (Class<?> iface : interfaces) {
            if (!iface.isInterface()) {
                throw new LuaError("[MLua] Java:Proxy only supports interfaces in v1: " + iface.getName());
            }
            for (Method method : iface.getMethods()) {
                if (Modifier.isStatic(method.getModifiers()) || method.isDefault()
                    || method.getDeclaringClass() == Object.class) {
                    continue;
                }
                requiredMethods.putIfAbsent(method.getName(), method);
            }
        }

        List<String> missing = new ArrayList<>();
        for (Map.Entry<String, Method> entry : requiredMethods.entrySet()) {
            LuaValue implementationMethod = implementation.get(entry.getKey());
            if (!implementationMethod.isfunction()) {
                missing.add(entry.getValue().toGenericString());
            }
        }
        if (!missing.isEmpty()) {
            missing.sort(String::compareTo);
            throw new LuaError("[MLua] Java:Proxy is missing Lua functions for interface methods: " + missing);
        }
    }

    private ClassLoader proxyClassLoader(Class<?>[] interfaces) {
        java.util.Set<ClassLoader> candidates = new java.util.LinkedHashSet<>();
        for (Class<?> iface : interfaces) {
            candidates.add(iface.getClassLoader());
        }
        candidates.add(Thread.currentThread().getContextClassLoader());
        candidates.add(this.context.getClass().getClassLoader());
        candidates.add(getClass().getClassLoader());
        candidates.add(ClassLoader.getSystemClassLoader());

        for (ClassLoader candidate : candidates) {
            boolean visible = true;
            for (Class<?> iface : interfaces) {
                try {
                    Class<?> resolved = Class.forName(iface.getName(), false, candidate);
                    if (resolved != iface) {
                        visible = false;
                        break;
                    }
                } catch (ClassNotFoundException | LinkageError error) {
                    visible = false;
                    break;
                }
            }
            if (visible) return candidate;
        }
        return null;
    }

    private Object unwrapJavaValue(LuaValue value) {
        return unwrapJavaValue(value, new IdentityHashMap<>());
    }

    private Object unwrapJavaValue(LuaValue value, IdentityHashMap<LuaTable, Boolean> activeTables) {
        if (value.isnil()) {
            return null;
        }
        if (value.istable()) {
            LuaValue proxiedObject = javaProxyField(value, "__javaObject");
            if (!proxiedObject.isnil()) {
                Object userdata = proxiedObject.touserdata();
                if (userdata != null) {
                    return userdata;
                }
            }

            LuaValue proxiedClass = javaProxyField(value, "__javaClass");
            if (proxiedClass.isuserdata(Class.class)) {
                return proxiedClass.touserdata(Class.class);
            }
        }
        if (value.istable()) return luaTableValue(value.checktable(), activeTables);
        return CoerceLuaToJava.coerce(value, Object.class);
    }

    private Object luaTableValue(LuaTable table, IdentityHashMap<LuaTable, Boolean> activeTables) {
        if (activeTables.put(table, Boolean.TRUE) != null) {
            throw new LuaError("[MLua] Cyclic Lua tables cannot be passed as Java values");
        }
        try {
            return luaTableValueContents(table, activeTables);
        } finally {
            activeTables.remove(table);
        }
    }

    private Object luaTableValueContents(LuaTable table, IdentityHashMap<LuaTable, Boolean> activeTables) {
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
            if (!key.isnumber() || key.todouble() < 1 || key.todouble() > Integer.MAX_VALUE
                || key.todouble() != Math.rint(key.todouble())) {
                arrayKeys = false;
                continue;
            }
            maximum = Math.max(maximum, key.toint());
        }
        if (arrayKeys && maximum == count) {
            for (int index = 1; index <= maximum; index++) list.add(unwrapJavaValue(table.rawget(index), activeTables));
            return list;
        }
        Map<Object, Object> map = new LinkedHashMap<>();
        key = LuaValue.NIL;
        while (true) {
            Varargs entry = table.next(key);
            key = entry.arg1();
            if (key.isnil()) return map;
            map.put(unwrapMapKey(key), unwrapJavaValue(entry.arg(2), activeTables));
        }
    }

    private Object convertLuaValue(LuaValue value, Type targetType) {
        return convertLuaValue(value, targetType, new IdentityHashMap<>());
    }

    private Object convertLuaValue(LuaValue value, Type targetType, IdentityHashMap<LuaTable, Boolean> activeTables) {
        Class<?> rawType = rawClass(targetType);
        if (isJavaProxy(value)) {
            return convertTypedValue(unwrapJavaValue(value), targetType);
        }
        if (!value.istable()) return convertTypedValue(unwrapJavaValue(value), targetType);
        LuaTable source = value.checktable();
        if (rawType.isInterface() && !Map.class.isAssignableFrom(rawType)
            && !Iterable.class.isAssignableFrom(rawType)) {
            return createLuaInterfaceProxy(new Class<?>[]{rawType}, source);
        }
        if (activeTables.put(source, Boolean.TRUE) != null) {
            throw new LuaError("[MLua] Cyclic Lua tables cannot be assigned to Java fields");
        }
        try {
        if (rawType.isArray()) {
            Type componentType = targetType instanceof java.lang.reflect.GenericArrayType genericArray
                ? genericArray.getGenericComponentType() : rawType.getComponentType();
            List<Object> elements = luaArrayValues(source, componentType, activeTables);
            Object array = Array.newInstance(rawType.getComponentType(), elements.size());
            for (int i = 0; i < elements.size(); i++) Array.set(array, i, elements.get(i));
            return array;
        }
        if (Iterable.class.isAssignableFrom(rawType)) {
            List<Object> elements = luaArrayValues(source, typeArgument(targetType, 0), activeTables);
            Collection<Object> collection = newCollection(rawType, elements);
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
                values.put(convertLuaValue(key, typeArgument(targetType, 0), activeTables),
                    convertLuaValue(entry.arg(2), typeArgument(targetType, 1), activeTables));
            }
            Map<Object, Object> converted = newMap(rawType, values);
            if (converted != null) return converted;
            throw new LuaError("[MLua] Cannot assign a Lua table to Java map type " + rawType.getName());
        }
        return convertValue(luaTableValueContents(source, activeTables), rawType);
        } finally {
            activeTables.remove(source);
        }
    }

    private List<Object> luaArrayValues(LuaTable table, Type elementType, IdentityHashMap<LuaTable, Boolean> activeTables) {
        List<Object> values = new ArrayList<>();
        for (int i = 1; ; i++) {
            LuaValue value = table.rawget(i);
            if (value.isnil()) break;
            values.add(convertLuaValue(value, elementType, activeTables));
        }
        LuaValue key = LuaValue.NIL;
        int count = 0;
        while (true) {
            Varargs entry = table.next(key);
            key = entry.arg1();
            if (key.isnil()) break;
            if (!key.isnumber() || key.todouble() < 1 || key.todouble() > values.size()
                || key.todouble() != Math.rint(key.todouble())) {
                throw new LuaError("[MLua] Java collection fields require a dense Lua array table");
            }
            count++;
        }
        if (count != values.size()) throw new LuaError("[MLua] Java collection fields require a dense Lua array table");
        return values;
    }

    private Class<?> rawClass(Type type) {
        if (type instanceof Class<?> clazz) return clazz;
        if (type instanceof ParameterizedType parameterized && parameterized.getRawType() instanceof Class<?> clazz) return clazz;
        if (type instanceof java.lang.reflect.GenericArrayType genericArray) {
            return Array.newInstance(rawClass(genericArray.getGenericComponentType()), 0).getClass();
        }
        return Object.class;
    }

    private Type typeArgument(Type type, int index) {
        if (type instanceof ParameterizedType parameterized && index < parameterized.getActualTypeArguments().length) {
            Type argument = parameterized.getActualTypeArguments()[index];
            if (argument instanceof java.lang.reflect.WildcardType wildcard && wildcard.getUpperBounds().length > 0) {
                return wildcard.getUpperBounds()[0];
            }
            return argument;
        }
        return Object.class;
    }

    private static final class LuaParameterizedType implements ParameterizedType {
        private final Type rawType;
        private final Type[] actualTypeArguments;

        private LuaParameterizedType(Type rawType, Type... actualTypeArguments) {
            this.rawType = rawType;
            this.actualTypeArguments = actualTypeArguments.clone();
        }

        @Override
        public Type[] getActualTypeArguments() {
            return actualTypeArguments.clone();
        }

        @Override
        public Type getRawType() {
            return rawType;
        }

        @Override
        public Type getOwnerType() {
            return null;
        }
    }

    private LuaValue wrapJavaValue(Object value) {
        return wrapJavaValue(value, Object.class);
    }

    private boolean isJavaProxy(LuaValue value) {
        if (!value.istable()) return false;
        LuaValue meta = value.getmetatable();
        if (meta == null || !meta.istable()) return false;
        LuaValue marker = meta.checktable().rawget("__mlua_java_proxy");
        return marker.isuserdata() && marker.touserdata() == this.javaProxyMarker;
    }

    private LuaValue javaProxyField(LuaValue value, String name) {
        return isJavaProxy(value) ? value.checktable().rawget(name) : LuaValue.NIL;
    }

    private LuaValue wrapJavaValue(Object value, Type declaredType) {
        if (value == null) {
            return LuaValue.NIL;
        }
        if (value instanceof Optional<?> optional) {
            return optional.isPresent()
                ? wrapJavaValue(optional.get(), typeArgument(declaredType, 0))
                : LuaValue.NIL;
        }
        if (value instanceof Class<?> clazz) {
            return classProxy(clazz);
        }
        if (value instanceof Character character) {
            return LuaValue.valueOf(character.toString());
        }
        if (value instanceof String
            || value instanceof Number
            || value instanceof Boolean) {
            return CoerceJavaToLua.coerce(value);
        }
        return objectProxy(value, declaredType);
    }

    private LuaValue classProxy(Class<?> clazz) {
        LuaTable proxy = new LuaTable();
        proxy.set("__javaClass", CoerceJavaToLua.coerce(clazz));

        LuaTable meta = new LuaTable();
        meta.set("__mlua_java_proxy", CoerceJavaToLua.coerce(this.javaProxyMarker));
        meta.set("__index", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                LuaValue key = args.arg(2);
                if (!key.isstring()) {
                    return LuaValue.NIL;
                }

                String memberName = key.tojstring();
                if ("new".equals(memberName)) {
                    return constructorInvoker(proxy, clazz);
                }

                Class<?> nestedClass = tryResolveClass(clazz.getName() + "$" + memberName);
                if (nestedClass != null) {
                    return wrapJavaValue(nestedClass);
                }

                LuaValue staticMethod = staticMethodInvoker(proxy, clazz, memberName);
                if (!staticMethod.isnil()) {
                    return staticMethod;
                }

                Field field = resolveField(clazz, memberName, true);
                if (field != null) {
                    try {
                        return wrapJavaValue(field.get(null), field.getGenericType());
                    } catch (ReflectiveOperationException e) {
                        throw new LuaError("[MLua] Failed to read field " + clazz.getName() + "." + memberName + ": " + rootMessage(e));
                    }
                }

                LuaValue classInstanceMethod = instanceMethodInvoker(proxy, clazz, memberName);
                if (!classInstanceMethod.isnil()) {
                    return classInstanceMethod;
                }

                return LuaValue.NIL;
            }
        });
        meta.set("__call", constructorInvoker(proxy, clazz));
        meta.set("__newindex", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                LuaValue key = args.arg(2);
                if (!key.isstring()) throw new LuaError("[MLua] Java fields must be addressed by name");
                String fieldName = key.checkjstring();
                Field field = resolveField(clazz, fieldName, true);
                if (field == null) {
                    throw new LuaError("[MLua] No static field " + clazz.getName() + "." + fieldName);
                }
                if (Modifier.isFinal(field.getModifiers())) {
                    throw new LuaError("[MLua] Field " + clazz.getName() + "." + fieldName + " is final");
                }
                try {
                    field.set(null, convertLuaValue(args.arg(3), field.getGenericType()));
                } catch (IllegalAccessException error) {
                    throw new LuaError("[MLua] Failed to write field " + clazz.getName() + "." + fieldName + ": " + rootMessage(error));
                }
                return LuaValue.NONE;
            }
        });
        meta.set("__tostring", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return LuaValue.valueOf("JavaClass(" + clazz.getName() + ")");
            }
        });
        proxy.setmetatable(meta);
        return proxy;
    }

    private LuaValue objectProxy(Object instance) {
        return objectProxy(instance, Object.class);
    }

    private LuaValue objectProxy(Object instance, Type declaredType) {
        LuaTable proxy = new LuaTable();
        proxy.set("__javaObject", CoerceJavaToLua.coerce(instance));
        proxy.set("__javaClass", CoerceJavaToLua.coerce(instance.getClass()));

        LuaTable meta = new LuaTable();
        meta.set("__mlua_java_proxy", CoerceJavaToLua.coerce(this.javaProxyMarker));
        meta.set("__index", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                LuaValue key = args.arg(2);

                LuaValue collectionValue = collectionIndex(instance, declaredType, key);
                if (!collectionValue.isnil()) {
                    return collectionValue;
                }

                if (!key.isstring()) {
                    return LuaValue.NIL;
                }

                String memberName = key.tojstring();
                LuaValue instanceMethod = instanceMethodInvoker(proxy, instance, memberName);
                if (!instanceMethod.isnil()) {
                    return instanceMethod;
                }

                Method getter = resolvePropertyGetter(instance.getClass(), memberName);
                if (getter != null) {
                    try {
                        return wrapJavaValue(getter.invoke(instance), getter.getGenericReturnType());
                    } catch (IllegalAccessException | InvocationTargetException e) {
                        throw new LuaError("[MLua] Failed to read property " + instance.getClass().getName() + "." + memberName + ": " + rootMessage(e));
                    }
                }

                Field field = resolveField(instance.getClass(), memberName, false);
                if (field != null) {
                    try {
                        return wrapJavaValue(field.get(instance), field.getGenericType());
                    } catch (ReflectiveOperationException e) {
                        throw new LuaError("[MLua] Failed to read field " + instance.getClass().getName() + "." + memberName + ": " + rootMessage(e));
                    }
                }

                return LuaValue.NIL;
            }
        });
        meta.set("__newindex", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                LuaValue key = args.arg(2);

                if (applyCollectionWrite(instance, declaredType, key, args.arg(3))) {
                    return LuaValue.NONE;
                }

                if (!key.isstring()) {
                    throw new LuaError("[MLua] Java object fields must be addressed by string name");
                }

                String memberName = key.tojstring();
                LuaValue luaValue = args.arg(3);
                Object rawValue = isPlainLuaTable(luaValue)
                    ? new LuaTableArgument(luaValue, unwrapJavaValue(luaValue))
                    : unwrapJavaValue(luaValue);
                Method setter = resolvePropertySetter(instance.getClass(), memberName, rawValue);
                if (setter != null) {
                    try {
                        setter.invoke(instance, convertArguments(new Object[]{rawValue}, setter));
                    } catch (IllegalAccessException | InvocationTargetException e) {
                        throw new LuaError("[MLua] Failed to write property " + instance.getClass().getName()
                            + "." + memberName + ": " + rootMessage(e));
                    }
                    return LuaValue.NONE;
                }

                Field field = resolveField(instance.getClass(), memberName, false);
                if (field == null) {
                    throw new LuaError("[MLua] No writable field or property " + instance.getClass().getName() + "." + memberName);
                }

                if (Modifier.isFinal(field.getModifiers())) {
                    throw new LuaError("[MLua] Field " + instance.getClass().getName() + "." + memberName + " is final");
                }

                try {
                    field.set(instance, convertLuaValue(args.arg(3), field.getGenericType()));
                } catch (IllegalAccessException e) {
                    throw new LuaError("[MLua] Failed to write field " + instance.getClass().getName() + "." + memberName + ": " + rootMessage(e));
                }
                return LuaValue.NONE;
            }
        });
        meta.set("__tostring", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return LuaValue.valueOf("JavaObject(" + instance.getClass().getName() + ")");
            }
        });
        meta.set("__len", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                Integer length = collectionLength(instance);
                if (length == null) {
                    throw new LuaError("[MLua] Length is unavailable for " + instance.getClass().getName());
                }
                return LuaValue.valueOf(length);
            }
        });
        proxy.setmetatable(meta);
        return proxy;
    }

    private LuaValue collectionIndex(Object instance, Type declaredType, LuaValue key) {
        if (instance.getClass().isArray() && key.isnumber()) {
            int index = zeroBasedIndex(key);
            int length = Array.getLength(instance);
            if (index < 0 || index >= length) {
                return LuaValue.NIL;
            }
            return wrapJavaValue(Array.get(instance, index), instance.getClass().getComponentType());
        }

        if (instance instanceof List<?> list && key.isnumber()) {
            int index = zeroBasedIndex(key);
            if (index < 0 || index >= list.size()) {
                return LuaValue.NIL;
            }
            return wrapJavaValue(list.get(index), typeArgument(declaredType, 0));
        }

        if (instance instanceof Map<?, ?> map) {
            Object convertedKey = convertLuaValue(key, typeArgument(declaredType, 0));
            Object mapKey = map.containsKey(convertedKey) || hasTypeArgument(declaredType, 0)
                ? convertedKey : resolveMapKey(map, key);
            if (map.containsKey(mapKey)) {
                return wrapJavaValue(map.get(mapKey), typeArgument(declaredType, 1));
            }
        }

        return LuaValue.NIL;
    }

    private boolean applyCollectionWrite(Object instance, Type declaredType, LuaValue key, LuaValue value) {
        if (instance.getClass().isArray() && key.isnumber()) {
            int index = zeroBasedIndex(key);
            int length = Array.getLength(instance);
            if (index < 0 || index >= length) {
                throw new LuaError("[MLua] Array index must be an integer between 1 and " + length);
            }
            Array.set(instance, index, convertLuaValue(value, instance.getClass().getComponentType()));
            return true;
        }

        if (instance instanceof List<?> rawList && key.isnumber()) {
            @SuppressWarnings("unchecked")
            List<Object> list = (List<Object>) rawList;
            int index = zeroBasedIndex(key);
            if (index < 0 || index >= list.size()) {
                throw new LuaError("[MLua] List index must be an integer between 1 and " + list.size());
            }
            try {
                list.set(index, convertLuaValue(value, typeArgument(declaredType, 0)));
            } catch (UnsupportedOperationException error) {
                throw new LuaError("[MLua] Java list " + instance.getClass().getName() + " is not writable");
            }
            return true;
        }

        if (instance instanceof Map<?, ?> rawMap) {
            @SuppressWarnings("unchecked")
            Map<Object, Object> map = (Map<Object, Object>) rawMap;
            Object convertedKey = convertLuaValue(key, typeArgument(declaredType, 0));
            Object mapKey = map.containsKey(convertedKey) || hasTypeArgument(declaredType, 0)
                ? convertedKey : resolveMapKey(map, key);
            try {
                map.put(mapKey, convertLuaValue(value, typeArgument(declaredType, 1)));
            } catch (UnsupportedOperationException error) {
                throw new LuaError("[MLua] Java map " + instance.getClass().getName() + " is not writable");
            }
            return true;
        }

        return false;
    }

    private boolean hasTypeArgument(Type type, int index) {
        return type instanceof ParameterizedType parameterized
            && index < parameterized.getActualTypeArguments().length;
    }

    private Integer collectionLength(Object instance) {
        if (instance.getClass().isArray()) {
            return Array.getLength(instance);
        }
        if (instance instanceof Collection<?> collection) {
            return collection.size();
        }
        if (instance instanceof Map<?, ?> map) {
            return map.size();
        }
        return null;
    }

    private int zeroBasedIndex(LuaValue key) {
        double number = key.todouble();
        if (!Double.isFinite(number) || number < 1 || number > Integer.MAX_VALUE
            || number != Math.rint(number)) {
            return -1;
        }
        return (int) number - 1;
    }

    private Object unwrapMapKey(LuaValue key) {
        if (key.isstring()) {
            return key.tojstring();
        }
        if (key.isnumber()) {
            return key.todouble();
        }
        if (key.isboolean()) {
            return key.toboolean();
        }
        return unwrapJavaValue(key);
    }

    private Object resolveMapKey(Map<?, ?> map, LuaValue key) {
        Object direct = unwrapMapKey(key);
        if (map.containsKey(direct) || !key.isnumber()) return direct;
        double number = key.todouble();
        if (!Double.isFinite(number) || number != Math.rint(number)) return direct;
        if (number >= Integer.MIN_VALUE && number <= Integer.MAX_VALUE) {
            int integer = (int) number;
            if (map.containsKey(integer)) return integer;
            if (integer >= Short.MIN_VALUE && integer <= Short.MAX_VALUE && map.containsKey((short) integer)) {
                return (short) integer;
            }
            if (integer >= Byte.MIN_VALUE && integer <= Byte.MAX_VALUE && map.containsKey((byte) integer)) {
                return (byte) integer;
            }
        }
        if (number >= Long.MIN_VALUE && number <= Long.MAX_VALUE) {
            long integer = (long) number;
            if (map.containsKey(integer)) return integer;
        }
        return direct;
    }

    private LuaValue constructorInvoker(LuaValue proxy, Class<?> clazz) {
        return new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                int startIndex = args.arg1() == proxy ? 2 : 1;
                Object[] rawArgs = extractInvocationArgs(args, startIndex);
                Constructor<?> ctor = resolveConstructor(clazz, rawArgs);
                if (ctor == null) {
                    throw new LuaError("[MLua] No matching public constructor found for " + clazz.getName()
                        + " with arguments " + describeArgs(rawArgs)
                        + ". Available: " + describeConstructors(clazz));
                }

                try {
                    Object instance = ctor.newInstance(convertArguments(rawArgs, ctor));
                    return wrapJavaValue(instance);
                } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
                    throw new LuaError("[MLua] Failed to construct " + clazz.getName() + ": " + rootMessage(e));
                }
            }
        };
    }

    private LuaValue staticMethodInvoker(LuaValue proxy, Class<?> clazz, String methodName) {
        List<Method> candidates = resolveMethods(clazz, methodName, true);

        if (candidates.isEmpty()) {
            return LuaValue.NIL;
        }

        return new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                int startIndex = args.arg1() == proxy ? 2 : 1;
                Object[] rawArgs = extractInvocationArgs(args, startIndex);
                Method method = resolveMethod(candidates, rawArgs);
                if (method == null) {
                    throw new LuaError("[MLua] No matching static method " + clazz.getName() + "." + methodName
                        + " for arguments " + describeArgs(rawArgs)
                        + ". Candidates: " + describeMethods(candidates));
                }

                try {
                    Object result = method.invoke(null, convertArguments(rawArgs, method));
                    return wrapJavaValue(result, method.getGenericReturnType());
                } catch (IllegalAccessException | InvocationTargetException e) {
                    throw new LuaError("[MLua] Failed to invoke " + clazz.getName() + "." + methodName + ": " + rootMessage(e));
                }
            }
        };
    }

    private LuaValue instanceMethodInvoker(LuaValue proxy, Object instance, String methodName) {
        List<Method> candidates = resolveMethods(instance.getClass(), methodName, false);

        if (candidates.isEmpty()) {
            return LuaValue.NIL;
        }

        return new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                int startIndex = args.arg1() == proxy ? 2 : 1;
                Object[] rawArgs = extractInvocationArgs(args, startIndex);
                Method method = resolveMethod(candidates, rawArgs);
                if (method == null) {
                    throw new LuaError("[MLua] No matching instance method " + instance.getClass().getName() + "." + methodName
                        + " for arguments " + describeArgs(rawArgs)
                        + ". Candidates: " + describeMethods(candidates));
                }

                try {
                    Object result = method.invoke(instance, convertArguments(rawArgs, method));
                    return wrapJavaValue(result, method.getGenericReturnType());
                } catch (IllegalAccessException | InvocationTargetException e) {
                    throw new LuaError("[MLua] Failed to invoke " + instance.getClass().getName() + "." + methodName + ": " + rootMessage(e));
                }
            }
        };
    }

    private List<Method> resolveMethods(Class<?> clazz, String methodName, boolean requireStatic) {
        MethodCacheKey cacheKey = new MethodCacheKey(clazz, methodName, requireStatic);
        List<Method> cached = this.resolvedMethods.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        List<Method> exact = new ArrayList<>();
        List<Method> fallback = new ArrayList<>();
        String normalized = normalizeMemberName(methodName);

        for (Method method : collectMethods(clazz)) {
            if (Modifier.isStatic(method.getModifiers()) != requireStatic) {
                continue;
            }
            if (method.getName().equals(methodName)) {
                exact.add(method);
                continue;
            }
            if (normalizeMemberName(method.getName()).equals(normalized)) {
                fallback.add(method);
            }
        }

        if (!exact.isEmpty()) {
            this.resolvedMethods.put(cacheKey, exact);
            return exact;
        }
        if (hasSingleResolvedMethodName(fallback)) {
            this.resolvedMethods.put(cacheKey, fallback);
            return fallback;
        }

        if (requireStatic && "getInstance".equals(methodName)) {
            List<Method> instanceFactories = new ArrayList<>();
            for (Method method : collectMethods(clazz)) {
                if (!Modifier.isStatic(method.getModifiers()) || method.getParameterCount() != 0 || !method.getReturnType().equals(clazz)) {
                    continue;
                }
                if (!prepareAccessible(method)) {
                    continue;
                }
                instanceFactories.add(method);
            }
            this.resolvedMethods.put(cacheKey, instanceFactories);
            return instanceFactories;
        }

        this.resolvedMethods.put(cacheKey, List.of());
        return List.of();
    }

    private List<Method> collectMethods(Class<?> clazz) {
        return this.collectedMethods.computeIfAbsent(clazz, key -> {
            Map<String, Method> methods = new LinkedHashMap<>();
            for (Class<?> current = key; current != null; current = current.getSuperclass()) {
                for (Method method : current.getDeclaredMethods()) {
                    if (method.isBridge() || method.isSynthetic()) {
                        continue;
                    }
                    String signature = executableSignature(method);
                    if (prepareAccessible(method)) {
                        methods.putIfAbsent(signature, method);
                    }
                }
            }
            for (Method method : key.getMethods()) {
                if (method.isBridge() || method.isSynthetic()) {
                    continue;
                }
                String signature = executableSignature(method);
                if (prepareAccessible(method)) {
                    methods.putIfAbsent(signature, method);
                }
            }
            List<Method> ordered = new ArrayList<>(methods.values());
            ordered.sort(java.util.Comparator.comparing(this::methodSignature));
            return List.copyOf(ordered);
        });
    }

    private List<Field> collectFields(Class<?> clazz) {
        return this.collectedFields.computeIfAbsent(clazz, key -> {
            Map<String, Field> fields = new LinkedHashMap<>();
            for (Class<?> current = key; current != null; current = current.getSuperclass()) {
                for (Field field : current.getDeclaredFields()) {
                    String signature = field.getDeclaringClass().getName() + "#" + field.getName();
                    if (prepareAccessible(field)) {
                        fields.putIfAbsent(signature, field);
                    }
                }
            }
            for (Field field : key.getFields()) {
                String signature = field.getDeclaringClass().getName() + "#" + field.getName();
                if (prepareAccessible(field)) {
                    fields.putIfAbsent(signature, field);
                }
            }
            List<Field> ordered = new ArrayList<>(fields.values());
            ordered.sort(java.util.Comparator.comparing(field -> field.getDeclaringClass().getName() + "#" + field.getName()));
            return List.copyOf(ordered);
        });
    }

    private Field resolveField(Class<?> clazz, String fieldName, boolean requireStatic) {
        String normalized = normalizeMemberName(fieldName);
        for (Class<?> current = clazz; current != null; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) != requireStatic) {
                    continue;
                }
                if (field.getName().equals(fieldName)) {
                    if (prepareAccessible(field)) {
                        return field;
                    }
                }
            }
        }

        if (requireStatic) {
            List<Field> inheritedInterfaceFields = collectInterfaceFields(clazz, fieldName, false);
            if (inheritedInterfaceFields.size() == 1) return inheritedInterfaceFields.get(0);
            if (inheritedInterfaceFields.size() > 1) {
                throw ambiguousInterfaceField(fieldName, clazz, inheritedInterfaceFields);
            }
        }

        for (Class<?> current = clazz; current != null; current = current.getSuperclass()) {
            Field fallback = null;
            for (Field field : current.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) == requireStatic
                    && normalizeMemberName(field.getName()).equals(normalized)
                    && prepareAccessible(field)) {
                    if (fallback != null) {
                        throw new LuaError("[MLua] Java field name '" + fieldName + "' is ambiguous in "
                            + current.getName() + "; use an exact Java field name");
                    }
                    fallback = field;
                }
            }
            if (fallback != null) return fallback;
        }

        if (requireStatic) {
            List<Field> inheritedInterfaceFields = collectInterfaceFields(clazz, fieldName, true);
            if (inheritedInterfaceFields.size() == 1) return inheritedInterfaceFields.get(0);
            if (inheritedInterfaceFields.size() > 1) {
                throw ambiguousInterfaceField(fieldName, clazz, inheritedInterfaceFields);
            }
        }

        return null;
    }

    private List<Field> collectInterfaceFields(Class<?> clazz, String fieldName, boolean normalized) {
        String expected = normalized ? normalizeMemberName(fieldName) : fieldName;
        java.util.Set<Class<?>> visited = new java.util.LinkedHashSet<>();
        List<Class<?>> pending = new ArrayList<>();
        for (Class<?> current = clazz; current != null; current = current.getSuperclass()) {
            java.util.Collections.addAll(pending, current.getInterfaces());
        }

        List<Field> matches = new ArrayList<>();
        for (int index = 0; index < pending.size(); index++) {
            Class<?> iface = pending.get(index);
            if (!visited.add(iface)) continue;
            for (Field field : iface.getDeclaredFields()) {
                String candidate = normalized ? normalizeMemberName(field.getName()) : field.getName();
                if (Modifier.isStatic(field.getModifiers()) && candidate.equals(expected) && prepareAccessible(field)) {
                    addInterfaceField(matches, field);
                }
            }
            java.util.Collections.addAll(pending, iface.getInterfaces());
        }
        return List.copyOf(matches);
    }

    private void addInterfaceField(List<Field> fields, Field candidate) {
        for (int index = fields.size() - 1; index >= 0; index--) {
            Field existing = fields.get(index);
            Class<?> existingOwner = existing.getDeclaringClass();
            Class<?> candidateOwner = candidate.getDeclaringClass();
            if (existingOwner == candidateOwner && existing.getName().equals(candidate.getName())) return;
            if (!existing.getName().equals(candidate.getName())) continue;
            if (existingOwner.isAssignableFrom(candidateOwner)) {
                fields.remove(index);
            } else if (candidateOwner.isAssignableFrom(existingOwner)) {
                return;
            }
        }
        fields.add(candidate);
    }

    private LuaError ambiguousInterfaceField(String fieldName, Class<?> clazz, List<Field> fields) {
        List<String> declarations = fields.stream()
            .map(field -> field.getDeclaringClass().getName() + "." + field.getName())
            .sorted()
            .toList();
        return new LuaError("[MLua] Java interface field '" + fieldName + "' is ambiguous on "
            + clazz.getName() + "; use Java:GetField with an interface class. Candidates: " + declarations);
    }

    private Method resolvePropertyGetter(Class<?> clazz, String fieldName) {
        if (fieldName == null || fieldName.isEmpty()) return null;
        String suffix = Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
        List<String> names = List.of("get" + suffix, "is" + suffix, fieldName);

        for (String candidateName : names) {
            List<Method> methods = resolveMethods(clazz, candidateName, false);
            for (Method method : methods) {
                if (method.getParameterCount() == 0) {
                    return method;
                }
            }
        }

        return null;
    }

    private Method resolvePropertySetter(Class<?> clazz, String fieldName, Object rawValue) {
        if (fieldName == null || fieldName.isEmpty()) return null;
        String suffix = Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
        for (String candidateName : List.of("set" + suffix, fieldName)) {
            List<Method> candidates = new ArrayList<>();
            for (Method method : resolveMethods(clazz, candidateName, false)) {
                if (method.getParameterCount() == 1) candidates.add(method);
            }
            if (!candidates.isEmpty()) {
                Method setter = resolveMethod(candidates, new Object[]{rawValue});
                if (setter != null) return setter;
            }
        }
        return null;
    }

    private Object[] extractInvocationArgs(Varargs args, int startIndex) {
        int count = Math.max(0, args.narg() - startIndex + 1);
        Object[] rawArgs = new Object[count];
        for (int i = 0; i < count; i++) {
            LuaValue argument = args.arg(startIndex + i);
            rawArgs[i] = isPlainLuaTable(argument)
                ? new LuaTableArgument(argument, unwrapJavaValue(argument))
                : unwrapJavaValue(argument);
        }
        return rawArgs;
    }

    private boolean isPlainLuaTable(LuaValue value) {
        return value.istable() && !isJavaProxy(value);
    }

    private Constructor<?> resolveConstructor(Class<?> clazz, Object[] rawArgs) {
        return resolveBestExecutable(List.of(this.publicConstructors.computeIfAbsent(clazz, Class::getConstructors)), rawArgs);
    }

    private Method resolveMethod(List<Method> candidates, Object[] rawArgs) {
        return resolveBestExecutable(candidates, rawArgs);
    }

    private <T extends Executable> T resolveBestExecutable(List<T> candidates, Object[] rawArgs) {
        int bestScore = Integer.MAX_VALUE;
        List<T> bestMatches = new ArrayList<>();
        for (T candidate : candidates) {
            int score = compatibilityScore(candidate, rawArgs);
            if (score < 0 || score > bestScore) continue;
            if (score < bestScore) {
                bestScore = score;
                bestMatches.clear();
            }
            bestMatches.add(candidate);
        }
        if (bestMatches.isEmpty()) {
            if (hasScalarContainerArgument(candidates, rawArgs)) {
                throw new LuaError("[MLua] Java array and Iterable parameters require a dense Lua array table, Java array, or Java collection; a scalar value is not wrapped automatically");
            }
            return null;
        }
        if (bestMatches.size() == 1) return bestMatches.get(0);

        for (T candidate : bestMatches) {
            boolean mostSpecific = true;
            for (T other : bestMatches) {
                if (candidate != other && !isPreferredExecutable(candidate, other, rawArgs)) {
                    mostSpecific = false;
                    break;
                }
            }
            if (mostSpecific) return candidate;
        }
        List<String> descriptions = new ArrayList<>(bestMatches.size());
        for (T candidate : bestMatches) descriptions.add(executableDescription(candidate));
        descriptions.sort(String::compareTo);
        throw new LuaError("[MLua] Ambiguous Java call for arguments " + describeArgs(rawArgs)
            + ". Matching signatures: " + descriptions);
    }

    private boolean hasScalarContainerArgument(List<? extends Executable> candidates, Object[] rawArgs) {
        for (Executable candidate : candidates) {
            Class<?>[] parameterTypes = candidate.getParameterTypes();
            if (candidate.isVarArgs() || parameterTypes.length != rawArgs.length) continue;
            for (int index = 0; index < parameterTypes.length; index++) {
                Class<?> parameterType = parameterTypes[index];
                Object value = rawArgs[index];
                if ((parameterType.isArray() || Iterable.class.isAssignableFrom(parameterType))
                    && value != null && !(value instanceof LuaTableArgument)
                    && !(value instanceof Iterable<?>) && !value.getClass().isArray()) {
                    return true;
                }
            }
        }
        return false;
    }

    private String executableDescription(Executable executable) {
        if (executable instanceof Method method) return methodSignature(method);
        Constructor<?> constructor = (Constructor<?>) executable;
        StringBuilder description = new StringBuilder(constructor.getDeclaringClass().getName()).append('(');
        Class<?>[] parameterTypes = constructor.getParameterTypes();
        for (int i = 0; i < parameterTypes.length; i++) {
            if (i > 0) description.append(", ");
            description.append(parameterTypes[i].getName());
        }
        return description.append(')').toString();
    }

    private int compatibilityScore(Executable executable, Object[] rawArgs) {
        Class<?>[] parameterTypes = executable.getParameterTypes();
        Type[] genericTypes = executable.getGenericParameterTypes();
        if (executable.isVarArgs() && rawArgs.length >= parameterTypes.length - 1) {
            return compatibilityScoreVarArgs(parameterTypes, genericTypes, rawArgs);
        }
        if (parameterTypes.length != rawArgs.length) return -1;

        int score = 0;
        for (int i = 0; i < parameterTypes.length; i++) {
            int parameterScore = coercionScore(rawArgs[i], parameterTypes[i], genericTypes[i]);
            if (parameterScore < 0) {
                return -1;
            }
            score += parameterScore;
        }
        return score;
    }

    private int compatibilityScoreVarArgs(Class<?>[] parameterTypes, Type[] genericTypes, Object[] rawArgs) {
        int fixedCount = parameterTypes.length - 1;
        int score = 1;
        for (int i = 0; i < fixedCount; i++) {
            int parameterScore = coercionScore(rawArgs[i], parameterTypes[i], genericTypes[i]);
            if (parameterScore < 0) {
                return -1;
            }
            score += parameterScore;
        }

        Class<?> componentType = parameterTypes[fixedCount].getComponentType();
        if (hasPackedVarargs(parameterTypes, rawArgs)) {
            int packedScore = coercionScore(rawArgs[fixedCount], parameterTypes[fixedCount], genericTypes[fixedCount]);
            return packedScore < 0 ? -1 : score + packedScore;
        }

        Type componentGenericType = genericTypes[fixedCount] instanceof java.lang.reflect.GenericArrayType genericArray
            ? genericArray.getGenericComponentType() : componentType;
        for (int i = fixedCount; i < rawArgs.length; i++) {
            int parameterScore = coercionScore(rawArgs[i], componentType, componentGenericType);
            if (parameterScore < 0) {
                return -1;
            }
            score += parameterScore;
        }
        return score;
    }

    private Object[] convertArguments(Object[] rawArgs, Executable executable) {
        Class<?>[] parameterTypes = executable.getParameterTypes();
        Type[] genericTypes = executable.getGenericParameterTypes();
        if (executable.isVarArgs() && !hasPackedVarargs(parameterTypes, rawArgs)) {
            return convertVarArgs(rawArgs, executable);
        }

        Object[] converted = new Object[rawArgs.length];
        for (int i = 0; i < rawArgs.length; i++) {
            converted[i] = convertTypedValue(rawArgs[i], genericTypes[i]);
        }
        return converted;
    }

    private boolean hasPackedVarargs(Class<?>[] parameterTypes, Object[] rawArgs) {
        if (rawArgs.length != parameterTypes.length) return false;
        int last = parameterTypes.length - 1;
        Object value = rawArgs[last];
        return value == null || parameterTypes[last].isInstance(value) || value instanceof Iterable<?>
            || value instanceof LuaTableArgument && coercionScore(value, parameterTypes[last]) >= 0;
    }

    private Object[] convertVarArgs(Object[] rawArgs, Executable executable) {
        Class<?>[] parameterTypes = executable.getParameterTypes();
        Type[] genericTypes = executable.getGenericParameterTypes();
        int fixedCount = parameterTypes.length - 1;
        Object[] converted = new Object[parameterTypes.length];
        for (int i = 0; i < fixedCount; i++) {
            converted[i] = convertTypedValue(rawArgs[i], genericTypes[i]);
        }

        Class<?> componentType = parameterTypes[fixedCount].getComponentType();
        Type componentGenericType = genericTypes[fixedCount] instanceof java.lang.reflect.GenericArrayType genericArray
            ? genericArray.getGenericComponentType() : componentType;
        Object varArray = Array.newInstance(componentType, rawArgs.length - fixedCount);
        for (int i = fixedCount; i < rawArgs.length; i++) {
            Array.set(varArray, i - fixedCount, convertTypedValue(rawArgs[i], componentGenericType));
        }
        converted[fixedCount] = varArray;
        return converted;
    }

    private Object convertTypedValue(Object rawValue, Type targetType) {
        if (rawValue instanceof LuaTableArgument tableArgument) {
            Class<?> targetClass = rawClass(targetType);
            if (targetClass.isArray() || Iterable.class.isAssignableFrom(targetClass)
                || Map.class.isAssignableFrom(targetClass) || targetClass.isInterface()) {
                return convertLuaValue(tableArgument.value(), targetType);
            }
            rawValue = tableArgument.defaultValue();
        }
        if (targetType instanceof java.lang.reflect.GenericArrayType genericArray) {
            if (rawValue == null) return null;
            Type componentType = genericArray.getGenericComponentType();
            Class<?> componentClass = rawClass(componentType);
            List<?> source;
            if (rawValue instanceof Iterable<?> iterable) {
                List<Object> values = new ArrayList<>();
                iterable.forEach(values::add);
                source = values;
            } else if (rawValue != null && rawValue.getClass().isArray()) {
                List<Object> values = new ArrayList<>(Array.getLength(rawValue));
                for (int i = 0; i < Array.getLength(rawValue); i++) values.add(Array.get(rawValue, i));
                source = values;
            } else {
                throw new LuaError("[MLua] Cannot convert value to Java array type " + componentClass.getName() + "[]");
            }
            Object result = Array.newInstance(componentClass, source.size());
            for (int i = 0; i < source.size(); i++) {
                Array.set(result, i, convertTypedValue(source.get(i), componentType));
            }
            return result;
        }
        Class<?> rawType = rawClass(targetType);
        if (Optional.class.isAssignableFrom(rawType)) {
            return rawValue == null ? Optional.empty() : Optional.ofNullable(
                convertTypedValue(rawValue, typeArgument(targetType, 0)));
        }
        if (rawValue == null) return convertValue(null, rawType);
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
                iterable.forEach(source::add);
            } else {
                for (int index = 0; index < Array.getLength(rawValue); index++) {
                    source.add(Array.get(rawValue, index));
                }
            }
            List<Object> converted = new ArrayList<>(source.size());
            Type elementType = typeArgument(targetType, 0);
            for (Object value : source) converted.add(convertTypedValue(value, elementType));
            Collection<Object> collection = newCollection(rawType, converted);
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
            Type keyType = typeArgument(targetType, 0);
            Type valueType = typeArgument(targetType, 1);
            source.forEach((key, value) -> converted.put(convertTypedValue(key, keyType), convertTypedValue(value, valueType)));
            Map<Object, Object> result = newMap(rawType, converted);
            if (result != null) return result;
            throw new LuaError("[MLua] Cannot convert Java values to map type " + rawType.getName());
        }
        return convertValue(rawValue, rawType);
    }

    private Map<Object, Object> newMap(Class<?> targetType, Map<Object, Object> values) {
        try {
            if (targetType.isInstance(values)) return values;
            if (ConcurrentNavigableMap.class.isAssignableFrom(targetType)
                && targetType.isAssignableFrom(ConcurrentSkipListMap.class)) {
                return new ConcurrentSkipListMap<>(values);
            }
            if ((NavigableMap.class.isAssignableFrom(targetType) || SortedMap.class.isAssignableFrom(targetType))
                && targetType.isAssignableFrom(java.util.TreeMap.class)) {
                return new java.util.TreeMap<>(values);
            }
            if (ConcurrentMap.class.isAssignableFrom(targetType)
                && targetType.isAssignableFrom(ConcurrentHashMap.class)) {
                return new ConcurrentHashMap<>(values);
            }
            if (targetType.isAssignableFrom(LinkedHashMap.class)) return new LinkedHashMap<>(values);
            if (!targetType.isInterface() && !Modifier.isAbstract(targetType.getModifiers())) {
                @SuppressWarnings("unchecked")
                Map<Object, Object> map = (Map<Object, Object>) targetType.getConstructor().newInstance();
                map.putAll(values);
                return map;
            }
        } catch (ClassCastException incompatibleKeys) {
            throw new LuaError("[MLua] Java map type " + targetType.getName()
                + " cannot order or accept the Lua table keys.");
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
        return null;
    }

    private Object convertValue(Object rawValue, Class<?> targetType) {
        if (rawValue == null) {
            if (targetType.isPrimitive()) {
                throw new LuaError("[MLua] Cannot assign nil to Java primitive type " + targetType.getName());
            }
            return null;
        }
        if (targetType.isInstance(rawValue)) {
            return rawValue;
        }
        if (targetType.isArray()) {
            return convertArrayValue(rawValue, targetType.getComponentType());
        }
        Class<?> boxedType = boxType(targetType);
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
        if (isNumericType(boxedType) && rawValue instanceof Number number) {
            return convertNumber(number, boxedType);
        }
        if (boxedType == Boolean.class && rawValue instanceof Boolean) {
            return rawValue;
        }
        return CoerceLuaToJava.coerce(CoerceJavaToLua.coerce(rawValue), targetType);
    }

    private int coercionScore(Object value, Class<?> parameterType) {
        return coercionScore(value, parameterType, parameterType);
    }

    private int coercionScore(Object value, Class<?> parameterType, Type targetType) {
        if (value instanceof LuaTableArgument tableArgument) {
            if (parameterType.isArray() || Iterable.class.isAssignableFrom(parameterType)
                || Map.class.isAssignableFrom(parameterType) || parameterType.isInterface()) {
                try {
                    convertLuaValue(tableArgument.value(), targetType);
                    return 3;
                } catch (RuntimeException error) {
                    return -1;
                }
            }
            value = tableArgument.defaultValue();
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
            if (parameterType.isAssignableFrom(ArrayList.class)
                || (java.util.Set.class.isAssignableFrom(parameterType)
                    && parameterType.isAssignableFrom(LinkedHashSet.class))
                || ((Queue.class.isAssignableFrom(parameterType) || Deque.class.isAssignableFrom(parameterType))
                    && parameterType.isAssignableFrom(LinkedList.class))) {
                return 3;
            }
            if (!parameterType.isInterface() && !Modifier.isAbstract(parameterType.getModifiers())
                && hasPublicNoArgumentConstructor(parameterType)) {
                return 4;
            }
        }

        if (Map.class.isAssignableFrom(parameterType)
            && (value instanceof Map<?, ?> || value instanceof Collection<?> collection && collection.isEmpty())) {
            if (parameterType.isAssignableFrom(LinkedHashMap.class)) return 3;
            if (!parameterType.isInterface() && !Modifier.isAbstract(parameterType.getModifiers())
                && hasPublicNoArgumentConstructor(parameterType)) {
                return 4;
            }
        }

        Class<?> boxedType = boxType(parameterType);
        if (Optional.class.isAssignableFrom(boxedType)) return 2;
        if (boxedType.isInstance(value)) {
            return 0;
        }
        if (boxedType == String.class) {
            return value instanceof CharSequence || value instanceof Character ? 2 : -1;
        }
        if (boxedType.isEnum() && value instanceof String) {
            return 2;
        }
        if (isNumericType(boxedType) && value instanceof Number number) {
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
            convertValue(value, parameterType);
            if (Number.class.isAssignableFrom(boxedType) && value instanceof Number) {
                return 2;
            }
            return 3;
        } catch (RuntimeException ex) {
            return -1;
        }
    }

    private boolean hasPublicNoArgumentConstructor(Class<?> type) {
        try {
            type.getConstructor();
            return true;
        } catch (NoSuchMethodException ignored) {
            return false;
        }
    }

    private Collection<Object> newCollection(Class<?> targetType, List<Object> values) {
        if (targetType.isAssignableFrom(ArrayList.class)) return new ArrayList<>(values);
        if (java.util.Set.class.isAssignableFrom(targetType)
            && targetType.isAssignableFrom(LinkedHashSet.class)) return new LinkedHashSet<>(values);
        if ((Queue.class.isAssignableFrom(targetType) || Deque.class.isAssignableFrom(targetType))
            && targetType.isAssignableFrom(LinkedList.class)) return new LinkedList<>(values);
        if (Collection.class.isAssignableFrom(targetType)
            && !targetType.isInterface() && !Modifier.isAbstract(targetType.getModifiers())) {
            try {
                @SuppressWarnings("unchecked")
                Collection<Object> collection = (Collection<Object>) targetType.getConstructor().newInstance();
                collection.addAll(values);
                return collection;
            } catch (ReflectiveOperationException | ClassCastException ignored) {
                return null;
            }
        }
        return null;
    }

    private boolean hasSingleResolvedMethodName(List<Method> methods) {
        if (methods.isEmpty()) {
            return false;
        }
        String name = methods.get(0).getName();
        for (Method method : methods) {
            if (!method.getName().equals(name)) {
                return false;
            }
        }
        return true;
    }

    private String executableSignature(Method method) {
        StringBuilder builder = new StringBuilder(method.getName()).append('(');
        for (Class<?> parameterType : method.getParameterTypes()) {
            builder.append(parameterType.getName()).append(';');
        }
        // Java does not overload methods by return type. Omitting it also collapses
        // covariant overrides so the subclass declaration wins during collection.
        return builder.append(')').toString();
    }

    private boolean isPreferredExecutable(Executable candidate, Executable currentBest, Object[] rawArgs) {
        if (currentBest == null) {
            return true;
        }
        if (candidate.isVarArgs() != currentBest.isVarArgs()) {
            return !candidate.isVarArgs();
        }
        int specificity = compareSpecificity(candidate.getParameterTypes(), currentBest.getParameterTypes(), rawArgs);
        if (specificity != 0) {
            return specificity < 0;
        }
        return currentBest.getDeclaringClass() != candidate.getDeclaringClass()
            && currentBest.getDeclaringClass().isAssignableFrom(candidate.getDeclaringClass());
    }

    private int compareSpecificity(Class<?>[] left, Class<?>[] right, Object[] rawArgs) {
        boolean leftBetter = false;
        boolean rightBetter = false;
        int len = Math.min(left.length, right.length);
        for (int i = 0; i < len; i++) {
            Class<?> leftType = boxType(left[i]);
            Class<?> rightType = boxType(right[i]);
            Object rawArg = i < rawArgs.length ? rawArgs[i] : null;

            if (rawArg != null) {
                Class<?> rawType = rawArg.getClass();
                boolean leftExact = leftType == rawType;
                boolean rightExact = rightType == rawType;
                if (leftExact != rightExact) {
                    if (leftExact) {
                        leftBetter = true;
                    } else {
                        rightBetter = true;
                    }
                    continue;
                }
            }

            if (leftType != rightType) {
                if (leftType.isAssignableFrom(rightType)) {
                    rightBetter = true;
                } else if (rightType.isAssignableFrom(leftType)) {
                    leftBetter = true;
                }
            }
        }
        if (leftBetter == rightBetter) {
            return 0;
        }
        return leftBetter ? -1 : 1;
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

    private boolean isNumericType(Class<?> type) {
        return type == Byte.class
            || type == Short.class
            || type == Integer.class
            || type == Long.class
            || type == Float.class
            || type == Double.class;
    }

    private int numericCoercionScore(Number value, Class<?> targetType) {
        try {
            convertNumber(value, targetType);
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

    private Object convertArrayValue(Object rawValue, Class<?> componentType) {
        if (rawValue.getClass().isArray()) {
            int length = Array.getLength(rawValue);
            Object array = Array.newInstance(componentType, length);
            for (int i = 0; i < length; i++) {
                Array.set(array, i, convertValue(Array.get(rawValue, i), componentType));
            }
            return array;
        }
        if (rawValue instanceof Iterable<?> iterable) {
            List<Object> values = new ArrayList<>();
            iterable.forEach(values::add);
            Object array = Array.newInstance(componentType, values.size());
            int index = 0;
            for (Object item : values) {
                Array.set(array, index++, convertValue(item, componentType));
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

    private Object convertNumber(Number number, Class<?> targetType) {
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

    private LuaValue toLuaTable(Object value) {
        return toLuaTable(value, new IdentityHashMap<>());
    }

    private LuaValue toLuaTable(Object value, IdentityHashMap<Object, Boolean> active) {
        if (value == null) {
            return LuaValue.NIL;
        }
        if (active.put(value, Boolean.TRUE) != null) {
            throw new LuaError("[MLua] Java:ToTable cannot convert a cyclic collection");
        }
        try {
            return convertCompositeToLuaTable(value, active);
        } finally {
            active.remove(value);
        }
    }

    private LuaValue convertCompositeToLuaTable(Object value, IdentityHashMap<Object, Boolean> active) {
        if (value.getClass().isArray()) {
            LuaTable array = new LuaTable();
            int length = Array.getLength(value);
            for (int i = 0; i < length; i++) {
                array.set(i + 1, toLuaTableValue(Array.get(value, i), active));
            }
            return array;
        }
        if (value instanceof List<?> list) {
            LuaTable array = new LuaTable();
            for (int i = 0; i < list.size(); i++) {
                array.set(i + 1, toLuaTableValue(list.get(i), active));
            }
            return array;
        }
        if (value instanceof Collection<?> collection) {
            LuaTable array = new LuaTable();
            int index = 1;
            for (Object item : collection) {
                array.set(index++, toLuaTableValue(item, active));
            }
            return array;
        }
        if (value instanceof Map<?, ?> map) {
            LuaTable table = new LuaTable();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                LuaValue key = toLuaMapKey(entry.getKey());
                if (!table.rawget(key).isnil()) {
                    throw new LuaError("[MLua] Java:ToTable found map keys that collapse to the same Lua key: "
                        + entry.getKey());
                }
                table.set(key, toLuaTableValue(entry.getValue(), active));
            }
            return table;
        }
        throw new LuaError("[MLua] Java:ToTable only supports arrays, collections, and maps. Got " + value.getClass().getName());
    }

    private LuaValue toLuaTableValue(Object value, IdentityHashMap<Object, Boolean> active) {
        if (value == null) {
            throw new LuaError("[MLua] Java:ToTable cannot preserve null collection or map values in a Lua table; keep the Java proxy to access them");
        }
        if (value instanceof String || value instanceof Number || value instanceof Boolean || value instanceof Character) {
            if (value instanceof Character character) return LuaValue.valueOf(character.toString());
            return wrapJavaValue(value);
        }
        if (value.getClass().isArray() || value instanceof Collection<?> || value instanceof Map<?, ?>) {
            return toLuaTable(value, active);
        }
        return wrapJavaValue(value);
    }

    private LuaValue toLuaMapKey(Object key) {
        if (key == null) throw new LuaError("[MLua] Java:ToTable cannot use a null map key");
        if (key instanceof String string) return LuaValue.valueOf(string);
        if (key instanceof Number number) {
            double converted = number.doubleValue();
            if (!Double.isFinite(converted)) {
                throw new LuaError("[MLua] Java:ToTable cannot represent non-finite numeric map keys");
            }
            if (!(number instanceof Float || number instanceof Double)) {
                try {
                    if (new BigDecimal(number.toString()).compareTo(BigDecimal.valueOf(converted)) != 0) {
                        throw new LuaError("[MLua] Java:ToTable cannot represent map key " + number
                            + " exactly as a Lua number");
                    }
                } catch (NumberFormatException error) {
                    throw new LuaError("[MLua] Java:ToTable cannot represent numeric map key " + number);
                }
            }
            return LuaValue.valueOf(converted);
        }
        if (key instanceof Boolean bool) return LuaValue.valueOf(bool);
        if (key instanceof Character character) return LuaValue.valueOf(character.toString());
        throw new LuaError("[MLua] Java:ToTable cannot preserve map key type " + key.getClass().getName()
            + "; use String, Number, Boolean, or Character keys");
    }

    private String describeArgs(Object[] rawArgs) {
        List<String> parts = new ArrayList<>(rawArgs.length);
        for (Object rawArg : rawArgs) {
            if (rawArg instanceof LuaTableArgument) {
                parts.add("LuaTable");
            } else {
                parts.add(rawArg == null ? "nil" : rawArg.getClass().getName());
            }
        }
        return parts.toString();
    }

    private String describeMethods(List<Method> methods) {
        List<String> parts = new ArrayList<>(methods.size());
        for (Method method : methods) {
            parts.add(methodSignature(method));
        }
        return parts.toString();
    }

    private String describeConstructors(Class<?> clazz) {
        List<String> parts = new ArrayList<>();
        for (Constructor<?> constructor : clazz.getConstructors()) {
            StringBuilder builder = new StringBuilder(clazz.getName()).append("(");
            Class<?>[] parameterTypes = constructor.getParameterTypes();
            for (int i = 0; i < parameterTypes.length; i++) {
                if (i > 0) {
                    builder.append(", ");
                }
                builder.append(parameterTypes[i].getName());
            }
            builder.append(")");
            parts.add(builder.toString());
        }
        return parts.toString();
    }

    private String rootMessage(Exception exception) {
        Throwable cause = exception instanceof InvocationTargetException ite && ite.getCause() != null
            ? ite.getCause()
            : exception;
        return cause.getClass().getName() + ": " + cause.getMessage();
    }

    private String methodSignature(Method method) {
        StringBuilder builder = new StringBuilder();
        builder.append(method.getName()).append("(");
        Class<?>[] parameterTypes = method.getParameterTypes();
        for (int i = 0; i < parameterTypes.length; i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(parameterTypes[i].getName());
        }
        builder.append(") -> ").append(method.getReturnType().getName());
        return builder.toString();
    }

    private boolean prepareAccessible(java.lang.reflect.AccessibleObject member) {
        try {
            member.setAccessible(true);
            return true;
        } catch (RuntimeException ex) {
            if (member instanceof Member reflectiveMember) {
                return Modifier.isPublic(reflectiveMember.getModifiers())
                    && Modifier.isPublic(reflectiveMember.getDeclaringClass().getModifiers());
            }
            return false;
        }
    }

    private Class<?> boxType(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        if (type == boolean.class) {
            return Boolean.class;
        }
        if (type == byte.class) {
            return Byte.class;
        }
        if (type == short.class) {
            return Short.class;
        }
        if (type == int.class) {
            return Integer.class;
        }
        if (type == long.class) {
            return Long.class;
        }
        if (type == float.class) {
            return Float.class;
        }
        if (type == double.class) {
            return Double.class;
        }
        if (type == char.class) {
            return Character.class;
        }
        return type;
    }

    private String normalizeMemberName(String name) {
        return name.replace("_", "").toLowerCase(Locale.ROOT);
    }

    private Class<?> tryResolveClass(String name) {
        Class<?> cached = this.resolvedClasses.get(name);
        if (cached != null) {
            return cached;
        }

        if (name.endsWith("[]")) {
            Class<?> componentType = tryResolveClass(name.substring(0, name.length() - 2));
            if (componentType == null) return null;
            Class<?> arrayType = Array.newInstance(componentType, 0).getClass();
            this.resolvedClasses.put(name, arrayType);
            return arrayType;
        }

        Class<?> primitive = primitiveType(name);
        if (primitive != null) {
            this.resolvedClasses.put(name, primitive);
            return primitive;
        }

        String runtimeName = LoaderApi.mappings().mapClassName(name);

        ClassLoader[] loaders = new ClassLoader[]{
            Thread.currentThread().getContextClassLoader(),
            this.context.getClass().getClassLoader(),
            this.getClass().getClassLoader(),
            ClassLoader.getSystemClassLoader()
        };

        Class<?> resolved = LoaderApi.javaClasses().resolve(runtimeName, loaders);
        if (resolved == null && !runtimeName.equals(name)) {
            resolved = LoaderApi.javaClasses().resolve(name, loaders);
        }

        if (resolved != null) {
            this.resolvedClasses.put(name, resolved);
        }
        return resolved;
    }

    private static Class<?> primitiveType(String name) {
        return switch (name) {
            case "boolean" -> boolean.class;
            case "byte" -> byte.class;
            case "char" -> char.class;
            case "short" -> short.class;
            case "int" -> int.class;
            case "long" -> long.class;
            case "float" -> float.class;
            case "double" -> double.class;
            default -> null;
        };
    }

    private record LuaTableArgument(LuaValue value, Object defaultValue) { }

    private static final class MethodCacheKey {
        private final Class<?> type;
        private final String methodName;
        private final boolean requireStatic;

        private MethodCacheKey(Class<?> type, String methodName, boolean requireStatic) {
            this.type = type;
            this.methodName = methodName;
            this.requireStatic = requireStatic;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof MethodCacheKey other)) {
                return false;
            }
            return this.type == other.type
                && this.requireStatic == other.requireStatic
                && this.methodName.equals(other.methodName);
        }

        @Override
        public int hashCode() {
            int result = System.identityHashCode(this.type);
            result = 31 * result + this.methodName.hashCode();
            result = 31 * result + Boolean.hashCode(this.requireStatic);
            return result;
        }
    }

    private final class LuaInvocationHandler implements InvocationHandler {
        private final LuaTable implementation;

        private LuaInvocationHandler(LuaTable implementation) {
            this.implementation = implementation;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "toString" -> "LuaProxy(" + proxy.getClass().getInterfaces()[0].getName() + ")";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == (args != null && args.length > 0 ? args[0] : null);
                    default -> throw new UnsupportedOperationException("Unsupported Object method: " + method.getName());
                };
            }

            LuaValue function = this.implementation.get(method.getName());
            if (function.isnil()) {
                if (method.isDefault()) {
                    try {
                        return InvocationHandler.invokeDefault(proxy, method, args);
                    } catch (Throwable t) {
                        throw new RuntimeException(t);
                    }
                }
                throw new UnsupportedOperationException("Lua proxy does not implement " + method.toGenericString());
            }

            LuaValue[] luaArgs = new LuaValue[args == null ? 0 : args.length];
            Type[] parameterTypes = method.getGenericParameterTypes();
            for (int i = 0; i < luaArgs.length; i++) {
                luaArgs[i] = wrapJavaValue(args[i], parameterTypes[i]);
            }

            LuaValue result = function.invoke(LuaValue.varargsOf(luaArgs)).arg1();
            if (method.getReturnType() == void.class) return null;
            if (result.isnil() && method.getReturnType().isPrimitive()) {
                throw new LuaError("[MLua] Lua proxy method " + method.getName() + " must return " + method.getReturnType().getName());
            }
            return convertLuaValue(result, method.getGenericReturnType());
        }
    }
}
