package dev.mlua.core;

import java.util.Map;

import dev.mlua.core.classes.MLuaClassRegistry;
import dev.mlua.core.service.MLuaServiceRegistry;
import dev.mlua.module.MLuaRegistration;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/** Keeps Java service registrations and their Lua class contracts in sync. */
final class MLuaRuntimeServiceClasses {
   private final MLuaServiceRegistry services;
   private final MLuaClassRegistry classes;
   private final MLuaRegistration registration;

   MLuaRuntimeServiceClasses(MLuaServiceRegistry services, MLuaClassRegistry classes,
                             MLuaRegistration registration) {
      this.services = services;
      this.classes = classes;
      this.registration = registration;
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
