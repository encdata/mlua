package dev.mlua.core.classes;

import dev.mlua.module.MLuaRegistration;
import java.util.HashSet;
import java.util.Map;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/** Owns class method and property definitions and keeps their docs in sync. */
final class MLuaClassMemberRegistry {
    interface Host {
        void markClassActive(String className);
        void removePendingClassIfUnused(String className);
    }

    private final Map<String, LuaTable> classes;
    private final MLuaClassHierarchy hierarchy;
    private final MLuaClassLiveInstances liveInstances;
    private final MLuaClassMetadata metadata;
    private final Host host;

    MLuaClassMemberRegistry(Map<String, LuaTable> classes, MLuaClassHierarchy hierarchy,
                            MLuaClassLiveInstances liveInstances, MLuaClassMetadata metadata, Host host) {
        this.classes = classes;
        this.hierarchy = hierarchy;
        this.liveInstances = liveInstances;
        this.metadata = metadata;
        this.host = host;
    }

    LuaTable registerFunction(LuaTable owner, String name, LuaValue function, String description) {
        java.util.Objects.requireNonNull(owner, "owner");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Function name must be non-empty");
        if (MLuaRegistration.isReservedFunctionName(name)) {
            throw new IllegalArgumentException("Function name is reserved for MLua class metadata: " + name);
        }
        if (function == null || !function.isfunction()) {
            throw new IllegalArgumentException("Registered function must be a Lua function");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("Function description must be non-empty");
        }
        String className = owner.rawget("__type").optjstring("");
        if (!owner.rawget("__class").toboolean() || this.classes.get(className) != owner) {
            throw new IllegalArgumentException("Function owner must be a class registered in this runtime");
        }
        LuaValue existing = owner.rawget(name);
        if (!existing.isnil() && !existing.isfunction()) {
            throw new IllegalStateException("Lua class '" + className + "' already defines non-function member '" + name + "'");
        }
        if (existing.isfunction() && existing != function) {
            throw new IllegalStateException("Lua class '" + className + "' already defines function '" + name
                + "'; registration cannot replace an existing class method");
        }
        if (this.metadata.hasPropertyDefinition(className, name, new HashSet<>())) {
            throw new IllegalStateException("Lua class '" + className + "' already defines property '" + name + "'");
        }
        this.ensureNoExistingInstanceFieldShadowingMethod(className, name);

        LuaValue docsValue = owner.rawget("__mlua_doc_methods");
        if (!docsValue.isnil() && !docsValue.istable()) {
            throw new IllegalStateException("Lua class '" + className + "' method descriptions must be a table");
        }
        LuaTable docs = docsValue.istable() ? docsValue.checktable() : new LuaTable();
        LuaValue oldDescription = docs.rawget(name);
        if (!oldDescription.isnil() && !oldDescription.isstring()) {
            throw new IllegalStateException("Lua class '" + className + "' function '" + name
                + "' has invalid description metadata");
        }
        if (oldDescription.isstring() && !description.equals(oldDescription.tojstring())) {
            throw new IllegalStateException("Lua class '" + className + "' function '" + name
                + "' is already registered with a different description");
        }
        this.host.markClassActive(className);
        owner.rawset(name, function);
        docs.rawset(name, LuaValue.valueOf(description));
        owner.rawset("__mlua_doc_methods", docs);
        this.metadata.refreshInheritedMethodDescriptionsFrom(className);
        return owner;
    }

    LuaTable registerProperty(String className, String propertyName, LuaValue getter, LuaValue setter,
                              String description) {
        return this.registerProperty(className, propertyName, getter, setter, null, null, description);
    }

    LuaTable registerProperty(String className, String propertyName, LuaValue getter, LuaValue setter,
                              String valueType, String side, String description) {
        if ((valueType == null) != (side == null)) {
            throw new IllegalArgumentException("Property value type and side must be declared together");
        }
        if (valueType != null && valueType.isBlank()) {
            throw new IllegalArgumentException("Property value type must be non-empty");
        }
        if (side != null && side.isBlank()) throw new IllegalArgumentException("Property side must be non-empty");
        LuaTable owner = this.classes.get(className);
        if (owner == null) throw new IllegalStateException("Lua class is not registered: " + className);
        if (propertyName == null || propertyName.isBlank()) {
            throw new IllegalArgumentException("Property name must be non-empty");
        }
        if (MLuaRegistration.isReservedFunctionName(propertyName)) {
            throw new IllegalArgumentException("Property name is reserved for MLua class metadata: " + propertyName);
        }
        if (getter == null || !getter.isfunction()) {
            throw new IllegalArgumentException("Property getter must be a Lua function");
        }
        if (setter != null && !setter.isnil() && !setter.isfunction()) {
            throw new IllegalArgumentException("Property setter must be a Lua function or nil");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("Property description must be non-empty");
        }
        this.ensureNoExistingInstanceField(className, propertyName);
        if (!owner.get(propertyName).isnil()) {
            throw new IllegalStateException("Lua class '" + className + "' already defines member '" + propertyName + "'");
        }

        LuaValue descriptorsValue = owner.rawget("__mlua_properties");
        if (!descriptorsValue.isnil() && !descriptorsValue.istable()) {
            throw new IllegalStateException("Lua class '" + className + "' has invalid property descriptors");
        }
        LuaTable descriptors = descriptorsValue.istable()
            ? MLuaClassMetadata.copyTableOrNil(descriptorsValue).checktable() : new LuaTable();
        LuaValue oldDescriptor = descriptorsValue.istable()
            ? descriptorsValue.checktable().rawget(propertyName) : LuaValue.NIL;
        if (oldDescriptor.istable()) {
            LuaTable previous = oldDescriptor.checktable();
            if (previous.rawget("get") != getter
                    || previous.rawget("set") != (setter == null ? LuaValue.NIL : setter)) {
                throw new IllegalStateException("Lua class '" + className + "' already defines property '"
                    + propertyName + "'");
            }
        } else if (!oldDescriptor.isnil()) {
            throw new IllegalStateException("Lua class '" + className + "' property '" + propertyName
                + "' has invalid metadata");
        }

        LuaValue docsValue = owner.rawget("__mlua_doc_properties");
        if (!docsValue.isnil() && !docsValue.istable()) {
            throw new IllegalStateException("Lua class '" + className + "' property descriptions must be a table");
        }
        LuaTable docs = docsValue.istable()
            ? MLuaClassMetadata.copyTableOrNil(docsValue).checktable() : new LuaTable();
        LuaValue oldDescription = docsValue.istable()
            ? docsValue.checktable().rawget(propertyName) : LuaValue.NIL;
        if (oldDescription.isstring() && !description.equals(oldDescription.tojstring())) {
            throw new IllegalStateException("Lua class '" + className + "' property '" + propertyName
                + "' has a different description");
        }
        if (!oldDescription.isnil() && !oldDescription.isstring()) {
            throw new IllegalStateException("Lua class '" + className + "' property '" + propertyName
                + "' has invalid description metadata");
        }

        LuaTable descriptor = oldDescriptor.istable()
            ? MLuaClassMetadata.copyTableOrNil(oldDescriptor).checktable() : new LuaTable();
        descriptor.rawset("get", getter);
        descriptor.rawset("set", setter == null ? LuaValue.NIL : setter);
        if (valueType != null) {
            descriptor.rawset("type", LuaValue.valueOf(valueType));
            descriptor.rawset("side", LuaValue.valueOf(side));
        }
        descriptors.rawset(propertyName, descriptor);
        docs.rawset(propertyName, LuaValue.valueOf(description));
        this.host.markClassActive(className);
        owner.rawset("__mlua_properties", descriptors);
        owner.rawset("__mlua_doc_properties", docs);
        this.metadata.refreshAttachedPropertyDescriptionsFrom(className);
        return owner;
    }

    boolean unregisterProperty(String className, String propertyName, LuaValue expectedGetter) {
        LuaTable owner = this.classes.get(className);
        if (owner == null || propertyName == null || expectedGetter == null) return false;
        LuaValue descriptorsValue = owner.rawget("__mlua_properties");
        if (!descriptorsValue.istable()) return false;
        LuaTable sourceDescriptors = descriptorsValue.checktable();
        LuaValue descriptorValue = sourceDescriptors.rawget(propertyName);
        if (!descriptorValue.istable() || descriptorValue.checktable().rawget("get") != expectedGetter) return false;
        LuaTable descriptors = MLuaClassMetadata.copyTableOrNil(descriptorsValue).checktable();
        descriptors.rawset(propertyName, LuaValue.NIL);
        owner.rawset("__mlua_properties", descriptors);
        LuaValue docsValue = owner.rawget("__mlua_doc_properties");
        if (docsValue.istable()) {
            LuaTable docs = MLuaClassMetadata.copyTableOrNil(docsValue).checktable();
            docs.rawset(propertyName, LuaValue.NIL);
            owner.rawset("__mlua_doc_properties", docs);
        }
        this.metadata.refreshAttachedPropertyDescriptionsFrom(className);
        this.host.removePendingClassIfUnused(className);
        return true;
    }

    boolean unregisterFunction(String className, String name, LuaValue expectedFunction) {
        LuaTable owner = this.classes.get(className);
        if (owner == null || name == null || expectedFunction == null || owner.rawget(name) != expectedFunction) {
            return false;
        }
        owner.rawset(name, LuaValue.NIL);
        if (name.equals("new") && owner.rawget("__mlua_generic_constructor") == expectedFunction) {
            owner.rawset("__mlua_generic_constructor", LuaValue.NIL);
        }
        LuaValue docs = owner.rawget("__mlua_doc_methods");
        if (docs.istable()) docs.checktable().rawset(name, LuaValue.NIL);
        LuaValue dispatchers = owner.rawget("__mlua_service_dispatchers");
        if (dispatchers.istable()) dispatchers.checktable().rawset(name, LuaValue.NIL);
        this.metadata.refreshInheritedMethodDescriptionsFrom(className);
        this.host.removePendingClassIfUnused(className);
        return true;
    }

    void ensureNoExistingInstanceField(String className, String propertyName) {
        for (LuaTable instance : this.liveInstances.ofClassAndDescendants(
                className, this.hierarchy.descendantsOf(className))) {
            String instanceClassName = instance.rawget("__type").optjstring("");
            if (!instance.rawget(propertyName).isnil()) {
                throw new IllegalStateException("Cannot register class property '" + propertyName
                    + "': a live " + instanceClassName + " instance already has that field");
            }
        }
    }

    void ensureNoExistingInstanceFieldShadowingMethod(String className, String methodName) {
        for (LuaTable instance : this.liveInstances.ofClassAndDescendants(
                className, this.hierarchy.descendantsOf(className))) {
            String instanceClassName = instance.rawget("__type").optjstring("");
            if (!instance.rawget(methodName).isnil() && !instance.rawget(methodName).isfunction()) {
                throw new IllegalStateException("Cannot register class method '" + methodName
                    + "': a live " + instanceClassName + " instance already has a non-function field with that name");
            }
        }
    }
}
