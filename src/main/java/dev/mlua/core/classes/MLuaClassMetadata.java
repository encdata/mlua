package dev.mlua.core.classes;

import dev.mlua.module.MLuaRegistration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

/** Owns inherited class documentation and property metadata synchronization. */
final class MLuaClassMetadata {
    private final Object registryLock;
    private final Map<String, LuaTable> classes;
    private final MLuaClassHierarchy hierarchy;
    private final MLuaClassMixinRegistry mixinRegistry;
    private final MLuaClassMixinApplications mixinApplications;
    private final MLuaClassLiveInstances liveInstances;
    private final Map<LuaTable, InstanceDescription> instanceDescriptions = new WeakHashMap<>();
    private final Map<String, Map<String, LuaTable>> propertyLookups = new HashMap<>();

    MLuaClassMetadata(Object registryLock, Map<String, LuaTable> classes, MLuaClassHierarchy hierarchy,
                      MLuaClassMixinRegistry mixinRegistry, MLuaClassMixinApplications mixinApplications,
                      MLuaClassLiveInstances liveInstances) {
        this.registryLock = registryLock;
        this.classes = classes;
        this.hierarchy = hierarchy;
        this.mixinRegistry = mixinRegistry;
        this.mixinApplications = mixinApplications;
        this.liveInstances = liveInstances;
    }

    void attachInstanceDocumentation(LuaTable instance, String className, LuaTable classTable) {
        LuaValue classDescription = classTable.rawget("__mlua_doc_description");
        LuaValue currentDescription = instance.rawget("__mlua_doc_description");
        InstanceDescription previousDescription = this.instanceDescriptions.get(instance);
        boolean inheritedDescription = currentDescription.isnil()
            || (previousDescription != null && previousDescription.inherited()
                && sameOptionalString(currentDescription, previousDescription.value()))
            || (classDescription.isstring() && currentDescription.isstring()
                && classDescription.tojstring().equals(currentDescription.tojstring()));
        if (inheritedDescription) {
            instance.rawset("__mlua_doc_description", classDescription.isstring() ? classDescription : LuaValue.NIL);
        }
        this.instanceDescriptions.put(instance, new InstanceDescription(inheritedDescription,
            inheritedDescription && classDescription.isstring() ? classDescription.tojstring() : null));
        instance.rawset("__mlua_doc_parents", copyTableOrNil(classTable.rawget("__mlua_doc_parents")));
        LuaTable methodDescriptions = new LuaTable();
        this.collectMethodDescriptions(className, methodDescriptions, new HashSet<>());
        LuaValue existingDescriptions = instance.rawget("__mlua_doc_methods");
        if (existingDescriptions.istable()) {
            LuaValue key = LuaValue.NIL;
            while (true) {
                Varargs next = existingDescriptions.checktable().next(key);
                key = next.arg1();
                if (key.isnil()) break;
                if (methodDescriptions.rawget(key).isnil()
                    && hasInstanceImplementation(instance, key)) {
                    methodDescriptions.rawset(key, next.arg(2));
                }
            }
        }
        instance.rawset("__mlua_doc_methods", methodDescriptions);
        LuaTable instanceDispatchers = new LuaTable();
        this.collectInstanceDispatchers(className, instanceDispatchers, new HashSet<>());
        instance.rawset("__mlua_instance_dispatchers", instanceDispatchers);
        LuaTable propertyDescriptions = new LuaTable();
        this.collectPropertyDescriptions(className, propertyDescriptions, new HashSet<>());
        instance.rawset("__mlua_doc_properties", propertyDescriptions);
    }

    void refreshAttachedPropertyDescriptionsFrom(String changedClass) {
      this.invalidatePropertyLookupsFrom(changedClass);
      for (LuaTable instance : this.liveInstances.ofClassAndDescendants(
            changedClass, this.hierarchy.descendantsOf(changedClass))) {
         String instanceClassName = instance.rawget("__type").optjstring("");
         LuaTable descriptions = new LuaTable();
         this.collectPropertyDescriptions(instanceClassName, descriptions, new HashSet<>());
         instance.rawset("__mlua_doc_properties", descriptions);
      }
   }

    LuaTable findPropertyDescriptor(String className, String propertyName) {
      synchronized (this.registryLock) {
         Map<String, LuaTable> lookup = this.propertyLookups.get(className);
         if (lookup == null) {
            lookup = this.buildPropertyLookup(className);
            if (lookup == null) return null;
            this.propertyLookups.put(className, lookup);
         }
         return lookup.get(propertyName);
      }
   }

    void invalidatePropertyLookupsFrom(String className) {
      synchronized (this.registryLock) {
         this.propertyLookups.remove(className);
         for (String descendant : this.hierarchy.descendantsOf(className)) {
            this.propertyLookups.remove(descendant);
         }
      }
   }

    private Map<String, LuaTable> buildPropertyLookup(String className) {
      Map<String, LuaTable> lookup = new HashMap<>();
      Set<String> visited = new HashSet<>();
      String currentName = className;
      while (currentName != null && visited.add(currentName)) {
         LuaTable owner = this.classes.get(currentName);
         if (owner == null) return null;
         LuaValue descriptors = owner.rawget("__mlua_properties");
         if (descriptors.istable()) {
            LuaValue key = LuaValue.NIL;
            while (true) {
               Varargs next = descriptors.checktable().next(key);
               key = next.arg1();
               if (key.isnil()) break;
               LuaValue descriptor = next.arg(2);
               if (key.isstring() && descriptor.istable()) {
                  lookup.putIfAbsent(key.tojstring(), descriptor.checktable());
               }
            }
         }
         currentName = this.hierarchy.parentOf(currentName);
      }
      return lookup;
   }

    boolean hasPropertyDefinition(String className, String propertyName, Set<String> visited) {
      synchronized (this.registryLock) {
         if (!visited.add(className)) return false;
         LuaTable owner = this.classes.get(className);
         if (owner == null) return false;
         LuaValue descriptors = owner.rawget("__mlua_properties");
         if (descriptors.istable() && !descriptors.checktable().rawget(propertyName).isnil()) return true;
         LuaValue documented = owner.rawget("__mlua_doc_properties");
         if (documented.istable() && !documented.checktable().rawget(propertyName).isnil()) return true;
         String parentName = this.hierarchy.parentOf(className);
         if (parentName != null) {
            if (this.hasPropertyDefinition(parentName, propertyName, visited)) return true;
         }
         return false;
      }
   }

    void collectMethodDescriptions(String className, LuaTable output, Set<String> visited) {
      if (!visited.add(className)) return;
      LuaTable classTable = this.classes.get(className);
      if (classTable != null) {
         LuaValue descriptions = classTable.rawget("__mlua_doc_methods");
         if (descriptions.istable()) {
            LuaValue key = LuaValue.NIL;
            while (true) {
               Varargs next = descriptions.checktable().next(key);
               key = next.arg1();
               if (key.isnil()) break;
               output.rawset(key, next.arg(2));
            }
         }
      }
      for (String mixinName : this.mixinApplications.directlyApplied(className)) {
         LuaTable mixin = this.mixinRegistry.get(mixinName);
         if (mixin == null) continue;
         LuaValue descriptions = mixin.rawget("__mlua_doc_methods");
         if (!descriptions.istable()) continue;
         LuaValue key = LuaValue.NIL;
         while (true) {
            Varargs next = descriptions.checktable().next(key);
            key = next.arg1();
            if (key.isnil()) break;
            if (output.rawget(key).isnil()) output.rawset(key, next.arg(2));
         }
      }
      String parentName = this.hierarchy.parentOf(className);
      if (parentName != null) {
         LuaTable parentDescriptions = new LuaTable();
         this.collectMethodDescriptions(parentName, parentDescriptions, new HashSet<>(visited));
         LuaValue key = LuaValue.NIL;
         while (true) {
            Varargs next = parentDescriptions.next(key);
            key = next.arg1();
            if (key.isnil()) break;
            if (output.rawget(key).isnil()) output.rawset(key, next.arg(2));
         }
      }
   }

    void collectInstanceDispatchers(String className, LuaTable output, Set<String> visited) {
      if (!visited.add(className)) return;
      LuaTable classTable = this.classes.get(className);
      if (classTable != null) {
         LuaValue key = LuaValue.NIL;
         while (true) {
            Varargs next = classTable.next(key);
            key = next.arg1();
            if (key.isnil()) break;
            if (!key.isstring() || !next.arg(2).isfunction()) continue;
            String methodName = key.tojstring();
            if (methodName.startsWith("__") || MLuaRegistration.isReservedFunctionName(methodName)) continue;
            LuaValue declared = classTable.rawget("__mlua_instance_dispatchers");
            LuaValue isDispatcher = declared.istable() ? declared.checktable().rawget(key) : LuaValue.NIL;
            output.rawset(key, isDispatcher.toboolean() ? LuaValue.TRUE : LuaValue.FALSE);
         }
      }
      for (String mixinName : this.mixinApplications.directlyApplied(className)) {
         LuaTable mixin = this.mixinRegistry.get(mixinName);
         if (mixin == null) continue;
         LuaTable methods = MLuaClassMixinRegistry.methods(mixin);
         LuaValue key = LuaValue.NIL;
         while (true) {
            Varargs next = methods.next(key);
            key = next.arg1();
            if (key.isnil()) break;
            if (output.rawget(key).isnil()) output.rawset(key, LuaValue.FALSE);
         }
      }
      String parentName = this.hierarchy.parentOf(className);
      if (parentName != null) {
         LuaTable parentDispatchers = new LuaTable();
         this.collectInstanceDispatchers(parentName, parentDispatchers, new HashSet<>(visited));
         LuaValue key = LuaValue.NIL;
         while (true) {
            Varargs next = parentDispatchers.next(key);
            key = next.arg1();
            if (key.isnil()) break;
            if (output.rawget(key).isnil()) output.rawset(key, next.arg(2));
         }
      }
   }

    void refreshInheritedMethodDescriptions() {
      for (String className : this.classes.keySet()) {
         this.refreshInheritedMethodDescriptions(className);
      }
   }

    void refreshInheritedMethodDescriptionsFrom(String changedClass) {
      this.refreshInheritedMethodDescriptions(changedClass);
      for (String descendant : this.hierarchy.descendantsOf(changedClass)) {
         this.refreshInheritedMethodDescriptions(descendant);
      }
   }

    void refreshInheritedMethodDescriptions(String className) {
      LuaTable classTable = this.classes.get(className);
      if (classTable == null) return;
      LuaTable inherited = new LuaTable();
      Set<String> ancestry = new HashSet<>();
      ancestry.add(className);
      for (String mixinName : this.mixinApplications.directlyApplied(className)) {
         LuaTable mixin = this.mixinRegistry.get(mixinName);
         if (mixin == null) continue;
         LuaValue descriptions = mixin.rawget("__mlua_doc_methods");
         if (!descriptions.istable()) continue;
         LuaValue key = LuaValue.NIL;
         while (true) {
            Varargs next = descriptions.checktable().next(key);
            key = next.arg1();
            if (key.isnil()) break;
            if (classTable.rawget(key).isnil() && inherited.rawget(key).isnil()) inherited.rawset(key, next.arg(2));
         }
      }
      String parentName = this.hierarchy.parentOf(className);
      if (parentName != null) {
         LuaTable parentDescriptions = new LuaTable();
         this.collectMethodDescriptions(parentName, parentDescriptions, new HashSet<>(ancestry));
         LuaValue key = LuaValue.NIL;
         while (true) {
            Varargs next = parentDescriptions.next(key);
            key = next.arg1();
            if (key.isnil()) break;
            if (classTable.rawget(key).isnil() && inherited.rawget(key).isnil()) {
               inherited.rawset(key, next.arg(2));
            }
         }
      }
      classTable.rawset("__mlua_doc_inherited_methods", inherited);
      LuaTable documentedMixins = new LuaTable();
      int mixinIndex = 1;
      for (String mixinName : this.mixinApplications.directlyApplied(className)) {
         documentedMixins.rawset(mixinIndex++, LuaValue.valueOf(mixinName));
      }
      classTable.rawset("__mlua_doc_mixins", documentedMixins);
      this.refreshAttachedInstanceDescriptions(className);
   }

    void refreshAttachedInstanceDescriptions(String className) {
      LuaTable registeredClass = this.classes.get(className);
      if (registeredClass == null) return;
      for (LuaTable instance : this.liveInstances.ofClass(className)) {
         // Luau-style freeze is an explicit promise that this instance will not change.
         // Keep it tracked for class identity checks, but do not let a later class or
         // mixin registration make an unrelated runtime operation fail on rawset.
         if (instance.isfrozen()) continue;
         InstanceDescription descriptionState = this.instanceDescriptions.get(instance);
         if (descriptionState != null && descriptionState.inherited()
             && sameOptionalString(instance.rawget("__mlua_doc_description"), descriptionState.value())) {
            LuaValue classDescription = registeredClass.rawget("__mlua_doc_description");
            instance.rawset("__mlua_doc_description", classDescription.isstring() ? classDescription : LuaValue.NIL);
            this.instanceDescriptions.put(instance, new InstanceDescription(true,
               classDescription.isstring() ? classDescription.tojstring() : null));
         } else if (descriptionState != null && descriptionState.inherited()) {
            this.instanceDescriptions.put(instance, new InstanceDescription(false, null));
         }
         instance.rawset("__mlua_doc_parents", copyTableOrNil(registeredClass.rawget("__mlua_doc_parents")));
         LuaTable dispatchers = new LuaTable();
         this.collectInstanceDispatchers(className, dispatchers, new HashSet<>());
         instance.rawset("__mlua_instance_dispatchers", dispatchers);
         LuaValue metaValue = instance.getmetatable();
         if (metaValue.istable() && !metaValue.checktable().isfrozen()) {
            metaValue.checktable().rawset("__mlua_class_thread_dispatchers", LuaValue.NIL);
         }
         LuaValue instanceMethodsValue = metaValue.istable()
            ? metaValue.checktable().rawget("__mlua_instance_methods") : LuaValue.NIL;
         if (instanceMethodsValue.istable()) {
            LuaTable instanceMethods = instanceMethodsValue.checktable();
            List<LuaValue> staleMethods = new ArrayList<>();
            LuaValue methodKey = LuaValue.NIL;
            while (true) {
               Varargs method = instanceMethods.next(methodKey);
               methodKey = method.arg1();
               if (methodKey.isnil()) break;
               if (!dispatchers.rawget(methodKey).toboolean()) staleMethods.add(methodKey);
            }
            staleMethods.forEach(method -> instanceMethods.rawset(method, LuaValue.NIL));
         }
         LuaTable descriptions = new LuaTable();
         this.collectMethodDescriptions(className, descriptions, new HashSet<>());
         LuaValue previousValue = instance.rawget("__mlua_doc_methods");
         if (previousValue.istable()) {
            LuaTable previous = previousValue.checktable();
            LuaValue key = LuaValue.NIL;
            while (true) {
               Varargs next = previous.next(key);
               key = next.arg1();
               if (key.isnil()) break;
               if (descriptions.rawget(key).isnil() && hasInstanceImplementation(instance, key)) {
                  descriptions.rawset(key, next.arg(2));
               }
            }
         }
         instance.rawset("__mlua_doc_methods", descriptions);
         LuaTable propertyDescriptions = new LuaTable();
         this.collectPropertyDescriptions(className, propertyDescriptions, new HashSet<>());
         instance.rawset("__mlua_doc_properties", propertyDescriptions);
      }
   }

    void collectPropertyDescriptions(String className, LuaTable output, Set<String> visited) {
      if (!visited.add(className)) return;
      LuaTable classTable = this.classes.get(className);
      if (classTable != null) {
         LuaValue descriptions = classTable.rawget("__mlua_doc_properties");
         if (descriptions.istable()) {
            LuaValue key = LuaValue.NIL;
            while (true) {
               Varargs next = descriptions.checktable().next(key);
               key = next.arg1();
               if (key.isnil()) break;
               if (output.rawget(key).isnil()) output.rawset(key, next.arg(2));
            }
         }
      }
      String parentName = this.hierarchy.parentOf(className);
      if (parentName != null) {
         this.collectPropertyDescriptions(parentName, output, visited);
      }
   }

    static LuaValue copyTableOrNil(LuaValue value) {
      if (!value.istable()) return LuaValue.NIL;
      LuaTable copy = new LuaTable();
      LuaValue key = LuaValue.NIL;
      while (true) {
         Varargs entry = value.checktable().next(key);
         key = entry.arg1();
         if (key.isnil()) return copy;
         copy.rawset(key, entry.arg(2));
      }
   }

    static boolean sameOptionalString(LuaValue value, String expected) {
      if (expected == null) return value.isnil();
      return value.isstring() && expected.equals(value.tojstring());
   }

    record InstanceDescription(boolean inherited, String value) { }

    static boolean hasInstanceImplementation(LuaTable instance, LuaValue key) {
      if (instance.rawget(key).isfunction()) return true;
      LuaValue metaValue = instance.getmetatable();
      if (!metaValue.istable()) return false;
      LuaValue methods = metaValue.checktable().rawget("__mlua_instance_methods");
      return methods.istable() && methods.checktable().get(key).isfunction();
   }
}
