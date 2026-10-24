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
   private final Set<String> pendingClassRemovals = new HashSet<>();
   private final MLuaClassLiveInstances liveInstances = new MLuaClassLiveInstances();
   private final MLuaClassMetadata metadata;
   private final MLuaClassInstanceLookup instanceLookup;
   private final MLuaClassMixinApplications mixinApplications;

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
      this.mixinApplications = new MLuaClassMixinApplications(new MLuaClassMixinApplications.Host() {
         @Override public boolean classExists(String name) { return MLuaClassRegistry.this.classes.containsKey(name); }
         @Override public Set<String> classNames() { return Set.copyOf(MLuaClassRegistry.this.classes.keySet()); }
         @Override public LuaTable classTable(String name) { return MLuaClassRegistry.this.classes.get(name); }
         @Override public boolean inheritsFrom(String className, String ancestor) {
            return MLuaClassRegistry.this.inheritsFrom(className, ancestor);
         }
         @Override public boolean hasPropertyDefinition(String className, String propertyName) {
            return MLuaClassRegistry.this.metadata.hasPropertyDefinition(className, propertyName, new HashSet<>());
         }
         @Override public void ensureNoExistingInstanceFieldShadowingMethod(String className, String methodName) {
            MLuaClassRegistry.this.ensureNoExistingInstanceFieldShadowingMethod(className, methodName);
         }
         @Override public void refreshParentLookup(String className) {
            LuaTable type = MLuaClassRegistry.this.classes.get(className);
            if (type != null) MLuaClassRegistry.this.refreshParentLookup(type, className);
         }
         @Override public void refreshInheritedMethodDescriptions(String className) {
            MLuaClassRegistry.this.metadata.refreshInheritedMethodDescriptionsFrom(className);
         }
         @Override public void refreshAllInheritedMethodDescriptions() {
            MLuaClassRegistry.this.metadata.refreshInheritedMethodDescriptions();
         }
         @Override public void markClassActive(String className) {
            MLuaClassRegistry.this.pendingClassRemovals.remove(className);
         }
         @Override public void removePendingClassIfUnused(String className) {
            MLuaClassRegistry.this.removePendingClassIfUnused(className);
         }
         @Override public void cleanupPendingClasses() {
            MLuaClassRegistry.this.cleanupPendingClassRemovals();
         }
      }, this.mixinRegistry, this.hierarchy);
      this.metadata = new MLuaClassMetadata(this, this.classes, this.hierarchy, this.mixinRegistry,
         this.mixinApplications, this.liveInstances);
      this.instanceLookup = new MLuaClassInstanceLookup(this.metadata);
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
         this.metadata.refreshInheritedMethodDescriptionsFrom(name);
         return existing;
      }

      LuaTable classTable = new LuaTable();
      this.pendingClassRemovals.remove(name);
      classTable.rawset("__type", LuaValue.valueOf(name));
      classTable.rawset("ClassName", LuaValue.valueOf(name));
      classTable.rawset("__class", LuaValue.TRUE);
      this.metadata.invalidatePropertyLookupsFrom(name);
      this.classes.put(name, classTable);
      this.registeredClassTables.add(classTable);
      if (parentName != null) this.recordParent(name, parentName);
      this.refreshParentLookup(classTable, name);
      this.metadata.refreshInheritedMethodDescriptionsFrom(name);
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
      if (this.liveInstances.contains(classTable)) {
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
      this.metadata.invalidatePropertyLookupsFrom(name);
      this.classes.put(name, classTable);
      this.registeredClassTables.add(classTable);
      if (parentName != null) this.recordParent(name, parentName);
      this.refreshParentLookup(classTable, name);
      this.metadata.refreshInheritedMethodDescriptionsFrom(name);
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
      this.mixinApplications.refreshClassesUsingMixin(mixinName);
   }

   private void validateMixinMethodAvailability(String mixinName, String functionName) {
      this.mixinApplications.validateMethodRegistration(mixinName, functionName);
   }

   public synchronized void applyMixin(String className, String mixinName) {
      this.mixinApplications.apply(className, mixinName);
   }

   public synchronized boolean removeMixin(String mixinName) {
      return this.mixinApplications.remove(mixinName);
   }

   public synchronized boolean hasMixin(String className, String mixinName) {
      return this.mixinApplications.has(className, mixinName);
   }

   public synchronized boolean removeMixin(String className, String mixinName) {
      return this.mixinApplications.remove(className, mixinName);
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
      this.metadata.refreshInheritedMethodDescriptionsFrom(className);
      this.metadata.refreshAttachedPropertyDescriptionsFrom(className);
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
      if (this.liveInstances.hasInstances(name)) return true;
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
      this.liveInstances.removeClass(name);
      this.metadata.invalidatePropertyLookupsFrom(name);
      String affectedParent = this.hierarchy.removeClass(name);
      this.mixinApplications.removeClass(name);
      if (affectedParent != null) this.removePendingClassIfUnused(affectedParent);
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
      this.pendingClassRemovals.remove(className);
      owner.rawset(name, function);
      docs.rawset(name, LuaValue.valueOf(description));
      owner.rawset("__mlua_doc_methods", docs);
      this.metadata.refreshInheritedMethodDescriptionsFrom(className);
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
         ? MLuaClassMetadata.copyTableOrNil(descriptorsValue).checktable() : new LuaTable();
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
         ? MLuaClassMetadata.copyTableOrNil(docsValue).checktable() : new LuaTable();
      LuaValue oldDescription = docsValue.istable()
         ? docsValue.checktable().rawget(propertyName) : LuaValue.NIL;
      if (oldDescription.isstring() && !description.equals(oldDescription.tojstring())) {
         throw new IllegalStateException("Lua class '" + className + "' property '" + propertyName + "' has a different description");
      }
      if (!oldDescription.isnil() && !oldDescription.isstring()) {
         throw new IllegalStateException("Lua class '" + className + "' property '" + propertyName + "' has invalid description metadata");
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
      this.pendingClassRemovals.remove(className);
      owner.rawset("__mlua_properties", descriptors);
      owner.rawset("__mlua_doc_properties", docs);
      this.metadata.refreshAttachedPropertyDescriptionsFrom(className);
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
      this.removePendingClassIfUnused(className);
      return true;
   }

   private void ensureNoExistingInstanceField(String className, String propertyName) {
      for (LuaTable instance : this.liveInstances.ofClassAndDescendants(
            className, this.hierarchy.descendantsOf(className))) {
         String instanceClassName = instance.rawget("__type").optjstring("");
         if (!instance.rawget(propertyName).isnil()) {
            throw new IllegalStateException("Cannot register class property '" + propertyName
               + "': a live " + instanceClassName + " instance already has that field");
         }
      }
   }

   private void ensureNoExistingInstanceFieldShadowingMethod(String className, String methodName) {
      for (LuaTable instance : this.liveInstances.ofClassAndDescendants(
            className, this.hierarchy.descendantsOf(className))) {
         String instanceClassName = instance.rawget("__type").optjstring("");
         if (!instance.rawget(methodName).isnil() && !instance.rawget(methodName).isfunction()) {
            throw new IllegalStateException("Cannot register class method '" + methodName
               + "': a live " + instanceClassName + " instance already has a non-function field with that name");
         }
      }
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
      this.metadata.refreshInheritedMethodDescriptionsFrom(className);
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
      LuaTable currentClass = this.liveInstances.classOf(instance);
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
      this.metadata.attachInstanceDocumentation(instance, className, classTable);
      LuaValue existingMetaValue = instance.getmetatable();
      LuaTable meta = existingMetaValue != null && existingMetaValue.istable() ? existingMetaValue.checktable() : new LuaTable();
      this.composeInstanceLookup(meta, classTable);
      instance.setmetatable(meta);
      this.liveInstances.attach(instance, classTable);
      return instance;
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

   private boolean inheritsFrom(String className, String ancestor) {
      return this.hierarchy.isAncestorOf(className, ancestor);
   }

   private boolean isClassOrParent(String className, String expectedName) {
      return this.isSubclassOf(className, expectedName);
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
            if (this.metadata.hasPropertyDefinition(className, propertyName, new HashSet<>())) {
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

   private void composeInstanceLookup(LuaTable meta, LuaTable classTable) {
      this.instanceLookup.compose(meta, classTable);
   }

   private void refreshParentLookup(LuaTable classTable, String className) {
      String parentName = this.hierarchy.parentOf(className);
      List<String> mixinNames = this.mixinApplications.directlyApplied(className);
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
      LuaTable registered = this.liveInstances.classOf(table);
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
