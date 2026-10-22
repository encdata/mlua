package dev.mlua.core.classes;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.Collections;
import java.util.WeakHashMap;
import dev.mlua.module.MLuaRegistration;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.LuaError;

public final class MLuaClassRegistry {
   private final Map<String, LuaTable> classes = new HashMap<>();
   private final Set<LuaTable> registeredClassTables = Collections.newSetFromMap(new IdentityHashMap<>());
   private final MLuaClassHierarchy hierarchy = new MLuaClassHierarchy();
   private final MLuaClassMixinRegistry mixinRegistry;
   private final Map<String, List<String>> appliedMixins = new HashMap<>();
   private final Set<String> pendingClassRemovals = new HashSet<>();
   private final Map<LuaTable, LuaTable> instanceClasses = new WeakHashMap<>();
   private final Map<String, Set<LuaTable>> instancesByClass = new HashMap<>();
   private final Map<LuaTable, InstanceDescription> instanceDescriptions = new WeakHashMap<>();

   public MLuaClassRegistry() {
      this.mixinRegistry = new MLuaClassMixinRegistry(new MLuaClassMixinRegistry.Host() {
         @Override public boolean classExists(String name) { return MLuaClassRegistry.this.classes.containsKey(name); }
         @Override public void validateMethodRegistration(String mixinName, String methodName) {
            MLuaClassRegistry.this.validateMixinMethodAvailability(mixinName, methodName);
         }
         @Override public void refreshAppliedClasses(String mixinName) {
            MLuaClassRegistry.this.refreshClassesUsingMixin(mixinName);
         }
      });
   }

   public synchronized LuaTable register(String name, String parentName) {
      requireClassName(name);
      if (this.mixinRegistry.contains(name)) throw new IllegalStateException("Lua mixin already uses class name: " + name);
      if (parentName == null || parentName.isBlank()) {
         parentName = null;
      } else {
         this.requireParent(parentName);
         if (wouldCreateCycle(name, parentName)) {
            throw new IllegalStateException("Lua class inheritance cycle: " + name + " -> " + parentName);
         }
      }
      LuaTable existing = this.classes.get(name);
      if (existing != null) {
         this.pendingClassRemovals.remove(name);
         this.requireStableParent(name, parentName);
         this.refreshParentLookup(existing, name);
         this.refreshInheritedMethodDescriptionsFrom(name);
         return existing;
      }

      LuaTable classTable = new LuaTable();
      this.pendingClassRemovals.remove(name);
      classTable.rawset("__type", LuaValue.valueOf(name));
      classTable.rawset("ClassName", LuaValue.valueOf(name));
      classTable.rawset("__class", LuaValue.TRUE);
      this.classes.put(name, classTable);
      this.registeredClassTables.add(classTable);
      if (parentName != null) this.recordParent(name, parentName);
      this.refreshParentLookup(classTable, name);
      this.refreshInheritedMethodDescriptionsFrom(name);
      return classTable;
   }

   public synchronized LuaTable register(String name, String parentName, String description) {
      validateClassDescription(this.classes.get(name), name, description);
      LuaTable type = this.register(name, parentName);
      recordClassDescription(type, name, description);
      this.refreshMetadata(name);
      return type;
   }

   @Deprecated(forRemoval = false)
   public synchronized LuaTable register(String name, LuaTable classTable, String... parentNames) {
      List<String> normalized = normalizedParents(parentNames);
      return this.register(name, classTable, normalized.isEmpty() ? null : normalized.get(0));
   }

   public synchronized LuaTable register(String name, LuaTable classTable, String parentName) {
      requireClassName(name);
      if (this.mixinRegistry.contains(name)) throw new IllegalStateException("Lua mixin already uses class name: " + name);
      Objects.requireNonNull(classTable, "classTable");
      this.validateDeclaredClassMethods(name, classTable);
      if (this.instanceClasses.containsKey(classTable)) {
         throw new IllegalArgumentException("A live Lua instance cannot be registered as a class table");
      }
      for (Map.Entry<String, LuaTable> entry : this.classes.entrySet()) {
         if (entry.getValue() == classTable && !entry.getKey().equals(name)) {
            throw new IllegalStateException("Lua class table is already registered as '" + entry.getKey() + "'");
         }
      }
      LuaValue declaredName = classTable.rawget("__type");
      if (!declaredName.isnil() && (!declaredName.isstring() || !name.equals(declaredName.tojstring()))) {
         throw new IllegalArgumentException("Lua class table declares a different class name than '" + name + "'");
      }
      LuaValue publicName = classTable.rawget("ClassName");
      if (!publicName.isnil() && (!publicName.isstring() || !name.equals(publicName.tojstring()))) {
         throw new IllegalArgumentException("Lua class table has a conflicting ClassName for '" + name + "'");
      }
      if (parentName != null && !parentName.isBlank()) {
         this.requireParent(parentName);
         if (wouldCreateCycle(name, parentName)) {
            throw new IllegalStateException("Lua class inheritance cycle: " + name + " -> " + parentName);
         }
      } else {
         parentName = null;
      }
      LuaTable existing = this.classes.get(name);
      if (existing != null && existing != classTable) {
         throw new IllegalStateException("Lua class already registered: " + name);
      }
      if (existing != null) this.requireStableParent(name, parentName);
      this.pendingClassRemovals.remove(name);
      classTable.rawset("__type", LuaValue.valueOf(name));
      if (classTable.rawget("ClassName").isnil()) classTable.rawset("ClassName", LuaValue.valueOf(name));
      classTable.rawset("__class", LuaValue.TRUE);
      this.classes.put(name, classTable);
      this.registeredClassTables.add(classTable);
      if (parentName != null) this.recordParent(name, parentName);
      this.refreshParentLookup(classTable, name);
      this.refreshInheritedMethodDescriptionsFrom(name);
      return classTable;
   }

   public synchronized LuaTable register(String name, LuaTable classTable, String parentName,
                                         String description) {
      validateClassDescription(this.classes.get(name), name, description);
      validateClassDescription(classTable, name, description);
      LuaTable type = this.register(name, classTable, parentName);
      recordClassDescription(type, name, description);
      this.refreshMetadata(name);
      return type;
   }

   private static void validateClassDescription(LuaTable type, String name, String description) {
      if (description == null || description.isBlank()) {
         throw new IllegalArgumentException("Lua class '" + name + "' must have a non-empty description");
      }
      if (type == null) return;
      LuaValue existing = type.rawget("__mlua_doc_description");
      if (!existing.isnil() && !existing.isstring()) {
         throw new IllegalStateException("Lua class '" + name + "' has invalid description metadata");
      }
      if (existing.isstring() && !description.equals(existing.tojstring())) {
         throw new IllegalStateException("Lua class '" + name + "' is already registered with a different description");
      }
   }

   private static void recordClassDescription(LuaTable type, String name, String description) {
      LuaValue existing = type.rawget("__mlua_doc_description");
      if (!existing.isnil() && !existing.isstring()) {
         throw new IllegalStateException("Lua class '" + name + "' has invalid description metadata");
      }
      if (existing.isstring() && !description.equals(existing.tojstring())) {
         throw new IllegalStateException("Lua class '" + name + "' is already registered with a different description");
      }
      type.rawset("__mlua_doc_description", LuaValue.valueOf(description));
   }

   private void validateDeclaredClassMethods(String className, LuaTable classTable) {
      LuaValue docsValue = classTable.rawget("__mlua_doc_methods");
      if (!docsValue.isnil() && !docsValue.istable()) {
         throw new IllegalStateException("Lua class '" + className + "' method descriptions must be a table");
      }
      LuaTable docs = docsValue.istable() ? docsValue.checktable() : null;
      LuaValue key = LuaValue.NIL;
      while (true) {
         Varargs entry = classTable.next(key);
         key = entry.arg1();
         if (key.isnil()) return;
         if (!key.isstring() || !entry.arg(2).isfunction()) continue;
         String methodName = key.tojstring();
         if (methodName.startsWith("__") || MLuaRegistration.isReservedFunctionName(methodName)) continue;
         LuaValue description = docs == null ? LuaValue.NIL : docs.rawget(key);
         if (!description.isstring() || description.tojstring().isBlank()) {
            throw new IllegalStateException("Lua class '" + className + "' function '" + methodName
               + "' must have a non-empty description before class registration");
         }
      }
   }

   public synchronized LuaTable get(String name) {
      this.cleanupPendingClassRemovals();
      return this.classes.get(name);
   }

   public synchronized LuaTable registerMixin(String name, String description) {
      return this.mixinRegistry.register(name, description);
   }

   public synchronized LuaTable mixin(String name) {
      return this.mixinRegistry.get(name);
   }

   public synchronized Set<String> mixinNames() {
      return this.mixinRegistry.names();
   }

   public synchronized LuaTable registerMixinFunction(String mixinName, String functionName,
                                                       LuaValue function, String description) {
      return this.mixinRegistry.registerFunction(mixinName, functionName, function, description);
   }

   public synchronized boolean unregisterMixinFunction(String mixinName, String functionName,
                                                        LuaValue expectedFunction) {
      return this.mixinRegistry.unregisterFunction(mixinName, functionName, expectedFunction);
   }

   private void refreshClassesUsingMixin(String mixinName) {
      Set<String> affectedClasses = new HashSet<>();
      for (Map.Entry<String, List<String>> entry : this.appliedMixins.entrySet()) {
         if (!entry.getValue().contains(mixinName)) continue;
         String mixinOwner = entry.getKey();
         affectedClasses.add(mixinOwner);
         affectedClasses.addAll(this.hierarchy.descendantsOf(mixinOwner));
      }
      affectedClasses.forEach(this::refreshInheritedMethodDescriptions);
   }

   public synchronized void applyMixin(String className, String mixinName) {
      if (!this.classes.containsKey(className)) throw new IllegalStateException("Lua class is not registered: " + className);
      if (!this.mixinRegistry.contains(mixinName)) throw new IllegalStateException("Lua mixin is not registered: " + mixinName);
      if (this.effectiveMixins(className, new HashSet<>()).contains(mixinName)) {
         this.pendingClassRemovals.remove(className);
         return;
      }
      this.validateMixinApplication(className, mixinName);
      this.pendingClassRemovals.remove(className);
      this.mixinRegistry.cancelPendingRemoval(mixinName);
      List<String> applied = this.appliedMixins.computeIfAbsent(className, ignored -> new ArrayList<>());
      applied.add(mixinName);
      this.refreshParentLookup(this.classes.get(className), className);
      this.refreshInheritedMethodDescriptionsFrom(className);
   }

   public synchronized boolean removeMixin(String mixinName) {
      if (!this.mixinRegistry.contains(mixinName)) return false;
      if (this.isMixinApplied(mixinName)) {
         this.mixinRegistry.markPendingRemoval(mixinName);
         return false;
      }
      this.removeMixinEntry(mixinName);
      return true;
   }

   private boolean isMixinApplied(String mixinName) {
      return this.appliedMixins.values().stream().anyMatch(values -> values.contains(mixinName));
   }

   private void removePendingMixinIfUnused(String mixinName) {
      if (this.mixinRegistry.isPendingRemoval(mixinName) && !this.isMixinApplied(mixinName)) {
         this.removeMixinEntry(mixinName);
      }
   }

   private void removeMixinEntry(String mixinName) {
      if (this.mixinRegistry.remove(mixinName) == null) return;
      this.appliedMixins.values().forEach(values -> values.remove(mixinName));
      this.appliedMixins.entrySet().removeIf(entry -> entry.getValue().isEmpty());
      for (String className : this.classes.keySet()) this.refreshParentLookup(this.classes.get(className), className);
      this.refreshInheritedMethodDescriptions();
      for (String className : List.copyOf(this.pendingClassRemovals)) this.removePendingClassIfUnused(className);
   }

   private void validateMixinApplication(String className, String mixinName) {
      LuaTable candidate = this.mixinRegistry.get(mixinName);
      LuaTable candidateMethods = MLuaClassMixinRegistry.methods(candidate);
      LuaValue candidateKey = LuaValue.NIL;
      while (true) {
         Varargs method = candidateMethods.next(candidateKey);
         candidateKey = method.arg1();
         if (candidateKey.isnil()) break;
         if (!candidateKey.isstring() || !method.arg(2).isfunction()) continue;
         String methodName = candidateKey.tojstring();
         for (String candidateClass : this.classes.keySet()) {
            if (!candidateClass.equals(className)
                && !this.inheritsFrom(candidateClass, className)) continue;
            if (this.hasPropertyDefinition(candidateClass, methodName, new HashSet<>())) {
               throw new IllegalStateException("Cannot apply behavior mixin '" + mixinName + "' to class '"
                  + className + "': method '" + methodName + "' conflicts with a property on class '"
                  + candidateClass + "'");
            }
         }
         this.ensureNoExistingInstanceFieldShadowingMethod(className, methodName);
      }
      for (String affectedClass : this.classes.keySet()) {
         if (!affectedClass.equals(className)
             && !this.inheritsFrom(affectedClass, className)) continue;
         for (String existingName : this.effectiveMixins(affectedClass, new HashSet<>())) {
            if (existingName.equals(mixinName)) continue;
            LuaTable existing = this.mixinRegistry.get(existingName);
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

   private void validateMixinMethodAvailability(String mixinName, String functionName) {
      for (String className : this.classes.keySet()) {
         List<String> effective = this.effectiveMixins(className, new HashSet<>());
         if (!effective.contains(mixinName)) continue;
         if (this.hasPropertyDefinition(className, functionName, new HashSet<>())) {
            throw new IllegalStateException("Cannot add method '" + functionName + "' to behavior mixin '"
               + mixinName + "': class '" + className + "' already defines it as a property");
         }
         this.ensureNoExistingInstanceFieldShadowingMethod(className, functionName);
         for (String otherName : effective) {
            if (otherName.equals(mixinName)) continue;
            LuaTable other = this.mixinRegistry.get(otherName);
            if (other != null && other.get(functionName).isfunction()) {
               throw new IllegalStateException("Cannot add method '" + functionName + "' to behavior mixin '"
                  + mixinName + "': class '" + className + "' already receives it from mixin '"
                  + otherName + "'");
            }
         }
      }
   }

   private List<String> effectiveMixins(String className, Set<String> visited) {
      if (!visited.add(className)) return List.of();
      List<String> result = new ArrayList<>(this.appliedMixins.getOrDefault(className, List.of()));
      String parentName = this.hierarchy.parentOf(className);
      if (parentName != null) {
         for (String inherited : this.effectiveMixins(parentName, visited)) {
            if (!result.contains(inherited)) result.add(inherited);
         }
      }
      return result;
   }

   public synchronized boolean hasMixin(String className, String mixinName) {
      return className != null && !className.isBlank() && mixinName != null && !mixinName.isBlank()
         && this.classes.containsKey(className)
         && this.effectiveMixins(className, new HashSet<>()).contains(mixinName);
   }

   public synchronized boolean removeMixin(String className, String mixinName) {
      List<String> applied = this.appliedMixins.get(className);
      if (applied == null || !applied.remove(mixinName)) return false;
      if (applied.isEmpty()) this.appliedMixins.remove(className);
      LuaTable classTable = this.classes.get(className);
      if (classTable != null) this.refreshParentLookup(classTable, className);
      this.refreshInheritedMethodDescriptionsFrom(className);
      this.removePendingClassIfUnused(className);
      this.removePendingMixinIfUnused(mixinName);
      return true;
   }

   public synchronized Set<String> names() {
      this.cleanupPendingClassRemovals();
      return Collections.unmodifiableSet(new TreeSet<>(this.classes.keySet()));
   }

   private void cleanupPendingClassRemovals() {
      for (String className : List.copyOf(this.pendingClassRemovals)) {
         this.removePendingClassIfUnused(className);
      }
   }

   public synchronized void refreshMetadata(String className) {
      if (!this.classes.containsKey(className)) return;
      this.refreshInheritedMethodDescriptionsFrom(className);
      this.refreshAttachedPropertyDescriptionsFrom(className);
      this.removePendingClassIfUnused(className);
   }

   public synchronized boolean unregisterIfUnused(String name) {
      if (!this.classes.containsKey(name)) return false;
      if (this.hasClassDependents(name)) {
         this.pendingClassRemovals.add(name);
         return false;
      }
      this.pendingClassRemovals.remove(name);
      return this.unregister(name);
   }

   private boolean hasClassDependents(String name) {
      LuaTable classTable = this.classes.get(name);
      if (classTable == null) return false;
      for (LuaTable instanceClass : this.instanceClasses.values()) {
         if (instanceClass == classTable) return true;
      }
      if (this.hierarchy.hasChildren(name)) return true;
      return false;
   }

   private void removePendingClassIfUnused(String name) {
      if (this.pendingClassRemovals.contains(name) && !this.hasClassDependents(name)) {
         this.pendingClassRemovals.remove(name);
         this.unregister(name);
      }
   }

   public synchronized boolean unregister(String name) {
      if (this.hasClassDependents(name)) return false;
      LuaTable removedClass = this.classes.remove(name);
      if (removedClass == null) return false;
      this.pendingClassRemovals.remove(name);
      this.registeredClassTables.remove(removedClass);
      String affectedParent = this.hierarchy.removeClass(name);
      List<String> removedMixins = List.copyOf(this.appliedMixins.getOrDefault(name, List.of()));
      this.appliedMixins.remove(name);
      if (affectedParent != null) this.removePendingClassIfUnused(affectedParent);
      for (String mixinName : removedMixins) this.removePendingMixinIfUnused(mixinName);
      return true;
   }

   public synchronized LuaTable registerFunction(LuaTable owner, String name, LuaValue function,
                                                 String description) {
      Objects.requireNonNull(owner, "owner");
      if (name == null || name.isBlank()) throw new IllegalArgumentException("Function name must be non-empty");
      if (MLuaRegistration.isReservedFunctionName(name)) {
         throw new IllegalArgumentException("Function name is reserved for MLua class metadata: " + name);
      }
      if (function == null || !function.isfunction()) throw new IllegalArgumentException("Registered function must be a Lua function");
      if (description == null || description.isBlank()) throw new IllegalArgumentException("Function description must be non-empty");
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
      if (this.hasPropertyDefinition(className, name, new HashSet<>())) {
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
      this.pendingClassRemovals.remove(className);
      owner.rawset(name, function);
      docs.rawset(name, LuaValue.valueOf(description));
      owner.rawset("__mlua_doc_methods", docs);
      this.refreshInheritedMethodDescriptionsFrom(className);
      return owner;
   }

   public synchronized LuaTable registerProperty(String className, String propertyName,
                                                 LuaValue getter, LuaValue setter, String description) {
      return this.registerProperty(className, propertyName, getter, setter, null, null, description);
   }

   public synchronized LuaTable registerProperty(String className, String propertyName,
                                                 LuaValue getter, LuaValue setter, String valueType,
                                                 String side, String description) {
      if ((valueType == null) != (side == null)) {
         throw new IllegalArgumentException("Property value type and side must be declared together");
      }
      if (valueType != null && valueType.isBlank()) throw new IllegalArgumentException("Property value type must be non-empty");
      if (side != null && side.isBlank()) throw new IllegalArgumentException("Property side must be non-empty");
      LuaTable owner = this.classes.get(className);
      if (owner == null) throw new IllegalStateException("Lua class is not registered: " + className);
      if (propertyName == null || propertyName.isBlank()) throw new IllegalArgumentException("Property name must be non-empty");
      if (MLuaRegistration.isReservedFunctionName(propertyName)) {
         throw new IllegalArgumentException("Property name is reserved for MLua class metadata: " + propertyName);
      }
      if (getter == null || !getter.isfunction()) throw new IllegalArgumentException("Property getter must be a Lua function");
      if (setter != null && !setter.isnil() && !setter.isfunction()) {
         throw new IllegalArgumentException("Property setter must be a Lua function or nil");
      }
      if (description == null || description.isBlank()) throw new IllegalArgumentException("Property description must be non-empty");
      this.ensureNoExistingInstanceField(className, propertyName);
      LuaValue existingMethod = owner.get(propertyName);
      if (!existingMethod.isnil()) {
         throw new IllegalStateException("Lua class '" + className + "' already defines member '" + propertyName + "'");
      }
      LuaValue descriptorsValue = owner.rawget("__mlua_properties");
      if (!descriptorsValue.isnil() && !descriptorsValue.istable()) {
         throw new IllegalStateException("Lua class '" + className + "' has invalid property descriptors");
      }
      LuaTable descriptors = descriptorsValue.istable()
         ? copyTableOrNil(descriptorsValue).checktable() : new LuaTable();
      LuaValue oldDescriptor = descriptorsValue.istable()
         ? descriptorsValue.checktable().rawget(propertyName) : LuaValue.NIL;
      if (oldDescriptor.istable()) {
         LuaTable previous = oldDescriptor.checktable();
         if (previous.rawget("get") != getter || previous.rawget("set") != (setter == null ? LuaValue.NIL : setter)) {
            throw new IllegalStateException("Lua class '" + className + "' already defines property '" + propertyName + "'");
         }
      } else if (!oldDescriptor.isnil()) {
         throw new IllegalStateException("Lua class '" + className + "' property '" + propertyName + "' has invalid metadata");
      }
      LuaValue docsValue = owner.rawget("__mlua_doc_properties");
      if (!docsValue.isnil() && !docsValue.istable()) {
         throw new IllegalStateException("Lua class '" + className + "' property descriptions must be a table");
      }
      LuaTable docs = docsValue.istable()
         ? copyTableOrNil(docsValue).checktable() : new LuaTable();
      LuaValue oldDescription = docsValue.istable()
         ? docsValue.checktable().rawget(propertyName) : LuaValue.NIL;
      if (oldDescription.isstring() && !description.equals(oldDescription.tojstring())) {
         throw new IllegalStateException("Lua class '" + className + "' property '" + propertyName + "' has a different description");
      }
      if (!oldDescription.isnil() && !oldDescription.isstring()) {
         throw new IllegalStateException("Lua class '" + className + "' property '" + propertyName + "' has invalid description metadata");
      }
      LuaTable descriptor = oldDescriptor.istable()
         ? copyTableOrNil(oldDescriptor).checktable() : new LuaTable();
      descriptor.rawset("get", getter);
      descriptor.rawset("set", setter == null ? LuaValue.NIL : setter);
      if (valueType != null) {
         descriptor.rawset("type", LuaValue.valueOf(valueType));
         descriptor.rawset("side", LuaValue.valueOf(side));
      }
      descriptors.rawset(propertyName, descriptor);
      docs.rawset(propertyName, LuaValue.valueOf(description));
      this.pendingClassRemovals.remove(className);
      owner.rawset("__mlua_properties", descriptors);
      owner.rawset("__mlua_doc_properties", docs);
      this.refreshAttachedPropertyDescriptionsFrom(className);
      return owner;
   }

   public synchronized boolean unregisterProperty(String className, String propertyName, LuaValue expectedGetter) {
      LuaTable owner = this.classes.get(className);
      if (owner == null || propertyName == null || expectedGetter == null) return false;
      LuaValue descriptorsValue = owner.rawget("__mlua_properties");
      if (!descriptorsValue.istable()) return false;
      LuaTable sourceDescriptors = descriptorsValue.checktable();
      LuaValue descriptorValue = sourceDescriptors.rawget(propertyName);
      if (!descriptorValue.istable() || descriptorValue.checktable().rawget("get") != expectedGetter) return false;
      LuaTable descriptors = copyTableOrNil(descriptorsValue).checktable();
      descriptors.rawset(propertyName, LuaValue.NIL);
      owner.rawset("__mlua_properties", descriptors);
      LuaValue docsValue = owner.rawget("__mlua_doc_properties");
      if (docsValue.istable()) {
         LuaTable docs = copyTableOrNil(docsValue).checktable();
         docs.rawset(propertyName, LuaValue.NIL);
         owner.rawset("__mlua_doc_properties", docs);
      }
      this.refreshAttachedPropertyDescriptionsFrom(className);
      this.removePendingClassIfUnused(className);
      return true;
   }

   private void ensureNoExistingInstanceField(String className, String propertyName) {
      for (LuaTable instance : this.instancesOfOrDescendants(className)) {
         String instanceClassName = instance.rawget("__type").optjstring("");
         if (!instance.rawget(propertyName).isnil()) {
            throw new IllegalStateException("Cannot register class property '" + propertyName
               + "': a live " + instanceClassName + " instance already has that field");
         }
      }
   }

   private void ensureNoExistingInstanceFieldShadowingMethod(String className, String methodName) {
      for (LuaTable instance : this.instancesOfOrDescendants(className)) {
         String instanceClassName = instance.rawget("__type").optjstring("");
         if (!instance.rawget(methodName).isnil() && !instance.rawget(methodName).isfunction()) {
            throw new IllegalStateException("Cannot register class method '" + methodName
               + "': a live " + instanceClassName + " instance already has a non-function field with that name");
         }
      }
   }

   private void refreshAttachedPropertyDescriptionsFrom(String changedClass) {
      for (LuaTable instance : this.instancesOfOrDescendants(changedClass)) {
         String instanceClassName = instance.rawget("__type").optjstring("");
         LuaTable descriptions = new LuaTable();
         this.collectPropertyDescriptions(instanceClassName, descriptions, new HashSet<>());
         instance.rawset("__mlua_doc_properties", descriptions);
      }
   }

   private List<LuaTable> instancesOfOrDescendants(String className) {
      List<LuaTable> instances = new ArrayList<>();
      Set<String> affectedClasses = new HashSet<>(this.hierarchy.descendantsOf(className));
      affectedClasses.add(className);
      for (String affectedClass : affectedClasses) {
         Set<LuaTable> indexed = this.instancesByClass.get(affectedClass);
         if (indexed != null) instances.addAll(indexed);
      }
      return instances;
   }

   private synchronized LuaTable findPropertyDescriptor(String className, String propertyName, Set<String> visited) {
      if (!visited.add(className)) return null;
      LuaTable owner = this.classes.get(className);
      if (owner == null) return null;
      LuaValue descriptors = owner.rawget("__mlua_properties");
      LuaValue descriptor = descriptors.istable() ? descriptors.checktable().rawget(propertyName) : LuaValue.NIL;
      if (descriptor.istable()) return descriptor.checktable();
      String parentName = this.hierarchy.parentOf(className);
      if (parentName != null) {
         LuaTable inherited = this.findPropertyDescriptor(parentName, propertyName, visited);
         if (inherited != null) return inherited;
      }
      return null;
   }

   private synchronized boolean hasPropertyDefinition(String className, String propertyName, Set<String> visited) {
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

   public synchronized boolean unregisterFunction(String className, String name, LuaValue expectedFunction) {
      LuaTable owner = this.classes.get(className);
      if (owner == null || name == null || expectedFunction == null || owner.rawget(name) != expectedFunction) return false;
      owner.rawset(name, LuaValue.NIL);
      if (name.equals("new") && owner.rawget("__mlua_generic_constructor") == expectedFunction) {
         owner.rawset("__mlua_generic_constructor", LuaValue.NIL);
      }
      LuaValue docs = owner.rawget("__mlua_doc_methods");
      if (docs.istable()) docs.checktable().rawset(name, LuaValue.NIL);
      LuaValue dispatchers = owner.rawget("__mlua_service_dispatchers");
      if (dispatchers.istable()) dispatchers.checktable().rawset(name, LuaValue.NIL);
      this.refreshInheritedMethodDescriptionsFrom(className);
      this.removePendingClassIfUnused(className);
      return true;
   }

   public synchronized LuaTable attachInstance(LuaTable instance, String className, String parentName) {
      Objects.requireNonNull(instance, "instance");
      requireClassName(className);
      LuaTable classTable = this.classes.get(className);
      if (classTable == null) {
         throw new IllegalStateException("Lua class is not registered: " + className);
      }
      if (instance == classTable) {
         throw new IllegalArgumentException("A registered Lua class table cannot also be attached as an instance");
      }
      if (instance.rawget("__class").toboolean()) {
         throw new IllegalArgumentException("A Lua class descriptor cannot be attached as a live instance");
      }
      LuaTable currentClass = this.instanceClasses.get(instance);
      if (currentClass != null && currentClass != classTable) {
         String currentName = currentClass.rawget("__type").optjstring("");
         if (!this.inheritsFrom(className, currentName)) {
            if (this.inheritsFrom(currentName, className)) {
               if (parentName != null && !parentName.isBlank()
                  && !this.isClassOrParent(className, parentName)) {
                  throw new IllegalArgumentException("Lua class '" + className
                     + "' does not inherit from requested instance parent '" + parentName + "'");
               }
               return instance;
            }
            throw new IllegalArgumentException("A live Lua instance attached as '" + currentName
               + "' cannot be reclassified as unrelated class '" + className + "'");
         }
      }
      if (parentName != null && !parentName.isBlank()) {
         if (!this.isClassOrParent(className, parentName)) {
            throw new IllegalArgumentException("Lua class '" + className
               + "' does not inherit from requested instance parent '" + parentName + "'");
         }
         this.refreshParentLookup(classTable, className);
      }
      this.ensureNoShadowedProperties(instance, className, new HashSet<>());
      this.ensureNoShadowedMethods(instance, className, new HashSet<>());
      instance.rawset("__type", LuaValue.valueOf(className));
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
      LuaValue existingMetaValue = instance.getmetatable();
      LuaTable meta = existingMetaValue != null && existingMetaValue.istable() ? existingMetaValue.checktable() : new LuaTable();
      this.composeInstanceLookup(meta, classTable);
      instance.setmetatable(meta);
      this.indexInstanceClass(instance, classTable);
      return instance;
   }

   private void indexInstanceClass(LuaTable instance, LuaTable classTable) {
      LuaTable previousClass = this.instanceClasses.put(instance, classTable);
      if (previousClass != null && previousClass != classTable) {
         String previousName = previousClass.rawget("__type").optjstring("");
         Set<LuaTable> previousInstances = this.instancesByClass.get(previousName);
         if (previousInstances != null) previousInstances.remove(instance);
      }
      String className = classTable.rawget("__type").optjstring("");
      this.instancesByClass.computeIfAbsent(className,
         ignored -> Collections.newSetFromMap(new WeakHashMap<>())).add(instance);
   }

   private void requireStableParent(String name, String declaredParent) {
      String existingParent = this.hierarchy.parentOf(name);
      if (!Objects.equals(existingParent, declaredParent)) {
         throw new IllegalStateException("Lua class '" + name + "' is already registered with superclass '"
            + (existingParent == null ? "<none>" : existingParent) + "' and cannot be redeclared with '"
            + (declaredParent == null ? "<none>" : declaredParent) + "'");
      }
   }

   private static List<String> normalizedParents(String[] parentNames) {
      if (parentNames == null || parentNames.length == 0) return List.of();
      List<String> normalized = new ArrayList<>();
      for (String parent : parentNames) {
         if (parent == null || parent.isBlank()) continue;
         if (normalized.contains(parent)) {
            throw new IllegalArgumentException("Lua class parent is listed more than once: " + parent);
         }
         normalized.add(parent);
      }
      if (normalized.size() > 1) {
         throw new IllegalArgumentException("Lua class inheritance supports one superclass; use behavior mixins for shared behavior");
      }
      return List.copyOf(normalized);
   }

   private void collectMethodDescriptions(String className, LuaTable output, Set<String> visited) {
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
      for (String mixinName : this.appliedMixins.getOrDefault(className, List.of())) {
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

   private void collectInstanceDispatchers(String className, LuaTable output, Set<String> visited) {
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
      for (String mixinName : this.appliedMixins.getOrDefault(className, List.of())) {
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

   private void refreshInheritedMethodDescriptions() {
      for (String className : this.classes.keySet()) {
         this.refreshInheritedMethodDescriptions(className);
      }
   }

   private void refreshInheritedMethodDescriptionsFrom(String changedClass) {
      this.refreshInheritedMethodDescriptions(changedClass);
      for (String descendant : this.hierarchy.descendantsOf(changedClass)) {
         this.refreshInheritedMethodDescriptions(descendant);
      }
   }

   private boolean inheritsFrom(String className, String ancestor) {
      return this.hierarchy.isAncestorOf(className, ancestor);
   }

   private boolean isClassOrParent(String className, String expectedName) {
      return this.isSubclassOf(className, expectedName);
   }

   private void refreshInheritedMethodDescriptions(String className) {
      LuaTable classTable = this.classes.get(className);
      if (classTable == null) return;
      LuaTable inherited = new LuaTable();
      Set<String> ancestry = new HashSet<>();
      ancestry.add(className);
      for (String mixinName : this.appliedMixins.getOrDefault(className, List.of())) {
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
      for (String mixinName : this.appliedMixins.getOrDefault(className, List.of())) {
         documentedMixins.rawset(mixinIndex++, LuaValue.valueOf(mixinName));
      }
      classTable.rawset("__mlua_doc_mixins", documentedMixins);
      this.refreshAttachedInstanceDescriptions(className);
   }

   private void refreshAttachedInstanceDescriptions(String className) {
      LuaTable registeredClass = this.classes.get(className);
      if (registeredClass == null) return;
      Set<LuaTable> indexedInstances = this.instancesByClass.get(className);
      if (indexedInstances == null || indexedInstances.isEmpty()) return;
      for (LuaTable instance : indexedInstances.toArray(LuaTable[]::new)) {
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

   private void collectPropertyDescriptions(String className, LuaTable output, Set<String> visited) {
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

   private void ensureNoShadowedProperties(LuaTable instance, String className, Set<String> visited) {
      if (!visited.add(className)) return;
      LuaTable owner = this.classes.get(className);
      if (owner == null) return;
      LuaValue instanceMetaValue = instance.getmetatable();
      LuaValue dynamicProperties = instanceMetaValue != null && instanceMetaValue.istable()
         ? instanceMetaValue.checktable().rawget("__mlua_doc_properties") : LuaValue.NIL;
      if (dynamicProperties.istable()) {
         LuaValue dynamicKey = LuaValue.NIL;
         while (true) {
            Varargs dynamicEntry = dynamicProperties.checktable().next(dynamicKey);
            dynamicKey = dynamicEntry.arg1();
            if (dynamicKey.isnil()) break;
            if (!dynamicKey.isstring()) continue;
            String propertyName = dynamicKey.tojstring();
            if (owner.get(dynamicKey).isfunction()) {
               throw new IllegalArgumentException("Live instance property '" + propertyName
                  + "' shadows a registered class method");
            }
            if (this.hasPropertyDefinition(className, propertyName, new HashSet<>())) {
               throw new IllegalArgumentException("Live instance property '" + propertyName
                  + "' conflicts with a registered class property");
            }
         }
      }
      LuaValue descriptorsValue = owner.rawget("__mlua_properties");
      if (descriptorsValue.istable()) {
         LuaValue key = LuaValue.NIL;
         while (true) {
            Varargs entry = descriptorsValue.checktable().next(key);
            key = entry.arg1();
            if (key.isnil()) break;
            if (!instance.rawget(key).isnil()) {
               throw new IllegalArgumentException("Live instance field shadows registered class property '" + key.tojstring() + "'");
            }
         }
      }
      String parentName = this.hierarchy.parentOf(className);
      if (parentName != null) {
         this.ensureNoShadowedProperties(instance, parentName, visited);
      }
   }

   private void ensureNoShadowedMethods(LuaTable instance, String className, Set<String> visited) {
      if (!visited.add(className)) return;
      LuaTable owner = this.classes.get(className);
      if (owner == null) return;
      LuaValue key = LuaValue.NIL;
      while (true) {
         Varargs entry = instance.next(key);
         key = entry.arg1();
         if (key.isnil()) break;
         if (key.isstring() && !entry.arg(2).isfunction() && owner.get(key).isfunction()) {
            throw new IllegalArgumentException("Live instance field '" + key.tojstring()
               + "' shadows a registered class method");
         }
      }
      String parentName = this.hierarchy.parentOf(className);
      if (parentName != null) {
         this.ensureNoShadowedMethods(instance, parentName, visited);
      }
   }

   private static LuaValue copyTableOrNil(LuaValue value) {
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

   private static boolean sameOptionalString(LuaValue value, String expected) {
      if (expected == null) return value.isnil();
      return value.isstring() && expected.equals(value.tojstring());
   }

   private record InstanceDescription(boolean inherited, String value) { }

   private static boolean hasInstanceImplementation(LuaTable instance, LuaValue key) {
      if (instance.rawget(key).isfunction()) return true;
      LuaValue metaValue = instance.getmetatable();
      if (!metaValue.istable()) return false;
      LuaValue methods = metaValue.checktable().rawget("__mlua_instance_methods");
      return methods.istable() && methods.checktable().get(key).isfunction();
   }

   private void composeInstanceLookup(LuaTable meta, LuaTable classTable) {
      LuaValue previousIndex = meta.rawget("__index");
      meta.rawset("__metatable", LuaValue.TRUE);
      LuaValue fallback = meta.rawget("__mlua_instance_fallback");
      if (!meta.rawget("__mlua_instance_lookup").toboolean()) {
         fallback = previousIndex.isnil() ? LuaValue.FALSE : previousIndex;
      }
      LuaValue instanceFallback = fallback;
      meta.rawset("__mlua_instance_fallback", instanceFallback);
      meta.rawset("__mlua_instance_lookup", LuaValue.TRUE);
      meta.rawset("__index", new VarArgFunction() {
         @Override
         public Varargs invoke(Varargs args) {
            LuaValue key = args.arg(2);
            String className = classTable.rawget("__type").optjstring("");
            if (key.isstring() && key.tojstring().equals("ClassName")) {
               return LuaValue.valueOf(className);
            }
            LuaTable property = key.isstring()
               ? MLuaClassRegistry.this.findPropertyDescriptor(className, key.tojstring(), new HashSet<>()) : null;
            if (property != null) {
               LuaValue getter = property.rawget("get");
               return getter.call(args.arg1());
            }
            LuaValue inherited = classTable.get(key);
            if (!inherited.isnil()) {
               if (inherited.isfunction()) {
                  LuaValue serviceResolver = meta.rawget("__mlua_service_resolver");
                  if (!meta.rawget("__mlua_service_instance").istable() && serviceResolver.isfunction()) {
                     serviceResolver.invoke(args.arg1());
                  }
                  LuaValue serviceDispatchers = classTable.rawget("__mlua_service_dispatchers");
                  if (classTable.rawget("__mlua_service_class").toboolean()
                     && serviceDispatchers.istable()
                     && serviceDispatchers.checktable().rawget(key).toboolean()) {
                     LuaValue dispatcher = inherited;
                     LuaTable boundInstance = args.arg1().checktable();
                     String serviceName = classTable.rawget("__mlua_service_name").optjstring("");
                     return new VarArgFunction() {
                        @Override
                        public Varargs invoke(Varargs callArgs) {
                           LuaValue first = callArgs.arg1();
                           if (first == boundInstance || isServiceReceiver(first, className, serviceName)) {
                              return dispatcher.invoke(callArgs);
                           }
                           return dispatcher.invoke(LuaValue.varargsOf(boundInstance, callArgs));
                        }
                     };
                  }
               }
               return inherited.isfunction()
                  ? MLuaClassThreadDispatcher.bind(args.arg1().checktable(), inherited)
                  : inherited;
            }
            LuaValue methodsValue = meta.rawget("__mlua_instance_methods");
            if (methodsValue.istable()) {
               LuaValue method = methodsValue.get(key);
               if (!method.isnil()) return method;
            }
            if (instanceFallback.istable()) return instanceFallback.get(args.arg(2));
            if (instanceFallback.isfunction()) return instanceFallback.invoke(args).arg1();
            return LuaValue.NIL;
         }
      });

      LuaValue previousNewIndex = meta.rawget("__newindex");
      LuaValue newIndexFallback = meta.rawget("__mlua_instance_newindex_fallback");
      if (!meta.rawget("__mlua_instance_newindex_lookup").toboolean()) {
         newIndexFallback = previousNewIndex.isnil() ? LuaValue.FALSE : previousNewIndex;
      }
      LuaValue instanceNewIndexFallback = newIndexFallback;
      meta.rawset("__mlua_instance_newindex_fallback", instanceNewIndexFallback);
      meta.rawset("__mlua_instance_newindex_lookup", LuaValue.TRUE);
      meta.rawset("__newindex", new VarArgFunction() {
         @Override
         public Varargs invoke(Varargs args) {
            LuaValue receiver = args.arg1();
            LuaValue key = args.arg(2);
            LuaValue value = args.arg(3);
            if (key.isstring()) {
               String name = key.tojstring();
               if (name.equals("__type") || name.equals("__class") || name.equals("ClassName")
                  || name.startsWith("__mlua_")) {
                  throw new LuaError("[MLua] Cannot overwrite reserved class metadata field '" + name + "'.");
               }
               if (classTable.get(key).isfunction()) {
                  throw new LuaError("[MLua] Cannot shadow registered class method '" + name
                     + "' on a live instance; register the method on its class with a description.");
               }
               LuaTable property = MLuaClassRegistry.this.findPropertyDescriptor(
                  classTable.rawget("__type").optjstring(""), name, new HashSet<>());
               if (property != null) {
                  LuaValue setter = property.rawget("set");
                  if (!setter.isfunction()) {
                     throw new LuaError("[MLua] Property '" + name + "' is read-only.");
                  }
                  setter.call(receiver, value);
                  return LuaValue.NONE;
               }
            }
            if (instanceNewIndexFallback.istable()) {
               instanceNewIndexFallback.set(key, value);
            } else if (instanceNewIndexFallback.isfunction()) {
               instanceNewIndexFallback.invoke(LuaValue.varargsOf(new LuaValue[]{receiver, key, value}));
            } else if (receiver.istable()) {
               receiver.checktable().rawset(key, value);
            }
            return LuaValue.NONE;
         }
      });
   }

   private static boolean isServiceReceiver(LuaValue value, String className, String serviceName) {
      if (!value.istable()) return false;
      LuaValue metaValue = value.getmetatable();
      if (!metaValue.istable()) return false;
      LuaTable meta = metaValue.checktable();
      if (serviceName.equals(meta.rawget("__mlua_service_name").optjstring(""))) return true;
      LuaValue type = value.rawget("__type");
      return type.isstring() && className.equals(type.tojstring());
   }

   private void refreshParentLookup(LuaTable classTable, String className) {
      String parentName = this.hierarchy.parentOf(className);
      List<String> mixinNames = this.appliedMixins.getOrDefault(className, List.of());
      LuaValue metaValue = classTable.getmetatable();
      LuaTable meta = metaValue != null && metaValue.istable() ? metaValue.checktable() : new LuaTable();
      this.installClassWriteGuard(classTable, meta);
      if (parentName == null && mixinNames.isEmpty()) {
         classTable.rawset("__mlua_doc_parents", LuaValue.NIL);
         if (meta.rawget("__mlua_parent_lookup").toboolean()) {
            LuaValue fallback = meta.rawget("__mlua_parent_fallback");
            if (fallback.isnil() || fallback == LuaValue.FALSE) meta.rawset("__index", LuaValue.NIL);
            else meta.rawset("__index", fallback);
            meta.rawset("__mlua_parent_fallback", LuaValue.NIL);
            meta.rawset("__mlua_parent_lookup", LuaValue.NIL);
         }
         if (metaValue == null || !metaValue.istable()) classTable.setmetatable(meta);
         return;
      }
      LuaValue previousIndex = meta.rawget("__index");
      LuaValue fallback = meta.rawget("__mlua_parent_fallback");
      if (!meta.rawget("__mlua_parent_lookup").toboolean()) {
         fallback = previousIndex.isnil() ? LuaValue.FALSE : previousIndex;
      }
      LuaValue parentFallback = fallback;
      List<String> orderedMixins = List.copyOf(mixinNames);
      LuaTable documentedParents = new LuaTable();
      if (parentName != null) documentedParents.rawset(1, LuaValue.valueOf(parentName));
      classTable.rawset("__mlua_doc_parents", parentName == null ? LuaValue.NIL : documentedParents);
      meta.rawset("__mlua_parent_fallback", parentFallback);
      meta.rawset("__mlua_parent_lookup", LuaValue.TRUE);
      meta.rawset("__index", new VarArgFunction() {
         @Override
         public Varargs invoke(Varargs args) {
            LuaValue key = args.arg(2);
            LuaTable inheritedParent;
            synchronized (MLuaClassRegistry.this) {
               for (String mixinName : orderedMixins) {
                  LuaTable mixin = MLuaClassRegistry.this.mixinRegistry.get(mixinName);
                  if (mixin != null) {
                     LuaValue mixed = mixin.get(key);
                     if (!mixed.isnil()) return mixed;
                  }
               }
               inheritedParent = parentName == null ? null : MLuaClassRegistry.this.classes.get(parentName);
            }
            if (inheritedParent != null) {
               LuaValue inherited = inheritedParent.get(key);
               if (!inherited.isnil()) return inherited;
            }
            if (parentFallback.istable()) return parentFallback.get(key);
            if (parentFallback.isfunction()) return parentFallback.invoke(args).arg1();
            return LuaValue.NIL;
         }
      });
      if (metaValue == null || !metaValue.istable()) {
         classTable.setmetatable(meta);
      }
   }

   private void installClassWriteGuard(LuaTable classTable, LuaTable meta) {
      LuaValue fallback = meta.rawget("__mlua_class_newindex_fallback");
      if (!meta.rawget("__mlua_class_newindex").toboolean()) {
         LuaValue previous = meta.rawget("__newindex");
         fallback = previous.isnil() ? LuaValue.FALSE : previous;
      }
      LuaValue classFallback = fallback;
      meta.rawset("__mlua_class_newindex_fallback", classFallback);
      meta.rawset("__mlua_class_newindex", LuaValue.TRUE);
      meta.rawset("__newindex", new VarArgFunction() {
         @Override
         public Varargs invoke(Varargs args) {
            LuaValue key = args.arg(2);
            LuaValue value = args.arg(3);
            if (key.isstring()) {
               String fieldName = key.tojstring();
               if (fieldName.equals("__type") || fieldName.equals("__class") || fieldName.equals("ClassName")
                   || fieldName.startsWith("__mlua_")) {
                  throw new LuaError("[MLua] Cannot assign class metadata field '" + fieldName + "'.");
               }
               if (value.isfunction()) {
                  throw new LuaError("[MLua] Register class function '" + fieldName
                     + "' with Minecraft:RegisterClassFunction and a non-empty description.");
               }
            }
            if (classFallback.istable()) classFallback.set(key, value);
            else if (classFallback.isfunction()) classFallback.invoke(args);
            else classTable.rawset(key, value);
            return LuaValue.NONE;
         }
      });
   }

   public synchronized boolean isA(LuaValue value, String targetName) {
      if (value == null || !value.istable() || targetName == null || targetName.isBlank()) {
         return false;
      }
      String actualName = this.classNameOf(value);
      return actualName != null && this.isSubclassOf(actualName, targetName);
   }

   /** Checks the registered single-parent class graph without allocating a traversal set. */
   public synchronized boolean isSubclassOf(String className, String parentName) {
      if (className == null || className.isBlank() || parentName == null || parentName.isBlank()) return false;
      return className.equals(parentName) || this.inheritsFrom(className, parentName);
   }

   public synchronized String classNameOf(LuaValue value) {
      if (value == null || !value.istable()) return null;
      LuaTable table = value.checktable();
      if (this.registeredClassTables.contains(table)) return null;
      LuaTable registered = this.instanceClasses.get(table);
      if (registered != null) {
         String registeredName = registered.rawget("__type").optjstring("");
         return this.classes.get(registeredName) == registered ? registeredName : null;
      }
      if (table.rawget("__class").toboolean()) return null;
      LuaValue declared = table.rawget("__type");
      return declared.isstring() && this.classes.get(declared.tojstring()) == table
         ? declared.tojstring() : null;
   }

   private void recordParent(String child, String parent) {
      if (wouldCreateCycle(child, parent)) {
         throw new IllegalStateException("Lua class inheritance cycle: " + child + " -> " + parent);
      }
      this.pendingClassRemovals.remove(child);
      this.pendingClassRemovals.remove(parent);
      String existing = this.hierarchy.registerParent(child, parent);
      if (existing != null && !existing.equals(parent)) {
         throw new IllegalStateException("Lua class '" + child + "' already has superclass '" + existing + "'");
      }
   }

   private boolean wouldCreateCycle(String child, String parent) {
      return this.hierarchy.wouldCreateCycle(child, parent);
   }

   private void requireParent(String parentName) {
      if (parentName != null && !parentName.isBlank() && !this.classes.containsKey(parentName)) {
         throw new IllegalStateException("Lua parent class is not registered: " + parentName);
      }
   }

   static void requireClassName(String name) {
      if (name == null || name.isBlank()) {
         throw new IllegalArgumentException("Lua class name must be non-empty");
      }
   }
}
