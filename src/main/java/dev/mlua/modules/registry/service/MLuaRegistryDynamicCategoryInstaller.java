package dev.mlua.modules.registry.service;

import java.util.List;

import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

import dev.mlua.core.MLuaCallbackBudget;
import dev.mlua.core.engine.MLuaLuaBindings;
import dev.mlua.core.service.MLuaServiceBase;
import dev.mlua.minecraft.binding.MLuaRegistryDefinition;
import dev.mlua.runtime.registry.MLuaDynamicRegistryCatalog;

/** Owns addon category endpoint installation and definition rollback lifecycle. */
final class MLuaRegistryDynamicCategoryInstaller {
    private final MLuaRegistryService service;

    MLuaRegistryDynamicCategoryInstaller(MLuaRegistryService service) {
        this.service = service;
    }

   void installCategoryRegistration(LuaTable registry) {
      MLuaRegistryService service = this.service;
      registry.set("RegisterCategory", new VarArgFunction() {
         @Override public Varargs invoke(Varargs args) {
            service.ensureRegistryPhase();
            String category = MLuaRegistryService.registryCategory(args.checkjstring(2));
            String key = category.toLowerCase(java.util.Locale.ROOT);
            String description = args.checkjstring(3);
            LuaValue register = args.checkfunction(4);
            if (description.isBlank()) throw new LuaError("[MLua] Registry category description must not be empty");
            if (description.length() > MLuaDynamicRegistryCatalog.MAX_CATEGORY_DESCRIPTION_LENGTH) {
               throw new LuaError("[MLua] Registry category descriptions cannot exceed "
                  + MLuaDynamicRegistryCatalog.MAX_CATEGORY_DESCRIPTION_LENGTH + " characters");
            }
            service.refreshDynamicCategoryEndpoints(registry);
            String owner = service.apiContext().mlua().ownerModId();
            String namespace = category.substring(0, category.indexOf(':'));
            if (!owner.equals(namespace)) {
               throw new LuaError("[MLua] Registry category '" + category
                  + "' must use the registering addon's namespace '" + owner + "'");
            }
            if (service.registryEndpoints.contains(key)) {
               throw new LuaError("[MLua] Registry category '" + category + "' is already registered");
            }
            String domain = service.dynamicContentTypeDomain(owner, category);
            dev.mlua.core.MLuaContext callbackContext = MLuaLuaBindings.boundContext(
               service.apiContext().mlua().globals());
            if (callbackContext == null || !owner.equals(callbackContext.ownerModId())) {
               throw new LuaError("[MLua] RegisterCategory must be called from its owning Lua script context");
            }
            MLuaDynamicRegistryCatalog.Category registered = new MLuaDynamicRegistryCatalog.Category(
               category, description, domain, owner, register, callbackContext);
            if (!service.dynamicRegistry.register(registered)) {
               throw new LuaError("[MLua] Registry category '" + category + "' is already registered");
            }
            Runnable rollback = () -> {
               if (service.dynamicRegistry.unregister(key, registered)) {
                  RegistryEndpoint endpoint = new RegistryEndpoint(
                     category, "Register", domain, category, null, 2, List.of());
                  service.registryEndpoints.remove(key, endpoint);
                  service.dynamicCategoryEndpoints.remove(key);
                  registry.set(category, LuaValue.NIL);
               }
            };
            service.apiContext().mlua().registration().trackRollback(rollback);
            service.installDynamicCategory(registry, registered);
            return registry.get(category);
         }
      });
   }

   void install(LuaTable registry, MLuaDynamicRegistryCatalog.Category category) {
      MLuaRegistryService service = this.service;
      String categoryName = category.name();
      String categoryDomain = category.domain();
      String categoryDescription = category.description();
      String key = categoryName.trim().toLowerCase(java.util.Locale.ROOT);
      RegistryEndpoint endpoint = new RegistryEndpoint(categoryName, "Register", categoryDomain,
         categoryName, null, 2, List.of());
      RegistryEndpoint existing = service.registryEndpoints.putIfAbsent(key, endpoint);
      if (existing != null) return;
      service.dynamicCategoryEndpoints.add(key);
      LuaTable target = service.registryTable(categoryName);
      target.set("Register", new VarArgFunction() {
         @Override public Varargs invoke(Varargs args) {
            service.ensureRegistryPhase();
            String categoryKey = key;
            MLuaDynamicRegistryCatalog.Category active = service.dynamicRegistry.category(categoryKey);
            if (active == null) throw new LuaError("[MLua] Registry category '" + categoryName + "' is no longer registered");
            String owner = service.apiContext().mlua().ownerModId();
            String id = service.ownedRegistryId(
               args.checkjstring(2), owner, categoryName);
            return MLuaLuaBindings.withContext(active.context(), () -> {
               if (service.dynamicRegistry.category(categoryKey) != active) {
                  throw new LuaError("[MLua] Registry category '" + categoryName + "' was removed before registration began");
               }
               LuaTable definition = args.checktable(3);
               LuaTable resolved = service.apiContext().mlua().registration()
                  .resolveContentType(active.domain(), owner, definition);
               MLuaDynamicRegistryCatalog.DefinitionReservation reservation =
                  service.dynamicRegistry.beginDefinition(categoryKey, id, owner, resolved);
               if (reservation == null) {
                  throw new LuaError("[MLua] Registry entry '" + id + "' is already registered in '" + categoryName + "'");
               }
               try {
                  LuaTable callbackDefinition = service.dynamicRegistry
                     .callbackDefinition(reservation);
                  if (callbackDefinition == null) {
                     throw new LuaError("[MLua] Registry entry '" + id + "' reservation expired before its callback began");
                  }
                  MLuaCallbackBudget.run(active.context(), () -> {
                     active.register().invoke(LuaValue.varargsOf(
                        new LuaValue[]{LuaValue.valueOf(id), callbackDefinition}));
                     return null;
                  });
                  MLuaDynamicRegistryCatalog.DefinitionHandle stored = service.dynamicRegistry
                     .registerDefinition(reservation);
                  if (stored == null) {
                     throw new LuaError("[MLua] Registry entry '" + id + "' is no longer registered in '" + categoryName + "'");
                  }
                  try {
                     service.apiContext().mlua().registration().trackRollback(() ->
                        service.dynamicRegistry.unregisterDefinition(categoryKey, id, stored));
                     LuaTable entry = MLuaRegistryDefinition.create(
                        service.apiContext().mlua().registration(),
                        service.dynamicRegistry, categoryKey, id);
                     if (entry == null) {
                        throw new LuaError("[MLua] Registry entry '" + id + "' was removed before its handle could be created");
                     }
                     return entry;
                  } catch (RuntimeException | Error failure) {
                     // The definition has left the pending queue; don't strand it if the
                     // owning category disappears before the Lua entry handle is returned.
                     service.dynamicRegistry.unregisterDefinition(categoryKey, id, stored);
                     throw failure;
                  }
               } finally {
                  service.dynamicRegistry.cancelPendingDefinition(reservation);
               }
            });
         }
      });
      target.set("Get", new VarArgFunction() {
         @Override public Varargs invoke(Varargs args) {
            MLuaRegistryDynamicCategoryInstaller.this.requireDynamicCategory(categoryName);
            String id = service.registryId(args.checkjstring(2));
            LuaTable definition = MLuaRegistryDefinition.create(
               service.apiContext().mlua().registration(),
               service.dynamicRegistry, key, id);
            return definition == null ? LuaValue.NIL : definition;
         }
      });
      target.set("GetEntry", new VarArgFunction() {
         @Override public Varargs invoke(Varargs args) {
            MLuaRegistryDynamicCategoryInstaller.this.requireDynamicCategory(categoryName);
            String id = service.registryId(args.checkjstring(2));
            LuaTable entry = MLuaRegistryDefinition.create(service.apiContext().mlua().registration(),
               service.dynamicRegistry, key, id);
            return entry == null ? LuaValue.NIL : entry;
         }
      });
      target.set("GetEntries", new VarArgFunction() {
         @Override public Varargs invoke(Varargs args) {
            MLuaRegistryDynamicCategoryInstaller.this.requireDynamicCategory(categoryName);
            LuaTable result = new LuaTable();
            int index = 1;
            for (String id : service.dynamicRegistry.ids(key, MLuaRegistryService.optionalNamespace(args.arg(2)))) {
               LuaTable entry = MLuaRegistryDefinition.create(
                  service.apiContext().mlua().registration(),
                  service.dynamicRegistry, key, id);
               if (entry != null) result.set(index++, entry);
            }
            return result;
         }
      });
      target.set("GetId", new VarArgFunction() {
         @Override public Varargs invoke(Varargs args) {
            return LuaValue.valueOf(service.registryId(args.checkjstring(2)));
         }
      });
      target.set("GetAll", new VarArgFunction() {
         @Override public Varargs invoke(Varargs args) {
            MLuaRegistryDynamicCategoryInstaller.this.requireDynamicCategory(categoryName);
            return service.dynamicRegistryEntryMap(key, MLuaRegistryService.optionalNamespace(args.arg(2)));
         }
      });
      target.set("GetIds", new VarArgFunction() {
         @Override public Varargs invoke(Varargs args) {
            MLuaRegistryDynamicCategoryInstaller.this.requireDynamicCategory(categoryName);
            return MLuaRegistryService.stringArray(service.dynamicRegistry.ids(key, MLuaRegistryService.optionalNamespace(args.arg(2))));
         }
      });
      target.set("Exists", new VarArgFunction() {
         @Override public Varargs invoke(Varargs args) {
            MLuaRegistryDynamicCategoryInstaller.this.requireDynamicCategory(categoryName);
            String id = service.registryId(args.checkjstring(2));
            return LuaValue.valueOf(service.dynamicRegistry.containsDefinition(key, id));
         }
      });
      service.installContentTypeApi(target, categoryDomain, List.of());
      MLuaRegistryService.documentRegistryTable(target, categoryName);
      target.rawset("__mlua_doc_description", LuaValue.valueOf(categoryDescription));
      registry.set(categoryName, MLuaServiceBase.normalizeServiceCalls(target, service.name()));
   }

   private MLuaDynamicRegistryCatalog.Category requireDynamicCategory(String category) {
      MLuaDynamicRegistryCatalog.Category registered = service.dynamicRegistry.category(category);
      if (registered == null) throw new LuaError("[MLua] Registry category '" + category + "' is not addon-owned");
      return registered;
   }

}
