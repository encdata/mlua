package dev.mlua.module;

import dev.mlua.core.MLuaLogger;
import dev.mlua.core.service.MLuaServiceRegistry;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

final class MLuaModuleRegistrations {
    private final Map<MLuaRegistration, Map<MLuaModule, List<String>>> classes = new WeakHashMap<>();
    private final Map<MLuaRegistration, Map<MLuaModule, Map<SnapshotKey, ClassSnapshot>>> classSnapshots = new WeakHashMap<>();
    private final Map<MLuaRegistration, Map<MLuaModule, List<String>>> addedMixins = new WeakHashMap<>();
    private final Map<MLuaRegistration, Map<MLuaModule, List<MixinApplication>>> appliedMixins = new WeakHashMap<>();
    private final Map<MLuaRegistration, Map<MLuaModule, List<Runnable>>> rollbackActions = new WeakHashMap<>();
    private final Map<MLuaServiceRegistry, Map<MLuaModule, List<MLuaServiceRegistry.Registration>>> services = new WeakHashMap<>();
    private final Map<MLuaModule, List<MLuaModule.ServiceEntry>> serviceEntries = new IdentityHashMap<>();

    boolean registerClasses(MLuaModule module, MLuaRegistration registration) {
        Map<MLuaModule, List<String>> byModule = this.classes.computeIfAbsent(registration, ignored -> new IdentityHashMap<>());
        if (byModule.containsKey(module)) return true;

        List<String> added = new ArrayList<>();
        List<String> newMixins = new ArrayList<>();
        List<MixinApplication> newMixinApplications = new ArrayList<>();
        List<Runnable> moduleRollbackActions = new ArrayList<>();
        Map<SnapshotKey, ClassSnapshot> snapshots = new LinkedHashMap<>();
        MLuaRegistration tracked = trackedRegistration(module, registration, added, snapshots, newMixins, newMixinApplications, moduleRollbackActions);

        try {
            module.registerClasses(tracked);
            for (Map.Entry<String, String> entry : declaredDescriptions(module, MLuaDocBuilder.DocEntryType.CLASS).entrySet()) {
                LuaTable classTable = registration.classTable(entry.getKey());
                if (classTable == null) continue;
                LuaValue existing = classTable.rawget("__mlua_doc_description");
                if (!existing.isnil() && !existing.isstring()) {
                    throw new IllegalStateException("Lua class '" + entry.getKey() + "' has invalid description metadata");
                }
                // Runtime class registration is the source of truth. Module docs may add
                // prose for types that were declared elsewhere, but must not prevent a
                // valid module from loading when older prose has drifted.
                if (existing.isnil()) {
                    snapshotClass(registration, entry.getKey(), snapshots);
                    classTable.rawset("__mlua_doc_description", LuaValue.valueOf(entry.getValue()));
                }
            }
            declaredMethods(module).forEach((receiver, methods) -> {
                LuaTable classTable = registration.classTable(receiver);
                if (classTable == null) return;
                snapshotClass(registration, receiver, snapshots);
                LuaValue documentedValue = classTable.rawget("__mlua_doc_methods");
                if (!documentedValue.isnil() && !documentedValue.istable()) {
                    throw new IllegalStateException("Lua class '" + receiver + "' method descriptions must be a table");
                }
                LuaTable documented = documentedValue.istable() ? documentedValue.checktable() : new LuaTable();
                methods.forEach((name, description) -> {
                    LuaValue existing = documented.rawget(name);
                    if (!existing.isnil() && !existing.isstring()) {
                        throw new IllegalStateException("Lua class '" + receiver + "' function '" + name
                            + "' has invalid description metadata");
                    }
                    if (existing.isnil()) {
                        documented.set(name, LuaValue.valueOf(description));
                    }
                });
                classTable.rawset("__mlua_doc_methods", documented);
                registration.refreshClassMetadata(receiver);
            });
            requireClassDescriptions(registration, added, module.id());
            byModule.put(module, List.copyOf(added));
            rememberSnapshots(registration, module, snapshots);
            rememberMixinChanges(registration, module, newMixins, newMixinApplications);
            this.rollbackActions.computeIfAbsent(registration, ignored -> new IdentityHashMap<>())
                .put(module, moduleRollbackActions);
            return true;
        } catch (Throwable error) {
            runRollbackActions(moduleRollbackActions, 0, error);
            restoreClassSnapshots(registration, snapshots);
            rollbackMixinChanges(registration, newMixins, newMixinApplications);
            unregisterAddedClasses(registration, added);
            if (byModule.isEmpty()) this.classes.remove(registration);
            MLuaLogger.LOGGER.error("MLua module '{}' failed during classes and was isolated", module.id(), error);
            return false;
        }
    }

    boolean registerServiceClasses(MLuaModule module, MLuaRegistration registration) {
        Map<MLuaModule, List<String>> byModule = this.classes.get(registration);
        List<String> existing = byModule != null ? byModule.get(module) : null;
        if (existing == null) return false;
        List<String> added = new ArrayList<>();
        List<String> newMixins = new ArrayList<>();
        List<MixinApplication> newMixinApplications = new ArrayList<>();
        List<Runnable> moduleRollbackActions = this.rollbackActions
            .getOrDefault(registration, Map.of()).getOrDefault(module, new ArrayList<>());
        int rollbackActionStart = moduleRollbackActions.size();
        Map<SnapshotKey, ClassSnapshot> snapshots = new LinkedHashMap<>();
        MLuaRegistration tracked = trackedRegistration(module, registration, added, snapshots, newMixins, newMixinApplications, moduleRollbackActions);

        try {
            Map<String, String> descriptions = declaredDescriptions(module, MLuaDocBuilder.DocEntryType.SERVICE);
            Map<String, Map<String, String>> methodDescriptions = declaredMethods(module);
            for (MLuaModule.ServiceEntry service : serviceEntries(module)) {
                String description = mergeServiceDescription(service.name(), descriptions.get(service.name()), service.description());
                Map<String, String> methods = mergeFunctionDescriptions(service.name(),
                    methodDescriptions.getOrDefault(service.name(), Map.of()), service.functionDescriptions());
                if (service.className() == null) {
                    tracked.registerServiceClass(service.name(), description, methods);
                } else {
                    tracked.registerServiceClass(service.name(), service.className(), description, methods);
                }
            }
            requireClassDescriptions(registration, added, module.id());
            List<String> combined = new ArrayList<>(existing);
            combined.addAll(added);
            byModule.put(module, List.copyOf(combined));
            rememberSnapshots(registration, module, snapshots);
            rememberMixinChanges(registration, module, newMixins, newMixinApplications);
            this.rollbackActions.computeIfAbsent(registration, ignored -> new IdentityHashMap<>())
                .put(module, moduleRollbackActions);
            return true;
        } catch (Throwable error) {
            runRollbackActions(moduleRollbackActions, rollbackActionStart, error);
            restoreClassSnapshots(registration, snapshots);
            rollbackMixinChanges(registration, newMixins, newMixinApplications);
            unregisterAddedClasses(registration, added);
            MLuaLogger.LOGGER.error("MLua module '{}' failed while registering service classes and was isolated", module.id(), error);
            return false;
        }
    }

    void rollbackClasses(MLuaModule module, MLuaRegistration registration) {
        if (registration != null) {
            this.rollbackClassesFrom(module, registration);
            return;
        }
        for (MLuaRegistration target : List.copyOf(this.classes.keySet())) {
            this.rollbackClassesFrom(module, target);
        }
    }

    private void rollbackClassesFrom(MLuaModule module, MLuaRegistration registration) {
        Map<MLuaModule, List<Runnable>> actionsByModule = this.rollbackActions.get(registration);
        List<Runnable> actions = actionsByModule == null ? null : actionsByModule.remove(module);
        if (actions != null) runRollbackActions(actions, 0, null);
        if (actionsByModule != null && actionsByModule.isEmpty()) this.rollbackActions.remove(registration);
        Map<MLuaModule, List<String>> byModule = this.classes.get(registration);
        if (byModule == null) return;
        List<String> added = byModule.remove(module);
        Map<MLuaModule, Map<SnapshotKey, ClassSnapshot>> snapshotsByModule = this.classSnapshots.get(registration);
        Map<SnapshotKey, ClassSnapshot> snapshots = snapshotsByModule != null ? snapshotsByModule.remove(module) : null;
        if (snapshots != null) {
            rebaseOtherSnapshots(snapshotsByModule, module, snapshots);
            restoreClassSnapshots(registration, snapshots);
        }
        Map<MLuaModule, List<String>> mixinsByModule = this.addedMixins.get(registration);
        List<String> moduleMixins = mixinsByModule != null ? mixinsByModule.remove(module) : null;
        Map<MLuaModule, List<MixinApplication>> applicationsByModule = this.appliedMixins.get(registration);
        List<MixinApplication> moduleApplications = applicationsByModule != null ? applicationsByModule.remove(module) : null;
        rollbackMixinChanges(registration, moduleMixins == null ? List.of() : moduleMixins,
            moduleApplications == null ? List.of() : moduleApplications);
        if (mixinsByModule != null && mixinsByModule.isEmpty()) this.addedMixins.remove(registration);
        if (applicationsByModule != null && applicationsByModule.isEmpty()) this.appliedMixins.remove(registration);
        if (snapshotsByModule != null && snapshotsByModule.isEmpty()) this.classSnapshots.remove(registration);
        if (byModule.isEmpty()) this.classes.remove(registration);
        if (added != null) unregisterAddedClasses(registration, added);
    }

    private static void rebaseOtherSnapshots(Map<MLuaModule, Map<SnapshotKey, ClassSnapshot>> byModule,
                                             MLuaModule removedModule,
                                             Map<SnapshotKey, ClassSnapshot> removedSnapshots) {
        if (byModule == null) return;
        byModule.forEach((module, snapshots) -> {
            if (module == removedModule) return;
            removedSnapshots.forEach((name, removed) -> {
                ClassSnapshot remaining = snapshots.get(name);
                if (remaining != null) remaining.rebaseOriginals(removed);
            });
        });
    }

    private static MLuaRegistration trackedRegistration(MLuaModule module, MLuaRegistration registration, List<String> added,
                                                        Map<SnapshotKey, ClassSnapshot> snapshots,
                                                        List<String> addedMixins, List<MixinApplication> appliedMixins,
                                                        List<Runnable> rollbackActions) {
        Map<String, Runnable> documentationCleanups = new LinkedHashMap<>();
        Map<String, Runnable> contentTypeCleanups = new LinkedHashMap<>();
        Map<String, LuaValue> ownedClassFunctions = new LinkedHashMap<>();
        Map<String, LuaValue> ownedClassProperties = new LinkedHashMap<>();
        Map<String, LuaValue> ownedMixinFunctions = new LinkedHashMap<>();
        Set<String> ownedMixinApplications = new HashSet<>();
        return new MLuaRegistration() {
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
                if (removed) removeRollbackAction(rollbackActions, documentationCleanups.remove(id + "\u0000" + owner));
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
                if (removed) removeRollbackAction(rollbackActions,
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
                if (!isNew) snapshotClass(registration, name, snapshots);
                LuaTable table = registration.registerClass(name, parentName);
                if (isNew) added.add(name);
                return table;
            }

            @Override
            public LuaTable registerClass(String name, String parentName, String description) {
                boolean isNew = registration.classTable(name) == null;
                if (!isNew) snapshotClass(registration, name, snapshots);
                LuaTable table = registration.registerClass(name, parentName, description);
                if (isNew) added.add(name);
                return table;
            }

            @Override
            public LuaTable registerClassTable(String name, LuaTable classTable, String parentName) {
                boolean isNew = registration.classTable(name) == null;
                if (!isNew) snapshotClass(registration, name, snapshots);
                LuaTable table = registration.registerClassTable(name, classTable, parentName);
                if (isNew) added.add(name);
                return table;
            }

            @Override
            public LuaTable registerClassTable(String name, LuaTable classTable, String parentName,
                                               String description) {
                boolean isNew = registration.classTable(name) == null;
                if (!isNew) snapshotClass(registration, name, snapshots);
                LuaTable table = registration.registerClassTable(name, classTable, parentName, description);
                if (isNew) added.add(name);
                return table;
            }

            @Override
            public LuaTable registerServiceBaseClass(String name, String parentName, String description) {
                boolean isNew = registration.classTable(name) == null;
                if (!isNew) snapshotClass(registration, name, snapshots);
                LuaTable type = registration.registerServiceBaseClass(name, parentName, description);
                if (isNew) added.add(name);
                return type;
            }

            @Override
            public LuaTable registerInstanceBaseClass(String name, String parentName, String description) {
                boolean isNew = registration.classTable(name) == null;
                if (!isNew) snapshotClass(registration, name, snapshots);
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
                snapshotTable(snapshots, SnapshotKey.mixin(mixinName), registration.behaviorMixinTable(mixinName));
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
                snapshotTable(snapshots, SnapshotKey.mixin(mixinName), registration.behaviorMixinTable(mixinName));
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
                snapshotClass(registration, className, snapshots);
                registration.applyBehaviorMixin(className, mixinName);
                ownedMixinApplications.add(className + "\u0000" + mixinName);
                appliedMixins.add(new MixinApplication(className, mixinName, true));
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
                    appliedMixins.add(new MixinApplication(className, mixinName, false));
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
                if (type.isstring()) snapshotClass(registration, type.tojstring(), snapshots);
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
                snapshotClass(registration, className, snapshots);
                boolean removed = registration.unregisterClassFunction(className, functionName, expectedFunction);
                if (removed) ownedClassFunctions.remove(key);
                return removed;
            }

            @Override
            public LuaTable registerClassProperty(String className, String propertyName,
                                                  LuaValue getter, LuaValue setter, String description) {
                snapshotClass(registration, className, snapshots);
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
                snapshotClass(registration, className, snapshots);
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
                snapshotClass(registration, className, snapshots);
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
                if (registration.classTable(className) != null) snapshotClass(registration, className, snapshots);
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
        };
    }

    private static void runRollbackActions(List<Runnable> actions, int start, Throwable primaryFailure) {
        for (int index = actions.size() - 1; index >= start; index--) {
            try {
                actions.get(index).run();
            } catch (Throwable cleanupFailure) {
                if (primaryFailure != null) primaryFailure.addSuppressed(cleanupFailure);
                else MLuaLogger.LOGGER.error("MLua module registration cleanup failed", cleanupFailure);
            }
        }
        actions.subList(start, actions.size()).clear();
    }

    private static void removeRollbackAction(List<Runnable> actions, Runnable action) {
        if (action != null) actions.removeIf(candidate -> candidate == action);
    }

    private void rememberSnapshots(MLuaRegistration registration, MLuaModule module,
                                  Map<SnapshotKey, ClassSnapshot> snapshots) {
        if (snapshots.isEmpty()) return;
        snapshots.values().forEach(ClassSnapshot::captureExpected);
        Map<SnapshotKey, ClassSnapshot> stored = this.classSnapshots
            .computeIfAbsent(registration, ignored -> new IdentityHashMap<>())
            .computeIfAbsent(module, ignored -> new LinkedHashMap<>());
        snapshots.forEach((name, snapshot) -> {
            ClassSnapshot existing = stored.get(name);
            if (existing == null) stored.put(name, snapshot);
            else existing.mergeExpected(snapshot);
        });
    }

    private void rememberMixinChanges(MLuaRegistration registration, MLuaModule module,
                                      List<String> mixins, List<MixinApplication> applications) {
        if (!mixins.isEmpty()) {
            List<String> stored = this.addedMixins
                .computeIfAbsent(registration, ignored -> new IdentityHashMap<>())
                .computeIfAbsent(module, ignored -> new ArrayList<>());
            mixins.forEach(value -> { if (!stored.contains(value)) stored.add(value); });
        }
        if (!applications.isEmpty()) {
            List<MixinApplication> stored = this.appliedMixins
                .computeIfAbsent(registration, ignored -> new IdentityHashMap<>())
                .computeIfAbsent(module, ignored -> new ArrayList<>());
            stored.addAll(applications);
        }
    }

    private static void rollbackMixinChanges(MLuaRegistration registration, List<String> mixins,
                                             List<MixinApplication> applications) {
        for (int index = applications.size() - 1; index >= 0; index--) {
            MixinApplication application = applications.get(index);
            if (application.applied()) {
                registration.removeBehaviorMixin(application.className(), application.mixinName());
            } else {
                registration.applyBehaviorMixin(application.className(), application.mixinName());
            }
        }
        for (int index = mixins.size() - 1; index >= 0; index--) registration.unregisterBehaviorMixin(mixins.get(index));
    }

    private record MixinApplication(String className, String mixinName, boolean applied) { }

    private static void snapshotClass(MLuaRegistration registration, String name,
                                      Map<SnapshotKey, ClassSnapshot> snapshots) {
        SnapshotKey key = SnapshotKey.classType(name);
        if (snapshots.containsKey(key)) return;
        snapshotTable(snapshots, key, registration.classTable(name));
    }

    private static void snapshotTable(Map<SnapshotKey, ClassSnapshot> snapshots, SnapshotKey key, LuaTable table) {
        if (!snapshots.containsKey(key) && table != null) snapshots.put(key, ClassSnapshot.capture(table));
    }

    private static void restoreSnapshots(Map<SnapshotKey, ClassSnapshot> snapshots) {
        snapshots.values().forEach(ClassSnapshot::restore);
    }

    private static void restoreClassSnapshots(MLuaRegistration registration,
                                              Map<SnapshotKey, ClassSnapshot> snapshots) {
        restoreSnapshots(snapshots);
        boolean mixinRestored = snapshots.keySet().stream().anyMatch(SnapshotKey::mixin);
        for (SnapshotKey key : snapshots.keySet()) {
            if (!key.mixin()) registration.refreshClassMetadata(key.name());
        }
        if (mixinRestored) registration.classNames().forEach(registration::refreshClassMetadata);
    }

    private record SnapshotKey(String name, boolean mixin) {
        private static SnapshotKey classType(String name) { return new SnapshotKey(name, false); }
        private static SnapshotKey mixin(String name) { return new SnapshotKey(name, true); }
    }

    private static void unregisterAddedClasses(MLuaRegistration registration, List<String> added) {
        for (int index = added.size() - 1; index >= 0; index--) {
            registration.unregisterClassIfUnused(added.get(index));
        }
    }

    private static void requireClassDescriptions(MLuaRegistration registration, List<String> classNames,
                                                String moduleId) {
        for (String name : classNames) {
            LuaTable type = registration.classTable(name);
            LuaValue description = type == null ? LuaValue.NIL : type.rawget("__mlua_doc_description");
            if (!description.isstring() || description.tojstring().isBlank()) {
                throw new IllegalStateException("MLua module '" + moduleId + "' registered class '" + name
                    + "' without a description. Add one in registerClass or contribute it with docs.clazz.");
            }
        }
    }

    private static final class ClassSnapshot {
        private final LuaTable table;
        private final Map<LuaValue, LuaValue> originalFields;
        private Map<LuaValue, LuaValue> originalMethods;
        private Map<LuaValue, LuaValue> originalMixinMethods;
        private Map<LuaValue, LuaValue> expectedFields;
        private Map<LuaValue, LuaValue> expectedMethods;
        private Map<LuaValue, LuaValue> expectedMixinMethods;

        private ClassSnapshot(LuaTable table, Map<LuaValue, LuaValue> fields,
                              Map<LuaValue, LuaValue> methods, Map<LuaValue, LuaValue> mixinMethods) {
            this.table = table;
            this.originalFields = fields;
            this.originalMethods = methods;
            this.originalMixinMethods = mixinMethods;
        }

        static ClassSnapshot capture(LuaTable table) {
            Map<LuaValue, LuaValue> fields = captureFields(table);
            LuaValue methods = table.rawget("__mlua_doc_methods");
            return new ClassSnapshot(table, fields,
                methods.istable() ? captureFields(methods.checktable()) : null,
                captureMixinMethods(table));
        }

        private static Map<LuaValue, LuaValue> captureMixinMethods(LuaTable table) {
            LuaValue meta = table.getmetatable();
            LuaValue methods = meta.istable() ? meta.checktable().rawget("__mlua_mixin_methods") : LuaValue.NIL;
            return methods.istable() ? captureFields(methods.checktable()) : null;
        }

        private static Map<LuaValue, LuaValue> captureFields(LuaTable table) {
            Map<LuaValue, LuaValue> fields = new LinkedHashMap<>();
            LuaValue key = LuaValue.NIL;
            while (true) {
                org.luaj.vm2.Varargs next = table.next(key);
                key = next.arg1();
                if (key.isnil()) return fields;
                fields.put(key, next.arg(2));
            }
        }

        void captureExpected() {
            this.expectedFields = captureFields(this.table);
            LuaValue methods = this.table.rawget("__mlua_doc_methods");
            this.expectedMethods = methods.istable() ? captureFields(methods.checktable()) : null;
            this.expectedMixinMethods = captureMixinMethods(this.table);
        }

        void mergeExpected(ClassSnapshot latest) {
            this.expectedFields = latest.expectedFields;
            this.expectedMethods = latest.expectedMethods;
            this.expectedMixinMethods = latest.expectedMixinMethods;
        }

        void rebaseOriginals(ClassSnapshot removed) {
            if (removed.expectedFields == null) return;
            java.util.Set<LuaValue> keys = new java.util.LinkedHashSet<>(removed.originalFields.keySet());
            keys.addAll(removed.expectedFields.keySet());
            for (LuaValue key : keys) {
                LuaValue before = removed.originalFields.getOrDefault(key, LuaValue.NIL);
                LuaValue after = removed.expectedFields.getOrDefault(key, LuaValue.NIL);
                LuaValue thisBefore = this.originalFields.getOrDefault(key, LuaValue.NIL);
                if (!sameValue(before, after) && sameValue(thisBefore, after)) {
                    this.originalFields.put(key, before);
                }
            }

            Map<LuaValue, LuaValue> removedBefore = removed.originalMethods == null
                ? Map.of() : removed.originalMethods;
            Map<LuaValue, LuaValue> removedAfter = removed.expectedMethods == null
                ? Map.of() : removed.expectedMethods;
            if (!removedBefore.isEmpty() || !removedAfter.isEmpty()) {
                if (this.originalMethods == null) this.originalMethods = new LinkedHashMap<>();
                java.util.Set<LuaValue> methodNames = new java.util.LinkedHashSet<>(removedBefore.keySet());
                methodNames.addAll(removedAfter.keySet());
                for (LuaValue method : methodNames) {
                    LuaValue before = removedBefore.getOrDefault(method, LuaValue.NIL);
                    LuaValue after = removedAfter.getOrDefault(method, LuaValue.NIL);
                    LuaValue thisBefore = this.originalMethods.getOrDefault(method, LuaValue.NIL);
                    if (!sameValue(before, after) && sameValue(thisBefore, after)) {
                        this.originalMethods.put(method, before);
                    }
                }
            }

            Map<LuaValue, LuaValue> mixinBefore = removed.originalMixinMethods == null
                ? Map.of() : removed.originalMixinMethods;
            Map<LuaValue, LuaValue> mixinAfter = removed.expectedMixinMethods == null
                ? Map.of() : removed.expectedMixinMethods;
            if (!mixinBefore.isEmpty() || !mixinAfter.isEmpty()) {
                if (this.originalMixinMethods == null) this.originalMixinMethods = new LinkedHashMap<>();
                java.util.Set<LuaValue> names = new java.util.LinkedHashSet<>(mixinBefore.keySet());
                names.addAll(mixinAfter.keySet());
                for (LuaValue name : names) {
                    LuaValue before = mixinBefore.getOrDefault(name, LuaValue.NIL);
                    LuaValue after = mixinAfter.getOrDefault(name, LuaValue.NIL);
                    LuaValue thisBefore = this.originalMixinMethods.getOrDefault(name, LuaValue.NIL);
                    if (!sameValue(before, after) && sameValue(thisBefore, after)) {
                        this.originalMixinMethods.put(name, before);
                    }
                }
            }
        }

        void restore() {
            if (this.expectedFields == null) this.captureExpected();
            Map<LuaValue, LuaValue> currentFields = captureFields(this.table);
            java.util.Set<LuaValue> keys = new java.util.LinkedHashSet<>(this.originalFields.keySet());
            keys.addAll(this.expectedFields.keySet());
            for (LuaValue key : keys) {
                String name = key.optjstring(null);
                if ("__mlua_doc_methods".equals(name)
                    || "__mlua_doc_inherited_methods".equals(name)
                    || "__mlua_doc_parents".equals(name)
                    || "__mlua_doc_mixins".equals(name)) continue;
                LuaValue original = this.originalFields.getOrDefault(key, LuaValue.NIL);
                LuaValue expected = this.expectedFields.getOrDefault(key, LuaValue.NIL);
                LuaValue current = currentFields.getOrDefault(key, LuaValue.NIL);
                if (!sameValue(original, expected) && sameValue(current, expected)) {
                    this.table.rawset(key, original);
                }
            }

            restoreMethodDescriptions();
            restoreMixinMethods();
        }

        private void restoreMixinMethods() {
            LuaValue meta = this.table.getmetatable();
            LuaValue methodsValue = meta.istable()
                ? meta.checktable().rawget("__mlua_mixin_methods") : LuaValue.NIL;
            if (!methodsValue.istable()) return;
            LuaTable methods = methodsValue.checktable();
            Map<LuaValue, LuaValue> expected = this.expectedMixinMethods == null
                ? Map.of() : this.expectedMixinMethods;
            java.util.Set<LuaValue> names = new java.util.LinkedHashSet<>();
            if (this.originalMixinMethods != null) names.addAll(this.originalMixinMethods.keySet());
            names.addAll(expected.keySet());
            for (LuaValue name : names) {
                LuaValue before = this.originalMixinMethods == null
                    ? LuaValue.NIL : this.originalMixinMethods.getOrDefault(name, LuaValue.NIL);
                LuaValue after = expected.getOrDefault(name, LuaValue.NIL);
                if (!sameValue(before, after) && sameValue(methods.rawget(name), after)) {
                    methods.rawset(name, before);
                }
            }
        }

        private void restoreMethodDescriptions() {
            LuaValue currentValue = this.table.rawget("__mlua_doc_methods");
            LuaTable current = currentValue.istable() ? currentValue.checktable() : null;
            if (current == null) return;
            Map<LuaValue, LuaValue> expected = this.expectedMethods == null ? Map.of() : this.expectedMethods;
            java.util.Set<LuaValue> keys = new java.util.LinkedHashSet<>();
            if (this.originalMethods != null) keys.addAll(this.originalMethods.keySet());
            keys.addAll(expected.keySet());
            for (LuaValue key : keys) {
                LuaValue original = this.originalMethods == null
                    ? LuaValue.NIL : this.originalMethods.getOrDefault(key, LuaValue.NIL);
                LuaValue after = expected.getOrDefault(key, LuaValue.NIL);
                LuaValue present = current.rawget(key);
                LuaValue expectedMethod = this.expectedFields.getOrDefault(key, LuaValue.NIL);
                if (!sameValue(original, after) && sameValue(present, after)
                    && sameValue(this.table.rawget(key), expectedMethod)) {
                    current.rawset(key, original);
                }
            }

            LuaValue expectedTable = this.expectedFields.getOrDefault(
                LuaValue.valueOf("__mlua_doc_methods"), LuaValue.NIL);
            boolean originallyHadMethodsTable = this.originalFields
                .getOrDefault(LuaValue.valueOf("__mlua_doc_methods"), LuaValue.NIL).istable();
            if (!originallyHadMethodsTable && current.next(LuaValue.NIL).arg1().isnil()
                && currentValue == expectedTable) {
                this.table.rawset("__mlua_doc_methods", LuaValue.NIL);
            }
        }

        private static boolean sameValue(LuaValue left, LuaValue right) {
            if (left == right) return true;
            if (left == null || right == null || left.type() != right.type()) return false;
            if (left.isnil()) return true;
            if (left.isstring()) return left.tojstring().equals(right.tojstring());
            if (left.isnumber()) return Double.compare(left.todouble(), right.todouble()) == 0;
            if (left.isboolean()) return left.toboolean() == right.toboolean();
            return false;
        }

    }

    boolean registerServices(MLuaModule module, MLuaServiceRegistry registry) {
        List<MLuaServiceRegistry.Registration> batch = new ArrayList<>();
        try {
            List<MLuaModule.ServiceEntry> entries = serviceEntries(module);
            List<MLuaServiceRegistry.Registration> declared = serviceRegistrations(module, entries);
            registry.registerBatch(module.side(), declared);
            batch.addAll(declared);
            this.rememberServices(registry, module, batch);
            for (MLuaModule.ServiceEntry entry : entries) {
                MLuaLogger.LOGGER.debug("  Registered service '{}' from module '{}'", entry.name(), module.id());
            }
            return true;
        } catch (Throwable error) {
            registry.unregisterBatch(module.side(), batch);
            MLuaLogger.LOGGER.error("MLua module '{}' failed during services and was isolated", module.id(), error);
            return false;
        }
    }

    List<MLuaServiceRegistry.Registration> serviceRegistrations(MLuaModule module) {
        return serviceRegistrations(module, serviceEntries(module));
    }

    private synchronized List<MLuaModule.ServiceEntry> serviceEntries(MLuaModule module) {
        return this.serviceEntries.computeIfAbsent(module,
            key -> List.copyOf(Objects.requireNonNull(key.services(), "module services")));
    }

    private static List<MLuaServiceRegistry.Registration> serviceRegistrations(
        MLuaModule module, List<MLuaModule.ServiceEntry> entries) {
        Map<String, String> documentedServices = declaredDescriptions(module, MLuaDocBuilder.DocEntryType.SERVICE);
        Map<String, Map<String, String>> documentedMethods = declaredMethods(module);
        return entries.stream().map(entry -> {
            String description = mergeServiceDescription(entry.name(), documentedServices.get(entry.name()), entry.description());
            Map<String, String> methodDescriptions = mergeFunctionDescriptions(entry.name(),
                documentedMethods.getOrDefault(entry.name(), Map.of()), entry.functionDescriptions());
            return new MLuaServiceRegistry.Registration(
                entry.name(), entry.className(), entry.factory(), description, methodDescriptions);
        }).toList();
    }

    private static String mergeServiceDescription(String serviceName, String documented, String registered) {
        // Service descriptors are the runtime declaration; docs are fallback prose.
        return registered != null ? registered : documented;
    }

    private static Map<String, String> mergeFunctionDescriptions(String serviceName,
            Map<String, String> documented, Map<String, String> registered) {
        Map<String, String> merged = new LinkedHashMap<>(documented);
        registered.forEach((name, description) -> {
            merged.put(name, description);
        });
        return java.util.Collections.unmodifiableMap(merged);
    }

    void rememberServices(MLuaServiceRegistry registry, MLuaModule module,
                          List<MLuaServiceRegistry.Registration> registrations) {
        this.services.computeIfAbsent(registry, ignored -> new IdentityHashMap<>())
            .put(module, List.copyOf(registrations));
    }

    void unregisterServices(MLuaModule module) {
        for (MLuaServiceRegistry registry : List.copyOf(this.services.keySet())) {
            Map<MLuaModule, List<MLuaServiceRegistry.Registration>> byModule = this.services.get(registry);
            List<MLuaServiceRegistry.Registration> registrations = byModule != null ? byModule.remove(module) : null;
            if (registrations != null) registry.unregisterBatch(module.side(), registrations);
            if (byModule != null && byModule.isEmpty()) this.services.remove(registry);
        }
        this.forgetServiceEntries(module);
    }

    void forgetServiceEntries(MLuaModule module) {
        synchronized (this) {
            this.serviceEntries.remove(module);
        }
    }

    private static Map<String, String> declaredDescriptions(MLuaModule module, MLuaDocBuilder.DocEntryType type) {
        MLuaDocBuilder docs = collectDocs(module);
        Map<String, String> descriptions = new java.util.LinkedHashMap<>();
        for (MLuaDocBuilder.DocEntry entry : docs.entries()) {
            if (entry.type() == type && entry.desc() != null && !entry.desc().isBlank()) {
                putConsistentDescription(descriptions, entry.name(), entry.desc(), entry.type() + " '" + entry.name() + "'");
            }
        }
        return descriptions;
    }

    private static Map<String, Map<String, String>> declaredMethods(MLuaModule module) {
        MLuaDocBuilder docs = collectDocs(module);
        Map<String, Map<String, Map<String, String>>> overloads = new java.util.LinkedHashMap<>();
        for (MLuaDocBuilder.MethodDoc method : docs.methods()) {
            String name = methodName(method.signature());
            if (name == null || method.description() == null || method.description().isBlank()) continue;
            Map<String, Map<String, String>> receiverMethods = overloads.computeIfAbsent(
                method.receiver(), ignored -> new java.util.LinkedHashMap<>());
            Map<String, String> signatures = receiverMethods.computeIfAbsent(name,
                ignored -> new java.util.LinkedHashMap<>());
            putConsistentDescription(signatures, method.signature().trim(), method.description(),
                "overload '" + method.signature() + "'");
        }
        Map<String, Map<String, String>> methods = new java.util.LinkedHashMap<>();
        overloads.forEach((receiver, receiverOverloads) -> {
            Map<String, String> descriptions = new java.util.LinkedHashMap<>();
            receiverOverloads.forEach((name, signatures) -> {
                String description = signatures.size() == 1
                    ? signatures.values().iterator().next()
                    : signatures.entrySet().stream()
                        .map(entry -> entry.getKey() + " — " + entry.getValue())
                        .collect(java.util.stream.Collectors.joining("; "));
                descriptions.put(name, description);
            });
            methods.put(receiver, descriptions);
        });
        return methods;
    }

    private static void putConsistentDescription(Map<String, String> descriptions, String key,
                                                  String description, String displayName) {
        String existing = descriptions.putIfAbsent(key, description);
        if (existing != null && !existing.equals(description)) {
            throw new IllegalStateException("Conflicting descriptions registered for " + displayName);
        }
    }

    private static String methodName(String signature) {
        if (signature == null) return null;
        int separator = Math.max(signature.lastIndexOf(':'), signature.lastIndexOf('.'));
        int start = separator + 1;
        int end = signature.indexOf('(', start);
        if (start >= signature.length() || end <= start) return null;
        return signature.substring(start, end).trim();
    }

    private static MLuaDocBuilder collectDocs(MLuaModule module) {
        MLuaDocBuilder docs = new MLuaDocBuilder();
        try {
            module.contributeDocs(docs);
        } catch (Throwable error) {
            MLuaLogger.LOGGER.warn("MLua module '{}' failed while contributing API descriptions", module.id(), error);
        }
        return docs;
    }
}
