package dev.mlua.minecraft.binding;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.function.Supplier;
import dev.mlua.core.MLuaRuntime;
import dev.mlua.core.scheduler.MLuaTaskSchedulerView;
import dev.mlua.module.MLuaRegistration;
import dev.mlua.minecraft.ui.element.GuiBase;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/** Owns native UI class validation, Lua construction, and reflected property metadata. */
final class MLuaUIClassRegistry {
    private MLuaUIClassRegistry() { }

    static MLuaTaskSchedulerView schedulerFor(MLuaRegistration registration) {
        return registration instanceof MLuaRuntime runtime
            ? runtime.session().clientScheduler()
            : MLuaRuntime.get().session().clientScheduler();
    }

    static LuaTable registerNativeClass(MLuaRegistration registration, String name,
                                               Class<? extends GuiBase> instanceType,
                                               Supplier<? extends GuiBase> factory,
                                               String description, String parentName) {
        if (registration == null) throw new IllegalArgumentException("UI class registration requires an MLua registry");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("UI class name must be non-empty");
        if (instanceType == null || !GuiBase.class.isAssignableFrom(instanceType)) {
            throw new IllegalArgumentException("Native UI class must use a GuiBase implementation type");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("UI class description must be non-empty");
        }
        LuaTable existing = registration.classTable(name);
        if (existing != null) {
            LuaValue existingType = existing.rawget("__mlua_ui_java_type");
            Object existingTypeObject = existingType.isuserdata() ? existingType.touserdata() : null;
            Class<?> existingJavaType = existingTypeObject instanceof Class<?> javaType ? javaType : null;
            if (existingJavaType == null) {
                throw new IllegalStateException("Lua class '" + name + "' already exists and has no native UI backing type");
            }
            if (existingJavaType != instanceType) {
                throw new IllegalStateException("UI class '" + name + "' already uses native type '"
                    + existingJavaType.getName() + "'");
            }
        }
        if (parentName == null || parentName.isBlank()) {
            throw new IllegalArgumentException("Native UI classes must declare a parent class");
        }
        LuaTable parent = registration.classTable(parentName);
        if (parent == null) {
            throw new IllegalStateException("UI class '" + name + "' requires registered parent class '"
                + parentName + "'");
        }
        boolean uiRootParent = name.equals("GuiBase")
            && parentName.equals(registration.requireInstanceBaseClassName());
        if (!uiRootParent && !registration.isSubclassOf(parentName, "GuiBase")) {
            throw new IllegalArgumentException("UI class '" + name + "' parent '" + parentName
                + "' must inherit from GuiBase");
        }
        if (!uiRootParent) validateNativeParentType(name, parentName, instanceType, registration, new HashSet<>());
        return registerClass(registration, name, instanceType, factory, description, parent);
    }

    @Deprecated(forRemoval = false)
    static LuaTable registerNativeClass(MLuaRegistration registration, String name,
                                               Class<? extends GuiBase> instanceType,
                                               Supplier<? extends GuiBase> factory,
                                               String description, String... parentNames) {
        if (parentNames == null || parentNames.length != 1) {
            throw new IllegalArgumentException("Native UI classes require exactly one superclass");
        }
        return registerNativeClass(registration, name, instanceType, factory, description, parentNames[0]);
    }

    private static void validateNativeParentType(String className, String parentName, Class<?> instanceType,
                                                 MLuaRegistration registration, java.util.Set<String> visited) {
        if (!visited.add(parentName)) return;
        LuaTable parent = registration.classTable(parentName);
        if (parent == null) return;
        LuaValue nativeTypeValue = parent.rawget("__mlua_ui_java_type");
        if (!nativeTypeValue.isnil()) {
            Object nativeTypeObject = nativeTypeValue.isuserdata() ? nativeTypeValue.touserdata() : null;
            if (!(nativeTypeObject instanceof Class<?> nativeType)) {
                throw new IllegalStateException("UI parent class '" + parentName + "' has invalid native type metadata");
            }
            if (!nativeType.isAssignableFrom(instanceType)) {
                throw new IllegalArgumentException("UI class '" + className + "' uses native type '"
                    + instanceType.getName() + "' which does not extend declared parent '" + parentName
                    + "' backed by '" + nativeType.getName() + "'");
            }
            return;
        }
        String inheritedParent = registration.superclassName(parentName);
        if (inheritedParent != null) {
            validateNativeParentType(className, inheritedParent, instanceType, registration, visited);
        }
    }

    private static LuaTable registerClass(MLuaRegistration registration, String name, Class<?> instanceType,
                                         Supplier<? extends GuiBase> factory, String description,
                                         LuaTable parent) {
        LuaTable existing = registration.classTable(name);
        LuaTable classTable = existing != null ? existing : new LuaTable();
        classTable.rawset("__mlua_doc_type", name);
        classTable.rawset("__mlua_doc_properties", propertyMetadata(instanceType));
        classTable.rawset("__mlua_ui_java_type", LuaValue.userdataOf(instanceType));
        VarArgFunction constructor = null;
        if (factory != null) {
            constructor = new VarArgFunction() {
                public Varargs invoke(Varargs args) {
                    LuaValue receiver = args.arg1();
                    boolean calledWithClass = receiver.istable() && receiver.rawget("__class").toboolean();
                    String instanceClass = calledWithClass
                        ? receiver.rawget("__type").optjstring(name) : name;
                    String instanceName = args.arg(calledWithClass ? 2 : 1).optjstring(name);
                    GuiBase instance = factory.get();
                    if (instance == null || !instanceType.isInstance(instance)) {
                        throw new LuaError("[MLua] UI factory for class '" + name
                            + "' must return a live " + instanceType.getName() + " instance.");
                    }
                    instance.setScheduler(schedulerFor(registration));
                    instance.setRegistration(registration);
                    instance.setName(instanceName);
                    LuaTable liveInstance = registration.attachClassInstance(instance.toLua(), instanceClass, null);
                    return liveInstance;
                }
            };
        }
        if (existing == null && parent != null) {
            LuaTable meta = new LuaTable();
            meta.set("__index", parent);
            classTable.setmetatable(meta);
        }
        String parentName = parent == null ? null : parent.rawget("__type").optjstring("");
        classTable = registration.registerClassTable(name, classTable, parentName, description);
        if (constructor != null) {
            registration.registerClassFunction(name, "new", constructor,
                "Creates a new " + name + " instance.");
        }
        return classTable;
    }

    private static LuaTable propertyMetadata(Class<?> type) {
        Map<String, PropertyMetadata> access = new HashMap<>();
        for (Method method : type.getMethods()) {
            if (!Modifier.isPublic(method.getModifiers()) || Modifier.isStatic(method.getModifiers())) continue;
            String methodName = method.getName();
            if (methodName.equals("getClass")) continue;
            String property = null;
            int accessIndex = -1;
            if (methodName.startsWith("get") && methodName.length() > 3 && method.getParameterCount() == 0
                && method.getReturnType() != Void.TYPE) {
                property = methodName.substring(3);
                accessIndex = 0;
            } else if (methodName.startsWith("is") && methodName.length() > 2 && method.getParameterCount() == 0
                && (method.getReturnType() == Boolean.TYPE || method.getReturnType() == Boolean.class)) {
                property = methodName.substring(2);
                accessIndex = 0;
            } else if (methodName.startsWith("set") && methodName.length() > 3 && method.getParameterCount() == 1) {
                property = methodName.substring(3);
                accessIndex = 1;
            }
            if (property == null || property.equals("SelfTable") || property.equals("Children")) continue;
            PropertyMetadata metadata = access.computeIfAbsent(property, ignored -> new PropertyMetadata());
            if (method.getReturnType() == dev.mlua.core.signal.MLuaSignal.class) {
                metadata.signal = true;
            } else if (accessIndex == 0) {
                metadata.readable = true;
                metadata.valueType = method.getReturnType();
            } else {
                metadata.writable = true;
                if (metadata.valueType == null) metadata.valueType = method.getParameterTypes()[0];
            }
        }
        PropertyMetadata parent = access.computeIfAbsent("Parent", ignored -> new PropertyMetadata());
        parent.readable = true;
        parent.writable = true;
        parent.valueType = GuiBase.class;

        LuaTable documented = new LuaTable();
        access.forEach((name, metadata) -> {
            LuaTable descriptor = new LuaTable();
            descriptor.set("readable", LuaValue.valueOf(metadata.readable));
            descriptor.set("writable", LuaValue.valueOf(metadata.writable));
            descriptor.set("signal", LuaValue.valueOf(metadata.signal));
            if (metadata.valueType != null) descriptor.set("type", LuaValue.valueOf(name.endsWith("Color3")
                ? "Color3" : luaTypeName(metadata.valueType)));
            descriptor.set("side", LuaValue.valueOf("client:Client"));
            documented.set(name, descriptor);
        });
        return documented;
    }

    private static String luaTypeName(Class<?> type) {
        if (type == String.class || type == Character.class || type == char.class) return "string";
        if (type == Boolean.class || type == boolean.class) return "boolean";
        if (Number.class.isAssignableFrom(type) || type.isPrimitive() && type != boolean.class && type != char.class) return "number";
        if (LuaValue.class.isAssignableFrom(type)) return "table";
        if (type.isEnum()) return type.getSimpleName();
        return type.getSimpleName();
    }

    private static final class PropertyMetadata {
        private boolean readable;
        private boolean writable;
        private boolean signal;
        private Class<?> valueType;
    }

}
