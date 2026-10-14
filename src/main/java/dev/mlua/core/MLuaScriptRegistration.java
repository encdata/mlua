package dev.mlua.core;

import dev.mlua.module.MLuaRegistration;
import dev.mlua.module.MLuaDocBuilder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/** Tracks the registry contributions made by one Lua registry script. */
final class MLuaScriptRegistration implements MLuaRegistration {
   private final MLuaRegistration delegate;
   private final List<String> classes = new ArrayList<>();
   private final List<String> mixins = new ArrayList<>();
   private final List<MixinApplication> applications = new ArrayList<>();
   private final List<ClassFunction> classFunctions = new ArrayList<>();
   private final List<ClassProperty> classProperties = new ArrayList<>();
   private final List<MixinFunction> mixinFunctions = new ArrayList<>();
   private final List<ContentType> contentTypes = new ArrayList<>();
   private final List<MLuaDocBuilder.Contribution> documentation = new ArrayList<>();
   private final List<Runnable> rollbackActions = new ArrayList<>();
   private final Map<String, LuaValue> previousClassDescriptions = new LinkedHashMap<>();
   private final Map<String, LuaValue> previousInstanceBaseRoles = new LinkedHashMap<>();
   private final Map<String, LuaValue> previousServiceBaseRoles = new LinkedHashMap<>();
   private final Map<String, ServiceClassMetadata> previousServiceClasses = new LinkedHashMap<>();

   MLuaScriptRegistration(MLuaRegistration delegate) {
      this.delegate = java.util.Objects.requireNonNull(delegate, "delegate");
   }

   @Override public void onRegistrationRollback(Runnable cleanup) {
      rollbackActions.add(java.util.Objects.requireNonNull(cleanup, "cleanup"));
   }

   @Override public void registerDocumentation(MLuaDocBuilder.Contribution contribution) {
      delegate.registerDocumentation(contribution);
      documentation.add(contribution);
   }
   @Override public boolean unregisterDocumentation(String id, String owner) {
      boolean removed = delegate.unregisterDocumentation(id, owner);
      if (removed) documentation.removeIf(entry -> entry.id().equals(id) && entry.owner().equals(owner));
      return removed;
   }

   @Override
   public LuaTable registerClass(String name, String parentName) {
      boolean added = delegate.classTable(name) == null;
      LuaTable type = delegate.registerClass(name, parentName);
      if (added) classes.add(name);
      return type;
   }

   @Override
   public LuaTable registerClass(String name, String parentName, String description) {
      this.snapshotClassDescription(name);
      return MLuaRegistration.super.registerClass(name, parentName, description);
   }

   @Override
   public LuaTable registerClass(String name, LuaTable classTable, String... parentNames) {
      boolean added = delegate.classTable(name) == null;
      LuaTable type = delegate.registerClass(name, classTable, parentNames);
      if (added) classes.add(name);
      return type;
   }

   @Override
   public LuaTable registerClass(String name, LuaTable classTable, List<String> parentNames,
                                 String description) {
      this.snapshotClassDescription(name);
      return MLuaRegistration.super.registerClass(name, classTable, parentNames, description);
   }

   @Override
   public LuaTable registerServiceClass(String serviceName, String description) {
      return this.registerServiceClass(serviceName, delegate.serviceClassName(serviceName), description);
   }

   @Override
   public LuaTable registerServiceClass(String serviceName, String className, String description) {
      this.snapshotClassDescription(className);
      LuaTable type = delegate.classTable(className);
      if (type != null) {
         this.previousServiceClasses.putIfAbsent(className, new ServiceClassMetadata(
            type.rawget("__mlua_service_class"), type.rawget("__mlua_service_name")));
      }
      return MLuaRegistration.super.registerServiceClass(serviceName, className, description);
   }

   @Override
   public LuaTable registerInstanceBaseClass(String name, String parentName, String description) {
      LuaTable type = delegate.classTable(name);
      if (type != null) this.previousInstanceBaseRoles.putIfAbsent(name, type.rawget("__mlua_role_instance_base"));
      return MLuaRegistration.super.registerInstanceBaseClass(name, parentName, description);
   }

   @Override
   public LuaTable registerServiceBaseClass(String name, String parentName, String description) {
      LuaTable type = delegate.classTable(name);
      if (type != null) this.previousServiceBaseRoles.putIfAbsent(name, type.rawget("__mlua_role_service_base"));
      return MLuaRegistration.super.registerServiceBaseClass(name, parentName, description);
   }

   private void snapshotClassDescription(String className) {
      if (this.previousClassDescriptions.containsKey(className)) return;
      LuaTable type = delegate.classTable(className);
      if (type != null) this.previousClassDescriptions.put(className, type.rawget("__mlua_doc_description"));
   }

   @Override public LuaTable classTable(String name) { return delegate.classTable(name); }
   @Override public void registerContentType(String domain, String owner, String name, String base,
                                             String description, LuaTable defaults) {
      delegate.registerContentType(domain, owner, name, base, description, defaults);
      contentTypes.add(new ContentType(domain, owner, name));
   }
   @Override public LuaTable resolveContentType(String domain, String owner, LuaTable properties) {
      return delegate.resolveContentType(domain, owner, properties);
   }
   @Override public Map<String, String> contentTypes(String domain, String owner) {
      return delegate.contentTypes(domain, owner);
   }
   @Override public boolean unregisterContentType(String domain, String owner, String name) {
      boolean removed = delegate.unregisterContentType(domain, owner, name);
      if (removed) contentTypes.remove(new ContentType(domain, owner, name));
      return removed;
   }
   @Override public java.util.Set<String> classNames() { return delegate.classNames(); }
   @Override public String classDescription(String name) { return delegate.classDescription(name); }
   @Override public String classMethodDescription(String type, String method) { return delegate.classMethodDescription(type, method); }
   @Override public void refreshClassMetadata(String name) { delegate.refreshClassMetadata(name); }
   @Override public boolean unregisterClassIfUnused(String name) {
      boolean removed = delegate.unregisterClassIfUnused(name);
      if (removed) classes.remove(name);
      return removed;
   }
   @Override public boolean isInstanceOf(LuaValue instance, String name) { return delegate.isInstanceOf(instance, name); }
   @Override public String classNameOf(LuaValue instance) { return delegate.classNameOf(instance); }
   @Override public LuaTable attachClassInstance(LuaTable instance, String name, String parent) {
      return delegate.attachClassInstance(instance, name, parent);
   }

   @Override
   public LuaTable registerFunction(LuaTable owner, String name, LuaValue function, String description) {
      LuaValue previous = owner.rawget(name);
      if (!previous.isnil() && previous != function) {
         throw new IllegalStateException("Registry script cannot replace existing class function '" + name + "'");
      }
      LuaTable registered = delegate.registerFunction(owner, name, function, description);
      LuaValue type = owner.rawget("__type");
      if (previous.isnil() && type.isstring()) {
         classFunctions.add(new ClassFunction(type.tojstring(), name, function));
      }
      return registered;
   }

   @Override
   public boolean unregisterClassFunction(String className, String functionName, LuaValue expectedFunction) {
      ClassFunction contribution = new ClassFunction(className, functionName, expectedFunction);
      if (!classFunctions.contains(contribution)) return false;
      boolean removed = delegate.unregisterClassFunction(className, functionName, expectedFunction);
      if (removed) classFunctions.remove(contribution);
      return removed;
   }

   @Override
   public LuaTable registerClassProperty(String className, String propertyName,
                                         LuaValue getter, LuaValue setter, String description) {
      LuaTable type = delegate.classTable(className);
      LuaValue properties = type == null ? LuaValue.NIL : type.rawget("__mlua_properties");
      boolean added = !properties.istable() || properties.checktable().rawget(propertyName).isnil();
      LuaTable registered = delegate.registerClassProperty(className, propertyName, getter, setter, description);
      if (added) classProperties.add(new ClassProperty(className, propertyName, getter));
      return registered;
   }

   @Override
   public boolean unregisterClassProperty(String className, String propertyName, LuaValue expectedGetter) {
      ClassProperty contribution = new ClassProperty(className, propertyName, expectedGetter);
      if (!classProperties.contains(contribution)) return false;
      boolean removed = delegate.unregisterClassProperty(className, propertyName, expectedGetter);
      if (removed) classProperties.remove(contribution);
      return removed;
   }

   @Override
   public LuaTable registerBehaviorMixin(String name, String description) {
      boolean added = delegate.behaviorMixinTable(name) == null;
      LuaTable mixin = delegate.registerBehaviorMixin(name, description);
      if (added) mixins.add(name);
      return mixin;
   }

   @Override public LuaTable behaviorMixinTable(String name) { return delegate.behaviorMixinTable(name); }
   @Override public java.util.Set<String> behaviorMixinNames() { return delegate.behaviorMixinNames(); }

   @Override
   public LuaTable registerBehaviorMixinFunction(String mixinName, String functionName,
                                                 LuaValue function, String description) {
      LuaTable mixin = delegate.behaviorMixinTable(mixinName);
      LuaValue previous = mixin == null ? LuaValue.NIL : mixin.get(functionName);
      LuaTable registered = delegate.registerBehaviorMixinFunction(mixinName, functionName, function, description);
      if (previous.isnil()) mixinFunctions.add(new MixinFunction(mixinName, functionName, function));
      return registered;
   }

   @Override
   public boolean unregisterBehaviorMixinFunction(String mixinName, String functionName, LuaValue expectedFunction) {
      MixinFunction contribution = new MixinFunction(mixinName, functionName, expectedFunction);
      if (!mixinFunctions.contains(contribution)) return false;
      boolean removed = delegate.unregisterBehaviorMixinFunction(mixinName, functionName, expectedFunction);
      if (removed) mixinFunctions.remove(contribution);
      return removed;
   }

   @Override
   public void applyBehaviorMixin(String className, String mixinName) {
      LuaTable type = delegate.classTable(className);
      LuaValue documented = type == null ? LuaValue.NIL : type.rawget("__mlua_doc_mixins");
      boolean alreadyApplied = false;
      if (documented.istable()) {
         LuaValue key = LuaValue.NIL;
         while (true) {
            org.luaj.vm2.Varargs entry = documented.checktable().next(key);
            key = entry.arg1();
            if (key.isnil()) break;
            if (mixinName.equals(entry.arg(2).optjstring(""))) alreadyApplied = true;
         }
      }
      if (alreadyApplied) return;
      delegate.applyBehaviorMixin(className, mixinName);
      applications.add(new MixinApplication(className, mixinName));
   }

   @Override public boolean removeBehaviorMixin(String className, String mixinName) {
      MixinApplication application = new MixinApplication(className, mixinName);
      if (!applications.contains(application)) return false;
      boolean removed = delegate.removeBehaviorMixin(className, mixinName);
      if (removed) applications.remove(application);
      return removed;
   }
   @Override public boolean unregisterBehaviorMixin(String mixinName) {
      if (!mixins.contains(mixinName)) return false;
      boolean removed = delegate.unregisterBehaviorMixin(mixinName);
      if (removed) mixins.remove(mixinName);
      return removed;
   }
   @Override public boolean unregisterClass(String name) {
      if (!classes.contains(name)) return false;
      boolean removed = delegate.unregisterClass(name);
      if (removed) classes.remove(name);
      return removed;
   }

   void rollback() {
      for (int index = rollbackActions.size() - 1; index >= 0; index--) rollbackActions.get(index).run();
      for (int index = documentation.size() - 1; index >= 0; index--) {
         MLuaDocBuilder.Contribution entry = documentation.get(index);
         delegate.unregisterDocumentation(entry.id(), entry.owner());
      }
      for (int index = applications.size() - 1; index >= 0; index--) {
         MixinApplication value = applications.get(index);
         delegate.removeBehaviorMixin(value.className(), value.mixinName());
      }
      for (int index = classFunctions.size() - 1; index >= 0; index--) {
         ClassFunction value = classFunctions.get(index);
         delegate.unregisterClassFunction(value.className(), value.name(), value.function());
      }
      for (int index = classProperties.size() - 1; index >= 0; index--) {
         ClassProperty value = classProperties.get(index);
         delegate.unregisterClassProperty(value.className(), value.name(), value.getter());
      }
      for (int index = mixinFunctions.size() - 1; index >= 0; index--) {
         MixinFunction value = mixinFunctions.get(index);
         delegate.unregisterBehaviorMixinFunction(value.mixinName(), value.name(), value.function());
      }
      previousClassDescriptions.forEach((className, description) -> {
         LuaTable type = delegate.classTable(className);
         if (type != null) {
            type.rawset("__mlua_doc_description", description);
            delegate.refreshClassMetadata(className);
         }
      });
      for (int index = classes.size() - 1; index >= 0; index--) {
         delegate.unregisterClassIfUnused(classes.get(index));
      }
      previousServiceBaseRoles.forEach((className, role) -> {
         LuaTable type = delegate.classTable(className);
         if (type != null && !classes.contains(className)) {
            type.rawset("__mlua_role_service_base", role);
            delegate.refreshClassMetadata(className);
         }
      });
      previousInstanceBaseRoles.forEach((className, role) -> {
         LuaTable type = delegate.classTable(className);
         if (type != null && !classes.contains(className)) {
            type.rawset("__mlua_role_instance_base", role);
            delegate.refreshClassMetadata(className);
         }
      });
      previousServiceClasses.forEach((className, metadata) -> {
         LuaTable type = delegate.classTable(className);
         if (type != null && !classes.contains(className)) {
            type.rawset("__mlua_service_class", metadata.classMarker());
            type.rawset("__mlua_service_name", metadata.serviceName());
            delegate.refreshClassMetadata(className);
         }
      });
      for (int index = mixins.size() - 1; index >= 0; index--) delegate.unregisterBehaviorMixin(mixins.get(index));
      for (int index = contentTypes.size() - 1; index >= 0; index--) {
         ContentType type = contentTypes.get(index);
         delegate.unregisterContentType(type.domain(), type.owner(), type.name());
      }
   }

   private record ClassFunction(String className, String name, LuaValue function) { }
   private record ClassProperty(String className, String name, LuaValue getter) { }
   private record MixinFunction(String mixinName, String name, LuaValue function) { }
   private record MixinApplication(String className, String mixinName) { }
   private record ServiceClassMetadata(LuaValue classMarker, LuaValue serviceName) { }
   private record ContentType(String domain, String owner, String name) { }
}
