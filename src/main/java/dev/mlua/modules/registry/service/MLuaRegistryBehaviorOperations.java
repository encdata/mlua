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
      MLuaBehaviorRegistry.Snapshot previous = MLuaBehaviorRegistry.snapshotBehaviors(domain, key);
      Map<String, LuaValue> installed = MLuaBehaviorRegistry.registerBehaviors(domain, key, callbacks, ownerContext);
      registration.trackRollback(() ->
         MLuaBehaviorRegistry.restoreBehaviors(domain, key, installed, previous, ownerContext));
   }

   void setEntityAIInterval(String key, int interval) {
      Integer previous = MLuaBehaviorRegistry.getRegisteredEntityAIInterval(key);
      MLuaBehaviorRegistry.registerEntityAIInterval(key, interval);
      int expected = Math.max(1, Math.min(120, interval));
      service.apiContext().mlua().registration().trackRollback(
         () -> MLuaBehaviorRegistry.restoreEntityAIInterval(key, expected, previous));
   }

   void replaceBlockContainer(MLuaRegistration registration, String key,
                              MLuaBehaviorRegistry.ContainerDefinition requested) {
      MLuaBehaviorRegistry.ContainerDefinition previous = MLuaBehaviorRegistry.getBlockContainer(key);
      MLuaBehaviorRegistry.ContainerDefinition installed = null;
      if (requested != null) {
         try {
            installed = MLuaBehaviorRegistry.registerBlockContainer(key, requested);
         } catch (IllegalArgumentException | IllegalStateException invalid) {
            throw MLuaLuaErrors.javaFailure("Registry behavior registration", invalid);
         }
      } else if (previous != null) {
         MLuaBehaviorRegistry.unregisterBlockContainer(key, previous);
      }
      MLuaBehaviorRegistry.ContainerDefinition expected = installed;
      registration.trackRollback(() -> MLuaBehaviorRegistry.restoreBlockContainer(key, expected, previous));
   }

   void registerContentClass(String domain, String key, String className) {
      MLuaBehaviorRegistry.ContentClassDefinition previous =
         MLuaBehaviorRegistry.getContentClassDefinition(domain, key);
      dev.mlua.core.MLuaContext callbackContext = MLuaLuaBindings.boundContext(service.apiContext().mlua().globals());
      MLuaBehaviorRegistry.ContentClassDefinition installed = className == null || className.isBlank()
         ? null : new MLuaBehaviorRegistry.ContentClassDefinition(className, callbackContext);
      MLuaBehaviorRegistry.registerContentClass(domain, key, className, callbackContext);
      service.apiContext().mlua().registration().trackRollback(
         () -> MLuaBehaviorRegistry.restoreContentClass(domain, key, installed, previous));
   }
}
