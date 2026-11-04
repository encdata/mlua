package dev.mlua.module;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/** Tracks class and registry changes made by one module registration transaction. */
final class MLuaTrackedRegistration implements MLuaRegistration {
    private final MLuaModule module;
    private final MLuaRegistration registration;
    private final List<String> added;
    private final Map<MLuaModuleRegistrations.SnapshotKey, MLuaModuleClassSnapshot> snapshots;
    private final List<String> addedMixins;
    private final List<MLuaModuleRegistrations.MixinApplication> appliedMixins;
    private final List<Runnable> rollbackActions;
    private final Map<String, Runnable> documentationCleanups = new LinkedHashMap<>();
    private final Map<String, Runnable> contentTypeCleanups = new LinkedHashMap<>();
    private final Map<String, LuaValue> ownedClassFunctions = new LinkedHashMap<>();
    private final Map<String, LuaValue> ownedClassProperties = new LinkedHashMap<>();
    private final Map<String, LuaValue> ownedMixinFunctions = new LinkedHashMap<>();
    private final Set<String> ownedMixinApplications = new HashSet<>();

    MLuaTrackedRegistration(MLuaModule module, MLuaRegistration registration, List<String> added,
                           Map<MLuaModuleRegistrations.SnapshotKey, MLuaModuleClassSnapshot> snapshots,
                           List<String> addedMixins,
                           List<MLuaModuleRegistrations.MixinApplication> appliedMixins,
                           List<Runnable> rollbackActions) {
        this.module = module;
        this.registration = registration;
        this.added = added;
        this.snapshots = snapshots;
        this.addedMixins = addedMixins;
        this.appliedMixins = appliedMixins;
        this.rollbackActions = rollbackActions;
    }

    @Override
    public void registerDocumentation(MLuaDocBuilder.Contribution contribution) {
        registration.registerDocumentation(contribution);
        String key = contribution.id() + "\u0000" + contribution.owner();
        Runnable cleanup = () -> registration.unregisterDocumentation(contribution.id(), contribution.owner());
        documentationCleanups.put(key, cleanup);
        rollbackActions.add(cleanup);
    }

    @Override
    public boolean unregisterDocumentation(String id, String owner) {
        boolean removed = registration.unregisterDocumentation(id, owner);
        if (removed) MLuaModuleRegistrations.removeRollbackAction(rollbackActions,
            documentationCleanups.remove(id + "\u0000" + owner));
        return removed;
    }

    @Override
    public void registerContentType(String domain, String owner, String name, String base,
                                    String description, LuaTable defaults) {
        registration.registerContentType(domain, owner, name, base, description, defaults);
        String key = domain + "\u0000" + owner + "\u0000" + name;
        Runnable cleanup = () -> registration.unregisterContentType(domain, owner, name);
        contentTypeCleanups.put(key, cleanup);
        rollbackActions.add(cleanup);
    }

    @Override
    public LuaTable resolveContentType(String domain, String owner, LuaTable properties) {
        return registration.resolveContentType(domain, owner, properties);
    }

    @Override
    public Map<String, String> contentTypes(String domain, String owner) {
        return registration.contentTypes(domain, owner);
    }

    @Override
    public boolean unregisterContentType(String domain, String owner, String name) {
        boolean removed = registration.unregisterContentType(domain, owner, name);
        if (removed) MLuaModuleRegistrations.removeRollbackAction(rollbackActions,
            contentTypeCleanups.remove(domain + "\u0000" + owner + "\u0000" + name));
        return removed;
    }

    @Override
    public void onRegistrationRollback(Runnable cleanup) {
        rollbackActions.add(Objects.requireNonNull(cleanup, "cleanup"));
    }

    @Override
    @Deprecated(forRemoval = false)
    public LuaTable registerClass(String name, String parentName) {
        boolean isNew = registration.classTable(name) == null;
        if (!isNew) MLuaModuleRegistrations.snapshotClass(registration, name, snapshots);
        LuaTable table = registration.registerClass(name, parentName);
        if (isNew) added.add(name);
        return table;
    }

    @Override
    public LuaTable registerClass(String name, String parentName, String description) {
        boolean isNew = registration.classTable(name) == null;
        if (!isNew) MLuaModuleRegistrations.snapshotClass(registration, name, snapshots);
        LuaTable table = registration.registerClass(name, parentName, description);
        if (isNew) added.add(name);
        return table;
    }

    @Override
    public LuaTable registerClassTable(String name, LuaTable classTable, String parentName) {
        boolean isNew = registration.classTable(name) == null;
        if (!isNew) MLuaModuleRegistrations.snapshotClass(registration, name, snapshots);
        LuaTable table = registration.registerClassTable(name, classTable, parentName);
        if (isNew) added.add(name);
        return table;
    }

    @Override
    public LuaTable registerClassTable(String name, LuaTable classTable, String parentName,
                                       String description) {
        boolean isNew = registration.classTable(name) == null;
        if (!isNew) MLuaModuleRegistrations.snapshotClass(registration, name, snapshots);
        LuaTable table = registration.registerClassTable(name, classTable, parentName, description);
        if (isNew) added.add(name);
        return table;
    }

    @Override
    public LuaTable registerServiceBaseClass(String name, String parentName, String description) {
        boolean isNew = registration.classTable(name) == null;
        if (!isNew) MLuaModuleRegistrations.snapshotClass(registration, name, snapshots);
        LuaTable type = registration.registerServiceBaseClass(name, parentName, description);
        if (isNew) added.add(name);
        return type;
    }

    @Override
    public LuaTable registerInstanceBaseClass(String name, String parentName, String description) {
        boolean isNew = registration.classTable(name) == null;
        if (!isNew) MLuaModuleRegistrations.snapshotClass(registration, name, snapshots);
        LuaTable type = registration.registerInstanceBaseClass(name, parentName, description);
        if (isNew) added.add(name);
        return type;
    }

    @Override
    public LuaTable classTable(String name) {
        return registration.classTable(name);
    }

    @Override
    public void refreshClassMetadata(String className) {
        registration.refreshClassMetadata(className);
    }

    @Override
    public LuaTable registerBehaviorMixin(String name, String description) {
        boolean isNew = registration.behaviorMixinTable(name) == null;
        LuaTable mixin = registration.registerBehaviorMixin(name, description);
        if (isNew) addedMixins.add(name);
        return mixin;
    }

    @Override
    public LuaTable behaviorMixinTable(String name) {
        return registration.behaviorMixinTable(name);
    }

    @Override
    public java.util.Set<String> behaviorMixinNames() {
        return registration.behaviorMixinNames();
    }

    @Override
    public LuaTable registerBehaviorMixinFunction(String mixinName, String functionName,
                                                  LuaValue function, String description) {
        MLuaModuleRegistrations.snapshotTable(snapshots, MLuaModuleRegistrations.SnapshotKey.mixin(mixinName),
            registration.behaviorMixinTable(mixinName));
        LuaTable mixin = registration.behaviorMixinTable(mixinName);
        boolean isNew = mixin == null || mixin.rawget(functionName).isnil();
        LuaTable result = registration.registerBehaviorMixinFunction(mixinName, functionName, function, description);
        if (isNew) ownedMixinFunctions.put(mixinName + "\u0000" + functionName, function);
        return result;
    }

    @Override
    public boolean unregisterBehaviorMixinFunction(String mixinName, String functionName,
                                                  LuaValue expectedFunction) {
        String key = mixinName + "\u0000" + functionName;
        if (ownedMixinFunctions.get(key) != expectedFunction) {
            throw new IllegalStateException("Module '" + module.id() + "' cannot unregister behavior mixin function '"
                + mixinName + "." + functionName + "' owned by another registration");
        }
        MLuaModuleRegistrations.snapshotTable(snapshots, MLuaModuleRegistrations.SnapshotKey.mixin(mixinName),
            registration.behaviorMixinTable(mixinName));
        boolean removed = registration.unregisterBehaviorMixinFunction(mixinName, functionName, expectedFunction);
        if (removed) ownedMixinFunctions.remove(key);
        return removed;
    }

    @Override
    public boolean unregisterBehaviorMixin(String mixinName) {
        if (!addedMixins.contains(mixinName)) {
            throw new IllegalStateException("Module '" + module.id()
                + "' cannot unregister behavior mixin '" + mixinName + "' owned by another registration");
        }
        boolean removed = registration.unregisterBehaviorMixin(mixinName);
        if (removed) addedMixins.remove(mixinName);
        return removed;
    }

    @Override
    public void applyBehaviorMixin(String className, String mixinName) {
        if (registration.hasBehaviorMixin(className, mixinName)) return;
        MLuaModuleRegistrations.snapshotClass(registration, className, snapshots);
        registration.applyBehaviorMixin(className, mixinName);
        ownedMixinApplications.add(className + "\u0000" + mixinName);
        appliedMixins.add(new MLuaModuleRegistrations.MixinApplication(className, mixinName, true));
    }

    @Override
    public boolean removeBehaviorMixin(String className, String mixinName) {
        String key = className + "\u0000" + mixinName;
        if (!ownedMixinApplications.contains(key)) {
            throw new IllegalStateException("Module '" + module.id() + "' cannot remove behavior mixin '"
                + mixinName + "' from class '" + className + "' when another registration applied it");
        }
        boolean removed = registration.removeBehaviorMixin(className, mixinName);
        if (removed) {
            ownedMixinApplications.remove(key);
            appliedMixins.add(new MLuaModuleRegistrations.MixinApplication(className, mixinName, false));
        }
        return removed;
    }

    @Override
    public java.util.Set<String> classNames() {
        return registration.classNames();
    }

    @Override
    public boolean isInstanceOf(LuaValue instance, String className) {
        return registration.isInstanceOf(instance, className);
    }

    @Override
    public String classNameOf(LuaValue instance) {
        return registration.classNameOf(instance);
    }

    @Override
    public LuaTable registerFunction(LuaTable owner, String name, LuaValue function, String description) {
        LuaValue type = owner.rawget("__type");
        if (type.isstring()) MLuaModuleRegistrations.snapshotClass(registration, type.tojstring(), snapshots);
        boolean isNew = owner.rawget(name).isnil();
        LuaTable result = registration.registerFunction(owner, name, function, description);
        if (isNew && type.isstring()) ownedClassFunctions.put(type.tojstring() + "\u0000" + name, function);
        return result;
    }

    @Override
    public boolean unregisterClassFunction(String className, String functionName,
                                           LuaValue expectedFunction) {
        String key = className + "\u0000" + functionName;
        if (ownedClassFunctions.get(key) != expectedFunction) {
            throw new IllegalStateException("Module '" + module.id() + "' cannot unregister class function '"
                + className + "." + functionName + "' owned by another registration");
        }
        MLuaModuleRegistrations.snapshotClass(registration, className, snapshots);
        boolean removed = registration.unregisterClassFunction(className, functionName, expectedFunction);
        if (removed) ownedClassFunctions.remove(key);
        return removed;
    }

    @Override
    public LuaTable registerClassProperty(String className, String propertyName,
                                          LuaValue getter, LuaValue setter, String description) {
        MLuaModuleRegistrations.snapshotClass(registration, className, snapshots);
        LuaTable owner = registration.classTable(className);
        LuaValue properties = owner == null ? LuaValue.NIL : owner.rawget("__mlua_properties");
        boolean isNew = !properties.istable() || properties.checktable().rawget(propertyName).isnil();
        LuaTable result = registration.registerClassProperty(className, propertyName, getter, setter, description);
        if (isNew) ownedClassProperties.put(className + "\u0000" + propertyName, getter);
        return result;
    }

    @Override
    public LuaTable registerClassProperty(String className, String propertyName,
                                          LuaValue getter, LuaValue setter, String valueType,
                                          String side, String description) {
        MLuaModuleRegistrations.snapshotClass(registration, className, snapshots);
        LuaTable owner = registration.classTable(className);
        LuaValue properties = owner == null ? LuaValue.NIL : owner.rawget("__mlua_properties");
        boolean isNew = !properties.istable() || properties.checktable().rawget(propertyName).isnil();
        LuaTable result = registration.registerClassProperty(className, propertyName, getter, setter,
            valueType, side, description);
        if (isNew) ownedClassProperties.put(className + "\u0000" + propertyName, getter);
        return result;
    }

    @Override
    public boolean unregisterClassProperty(String className, String propertyName, LuaValue expectedGetter) {
        String key = className + "\u0000" + propertyName;
        if (ownedClassProperties.get(key) != expectedGetter) {
            throw new IllegalStateException("Module '" + module.id() + "' cannot unregister class property '"
                + className + "." + propertyName + "' owned by another registration");
        }
        MLuaModuleRegistrations.snapshotClass(registration, className, snapshots);
        boolean removed = registration.unregisterClassProperty(className, propertyName, expectedGetter);
        if (removed) ownedClassProperties.remove(key);
        return removed;
    }

    @Override
    public LuaTable registerServiceClass(String serviceName, String description) {
        return this.registerServiceClass(serviceName, registration.serviceClassName(serviceName), description);
    }

    @Override
    public LuaTable registerServiceClass(String serviceName, String className, String description) {
        if (registration.classTable(className) != null) MLuaModuleRegistrations.snapshotClass(registration, className, snapshots);
        return MLuaRegistration.super.registerServiceClass(serviceName, className, description);
    }

    @Override
    public LuaTable attachClassInstance(LuaTable instance, String name, String parentName) {
        return registration.attachClassInstance(instance, name, parentName);
    }

    @Override
    public boolean unregisterClassIfUnused(String name) {
        if (!added.contains(name)) {
            throw new IllegalStateException("Module '" + module.id()
                + "' cannot unregister Lua class '" + name + "' owned by another registration");
        }
        boolean removed = registration.unregisterClassIfUnused(name);
        if (removed) added.remove(name);
        return removed;
    }

    @Override
    public boolean unregisterClass(String name) {
        if (!added.contains(name)) {
            throw new IllegalStateException("Module '" + module.id()
                + "' cannot unregister Lua class '" + name + "' owned by another registration");
        }
        boolean removed = registration.unregisterClass(name);
        if (removed) added.remove(name);
        return removed;
    }
}
