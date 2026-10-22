package dev.mlua.modules.registry.service;

import java.util.List;

import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

import dev.mlua.loaderapi.GeneratedAssetKind;
import dev.mlua.runtime.registry.MLuaDynamicRegistryCatalog;

/** Assembles Registry's Lua service and typed category tables. Registration behavior stays in MLuaRegistryService. */
final class MLuaRegistryApi {
   private MLuaRegistryApi() { }

   static LuaTable build(MLuaRegistryService service) {
      LuaTable table = new LuaTable();
      LuaTable items = service.registryTable("Items");
      LuaTable blocks = service.registryTable("Blocks");
      LuaTable entities = service.registryTable("Entities");
      LuaTable effects = service.registryTable("Effects");
      LuaTable potions = service.registryTable("Potions");
      LuaTable particles = service.registryTable("Particles");
      LuaTable recipes = service.registryTable("Recipes");
      LuaTable loot = service.registryTable("Loot");
      LuaTable tags = service.registryTable("Tags");
      LuaTable containers = service.registryTable("Containers");
      LuaTable projectiles = service.registryTable("Projectiles");
      LuaTable tabs = service.registryTable("Tabs");
      LuaTable config = service.registryTable("Config");
      LuaTable damageSource = service.registryTable("DamageSource");
      LuaTable dataPack = service.registryTable("DataPack");
      LuaTable assets = service.registryTable("Assets");
      LuaTable worldGeneration = service.registryTable("WorldGeneration");
      LuaTable contentTypes = service.registryTable("ContentTypes");
      table.set("__side", "Registry");
      MLuaRegistryLimitsApi.install(service, table);
      table.set("Items", items);
      table.set("Blocks", blocks);
      table.set("Entities", entities);
      table.set("Effects", effects);
      table.set("Potions", potions);
      table.set("Particles", particles);
      table.set("Recipes", recipes);
      table.set("Loot", loot);
      table.set("Tags", tags);
      table.set("Containers", containers);
      table.set("Projectiles", projectiles);
      table.set("Tabs", tabs);
      table.set("Config", config);
      table.set("DamageSource", damageSource);
      table.set("DataPack", dataPack);
      table.set("Assets", assets);
      table.set("WorldGeneration", worldGeneration);
      table.set("ContentTypes", contentTypes);
      for (MLuaDynamicRegistryCatalog.Category category : service.dynamicRegistry.categories()) {
         service.installDynamicCategory(table, category);
      }
      service.installDynamicCategoryRegistration(table);
      MLuaRegistryGeneratedResourceApi.install(service, dataPack, assets, worldGeneration);
      MLuaRegistryContainerApi.install(service, containers);
      MLuaRegistryCreativeTabApi.install(service, tabs);
      MLuaRegistryDataApi.install(service, recipes, loot, tags, blocks);
      MLuaRegistryVisualContentApi.install(service, particles, potions, effects);
      MLuaRegistryConfigApi.install(service, config);
      MLuaRegistryDamageSourceApi.install(service, damageSource);
      MLuaRegistryItemBlockApi.install(service, items, blocks);
      MLuaRegistryEntityApi.install(service, entities);
      MLuaRegistryProjectileApi.install(service, projectiles);
      table.set("Register", new VarArgFunction() {
          @Override public Varargs invoke(Varargs args) {
             String category = args.checkjstring(2);
             RegistryEndpoint endpoint = service.endpointResolver.resolve(category, "category", table);
             LuaTable target = table.get(endpoint.table()).checktable();
             LuaValue register = target.get(endpoint.registerMethod());
             if (!register.isfunction()) throw new LuaError("[MLua] Registry category '" + category + "' does not support generic registration");
             LuaValue[] registrationArgs = new LuaValue[endpoint.genericArgumentCount() + 1];
             registrationArgs[0] = target;
             for (int index = 1; index < registrationArgs.length; index++) {
                registrationArgs[index] = args.arg(index + 2);
             }
             return register.invoke(LuaValue.varargsOf(registrationArgs));
          }
       });
      table.set("RegisterType", new VarArgFunction() {
          @Override public Varargs invoke(Varargs args) {
             RegistryEndpoint endpoint = service.endpointResolver.resolve(args.checkjstring(2), "type domain", table);
             LuaTable target = table.get(endpoint.typeTable()).checktable();
             return target.get("RegisterType").invoke(LuaValue.varargsOf(new LuaValue[]{target, args.arg(3), args.arg(4)}));
          }
       });
       service.installRootContentTypeApi(contentTypes);
      MLuaRegistryRootQueries.install(service, table);
       service.installGeneratedAssetLookupApi(recipes, GeneratedAssetKind.RECIPE);
       service.installGeneratedAssetLookupApi(loot, GeneratedAssetKind.LOOT_TABLE);
       for (RegistryEndpoint endpoint : service.endpointResolver.uniqueEndpoints()) {
          if (!endpoint.table().equals(endpoint.typeTable())) continue;
          if (service.dynamicRegistry.category(endpoint.table()) != null) continue;
          LuaTable target = table.get(endpoint.table()).checktable();
          service.installContentTypeApi(target, endpoint.typeDomain(), endpoint.typedDefinitions());
          if (endpoint.registryKind() != null) service.installRegistryEntryApi(target, endpoint.registryKind());
          service.installEntryLookupApi(target, endpoint.table());
          service.documentRegistryTable(target, endpoint.table());
       }
       service.documentRegistryTable(table, "Registry");
       service.documentRegistryTable(contentTypes, "ContentTypes");
      MLuaRegistryMetadata.install(service, table);
      service.wrapTypedDefinition(entities, "item", List.of(new RegistryEndpoint.TypedDefinition("RegisterSpawnEgg", 3)));
      service.wrapTypedDefinition(blocks, "item", List.of(new RegistryEndpoint.TypedDefinition("RegisterItem", 3)));
       return dev.mlua.core.service.MLuaServiceBase.normalizeServiceCalls(table, service.name());
    
   }
}
