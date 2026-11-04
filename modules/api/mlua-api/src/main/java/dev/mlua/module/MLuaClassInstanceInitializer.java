package dev.mlua.module;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

final class MLuaClassInstanceInitializer {
    private MLuaClassInstanceInitializer() { }

    static LuaTable createClassInstance(MLuaRegistration registration, String className, LuaTable initialFields) {
        LuaTable type = registration.classTable(className);
        if (type == null) throw new IllegalStateException("Lua class is not registered: " + className);
        if (type.rawget("__mlua_service_class").toboolean()) {
            throw new IllegalArgumentException("Service class '" + className
                + "' instances are owned by Minecraft:GetService; use GetService instead of CreateInstance.");
        }
        LuaTable instance = registration.attachClassInstance(new LuaTable(), className, null);
        return initializeClassInstance(registration, instance, className, initialFields);
    }

    static LuaTable initializeClassInstance(MLuaRegistration registration, LuaTable instance,
                                            String className, LuaTable initialFields) {
        java.util.Objects.requireNonNull(instance, "instance");
        LuaTable type = registration.classTable(className);
        if (type == null) throw new IllegalStateException("Lua class is not registered: " + className);
        if (type.rawget("__mlua_service_class").toboolean()) {
            throw new IllegalArgumentException("Service class '" + className
                + "' instances are owned by Minecraft:GetService and cannot be initialized manually.");
        }
        String actualClass = registration.classNameOf(instance);
        if (actualClass == null || !registration.isInstanceOf(instance, className)) {
            throw new IllegalArgumentException("Live instance is not attached to class '" + className + "'");
        }
        if (instance.rawget("__mlua_instance_initialized").toboolean()) return instance;
        if (instance.rawget("__mlua_instance_initializing").toboolean()) {
            throw new IllegalStateException("Lua class instance '" + className + "' is already initializing");
        }
        validateFields(type, className, initialFields);
        instance.rawset("__mlua_instance_initializing", LuaValue.TRUE);
        try {
            copyFields(instance, initialFields);
            LuaTable fields = initialFields == null ? new LuaTable() : initialFields;
            initializeClassInitializers(registration, instance, className, fields);
            instance.rawset("__mlua_instance_initialized", LuaValue.TRUE);
            return instance;
        } finally {
            instance.rawset("__mlua_instance_initializing", LuaValue.NIL);
        }
    }

    private static void validateFields(LuaTable type, String className, LuaTable fields) {
        if (fields == null) return;
        LuaValue key = LuaValue.NIL;
        while (true) {
            Varargs next = fields.next(key);
            key = next.arg1();
            if (key.isnil()) return;
            if (!key.isstring()) continue;
            String fieldName = key.tojstring();
            if (fieldName.equals("__type") || fieldName.equals("__class")
                || fieldName.equals("ClassName") || fieldName.equals("__mixin")
                || fieldName.equals("__mixin_name") || fieldName.startsWith("__mlua_")) {
                throw new LuaError("[MLua] Instance field '" + fieldName + "' is reserved for runtime class metadata.");
            }
            if (type.get(key).isfunction()) {
                throw new LuaError("[MLua] Instance field '" + fieldName
                    + "' would shadow a method on class '" + className + "'.");
            }
        }
    }

    private static void copyFields(LuaTable instance, LuaTable fields) {
        if (fields == null) return;
        LuaValue key = LuaValue.NIL;
        while (true) {
            Varargs next = fields.next(key);
            key = next.arg1();
            if (key.isnil()) return;
            instance.set(key, next.arg(2));
        }
    }

    static void initializeClassInitializers(MLuaRegistration registration, LuaTable instance,
                                             String className, LuaTable fields) {
        List<LuaValue> initializers = new ArrayList<>();
        collectClassInitializers(registration, className, new HashSet<>(), initializers);
        for (LuaValue initializer : initializers) {
            initializer.invoke(LuaValue.varargsOf(new LuaValue[]{instance, fields}));
        }
    }

    private static void collectClassInitializers(MLuaRegistration registration, String className,
                                                 Set<String> visited, List<LuaValue> initializers) {
        if (!visited.add(className)) return;
        LuaTable type = registration.classTable(className);
        if (type == null) return;
        String parentName = superclassName(type);
        if (parentName != null) collectClassInitializers(registration, parentName, visited, initializers);
        LuaValue mixinsValue = type.rawget("__mlua_doc_mixins");
        if (mixinsValue.istable()) {
            LuaTable mixins = mixinsValue.checktable();
            for (int index = 1; index <= mixins.length(); index++) {
                String mixinName = mixins.get(index).optjstring("");
                LuaTable mixin = registration.behaviorMixinTable(mixinName);
                LuaValue initializer = mixin == null ? LuaValue.NIL : mixin.get("Init");
                addClassInitializer(className, "mixin '" + mixinName + "'", mixin, initializer, initializers);
            }
        }
        addClassInitializer(className, "class", type, type.rawget("Init"), initializers);
    }

    private static String superclassName(LuaTable type) {
        LuaValue parentsValue = type.rawget("__mlua_doc_parents");
        if (!parentsValue.istable()) return null;
        LuaValue parentValue = parentsValue.checktable().rawget(1);
        if (!parentValue.isstring()) return null;
        String parentName = parentValue.tojstring();
        return parentName.isBlank() ? null : parentName;
    }

    private static void addClassInitializer(String className, String ownerName, LuaTable owner,
                                            LuaValue initializer, List<LuaValue> initializers) {
        if (!initializer.isfunction()) return;
        LuaValue descriptionsValue = owner.rawget("__mlua_doc_methods");
        LuaValue description = descriptionsValue.istable()
            ? descriptionsValue.checktable().rawget("Init") : LuaValue.NIL;
        if (!description.isstring() || description.tojstring().isBlank()) {
            throw new IllegalStateException("Lua " + ownerName + " on class '" + className
                + "' defines Init without registering its description");
        }
        initializers.add(initializer);
    }
}
