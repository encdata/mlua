package dev.mlua.module;

import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;

public interface MLuaRegistration {
    default void onRegistrationRollback(Runnable cleanup) {
        throw new UnsupportedOperationException("Registration rollback callbacks are not supported by this runtime");
    }

    default void registerDocumentation(MLuaDocBuilder.Contribution contribution) {
        throw new UnsupportedOperationException("Addon documentation registration is not supported by this runtime");
    }

    default boolean unregisterDocumentation(String id, String owner) {
        throw new UnsupportedOperationException("Addon documentation removal is not supported by this runtime");
    }

    @Deprecated(forRemoval = false)
    default LuaTable registerClass(String name, String parentName) {
        throw new UnsupportedOperationException("Use class registration with a description");
    }

    LuaTable registerClass(String name, String parentName, String description);

    @Deprecated(forRemoval = false)
    default LuaTable registerClassTable(String name, LuaTable classTable, String parentName) {
        throw new UnsupportedOperationException("Use class table registration with a description");
    }

    LuaTable registerClassTable(String name, LuaTable classTable, String parentName, String description);

    @Deprecated(forRemoval = false)
    default LuaTable registerClass(String name, LuaTable classTable, String... parentNames) {
        java.util.List<String> parents = normalizeParentNames(parentNames == null
            ? java.util.List.of() : java.util.Arrays.asList(parentNames));
        return registerClassTable(name, classTable, parents.isEmpty() ? null : parents.get(0));
    }

    @Deprecated(forRemoval = false)
    default LuaTable registerClassWithParent(String name, LuaTable classTable, String parentName,
                                             String description) {
        return registerClassTable(name, classTable, parentName, description);
    }

    @Deprecated(forRemoval = false)
    default LuaTable registerClass(String name, LuaTable classTable, java.util.List<String> parentNames, String description) {
        java.util.Objects.requireNonNull(parentNames, "parentNames");
        java.util.List<String> parents = normalizeParentNames(parentNames);
        return registerClassTable(name, classTable, parents.isEmpty() ? null : parents.get(0), description);
    }

    @Deprecated(forRemoval = false)
    default LuaTable registerClassWithParents(String name, java.util.List<String> parentNames, String description) {
        java.util.Objects.requireNonNull(parentNames, "parentNames");
        LuaTable classTable = classTable(name);
        if (classTable == null) classTable = new LuaTable();
        return registerClass(name, classTable, parentNames, description);
    }

    @Deprecated(forRemoval = false)
    default LuaTable registerInstantiableClass(String name, java.util.List<String> parentNames,
                                               String description) {
        java.util.List<String> parents = normalizeParentNames(parentNames == null ? java.util.List.of() : parentNames);
        return registerInstantiableClassInternal(name, parents.isEmpty() ? null : parents.get(0), description);
    }

    private LuaTable registerInstantiableClassInternal(String name, String parentName, String description) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Class name must be non-empty");
        if (description == null || description.isBlank()) throw new IllegalArgumentException("Class description must be non-empty");
        String instanceBase = requireInstanceBaseClassName();
        if (name.equals(instanceBase)) {
            throw new IllegalArgumentException("The shared instance base class cannot be registered as a concrete instance class");
        }
        String parent = parentName == null || parentName.isBlank() ? null : parentName;
        if (parent != null && !isClassOrParent(parent, instanceBase, new HashSet<>())) {
            if (isClassOrParent(instanceBase, parent, new HashSet<>())) {
                parent = null;
            } else {
                throw new IllegalArgumentException("Instantiable Lua class '" + name + "' must inherit from '"
                    + instanceBase + "'; superclass '" + parent
                    + "' does not. Extend the instance class and use behavior mixins for shared behavior.");
            }
        }
        if (parent == null) parent = instanceBase;
        LuaTable existing = classTable(name);
        LuaValue declaredConstructor = existing == null ? LuaValue.NIL : existing.rawget("new");
        if (!declaredConstructor.isnil() && !declaredConstructor.isfunction()) {
            throw new IllegalStateException("Lua class '" + name
                + "' defines 'new' as a non-function; constructors must be callable class functions");
        }
        LuaTable type = registerClassTable(name, existing == null ? new LuaTable() : existing,
            parent, description);
        if (type.rawget("__mlua_service_class").toboolean()
            || type.get("__mlua_ui_java_type").isuserdata()) return type;
        if (!type.rawget("new").isnil()) return type;
        LuaValue inheritedConstructor = type.get("new");
        if (inheritedConstructor.isfunction()
            && !hasInheritedGenericConstructor(this, type, inheritedConstructor, new HashSet<>())) return type;

        VarArgFunction constructor = new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                int fieldsIndex = args.arg1() == type ? 2 : 1;
                LuaValue fields = args.arg(fieldsIndex);
                if (!fields.isnil() && !fields.istable()) {
                    throw new LuaError("[MLua] " + name + ".new fields must be a table when provided.");
                }
                return createClassInstance(name, fields.istable() ? fields.checktable() : null);
            }
        };
        registerClassFunction(name, "new", constructor,
            "Creates a live " + name + " instance and initializes it with the supplied fields.");
        type.rawset("__mlua_generic_constructor", constructor);
        return type;
    }

    default LuaTable registerInstantiableClass(String name, String parentName, String description) {
        return registerInstantiableClassInternal(name, parentName, description);
    }

    private static boolean hasInheritedGenericConstructor(MLuaRegistration registration, LuaTable type,
                                                          LuaValue expectedConstructor, Set<String> visited) {
        String className = type.rawget("__type").optjstring("");
        if (className.isBlank() || !visited.add(className)) return false;
        if (type.rawget("new") == expectedConstructor) {
            return type.rawget("__mlua_generic_constructor") == expectedConstructor;
        }
        LuaValue mixinsValue = type.rawget("__mlua_doc_mixins");
        if (mixinsValue.istable()) {
            LuaTable mixins = mixinsValue.checktable();
            for (int index = 1; index <= mixins.length(); index++) {
                LuaTable mixin = registration.behaviorMixinTable(mixins.rawget(index).optjstring(""));
                if (mixin != null && !mixin.get("new").isnil()) return false;
            }
        }
        String parentName = superclassName(type);
        if (parentName == null) return false;
        LuaTable parent = registration.classTable(parentName);
        if (parent == null) return false;
        LuaValue parentConstructor = parent.get("new");
        if (parentConstructor.isnil() || parentConstructor != expectedConstructor) return false;
        return hasInheritedGenericConstructor(registration, parent, expectedConstructor, visited);
    }

    private static java.util.List<String> normalizeParentNames(java.util.List<String> parentNames) {
        java.util.List<String> parents = new java.util.ArrayList<>();
        for (String parent : parentNames) {
            if (parent == null || parent.isBlank()) continue;
            if (parents.contains(parent)) {
                throw new IllegalArgumentException("Lua class parent is listed more than once: " + parent);
            }
            parents.add(parent);
        }
        if (parents.size() > 1) {
            throw new IllegalArgumentException("Lua classes support one superclass; use behavior mixins for shared behavior");
        }
        return parents;
    }

    private static void recordClassDescription(LuaTable table, String name, String description) {
        if (description == null || description.isBlank()) return;
        LuaValue existing = table.rawget("__mlua_doc_description");
        if (existing.isstring() && !description.equals(existing.tojstring())) {
            throw new IllegalStateException("Lua class '" + name + "' is already registered with a different description");
        }
        if (!existing.isnil() && !existing.isstring()) {
            throw new IllegalStateException("Lua class '" + name + "' has invalid description metadata");
        }
        table.rawset("__mlua_doc_description", LuaValue.valueOf(description));
    }

    private static void validateClassDescription(LuaTable table, String name, String description) {
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("Lua class '" + name + "' must have a non-empty description");
        }
        if (table == null) return;
        LuaValue existing = table.rawget("__mlua_doc_description");
        if (!existing.isnil() && !existing.isstring()) {
            throw new IllegalStateException("Lua class '" + name + "' has invalid description metadata");
        }
        if (existing.isstring() && !description.equals(existing.tojstring())) {
            throw new IllegalStateException("Lua class '" + name + "' is already registered with a different description");
        }
    }

    LuaTable classTable(String name);

    default void registerContentType(String domain, String owner, String name, String base,
                                    String description, LuaTable defaults) {
        throw new UnsupportedOperationException("Script-defined content types are not supported by this runtime");
    }

    default LuaTable resolveContentType(String domain, String owner, LuaTable properties) {
        throw new UnsupportedOperationException("Script-defined content types are not supported by this runtime");
    }

    default Map<String, String> contentTypes(String domain, String owner) {
        throw new UnsupportedOperationException("Script-defined content types are not supported by this runtime");
    }

    default boolean unregisterContentType(String domain, String owner, String name) {
        throw new UnsupportedOperationException("Script-defined content type removal is not supported by this runtime");
    }

    default String classDescription(String className) {
        LuaTable type = classTable(className);
        LuaValue value = type == null ? LuaValue.NIL : type.rawget("__mlua_doc_description");
        return value.isstring() ? value.tojstring() : null;
    }

    default String classMethodDescription(String className, String methodName) {
        LuaTable type = classTable(className);
        if (type == null || methodName == null) return null;
        LuaValue own = type.rawget("__mlua_doc_methods");
        LuaValue description = own.istable() ? own.checktable().rawget(methodName) : LuaValue.NIL;
        if (description.isstring()) return description.tojstring();
        LuaValue inherited = type.rawget("__mlua_doc_inherited_methods");
        description = inherited.istable() ? inherited.checktable().rawget(methodName) : LuaValue.NIL;
        return description.isstring() ? description.tojstring() : null;
    }

    default void refreshClassMetadata(String className) {
        throw new UnsupportedOperationException("Class metadata refresh is not supported by this runtime");
    }

    default boolean unregisterClass(String name) {
        throw new UnsupportedOperationException("Class unregistration is not supported by this runtime");
    }

    default boolean unregisterClassIfUnused(String name) {
        throw new UnsupportedOperationException("Safe class unregistration is not supported by this runtime");
    }

    default LuaTable registerBehaviorMixin(String name, String description) {
        throw new UnsupportedOperationException("Behavior mixin registration is not supported by this runtime");
    }

    default LuaTable behaviorMixinTable(String name) {
        throw new UnsupportedOperationException("Behavior mixin lookup is not supported by this runtime");
    }

    default java.util.Set<String> behaviorMixinNames() {
        throw new UnsupportedOperationException("Behavior mixin lookup is not supported by this runtime");
    }

    default boolean hasBehaviorMixin(String className, String mixinName) {
        if (className == null || className.isBlank() || mixinName == null || mixinName.isBlank()) return false;
        return hasBehaviorMixinInHierarchy(className, mixinName, new HashSet<>());
    }

    private boolean hasBehaviorMixinInHierarchy(String className, String mixinName, Set<String> visited) {
        if (!visited.add(className)) return false;
        LuaTable type = classTable(className);
        if (type == null) return false;
        LuaValue mixinsValue = type.rawget("__mlua_doc_mixins");
        if (mixinsValue.istable()) {
            LuaTable mixins = mixinsValue.checktable();
            for (int index = 1; index <= mixins.length(); index++) {
                if (mixinName.equals(mixins.rawget(index).optjstring(""))) return true;
            }
        }
        String parentName = superclassName(type);
        return parentName != null && hasBehaviorMixinInHierarchy(parentName, mixinName, visited);
    }

    default boolean unregisterBehaviorMixin(String mixinName) {
        throw new UnsupportedOperationException("Behavior mixin unregistration is not supported by this runtime");
    }

    default boolean removeBehaviorMixin(String className, String mixinName) {
        throw new UnsupportedOperationException("Behavior mixin removal is not supported by this runtime");
    }

    default LuaTable registerBehaviorMixinFunction(String mixinName, String functionName,
                                                   LuaValue function, String description) {
        throw new UnsupportedOperationException("Behavior mixin registration is not supported by this runtime");
    }

    default boolean unregisterBehaviorMixinFunction(String mixinName, String functionName,
                                                    LuaValue expectedFunction) {
        throw new UnsupportedOperationException("Behavior mixin function unregistration is not supported by this runtime");
    }

    default void applyBehaviorMixin(String className, String mixinName) {
        throw new UnsupportedOperationException("Behavior mixin registration is not supported by this runtime");
    }

    default java.util.Set<String> classNames() {
        throw new UnsupportedOperationException("Class registry lookup is not supported by this runtime");
    }

    default boolean isSubclassOf(String className, String parentName) {
        if (className == null || className.isBlank() || parentName == null || parentName.isBlank()) return false;
        return isClassOrParent(className, parentName, new HashSet<>());
    }

    default String superclassName(String className) {
        if (className == null || className.isBlank()) return null;
        LuaTable type = classTable(className);
        return type == null ? null : superclassName(type);
    }

    default boolean isInstanceOf(LuaValue instance, String className) {
        if (instance == null || !instance.istable() || className == null || className.isBlank()) return false;
        String actual = classNameOf(instance);
        return actual != null && isClassOrParent(actual, className, new HashSet<>());
    }

    default String classNameOf(LuaValue instance) {
        if (instance == null || !instance.istable()) return null;
        LuaTable table = instance.checktable();
        if (table.rawget("__class").toboolean()) return null;
        LuaValue actual = table.rawget("__type");
        if (!actual.isstring()) return null;
        String className = actual.tojstring();
        return classTable(className) == null ? null : className;
    }

    private boolean isClassOrParent(String currentName, String targetName, Set<String> visited) {
        if (targetName.equals(currentName)) return true;
        if (!visited.add(currentName)) return false;
        LuaTable currentClass = classTable(currentName);
        if (currentClass == null) return false;
        String parentName = superclassName(currentClass);
        return parentName != null && isClassOrParent(parentName, targetName, visited);
    }

    default LuaTable registerClassFunction(String className, String functionName,
                                           LuaValue function, String description) {
        if (functionName == null || functionName.isBlank()) throw new IllegalArgumentException("Function name must be non-empty");
        requireUserFunctionName(functionName);
        if (function == null || !function.isfunction()) throw new IllegalArgumentException("Registered function must be a Lua function");
        if (description == null || description.isBlank()) throw new IllegalArgumentException("Function description must be non-empty");
        LuaTable owner = classTable(className);
        if (owner == null) throw new IllegalStateException("Lua class is not registered: " + className);
        LuaValue existing = owner.rawget(functionName);
        boolean replacingGeneratedConstructor = functionName.equals("new") && existing.isfunction()
            && owner.rawget("__mlua_generic_constructor") == existing;
        if (!existing.isnil() && existing != function && !replacingGeneratedConstructor) {
            throw new IllegalStateException("Lua class '" + className + "' already defines function '"
                + functionName + "'; use registerFunction for an intentional override");
        }
        LuaValue docsValue = owner.rawget("__mlua_doc_methods");
        if (!docsValue.isnil() && !docsValue.istable()) {
            throw new IllegalStateException("Lua class '" + className + "' method descriptions must be a table");
        }
        if (docsValue.istable()) {
            LuaValue existingDescription = docsValue.checktable().rawget(functionName);
            if (!existingDescription.isnil() && !existingDescription.isstring()) {
                throw new IllegalStateException("Lua class '" + className + "' function '" + functionName
                    + "' has invalid description metadata");
            }
            if (existingDescription.isstring() && !description.equals(existingDescription.tojstring())
                && existing != function && !replacingGeneratedConstructor) {
                throw new IllegalStateException("Lua class '" + className + "' function '" + functionName
                    + "' is already registered with a different description");
            }
        }
        if (replacingGeneratedConstructor && !unregisterClassFunction(className, functionName, existing)) {
            throw new IllegalStateException("Generated constructor for Lua class '" + className
                + "' can only be replaced by the registration that owns it");
        }
        return registerFunction(owner, functionName, function, description);
    }

    default boolean unregisterClassFunction(String className, String functionName, LuaValue expectedFunction) {
        throw new UnsupportedOperationException("Class function unregistration is not supported by this runtime");
    }

    default LuaTable registerClassProperty(String className, String propertyName,
                                           LuaValue getter, LuaValue setter, String description) {
        throw new UnsupportedOperationException("Class property registration is not supported by this runtime");
    }

    default LuaTable registerClassProperty(String className, String propertyName,
                                           LuaValue getter, LuaValue setter, String valueType,
                                           String side, String description) {
        if (valueType == null || valueType.isBlank()) throw new IllegalArgumentException("Property value type must be non-empty");
        if (side == null || side.isBlank()) throw new IllegalArgumentException("Property side must be non-empty");
        LuaTable owner = registerClassProperty(className, propertyName, getter, setter, description);
        LuaValue descriptors = owner.rawget("__mlua_properties");
        LuaValue descriptor = descriptors.istable() ? descriptors.checktable().rawget(propertyName) : LuaValue.NIL;
        if (!descriptor.istable()) throw new IllegalStateException("Registered class property has no descriptor: " + className + "." + propertyName);
        descriptor.set("type", LuaValue.valueOf(valueType));
        descriptor.set("side", LuaValue.valueOf(side));
        return owner;
    }

    default boolean unregisterClassProperty(String className, String propertyName, LuaValue expectedGetter) {
        throw new UnsupportedOperationException("Class property unregistration is not supported by this runtime");
    }

    default LuaTable registerServiceFunction(String className, String functionName, String description) {
        if (className == null || className.isBlank()) throw new IllegalArgumentException("Service class name must be non-empty");
        if (functionName == null || functionName.isBlank()) throw new IllegalArgumentException("Service function name must be non-empty");
        requireUserFunctionName(functionName);
        if (description == null || description.isBlank()) throw new IllegalArgumentException("Service function description must be non-empty");
        LuaTable owner = classTable(className);
        if (owner == null) throw new IllegalStateException("Lua class is not registered: " + className);
        if (!owner.rawget("__mlua_service_class").optboolean(false)) {
            throw new IllegalStateException("Lua class '" + className + "' is not a registered service class");
        }
        LuaValue classDescription = owner.rawget("__mlua_doc_description");
        if (!classDescription.isstring() || classDescription.tojstring().isBlank()) {
            throw new IllegalStateException("Register a described service class before registering its functions: " + className);
        }
        LuaValue dispatchersValue = owner.rawget("__mlua_service_dispatchers");
        if (!dispatchersValue.isnil() && !dispatchersValue.istable()) {
            throw new IllegalStateException("Lua service class '" + className + "' dispatcher metadata must be a table");
        }
        LuaValue existing = owner.rawget(functionName);
        if (existing.isfunction()) return registerClassFunction(className, functionName, existing, description);
        if (!existing.isnil()) throw new IllegalStateException("Lua class '" + className
            + "' already defines non-function member '" + functionName + "'");
        LuaValue dispatcher = new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                LuaValue receiver = args.arg1();
                LuaValue target = serviceInstanceMethod(receiver, functionName);
                if (!target.isfunction() || target == this) {
                    throw new LuaError("[MLua] Service method '" + functionName
                        + "' requires a live service instance.");
                }
                return target.invoke(args);
            }
        };
        LuaTable registered = registerClassFunction(className, functionName, dispatcher, description);
        LuaTable dispatchers;
        if (dispatchersValue.isnil()) {
            dispatchers = new LuaTable();
            registered.rawset("__mlua_service_dispatchers", dispatchers);
        } else if (dispatchersValue.istable()) {
            dispatchers = dispatchersValue.checktable();
        } else {
            throw new IllegalStateException("Lua service class '" + className + "' dispatcher metadata must be a table");
        }
        dispatchers.rawset(functionName, LuaValue.TRUE);
        return registered;
    }

    default LuaTable registerServiceClassFunction(String serviceName, String functionName,
                                                  LuaValue function, String description) {
        return registerServiceClassFunction(serviceName, serviceClassName(serviceName), functionName,
            function, description);
    }

    default LuaTable registerServiceClassFunction(String serviceName, String className, String functionName,
                                                  LuaValue function, String description) {
        if (serviceName == null || serviceName.isBlank()) {
            throw new IllegalArgumentException("Service name must be non-empty");
        }
        if (className == null || className.isBlank()) {
            throw new IllegalArgumentException("Service class name must be non-empty");
        }
        if (functionName == null || functionName.isBlank()) {
            throw new IllegalArgumentException("Service function name must be non-empty");
        }
        requireUserFunctionName(functionName);
        if (function == null || !function.isfunction()) {
            throw new IllegalArgumentException("Registered service function must be a Lua function");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("Service function description must be non-empty");
        }
        LuaTable serviceClass = classTable(className);
        LuaValue classDescription = serviceClass == null
            ? LuaValue.NIL : serviceClass.rawget("__mlua_doc_description");
        if (serviceClass == null || !serviceClass.rawget("__mlua_service_class").toboolean()
            || !serviceName.equals(serviceClass.rawget("__mlua_service_name").optjstring(""))
            || !classDescription.isstring() || classDescription.tojstring().isBlank()) {
            throw new IllegalStateException("Register a service class with a non-empty description before registering "
                + "functions for service '" + serviceName + "'");
        }
        LuaValue existingFunction = serviceClass.rawget(functionName);
        if (!existingFunction.isnil() && existingFunction != function) {
            throw new IllegalStateException("Lua service class '" + className + "' already defines function '"
                + functionName + "'");
        }
        LuaValue methods = serviceClass.rawget("__mlua_doc_methods");
        if (!methods.isnil() && !methods.istable()) {
            throw new IllegalStateException("Lua service class '" + className + "' method descriptions must be a table");
        }
        LuaValue existingDescription = methods.istable()
            ? methods.checktable().rawget(functionName) : LuaValue.NIL;
        if (!existingDescription.isnil() && !existingDescription.isstring()) {
            throw new IllegalStateException("Lua service class '" + className + "' function '" + functionName
                + "' has invalid description metadata");
        }
        if (existingDescription.isstring() && !description.equals(existingDescription.tojstring())) {
            throw new IllegalStateException("Lua service class '" + className + "' function '" + functionName
                + "' is already registered with a different description");
        }
        if (hasClassProperty(className, functionName)) {
            throw new IllegalStateException("Lua service class '" + className
                + "' cannot register function '" + functionName + "' because it is an inherited property");
        }
        return registerClassFunction(className, functionName, function, description);
    }

    default boolean unregisterServiceClassFunction(String serviceName, String functionName,
                                                   LuaValue expectedFunction) {
        if (serviceName == null || serviceName.isBlank() || functionName == null || functionName.isBlank()
            || expectedFunction == null) return false;
        return unregisterServiceClassFunction(serviceName, serviceClassName(serviceName), functionName,
            expectedFunction);
    }

    default boolean unregisterServiceClassFunction(String serviceName, String className, String functionName,
                                                   LuaValue expectedFunction) {
        if (serviceName == null || serviceName.isBlank() || className == null || className.isBlank()
            || functionName == null || functionName.isBlank() || expectedFunction == null) return false;
        LuaTable type = classTable(className);
        if (type == null || !type.rawget("__mlua_service_class").toboolean()
            || !serviceName.equals(type.rawget("__mlua_service_name").optjstring(""))) return false;
        return unregisterClassFunction(className, functionName, expectedFunction);
    }

    private static LuaValue serviceInstanceMethod(LuaValue receiver, String functionName) {
        if (!receiver.istable()) return LuaValue.NIL;
        LuaValue key = LuaValue.valueOf(functionName);
        LuaValue method = receiver.rawget(key);
        if (method.isfunction()) return method;
        LuaValue metaValue = receiver.getmetatable();
        if (metaValue.istable()) {
            LuaTable meta = metaValue.checktable();
            LuaValue methods = meta.rawget("__mlua_instance_methods");
            if (methods.istable()) {
                method = methods.get(key);
                if (method.isfunction()) return method;
            }
            LuaValue service = meta.rawget("__mlua_service_instance");
            if (service.istable()) {
                method = service.checktable().rawget(key);
                if (method.isfunction()) return method;
            }
            if (!service.istable()) {
                LuaValue resolver = meta.rawget("__mlua_service_resolver");
                if (resolver.isfunction()) {
                    service = resolver.invoke(receiver).arg1();
                    if (service.istable()) {
                        meta.rawset("__mlua_service_instance", service);
                        method = service.rawget(key);
                        if (method.isfunction()) return method;
                    }
                }
            }
            LuaValue fallback = meta.rawget("__mlua_instance_fallback");
            if (fallback.istable()) method = fallback.get(key);
            else if (fallback.isfunction()) method = fallback.invoke(receiver, key).arg1();
            if (method.isfunction()) return method;
        }
        return receiver.get(key);
    }

    default String serviceClassName(String serviceName) {
        if (serviceName == null || serviceName.isBlank()) {
            throw new IllegalArgumentException("Service class name must be non-empty");
        }
        String registeredName = null;
        for (String candidate : classNames()) {
            LuaTable type = classTable(candidate);
            if (type == null) continue;
            LuaValue marker = type.rawget("__mlua_service_class");
            if (marker.isnil()) continue;
            if (!marker.isboolean()) {
                throw new IllegalStateException("Lua class '" + candidate + "' has invalid service class metadata");
            }
            if (!marker.toboolean()) continue;
            LuaValue registeredServiceName = type.rawget("__mlua_service_name");
            if (!registeredServiceName.isstring()) {
                throw new IllegalStateException("Lua class '" + candidate + "' has no valid service name metadata");
            }
            if (!serviceName.equals(registeredServiceName.tojstring())) continue;
            if (registeredName != null && !registeredName.equals(candidate)) {
                throw new IllegalStateException("Service '" + serviceName + "' has multiple registered classes: '"
                    + registeredName + "' and '" + candidate + "'");
            }
            registeredName = candidate;
        }
        if (registeredName != null) return registeredName;
        LuaTable existingClass = classTable(serviceName);
        if (existingClass != null) {
            throw new IllegalStateException("Service '" + serviceName + "' conflicts with an existing Lua class; "
                + "register the service with an explicit class name");
        }
        return serviceName;
    }

    default LuaTable registerInstanceBaseClass(String name, String parentName, String description) {
        String existingBase = instanceBaseClassName();
        if (existingBase != null && !existingBase.equals(name)) {
            throw new IllegalStateException("MLua instance base class is already registered as '" + existingBase + "'");
        }
        LuaTable existing = classTable(name);
        LuaValue role = existing == null ? LuaValue.NIL : existing.rawget("__mlua_role_instance_base");
        if (!role.isnil() && (!role.isboolean() || !role.toboolean())) {
            throw new IllegalStateException("Lua class '" + name + "' has conflicting instance base metadata");
        }
        LuaTable type = registerClass(name, parentName, description);
        type.rawset("__mlua_role_instance_base", LuaValue.TRUE);
        return type;
    }

    default String instanceBaseClassName() {
        String result = null;
        for (String name : classNames()) {
            LuaTable type = classTable(name);
            if (type == null) continue;
            LuaValue role = type.rawget("__mlua_role_instance_base");
            if (role.isnil()) continue;
            if (!role.isboolean()) {
                throw new IllegalStateException("Lua class '" + name + "' has invalid instance base metadata");
            }
            if (!role.toboolean()) continue;
            if (result != null && !result.equals(name)) {
                throw new IllegalStateException("Multiple MLua instance base classes are registered: '"
                    + result + "' and '" + name + "'");
            }
            result = name;
        }
        return result;
    }

    default String requireInstanceBaseClassName() {
        String name = instanceBaseClassName();
        if (name == null) throw new IllegalStateException("Register an instance base class before registering instance classes");
        return name;
    }

    default LuaTable registerServiceBaseClass(String name, String parentName, String description) {
        String existingBase = serviceBaseClassName();
        if (existingBase != null && !existingBase.equals(name)) {
            throw new IllegalStateException("MLua service base class is already registered as '" + existingBase + "'");
        }
        LuaTable existing = classTable(name);
        LuaValue role = existing == null ? LuaValue.NIL : existing.rawget("__mlua_role_service_base");
        if (!role.isnil() && (!role.isboolean() || !role.toboolean())) {
            throw new IllegalStateException("Lua class '" + name + "' has conflicting service base metadata");
        }
        LuaTable type = registerClass(name, parentName, description);
        type.rawset("__mlua_role_service_base", LuaValue.TRUE);
        return type;
    }

    default String serviceBaseClassName() {
        String result = null;
        for (String name : classNames()) {
            LuaTable type = classTable(name);
            if (type == null) continue;
            LuaValue role = type.rawget("__mlua_role_service_base");
            if (role.isnil()) continue;
            if (!role.isboolean()) {
                throw new IllegalStateException("Lua class '" + name + "' has invalid service base metadata");
            }
            if (!role.toboolean()) continue;
            if (result != null && !result.equals(name)) {
                throw new IllegalStateException("Multiple MLua service base classes are registered: '"
                    + result + "' and '" + name + "'");
            }
            result = name;
        }
        return result;
    }

    default LuaTable registerServiceClass(String serviceName, String description) {
        return registerServiceClass(serviceName, serviceClassName(serviceName), description);
    }

    default LuaTable registerServiceClass(String serviceName, String className, String description) {
        if (serviceName == null || serviceName.isBlank()) throw new IllegalArgumentException("Service name must be non-empty");
        if (className == null || className.isBlank()) throw new IllegalArgumentException("Service class name must be non-empty");
        String serviceBaseClass = serviceBaseClassName();
        if (serviceBaseClass == null) {
            throw new IllegalStateException("Register a service base class before registering service classes");
        }
        if (className.equals(serviceBaseClass)) {
            throw new IllegalArgumentException("Service class cannot be the service base class itself");
        }
        LuaTable serviceClass = classTable(className);
        if (serviceClass != null) {
            LuaValue existingServiceClass = serviceClass.rawget("__mlua_service_class");
            LuaValue existingServiceName = serviceClass.rawget("__mlua_service_name");
            if (!existingServiceClass.isnil()
                && (!existingServiceClass.isboolean() || !existingServiceClass.toboolean())) {
                throw new IllegalStateException("Lua class '" + className + "' has conflicting service class metadata");
            }
            if (!existingServiceName.isnil()
                && (!existingServiceName.isstring() || !serviceName.equals(existingServiceName.tojstring()))) {
                throw new IllegalStateException("Lua class '" + className + "' is already assigned to another service");
            }
            if (!isClassOrParent(className, serviceBaseClass, new HashSet<>())) {
                throw new IllegalStateException("Lua class '" + className + "' must inherit from service base class '"
                    + serviceBaseClass + "'");
            }
            validateClassDescription(serviceClass, className, description);
        } else {
            serviceClass = registerClass(className, serviceBaseClass, description);
        }
        if (description != null && !description.isBlank()) {
            recordClassDescription(serviceClass, className, description);
        }
        refreshClassMetadata(className);
        serviceClass.rawset("__mlua_service_class", LuaValue.TRUE);
        serviceClass.rawset("__mlua_service_name", LuaValue.valueOf(serviceName));
        return serviceClass;
    }

    default LuaTable registerServiceClass(String serviceName, String description,
                                          java.util.Map<String, String> functionDescriptions) {
        if (functionDescriptions == null) throw new NullPointerException("functionDescriptions");
        String className = serviceClassName(serviceName);
        return registerServiceClass(serviceName, className, description, functionDescriptions);
    }

    default LuaTable registerServiceClass(String serviceName, String className, String description,
                                          java.util.Map<String, String> functionDescriptions) {
        if (serviceName == null || serviceName.isBlank()) throw new IllegalArgumentException("Service name must be non-empty");
        if (className == null || className.isBlank()) throw new IllegalArgumentException("Service class name must be non-empty");
        if (functionDescriptions == null) throw new NullPointerException("functionDescriptions");
        java.util.Map<String, String> methodsToRegister = new java.util.LinkedHashMap<>(functionDescriptions);
        LuaTable existingClass = classTable(className);
        String propertyOwnerName = existingClass == null ? serviceBaseClassName() : className;
        LuaValue methods = existingClass == null ? LuaValue.NIL : existingClass.rawget("__mlua_doc_methods");
        if (existingClass != null) {
            validateClassDescription(existingClass, className, description);
            if (!methods.isnil() && !methods.istable()) {
                throw new IllegalStateException("Lua service class '" + className + "' method descriptions must be a table");
            }
        }
        methodsToRegister.forEach((name, methodDescription) -> {
            if (name == null || name.isBlank() || methodDescription == null || methodDescription.isBlank()) {
                throw new IllegalArgumentException("Service function names and descriptions must be non-empty");
            }
            requireUserFunctionName(name);
            if (propertyOwnerName != null
                && hasClassProperty(propertyOwnerName, name)) {
                throw new IllegalStateException("Lua service class '" + className
                    + "' cannot register function '" + name + "' because it is an inherited property");
            }
            if (existingClass == null) return;
            LuaValue existingMethod = existingClass.rawget(name);
            if (!existingMethod.isnil() && !existingMethod.isfunction()) {
                throw new IllegalStateException("Lua service class '" + className
                    + "' already defines non-function member '" + name + "'");
            }
            LuaValue documented = methods.istable() ? methods.checktable().rawget(name) : LuaValue.NIL;
            if (!documented.isnil() && !documented.isstring()) {
                throw new IllegalStateException("Lua service class '" + className + "' function '" + name
                    + "' has invalid description metadata");
            }
        });
        LuaValue previousClassDescription = existingClass == null
            ? LuaValue.NIL : existingClass.rawget("__mlua_doc_description");
        LuaValue previousServiceMarker = existingClass == null
            ? LuaValue.NIL : existingClass.rawget("__mlua_service_class");
        LuaValue previousServiceName = existingClass == null
            ? LuaValue.NIL : existingClass.rawget("__mlua_service_name");
        LuaValue previousMethodTable = existingClass == null
            ? LuaValue.NIL : existingClass.rawget("__mlua_doc_methods");
        LuaValue previousDispatcherTable = existingClass == null
            ? LuaValue.NIL : existingClass.rawget("__mlua_service_dispatchers");
        java.util.Map<String, LuaValue> previousMethods = new java.util.LinkedHashMap<>();
        java.util.Map<String, Boolean> previouslyPresent = new java.util.LinkedHashMap<>();
        for (String name : methodsToRegister.keySet()) {
            LuaValue current = existingClass == null ? LuaValue.NIL : existingClass.rawget(name);
            previouslyPresent.put(name, !current.isnil());
            previousMethods.put(name, methods.istable()
                ? methods.checktable().rawget(name) : LuaValue.NIL);
        }
        java.util.List<String> registeredDispatchers = new java.util.ArrayList<>();
        try {
            LuaTable serviceClass = registerServiceClass(serviceName, className, description);
            for (java.util.Map.Entry<String, String> entry : methodsToRegister.entrySet()) {
                if (!previouslyPresent.get(entry.getKey())) registeredDispatchers.add(entry.getKey());
                registerServiceFunction(className, entry.getKey(), entry.getValue());
            }
            return serviceClass;
        } catch (RuntimeException | Error failure) {
            for (int index = registeredDispatchers.size() - 1; index >= 0; index--) {
                String name = registeredDispatchers.get(index);
                LuaTable currentClass = classTable(className);
                LuaValue function = currentClass == null ? LuaValue.NIL : currentClass.rawget(name);
                if (function.isfunction()) {
                    try {
                        unregisterClassFunction(className, name, function);
                    } catch (RuntimeException | Error rollbackFailure) {
                        failure.addSuppressed(rollbackFailure);
                    }
                }
            }
            LuaTable currentClass = classTable(className);
            if (currentClass != null) {
                if (existingClass == null) {
                    try {
                        unregisterClassIfUnused(className);
                    } catch (RuntimeException | Error rollbackFailure) {
                        failure.addSuppressed(rollbackFailure);
                    }
                } else {
                    currentClass.rawset("__mlua_doc_description", previousClassDescription);
                    currentClass.rawset("__mlua_service_class", previousServiceMarker);
                    currentClass.rawset("__mlua_service_name", previousServiceName);
                    currentClass.rawset("__mlua_service_dispatchers", previousDispatcherTable);
                    if (previousMethodTable.istable()) {
                        for (java.util.Map.Entry<String, LuaValue> entry : previousMethods.entrySet()) {
                            previousMethodTable.checktable().rawset(entry.getKey(), entry.getValue());
                        }
                    }
                    currentClass.rawset("__mlua_doc_methods", previousMethodTable);
                    refreshClassMetadata(className);
                }
            }
            throw failure;
        }
    }

    default boolean hasClassProperty(String className, String propertyName) {
        return hasClassProperty(className, propertyName, new HashSet<>());
    }

    private boolean hasClassProperty(String className, String propertyName, Set<String> visited) {
        if (className == null || !visited.add(className)) return false;
        LuaTable type = classTable(className);
        if (type == null) return false;
        LuaValue properties = type.rawget("__mlua_properties");
        if (properties.istable() && !properties.checktable().rawget(propertyName).isnil()) return true;
        LuaValue documentedProperties = type.rawget("__mlua_doc_properties");
        if (documentedProperties.istable()
            && !documentedProperties.checktable().rawget(propertyName).isnil()) return true;
        String parentName = superclassName(type);
        return parentName != null && hasClassProperty(parentName, propertyName, visited);
    }

    default LuaTable registerFunction(LuaTable owner, String name, LuaValue function, String description) {
        if (owner == null) throw new NullPointerException("owner");
        String className = owner.rawget("__type").optjstring("");
        if (!owner.rawget("__class").toboolean() || className.isBlank() || classTable(className) != owner) {
            throw new IllegalArgumentException("Function owner must be a registered Lua class");
        }
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Function name must be non-empty");
        requireUserFunctionName(name);
        if (function == null || !function.isfunction()) throw new IllegalArgumentException("Registered function must be a Lua function");
        if (description == null || description.isBlank()) throw new IllegalArgumentException("Function description must be non-empty");
        LuaValue existing = owner.rawget(name);
        if (!existing.isnil() && !existing.isfunction()) {
            throw new IllegalStateException("Lua class already defines non-function member '" + name + "'");
        }
        if (existing.isfunction() && existing != function) {
            throw new IllegalStateException("Lua class already defines function '" + name
                + "'; registration cannot replace an existing class method");
        }
        if (hasClassProperty(className, name)) {
            throw new IllegalStateException("Lua class '" + className
                + "' already defines property '" + name + "' in its inheritance chain");
        }
        LuaValue docsValue = owner.rawget("__mlua_doc_methods");
        if (!docsValue.isnil() && !docsValue.istable()) {
            throw new IllegalStateException("Lua class method descriptions must be a table");
        }
        LuaValue oldDescription = docsValue.istable()
            ? docsValue.checktable().rawget(name) : LuaValue.NIL;
        if (!oldDescription.isnil() && !oldDescription.isstring()) {
            throw new IllegalStateException("Lua class function '" + name + "' has invalid description metadata");
        }
        if (oldDescription.isstring() && !description.equals(oldDescription.tojstring())) {
            throw new IllegalStateException("Lua class function '" + name
                + "' is already registered with a different description");
        }
        owner.rawset(name, function);
        LuaTable docs = docsValue.istable() ? docsValue.checktable() : new LuaTable();
        docs.rawset(name, LuaValue.valueOf(description));
        owner.rawset("__mlua_doc_methods", docs);
        return owner;
    }

    static boolean isReservedFunctionName(String name) {
        return name == null || name.equals("__type") || name.equals("__class") || name.equals("ClassName")
            || name.equals("__mixin") || name.equals("__mixin_name") || name.startsWith("__mlua_");
    }

    private static void requireUserFunctionName(String name) {
        if (isReservedFunctionName(name)) {
            throw new IllegalArgumentException("Function name is reserved for MLua class metadata: " + name);
        }
    }

    default LuaTable attachClassInstance(LuaTable instance, String name, String parentName) {
        throw new UnsupportedOperationException("Class instance attachment is not supported by this runtime");
    }

    default LuaTable createClassInstance(String className, LuaTable initialFields) {
        LuaTable type = classTable(className);
        if (type == null) throw new IllegalStateException("Lua class is not registered: " + className);
        if (type.rawget("__mlua_service_class").toboolean()) {
            throw new IllegalArgumentException("Service class '" + className
                + "' instances are owned by Minecraft:GetService; use GetService instead of CreateInstance.");
        }
        LuaTable instance = attachClassInstance(new LuaTable(), className, null);
        return initializeClassInstance(instance, className, initialFields);
    }

    default LuaTable initializeClassInstance(LuaTable instance, String className, LuaTable initialFields) {
        java.util.Objects.requireNonNull(instance, "instance");
        LuaTable type = classTable(className);
        if (type == null) throw new IllegalStateException("Lua class is not registered: " + className);
        if (type.rawget("__mlua_service_class").toboolean()) {
            throw new IllegalArgumentException("Service class '" + className
                + "' instances are owned by Minecraft:GetService and cannot be initialized manually.");
        }
        String actualClass = classNameOf(instance);
        if (actualClass == null || !isInstanceOf(instance, className)) {
            throw new IllegalArgumentException("Live instance is not attached to class '" + className + "'");
        }
        if (instance.rawget("__mlua_instance_initialized").toboolean()) return instance;
        if (instance.rawget("__mlua_instance_initializing").toboolean()) {
            throw new IllegalStateException("Lua class instance '" + className + "' is already initializing");
        }
        if (initialFields != null) {
            LuaValue key = LuaValue.NIL;
            while (true) {
                org.luaj.vm2.Varargs next = initialFields.next(key);
                key = next.arg1();
                if (key.isnil()) break;
                if (key.isstring()) {
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
        }
        instance.rawset("__mlua_instance_initializing", LuaValue.TRUE);
        try {
            if (initialFields != null) {
                LuaValue key = LuaValue.NIL;
                while (true) {
                    org.luaj.vm2.Varargs next = initialFields.next(key);
                    key = next.arg1();
                    if (key.isnil()) break;
                    instance.set(key, next.arg(2));
                }
            }
            LuaTable fields = initialFields == null ? new LuaTable() : initialFields;
            initializeClassInitializers(instance, className, fields);
            instance.rawset("__mlua_instance_initialized", LuaValue.TRUE);
            return instance;
        } finally {
            instance.rawset("__mlua_instance_initializing", LuaValue.NIL);
        }
    }

    default void initializeClassInitializers(LuaTable instance, String className, LuaTable fields) {
        List<LuaValue> initializers = new ArrayList<>();
        collectClassInitializers(this, className, new HashSet<>(), initializers);
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

    default LuaTable createClassInstance(String className) {
        return createClassInstance(className, null);
    }
}
