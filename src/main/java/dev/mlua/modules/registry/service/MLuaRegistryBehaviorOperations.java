package dev.mlua.modules.registry.service;

import dev.mlua.api.MLuaLuaErrors;
import java.util.HashMap;
import java.util.Map;

import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;

import dev.mlua.core.engine.MLuaLuaBindings;
import dev.mlua.minecraft.scripted.MLuaBehaviorRegistry;
import dev.mlua.module.MLuaRegistration;

/** Owns behavior, AI, container, and content-class registration rollback. */
final class MLuaRegistryBehaviorOperations {
   private final MLuaRegistryService service;

   MLuaRegistryBehaviorOperations(MLuaRegistryService service) {
      this.service = service;
   }

   void replaceBehaviors(String domain, String key, Map<String, LuaValue> requested) {
      Map<String, LuaValue> callbacks = new HashMap<>();
      if (requested != null) requested.forEach((name, callback) -> {
         if (name != null && !name.isBlank() && callback != null && !callback.isnil()) callbacks.put(name, callback);
      });
      MLuaRegistration registration = service.apiContext().mlua().registration();
      dev.mlua.core.MLuaContext callbackContext = MLuaLuaBindings.activeContext(registration);
      if (callbackContext == null) callbackContext = MLuaLuaBindings.boundContext(service.apiContext().mlua().globals());
      final dev.mlua.core.MLuaContext ownerContext = callbackContext;
      MLuaBehaviorRegistry.Snapshot previous = MLuaBehaviorRegistry.snapshotBehaviors(domain, key, ownerContext);
      MLuaBehaviorRegistry.Snapshot installed =
         MLuaBehaviorRegistry.registerBehaviorsTracked(domain, key, callbacks, ownerContext);
      registration.trackRollback(() ->
         MLuaBehaviorRegistry.restoreBehaviorsTracked(domain, key, installed, previous, ownerContext));
   }

   void setEntityAIInterval(String key, int interval) {
      dev.mlua.core.MLuaContext context = MLuaLuaBindings.activeContext(service.apiContext().mlua().registration());
      if (context == null) context = MLuaLuaBindings.boundContext(service.apiContext().mlua().globals());
      dev.mlua.core.MLuaContext installedContext = context;
      Object previousRegistration = MLuaBehaviorRegistry.getEntityAIIntervalRegistration(key, installedContext);
      Object installedRegistration = MLuaBehaviorRegistry.registerEntityAIIntervalTracked(
         key, interval, installedContext);
      service.apiContext().mlua().registration().trackRollback(
         () -> MLuaBehaviorRegistry.restoreEntityAIIntervalTracked(
            key, installedRegistration, previousRegistration, installedContext));
   }

   void replaceBlockContainer(MLuaRegistration registration, String key,
                              MLuaBehaviorRegistry.ContainerDefinition requested) {
      dev.mlua.core.MLuaContext installedContext = MLuaLuaBindings.activeContext(registration);
      if (installedContext == null) {
         installedContext = MLuaLuaBindings.boundContext(service.apiContext().mlua().globals());
      }
      MLuaBehaviorRegistry.ContainerDefinition previous = MLuaBehaviorRegistry.getBlockContainer(key, installedContext);
      dev.mlua.core.MLuaContext previousContext = installedContext;
      MLuaBehaviorRegistry.ContainerDefinition installed = null;
      if (requested != null) {
         try {
            installed = MLuaBehaviorRegistry.registerBlockContainer(key, requested, installedContext);
         } catch (IllegalArgumentException | IllegalStateException invalid) {
            throw MLuaLuaErrors.javaFailure("Registry behavior registration", invalid);
         }
      } else if (previous != null) {
         MLuaBehaviorRegistry.unregisterBlockContainer(key, previous, installedContext);
      }
      MLuaBehaviorRegistry.ContainerDefinition expected = installed;
      dev.mlua.core.MLuaContext expectedContext = installedContext;
      registration.trackRollback(() -> MLuaBehaviorRegistry.restoreBlockContainer(
         key, expected, expectedContext, previous, previousContext));
   }

   void registerContentClass(String domain, String key, String className) {
      dev.mlua.core.MLuaContext callbackContext =
         MLuaLuaBindings.boundContext(service.apiContext().mlua().globals());
      MLuaBehaviorRegistry.ContentClassDefinition previous =
         MLuaBehaviorRegistry.getContentClassDefinition(domain, key, callbackContext);
      MLuaBehaviorRegistry.ContentClassDefinition installed =
         MLuaBehaviorRegistry.registerContentClassDefinition(domain, key, className, callbackContext);
      service.apiContext().mlua().registration().trackRollback(
         () -> MLuaBehaviorRegistry.restoreContentClassExact(domain, key, installed, previous, callbackContext));
   }
}
