package dev.mlua.modules.javabridge.service;

import dev.mlua.common.mixin.MLuaClassViewer;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;

/** Installs Java type inspection, exact invocation, and field access. */
final class MLuaJavaReflectionApi {
    private MLuaJavaReflectionApi() { }

    static void install(LuaTable table, MLuaJavaService service) {
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
                Method method = service.reflection.exactMethod(clazz, methodName, descriptor, staticCall);
                if (method == null) {
                    throw new LuaError("[MLua] No exact " + (staticCall ? "static " : "instance ") + "method "
                        + clazz.getName() + "." + methodName + descriptor);
                }
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
    }
}
