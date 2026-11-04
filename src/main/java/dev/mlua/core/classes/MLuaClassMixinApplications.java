package dev.mlua.core.classes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

/** Owns behavior-mixin application, inheritance resolution, and deferred mixin removal. */
final class MLuaClassMixinApplications {
    interface Host {
        boolean classExists(String name);
        Set<String> classNames();
        LuaTable classTable(String name);
        boolean inheritsFrom(String className, String ancestor);
        boolean hasPropertyDefinition(String className, String propertyName);
        void ensureNoExistingInstanceFieldShadowingMethod(String className, String methodName);
        void refreshParentLookup(String className);
        void refreshInheritedMethodDescriptions(String className);
        void refreshAllInheritedMethodDescriptions();
        void markClassActive(String className);
        void removePendingClassIfUnused(String className);
        void cleanupPendingClasses();
    }

    private final Host host;
    private final MLuaClassMixinRegistry definitions;
    private final MLuaClassHierarchy hierarchy;
    private final Map<String, List<String>> appliedMixins = new HashMap<>();
    private final Map<String, List<String>> effectiveMixins = new HashMap<>();

    MLuaClassMixinApplications(Host host, MLuaClassMixinRegistry definitions,
                               MLuaClassHierarchy hierarchy) {
        this.host = host;
        this.definitions = definitions;
        this.hierarchy = hierarchy;
    }

    void refreshClassesUsingMixin(String mixinName) {
        Set<String> affectedClasses = new HashSet<>();
        for (Map.Entry<String, List<String>> entry : this.appliedMixins.entrySet()) {
            if (!entry.getValue().contains(mixinName)) continue;
            String mixinOwner = entry.getKey();
            affectedClasses.add(mixinOwner);
            affectedClasses.addAll(this.hierarchy.descendantsOf(mixinOwner));
        }
        affectedClasses.forEach(this.host::refreshInheritedMethodDescriptions);
    }

    void apply(String className, String mixinName) {
        if (!this.host.classExists(className)) throw new IllegalStateException("Lua class is not registered: " + className);
        if (!this.definitions.contains(mixinName)) throw new IllegalStateException("Lua mixin is not registered: " + mixinName);
        if (this.effectiveMixins(className, new HashSet<>()).contains(mixinName)) {
            this.host.markClassActive(className);
            return;
        }
        this.validateApplication(className, mixinName);
        this.host.markClassActive(className);
        this.definitions.cancelPendingRemoval(mixinName);
        List<String> applied = this.appliedMixins.computeIfAbsent(className, ignored -> new ArrayList<>());
        applied.add(mixinName);
        this.invalidateEffectiveMixins(className);
        this.host.refreshParentLookup(className);
        this.host.refreshInheritedMethodDescriptions(className);
    }

    boolean remove(String mixinName) {
        if (!this.definitions.contains(mixinName)) return false;
        if (this.isApplied(mixinName)) {
            this.definitions.markPendingRemoval(mixinName);
            return false;
        }
        this.removeDefinition(mixinName);
        return true;
    }

    private boolean isApplied(String mixinName) {
        return this.appliedMixins.values().stream().anyMatch(values -> values.contains(mixinName));
    }

    void removePendingIfUnused(String mixinName) {
        if (this.definitions.isPendingRemoval(mixinName) && !this.isApplied(mixinName)) {
            this.removeDefinition(mixinName);
        }
    }

    private void removeDefinition(String mixinName) {
        if (this.definitions.remove(mixinName) == null) return;
        this.appliedMixins.values().forEach(values -> values.remove(mixinName));
        this.appliedMixins.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        this.effectiveMixins.clear();
        for (String className : this.host.classNames()) this.host.refreshParentLookup(className);
        this.host.refreshAllInheritedMethodDescriptions();
        this.host.cleanupPendingClasses();
    }

    private void validateApplication(String className, String mixinName) {
        LuaTable candidate = this.definitions.get(mixinName);
        LuaTable candidateMethods = MLuaClassMixinRegistry.methods(candidate);
        LuaValue candidateKey = LuaValue.NIL;
        while (true) {
            Varargs method = candidateMethods.next(candidateKey);
            candidateKey = method.arg1();
            if (candidateKey.isnil()) break;
            if (!candidateKey.isstring() || !method.arg(2).isfunction()) continue;
            String methodName = candidateKey.tojstring();
            for (String candidateClass : this.host.classNames()) {
                if (!candidateClass.equals(className)
                    && !this.host.inheritsFrom(candidateClass, className)) continue;
                if (this.host.hasPropertyDefinition(candidateClass, methodName)) {
                    throw new IllegalStateException("Cannot apply behavior mixin '" + mixinName + "' to class '"
                        + className + "': method '" + methodName + "' conflicts with a property on class '"
                        + candidateClass + "'");
                }
            }
            this.host.ensureNoExistingInstanceFieldShadowingMethod(className, methodName);
        }
        for (String affectedClass : this.host.classNames()) {
            if (!affectedClass.equals(className)
                && !this.host.inheritsFrom(affectedClass, className)) continue;
            for (String existingName : this.effectiveMixins(affectedClass, new HashSet<>())) {
                if (existingName.equals(mixinName)) continue;
                LuaTable existing = this.definitions.get(existingName);
                if (existing == null) continue;
                LuaTable existingMethods = MLuaClassMixinRegistry.methods(existing);
                LuaValue key = LuaValue.NIL;
                while (true) {
                    Varargs entry = existingMethods.next(key);
                    key = entry.arg1();
                    if (key.isnil()) break;
                    if (key.isstring() && entry.arg(2).isfunction() && candidateMethods.get(key).isfunction()) {
                        throw new IllegalStateException("Cannot apply behavior mixin '" + mixinName + "' to class '"
                            + affectedClass + "': method '" + key.tojstring() + "' is already supplied by mixin '"
                            + existingName + "'");
                    }
                }
            }
        }
    }

    void validateMethodRegistration(String mixinName, String functionName) {
        for (String className : this.host.classNames()) {
            List<String> effective = this.effectiveMixins(className, new HashSet<>());
            if (!effective.contains(mixinName)) continue;
            if (this.host.hasPropertyDefinition(className, functionName)) {
                throw new IllegalStateException("Cannot add method '" + functionName + "' to behavior mixin '"
                    + mixinName + "': class '" + className + "' already defines it as a property");
            }
            this.host.ensureNoExistingInstanceFieldShadowingMethod(className, functionName);
            for (String otherName : effective) {
                if (otherName.equals(mixinName)) continue;
                LuaTable other = this.definitions.get(otherName);
                if (other != null && other.get(functionName).isfunction()) {
                    throw new IllegalStateException("Cannot add method '" + functionName + "' to behavior mixin '"
                        + mixinName + "': class '" + className + "' already receives it from mixin '"
                        + otherName + "'");
                }
            }
        }
    }

    private List<String> effectiveMixins(String className, Set<String> visited) {
        List<String> cached = this.effectiveMixins.get(className);
        if (cached != null) return cached;
        if (!visited.add(className)) return List.of();
        LinkedHashSet<String> result = new LinkedHashSet<>(this.appliedMixins.getOrDefault(className, List.of()));
        String parentName = this.hierarchy.parentOf(className);
        if (parentName != null) {
            result.addAll(this.effectiveMixins(parentName, visited));
        }
        List<String> resolved = List.copyOf(result);
        this.effectiveMixins.put(className, resolved);
        return resolved;
    }

    boolean has(String className, String mixinName) {
        return className != null && !className.isBlank() && mixinName != null && !mixinName.isBlank()
            && this.host.classExists(className)
            && this.effectiveMixins(className, new HashSet<>()).contains(mixinName);
    }

    boolean remove(String className, String mixinName) {
        List<String> applied = this.appliedMixins.get(className);
        if (applied == null || !applied.remove(mixinName)) return false;
        if (applied.isEmpty()) this.appliedMixins.remove(className);
        this.invalidateEffectiveMixins(className);
        if (this.host.classTable(className) != null) this.host.refreshParentLookup(className);
        this.host.refreshInheritedMethodDescriptions(className);
        this.host.removePendingClassIfUnused(className);
        this.removePendingIfUnused(mixinName);
        return true;
    }

    List<String> removeClass(String className) {
        List<String> removed = List.copyOf(this.appliedMixins.getOrDefault(className, List.of()));
        this.appliedMixins.remove(className);
        this.effectiveMixins.remove(className);
        if (!removed.isEmpty()) this.invalidateEffectiveMixins(className);
        for (String mixinName : removed) this.removePendingIfUnused(mixinName);
        return removed;
    }

    List<String> directlyApplied(String className) {
        return this.appliedMixins.getOrDefault(className, List.of());
    }

    private void invalidateEffectiveMixins(String className) {
        this.effectiveMixins.remove(className);
        for (String descendant : this.hierarchy.descendantsOf(className)) {
            this.effectiveMixins.remove(descendant);
        }
    }
}
