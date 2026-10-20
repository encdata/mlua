package dev.mlua.modules.javabridge.service;

import dev.mlua.api.MLuaLuaCoercions;
import dev.mlua.core.service.MLuaService;
import dev.mlua.core.service.MLuaServiceBase;
import dev.mlua.core.MLuaContext;
import dev.mlua.core.MLuaCallbackBudget;
import dev.mlua.core.engine.MLuaLuaBindings;
import dev.mlua.common.enums.Side;
import dev.mlua.common.service.MLuaServiceMetadata;
import dev.mlua.common.mixin.MLuaClassViewer;
import dev.mlua.runtime.registry.MLuaContentTypeRegistry;
import java.lang.reflect.Array;
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
import java.util.Map;
import java.util.Set;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.Deque;
import java.util.Queue;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.NavigableSet;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ConcurrentSkipListMap;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.jse.CoerceJavaToLua;
import org.luaj.vm2.lib.jse.CoerceLuaToJava;

/** Builds the Lua-facing Java service table. Reflection and conversion behavior stays in the service. */
final class MLuaJavaApi {
    private MLuaJavaApi() { }

    static LuaTable build(MLuaJavaService service) {
        LuaTable table = new LuaTable();
        table.set("Null", MLuaJavaService.JAVA_NULL);
        LuaValue loadClass = new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                String rootName = args.checkjstring(2);
                Class<?> direct = service.tryResolveClass(rootName);
                if (direct == null) {
                    throw new LuaError("[MLua] Could not resolve Java class '" + rootName + "'");
                }
                return service.wrapJavaValue(direct);
            }
        };
        table.set("LoadClass", loadClass);
        table.set("Import", loadClass);
        table.set("import", loadClass);
        table.set("bindClass", loadClass);

        LuaValue packageFn = new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                String rootName = args.checkjstring(2);
                return service.packageProxy(rootName);
            }
        };
        table.set("Package", packageFn);
        table.set("package", packageFn);

        LuaValue newFn = new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                int classIndex = 2;
                Class<?> clazz = service.resolveClassArg(args.arg(classIndex));
                LuaValue constructor = service.wrapJavaValue(clazz).get("new");
                if (constructor.isnil()) {
                    throw new LuaError("[MLua] No public constructor found for " + clazz.getName());
                }
                return constructor.invoke(args.subargs(classIndex + 1));
            }
        };
        table.set("New", newFn);
        table.set("newInstance", newFn);
        table.set("NewExact", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                int classIndex = 2;
                Class<?> clazz = service.resolveClassArg(args.arg(classIndex));
                String descriptor = args.checkjstring(classIndex + 1);
                Constructor<?> constructor = null;
                for (Constructor<?> candidate : service.reflection.constructors(clazz)) {
                    if (MLuaClassViewer.constructorDescriptor(candidate).equals(descriptor)) {
                        constructor = candidate;
                        break;
                    }
                }
                if (constructor == null) {
                    throw new LuaError("[MLua] No public constructor " + clazz.getName() + descriptor
                        + ". Inspect the class with Java:Inspect to see available constructor descriptors.");
                }
                Object[] rawArgs = service.extractInvocationArgs(args, classIndex + 2);
                service.requireExactCallArgumentCount("NewExact", clazz.getName() + descriptor,
                    constructor, rawArgs.length);
                try {
                    return service.wrapJavaValue(constructor.newInstance(service.convertArguments(rawArgs, constructor)));
                } catch (InstantiationException | IllegalAccessException | IllegalArgumentException
                         | InvocationTargetException failure) {
                    throw new LuaError("[MLua] Failed to construct " + clazz.getName() + descriptor
                        + ": " + service.rootMessage(failure));
                }
            }
        });

        LuaValue proxyFn = new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                int startIndex = 2;
                int n = args.narg() - startIndex + 1;
                if (n < 2) {
                    throw new LuaError("[MLua] Java:Proxy requires at least one interface and a Lua table");
                }

                LuaTable implementation = args.checktable(startIndex + n - 1);
                Class<?>[] interfaces = new Class<?>[n - 1];
                for (int i = 0; i < interfaces.length; i++) {
                    Class<?> iface = service.resolveClassArg(args.arg(startIndex + i));
                    if (!iface.isInterface()) {
                        throw new LuaError("[MLua] Java:Proxy only supports interfaces in v1: " + iface.getName());
                    }
                    interfaces[i] = iface;
                }

                return service.wrapJavaValue(service.createLuaInterfaceProxy(interfaces, implementation, service.activeProxyContext()));
            }
        };
        table.set("Proxy", proxyFn);
        table.set("createProxy", proxyFn);
        table.set("GetProxyLimits", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                return service.proxyLimits();
            }
        });
        table.set("IsInstance", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                int valueIndex = 2;
                Object value = service.unwrapJavaValue(args.arg(valueIndex));
                Class<?> clazz = service.resolveClassArg(args.arg(valueIndex + 1));
                return LuaValue.valueOf(value != null && clazz.isInstance(value));
            }
        });
        table.set("Cast", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                int valueIndex = 2;
                Object value = service.unwrapJavaValue(args.arg(valueIndex));
                Class<?> clazz = service.resolveClassArg(args.arg(valueIndex + 1));
                if (value == null) {
                    return LuaValue.NIL;
                }
                if (!clazz.isInstance(value)) {
                    throw new LuaError("[MLua] Cannot cast " + value.getClass().getName() + " to " + clazz.getName());
                }
                return service.wrapJavaValue(value, Object.class, service.frameScope(args.arg(valueIndex)));
            }
        });
        table.set("TypeOf", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                Object value = service.unwrapJavaValue(args.arg(2));
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
                Class<?> clazz = service.resolveTargetClass(args.arg(2));
                LuaTable methods = new LuaTable();
                int index = 1;
                for (Method method : service.reflection.methods(clazz)) {
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
                    entry.set("Descriptor", service.methodSignature(method));
                    entry.set("Signature", service.methodSignature(method));
                    entry.set("JvmDescriptor", MLuaClassViewer.descriptor(method));
                    methods.set(index++, entry);
                }
                return methods;
            }
        });
        table.set("GetFields", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                Class<?> clazz = service.resolveTargetClass(args.arg(2));
                LuaTable fields = new LuaTable();
                int index = 1;
                for (Field field : service.reflection.fields(clazz)) {
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
        table.set("Invoke", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                int targetIndex = 2;
                LuaValue targetValue = args.arg(targetIndex);
                dev.mlua.runtime.client.MLuaRenderFrameScope.View frameScope = service.frameScope(targetValue);
                String methodName = args.checkjstring(targetIndex + 1);
                String descriptor = args.checkjstring(targetIndex + 2);
                Object target;
                boolean staticCall;
                if (targetValue.type() == LuaValue.TSTRING || service.isJavaClassProxy(targetValue)) {
                    target = service.resolveClassArg(targetValue);
                    staticCall = true;
                } else {
                    target = service.unwrapJavaValue(targetValue);
                    if (target == null) throw new LuaError("[MLua] Java:Invoke target must be a Java class or object");
                    staticCall = false;
                }
                Class<?> clazz = target instanceof Class<?> javaClass ? javaClass : target.getClass();
                List<Method> candidates = service.reflection.methods(clazz).stream()
                    .filter(method -> method.getName().equals(methodName))
                    .filter(method -> Modifier.isStatic(method.getModifiers()) == staticCall)
                    .filter(method -> MLuaClassViewer.descriptor(method).equals(descriptor))
                    .toList();
                if (candidates.isEmpty()) {
                    throw new LuaError("[MLua] No exact " + (staticCall ? "static " : "instance ") + "method "
                        + clazz.getName() + "." + methodName + descriptor);
                }
                Method method = candidates.get(0);
                Object[] rawArgs = service.extractInvocationArgs(args, targetIndex + 3);
                service.requireExactCallArgumentCount("Invoke", clazz.getName() + "." + methodName + descriptor,
                    method, rawArgs.length);
                try {
                    Object result = method.invoke(staticCall ? null : target, service.convertArguments(rawArgs, method));
                    return service.wrapJavaValue(result, method.getGenericReturnType(), frameScope);
                } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException error) {
                    throw new LuaError("[MLua] Failed to invoke " + clazz.getName() + "." + methodName
                        + descriptor + ": " + service.rootMessage(error));
                }
            }
        });
        table.set("GetField", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                int targetIndex = 2;
                LuaValue targetValue = args.arg(targetIndex);
                dev.mlua.runtime.client.MLuaRenderFrameScope.View frameScope = service.frameScope(targetValue);
                Object target = service.unwrapJavaValue(targetValue);
                if (target == null) throw new LuaError("[MLua] Java:GetField target cannot be nil");
                String fieldName = args.checkjstring(targetIndex + 1);
                boolean staticTarget = target instanceof Class<?>;
                Class<?> targetClass = staticTarget ? (Class<?>) target : target.getClass();
                Field field = service.members.field(targetClass, fieldName, staticTarget);
                if (field == null) {
                    throw new LuaError("[MLua] No " + (staticTarget ? "static " : "instance ")
                        + "field " + targetClass.getName() + "." + fieldName);
                }
                try {
                    return service.wrapJavaValue(field.get(staticTarget ? null : target), field.getGenericType(), frameScope);
                } catch (IllegalAccessException | IllegalArgumentException error) {
                    throw new LuaError("[MLua] Failed to read field " + targetClass.getName() + "."
                        + fieldName + ": " + service.rootMessage(error));
                }
            }
        });
        table.set("SetField", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                int targetIndex = 2;
                LuaValue targetValue = args.arg(targetIndex);
                dev.mlua.runtime.client.MLuaRenderFrameScope.View frameScope = service.frameScope(targetValue);
                Object target = service.unwrapJavaValue(targetValue);
                if (target == null) throw new LuaError("[MLua] Java:SetField target cannot be nil");
                String fieldName = args.checkjstring(targetIndex + 1);
                boolean staticTarget = target instanceof Class<?>;
                Class<?> targetClass = staticTarget ? (Class<?>) target : target.getClass();
                Field field = service.members.field(targetClass, fieldName, staticTarget);
                if (field == null) {
                    throw new LuaError("[MLua] No " + (staticTarget ? "static " : "instance ")
                        + "field " + targetClass.getName() + "." + fieldName);
                }
                if (Modifier.isFinal(field.getModifiers())) {
                    throw new LuaError("[MLua] Field " + targetClass.getName() + "." + fieldName + " is final");
                }
                try {
                    field.set(staticTarget ? null : target, service.convertLuaValue(args.arg(targetIndex + 2), field.getGenericType()));
                    return service.wrapJavaValue(field.get(staticTarget ? null : target), field.getGenericType(), frameScope);
                } catch (IllegalAccessException | IllegalArgumentException error) {
                    throw new LuaError("[MLua] Failed to write field " + targetClass.getName() + "."
                        + fieldName + ": " + service.rootMessage(error));
                }
            }
        });
        table.set("Inspect", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                Class<?> clazz = service.resolveTargetClass(args.arg(2));
                return MLuaClassViewer.inspect(clazz);
            }
        });
        table.set("ViewClass", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                Class<?> clazz = service.resolveTargetClass(args.arg(2));
                return LuaValue.valueOf(MLuaClassViewer.renderLua(clazz));
            }
        });
        table.set("ClassViewer", table.get("ViewClass"));
        table.set("GetEnumConstants", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                Class<?> clazz = service.resolveClassArg(args.arg(2));
                if (!clazz.isEnum()) {
                    throw new LuaError("[MLua] " + clazz.getName() + " is not an enum type");
                }
                Object[] constants = clazz.getEnumConstants();
                LuaTable values = new LuaTable();
                for (int i = 0; i < constants.length; i++) {
                    values.set(i + 1, service.wrapJavaValue(constants[i]));
                }
                return values;
            }
        });
        table.set("ToTable", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                LuaValue source = args.arg(2);
                Object javaValue = service.unwrapJavaValue(source);
                if (javaValue == null) {
                    throw new LuaError("[MLua] Java:ToTable expects a non-null Java array, Iterable, or map.");
                }
                return service.toLuaTable(javaValue, service.frameScope(source));
            }
        });
        table.set("FromTable", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                int valueIndex = 2;
                LuaValue source = args.arg(valueIndex);
                if (!source.istable()) {
                    throw new LuaError("[MLua] Java:FromTable expects a Lua table as its first argument");
                }
                Class<?> targetType = service.resolveClassArg(args.arg(valueIndex + 1));
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
                        Type keyType = service.resolveClassArg(args.arg(valueIndex + 2));
                        Type valueType = typeArgumentCount == 2
                            ? service.resolveClassArg(args.arg(valueIndex + 3)) : Object.class;
                        conversionType = new MLuaJavaParameterizedType(targetType, keyType, valueType);
                    }
                } else if (Iterable.class.isAssignableFrom(targetType)) {
                    if (typeArgumentCount > 1) {
                        throw new LuaError("[MLua] Java:FromTable iterable conversion accepts one optional element class");
                    }
                    if (typeArgumentCount == 1) {
                        conversionType = new MLuaJavaParameterizedType(targetType,
                            service.resolveClassArg(args.arg(valueIndex + 2)));
                    }
                } else if (typeArgumentCount != 0) {
                    throw new LuaError("[MLua] Java:FromTable array conversion uses the component type declared by the array class");
                }
                return service.wrapJavaValue(service.convertLuaValue(source, conversionType), conversionType);
            }
        });
        MLuaServiceMetadata.attachMethodDescriptions(table, JavaServiceMethods.serviceMethodDescriptions());
        return MLuaServiceBase.normalizeServiceCalls(table, service.name());
    }
}
