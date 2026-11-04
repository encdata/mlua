package dev.mlua.module;

import dev.mlua.core.MLuaLogger;
import dev.mlua.core.service.MLuaServiceRegistry;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

final class MLuaModuleRegistrations {
    private final Map<MLuaRegistration, Map<MLuaModule, List<String>>> classes = new WeakHashMap<>();
    private final Map<MLuaRegistration, Map<MLuaModule, Map<SnapshotKey, MLuaModuleClassSnapshot>>> classSnapshots = new WeakHashMap<>();
    private final Map<MLuaRegistration, Map<MLuaModule, List<String>>> addedMixins = new WeakHashMap<>();
    private final Map<MLuaRegistration, Map<MLuaModule, List<MixinApplication>>> appliedMixins = new WeakHashMap<>();
    private final Map<MLuaRegistration, Map<MLuaModule, List<Runnable>>> rollbackActions = new WeakHashMap<>();
    private final Map<MLuaModule, MLuaModuleDocumentation> documentationByModule = new IdentityHashMap<>();
    private final MLuaModuleServiceRegistrations serviceLifecycle = new MLuaModuleServiceRegistrations();

    boolean registerClasses(MLuaModule module, MLuaRegistration registration) {
        Map<MLuaModule, List<String>> byModule = this.classes.computeIfAbsent(registration, ignored -> new IdentityHashMap<>());
        if (byModule.containsKey(module)) return true;

        List<String> added = new ArrayList<>();
        List<String> newMixins = new ArrayList<>();
        List<MixinApplication> newMixinApplications = new ArrayList<>();
        List<Runnable> moduleRollbackActions = new ArrayList<>();
        Map<SnapshotKey, MLuaModuleClassSnapshot> snapshots = new LinkedHashMap<>();
        MLuaRegistration tracked = trackedRegistration(module, registration, added, snapshots, newMixins, newMixinApplications, moduleRollbackActions);

        try {
            module.registerClasses(tracked);
            MLuaModuleDocumentation documentation = this.documentation(module);
            for (Map.Entry<String, String> entry : documentation.descriptions(MLuaDocBuilder.DocEntryType.CLASS).entrySet()) {
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
            documentation.methodsByReceiver().forEach((receiver, methods) -> {
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
        Map<SnapshotKey, MLuaModuleClassSnapshot> snapshots = new LinkedHashMap<>();
        MLuaRegistration tracked = trackedRegistration(module, registration, added, snapshots, newMixins, newMixinApplications, moduleRollbackActions);

        try {
            MLuaModuleDocumentation documentation = this.documentation(module);
            Map<String, String> descriptions = documentation.descriptions(MLuaDocBuilder.DocEntryType.SERVICE);
            Map<String, Map<String, String>> methodDescriptions = documentation.methodsByReceiver();
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
        synchronized (this) {
            this.documentationByModule.remove(module);
        }
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
        Map<MLuaModule, Map<SnapshotKey, MLuaModuleClassSnapshot>> snapshotsByModule = this.classSnapshots.get(registration);
        Map<SnapshotKey, MLuaModuleClassSnapshot> snapshots = snapshotsByModule != null ? snapshotsByModule.remove(module) : null;
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

    private static void rebaseOtherSnapshots(Map<MLuaModule, Map<SnapshotKey, MLuaModuleClassSnapshot>> byModule,
                                             MLuaModule removedModule,
                                             Map<SnapshotKey, MLuaModuleClassSnapshot> removedSnapshots) {
        if (byModule == null) return;
        byModule.forEach((module, snapshots) -> {
            if (module == removedModule) return;
            removedSnapshots.forEach((name, removed) -> {
                MLuaModuleClassSnapshot remaining = snapshots.get(name);
                if (remaining != null) remaining.rebaseOriginals(removed);
            });
        });
    }

    private static MLuaRegistration trackedRegistration(MLuaModule module, MLuaRegistration registration, List<String> added,
                                                        Map<SnapshotKey, MLuaModuleClassSnapshot> snapshots,
                                                        List<String> addedMixins, List<MixinApplication> appliedMixins,
                                                        List<Runnable> rollbackActions) {
        return new MLuaTrackedRegistration(module, registration, added, snapshots, addedMixins, appliedMixins, rollbackActions);
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

    static void removeRollbackAction(List<Runnable> actions, Runnable action) {
        if (action != null) actions.removeIf(candidate -> candidate == action);
    }

    private void rememberSnapshots(MLuaRegistration registration, MLuaModule module,
                                  Map<SnapshotKey, MLuaModuleClassSnapshot> snapshots) {
        if (snapshots.isEmpty()) return;
        snapshots.values().forEach(MLuaModuleClassSnapshot::captureExpected);
        Map<SnapshotKey, MLuaModuleClassSnapshot> stored = this.classSnapshots
            .computeIfAbsent(registration, ignored -> new IdentityHashMap<>())
            .computeIfAbsent(module, ignored -> new LinkedHashMap<>());
        snapshots.forEach((name, snapshot) -> {
            MLuaModuleClassSnapshot existing = stored.get(name);
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

    record MixinApplication(String className, String mixinName, boolean applied) { }

    static void snapshotClass(MLuaRegistration registration, String name,
                                      Map<SnapshotKey, MLuaModuleClassSnapshot> snapshots) {
        SnapshotKey key = SnapshotKey.classType(name);
        if (snapshots.containsKey(key)) return;
        snapshotTable(snapshots, key, registration.classTable(name));
    }

    static void snapshotTable(Map<SnapshotKey, MLuaModuleClassSnapshot> snapshots, SnapshotKey key, LuaTable table) {
        if (!snapshots.containsKey(key) && table != null) snapshots.put(key, MLuaModuleClassSnapshot.capture(table));
    }

    private static void restoreSnapshots(Map<SnapshotKey, MLuaModuleClassSnapshot> snapshots) {
        snapshots.values().forEach(MLuaModuleClassSnapshot::restore);
    }

    private static void restoreClassSnapshots(MLuaRegistration registration,
                                              Map<SnapshotKey, MLuaModuleClassSnapshot> snapshots) {
        restoreSnapshots(snapshots);
        boolean mixinRestored = snapshots.keySet().stream().anyMatch(SnapshotKey::mixin);
        for (SnapshotKey key : snapshots.keySet()) {
            if (!key.mixin()) registration.refreshClassMetadata(key.name());
        }
        if (mixinRestored) registration.classNames().forEach(registration::refreshClassMetadata);
    }

    record SnapshotKey(String name, boolean mixin) {
        static SnapshotKey classType(String name) { return new SnapshotKey(name, false); }
        static SnapshotKey mixin(String name) { return new SnapshotKey(name, true); }
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

    boolean registerServices(MLuaModule module, MLuaServiceRegistry registry) {
        return this.serviceLifecycle.registerServices(module, registry, this.documentation(module));
    }

    List<MLuaServiceRegistry.Registration> serviceRegistrations(MLuaModule module) {
        return this.serviceLifecycle.serviceRegistrations(module, this.documentation(module));
    }

    private MLuaModuleDocumentation documentation(MLuaModule module) {
        synchronized (this) {
            MLuaModuleDocumentation existing = this.documentationByModule.get(module);
            if (existing != null) return existing;
        }
        MLuaModuleDocumentation collected = MLuaModuleDocumentation.collect(module);
        synchronized (this) {
            MLuaModuleDocumentation existing = this.documentationByModule.get(module);
            if (existing != null) return existing;
            this.documentationByModule.put(module, collected);
            return collected;
        }
    }

    private List<MLuaModule.ServiceEntry> serviceEntries(MLuaModule module) {
        return this.serviceLifecycle.serviceEntries(module);
    }

    static String mergeServiceDescription(String serviceName, String documented, String registered) {
        return registered != null ? registered : documented;
    }

    static Map<String, String> mergeFunctionDescriptions(String serviceName,
            Map<String, String> documented, Map<String, String> registered) {
        Map<String, String> merged = new LinkedHashMap<>(documented);
        merged.putAll(registered);
        return java.util.Collections.unmodifiableMap(merged);
    }

    void rememberServices(MLuaServiceRegistry registry, MLuaModule module,
                          List<MLuaServiceRegistry.Registration> registrations) {
        this.serviceLifecycle.rememberServices(registry, module, registrations);
    }

    synchronized void unregisterServices(MLuaModule module) {
        this.serviceLifecycle.unregisterServices(module);
        this.documentationByModule.remove(module);
    }

    synchronized void forgetServiceEntries(MLuaModule module) {
        this.serviceLifecycle.forgetServiceEntries(module);
        this.documentationByModule.remove(module);
    }

}
