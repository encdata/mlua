package dev.mlua.core;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import dev.mlua.common.enums.Side;
import dev.mlua.core.classes.MLuaClassRegistry;
import dev.mlua.core.service.MLuaService;
import dev.mlua.core.service.MLuaServiceRegistry;
import dev.mlua.module.MLuaRegistration;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/** Keeps Java service registrations and their Lua class contracts in sync. */
final class MLuaRuntimeServiceRegistration {
   private final MLuaServiceRegistry services;
   private final MLuaClassRegistry classes;
   private final MLuaRegistration registration;

   MLuaRuntimeServiceRegistration(MLuaServiceRegistry services, MLuaClassRegistry classes,
                                  MLuaRegistration registration) {
      this.services = services;
      this.classes = classes;
      this.registration = registration;
   }

   void register(String name, String className, Side side,
                 Function<MLuaService.Context, MLuaService> factory,
                 String description, Map<String, String> functionDescriptions) {
      if (className != null && className.isBlank()) {
         throw new IllegalArgumentException("Service class name must be non-empty");
      }
      String existingClassName = this.services.explicitClassName(name);
      String recordedClassName = existingClassName != null
         ? existingClassName : this.registeredClassName(name);
      if (recordedClassName != null && className != null && !recordedClassName.equals(className)) {
         throw new IllegalStateException("Service '" + name + "' is already assigned to Lua class '"
            + recordedClassName + "'");
      }
      String selectedClassName = className != null ? className : recordedClassName != null ? recordedClassName : name;
      LuaTable namedClass = this.classes.get(selectedClassName);
      if (namedClass != null && (!namedClass.rawget("__mlua_service_class").optboolean(false)
         || !name.equals(namedClass.rawget("__mlua_service_name").optjstring("")))) {
         throw new IllegalStateException("Service '" + name + "' conflicts with Lua class '" + selectedClassName
            + "'; register it with a distinct explicit service class name");
      }
      MLuaServiceRegistry.Registration service = new MLuaServiceRegistry.Registration(
         name, selectedClassName, factory, description, functionDescriptions);
      this.services.validateBatch(side, List.of(service));
      this.validate(selectedClassName, description, service.functionDescriptions());
      boolean addedClassName = existingClassName == null;
      try {
         this.services.registerBatch(side, List.of(service));
         if (addedClassName) this.services.assignClassName(name, selectedClassName);
         this.registerIfAvailable(name, selectedClassName);
      } catch (RuntimeException | Error failure) {
         this.services.unregisterBatch(side, List.of(service));
         if (addedClassName) this.services.clearClassName(name, selectedClassName);
         throw failure;
      }
   }

   String registeredClassName(String serviceName) {
      String found = null;
      for (String candidate : this.classes.names()) {
         LuaTable type = this.classes.get(candidate);
         if (type == null) continue;
         LuaValue marker = type.rawget("__mlua_service_class");
         if (marker.isnil() || !marker.optboolean(false)
            || !serviceName.equals(type.rawget("__mlua_service_name").optjstring(""))) continue;
         if (found != null && !found.equals(candidate)) {
            throw new IllegalStateException("Service '" + serviceName + "' has multiple registered classes");
         }
         found = candidate;
      }
      return found;
   }

   void validate(String className, String description, Map<String, String> functionDescriptions) {
      String serviceBaseClassName = this.registration.serviceBaseClassName();
      if (serviceBaseClassName == null) return;

      LuaTable serviceClass = this.classes.get(className);
      LuaValue existingDescription = serviceClass == null
         ? LuaValue.NIL : serviceClass.rawget("__mlua_doc_description");
      if (!existingDescription.isnil() && !existingDescription.isstring()) {
         throw new IllegalStateException("Lua service class '" + className + "' has invalid description metadata");
      }
      if (description != null && !description.isBlank() && existingDescription.isstring()
         && !description.equals(existingDescription.tojstring())) {
         throw new IllegalStateException("Lua service class '" + className
            + "' is already registered with a different description");
      }

      LuaValue docsValue = serviceClass == null ? LuaValue.NIL : serviceClass.rawget("__mlua_doc_methods");
      if (!docsValue.isnil() && !docsValue.istable()) {
         throw new IllegalStateException("Lua service class '" + className + "' method descriptions must be a table");
      }
      LuaTable docs = docsValue.istable() ? docsValue.checktable() : null;
      String inheritedOwner = serviceClass == null ? serviceBaseClassName : className;
      functionDescriptions.forEach((methodName, methodDescription) -> {
         LuaValue existingMethod = serviceClass == null ? LuaValue.NIL : serviceClass.rawget(methodName);
         if (!existingMethod.isnil() && !existingMethod.isfunction()) {
            throw new IllegalStateException("Lua service class '" + className
               + "' already defines non-function member '" + methodName + "'");
         }
         if (this.registration.hasClassProperty(inheritedOwner, methodName)) {
            throw new IllegalStateException("Lua service class '" + className
               + "' cannot register function '" + methodName + "' because it is an inherited property");
         }
         LuaValue existingMethodDescription = docs == null ? LuaValue.NIL : docs.rawget(methodName);
         if (!existingMethodDescription.isnil() && !existingMethodDescription.isstring()) {
            throw new IllegalStateException("Lua service class '" + className + "' function '" + methodName
               + "' has invalid description metadata");
         }
         if (existingMethodDescription.isstring()
            && !methodDescription.equals(existingMethodDescription.tojstring())) {
            throw new IllegalStateException("Lua service class '" + className + "' function '" + methodName
               + "' is already registered with a different description");
         }
      });
   }

   void registerIfAvailable(String serviceName, String className) {
      if (this.registration.serviceBaseClassName() == null) return;
      MLuaServiceRegistry.ServiceMetadata metadata = this.services.metadata(serviceName);
      if (metadata == null) return;
      String selectedClassName = metadata.className() == null ? className : metadata.className();
      this.registration.registerServiceClass(serviceName, selectedClassName,
         metadata.description(), metadata.functionDescriptions());
   }
}
