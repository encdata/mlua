package dev.mlua.modules.registry.service;

import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

import dev.mlua.minecraft.binding.MLuaRegistryDefinition;
import dev.mlua.minecraft.binding.MLuaRegistryEntry;
import dev.mlua.runtime.registry.MLuaDynamicRegistryCatalog;
import dev.mlua.loaderapi.RegistryKind;

/** Owns category-level lookup routing for the root Registry service. */
final class MLuaRegistryRootQueries {
    private MLuaRegistryRootQueries() { }

    static void install(MLuaRegistryService service, LuaTable table) {
      table.set("GetTypes", new VarArgFunction() {
          @Override public Varargs invoke(Varargs args) {
             RegistryEndpoint endpoint = service.endpointResolver.resolve(args.checkjstring(2), "type domain", table);
             LuaTable target = table.get(endpoint.typeTable()).checktable();
             return target.get("GetTypes").invoke(target);
          }
       });
      table.set("ResolveType", new VarArgFunction() {
          @Override public Varargs invoke(Varargs args) {
             RegistryEndpoint endpoint = service.endpointResolver.resolve(args.checkjstring(2), "type domain", table);
             LuaTable target = table.get(endpoint.typeTable()).checktable();
             return target.get("ResolveType").invoke(LuaValue.varargsOf(new LuaValue[]{target, args.arg(3)}));
          }
       });
       table.set("GetRegistered", new VarArgFunction() {
          @Override public Varargs invoke(Varargs args) {
             return service.registeredIds(MLuaRegistryEntry.kind(args.checkjstring(2)), service.optionalNamespace(args.arg(3)));
          }
       });
       table.set("GetEntries", new VarArgFunction() {
          @Override public Varargs invoke(Varargs args) {
             String category = args.checkjstring(2);
             RegistryEndpoint endpoint = service.endpointResolver.resolve(category, "category", table);
             if (service.isGeneratedDataEndpoint(endpoint.table())) {
                return service.generatedDataEntryList(
                   args.checkjstring(3), service.optionalNamespace(args.arg(4)));
             }
             String namespace = service.optionalNamespace(args.arg(3));
             if (endpoint.registryKind() != null) {
                return service.registeredEntries(endpoint.registryKind(), namespace);
             }
             if (service.dynamicRegistry.category(endpoint.table()) != null) {
                LuaTable result = new LuaTable();
                int index = 1;
                for (String id : service.dynamicRegistry.ids(endpoint.table(), namespace)) {
                   LuaTable entry = MLuaRegistryDefinition.create(service.apiContext().mlua().registration(),
                      service.dynamicRegistry, endpoint.table(), id);
                   if (entry != null) result.set(index++, entry);
                }
                return result;
             }
             LuaTable target = table.get(endpoint.table()).checktable();
             LuaValue entries = target.get("GetEntries");
             if (entries.isfunction()) {
                return entries.invoke(LuaValue.varargsOf(new LuaValue[]{target,
                   namespace == null ? LuaValue.NIL : LuaValue.valueOf(namespace)}));
             }
             LuaValue getIds = target.get("GetIds");
             if (!getIds.isfunction()) {
                throw new LuaError("[MLua] Registry category '" + category + "' does not support entry listing");
             }
             LuaTable ids = getIds.invoke(LuaValue.varargsOf(new LuaValue[]{target,
                namespace == null ? LuaValue.NIL : LuaValue.valueOf(namespace)})).arg1().checktable();
             LuaTable result = new LuaTable();
             int resultIndex = 1;
             for (int index = 1; index <= ids.length(); index++) {
                LuaValue entry = service.registryEntryFor(endpoint, ids.get(index).checkjstring(), table);
                if (!entry.isnil()) result.set(resultIndex++, entry);
             }
             return result;
          }
       });
       table.set("GetId", new VarArgFunction() {
          @Override public Varargs invoke(Varargs args) {
             return LuaValue.valueOf(service.registryId(args.checkjstring(2)));
          }
       });
       table.set("GetKinds", new VarArgFunction() {
          @Override public Varargs invoke(Varargs args) {
             LuaTable result = new LuaTable();
             int index = 1;
             for (RegistryKind kind : RegistryKind.values()) {
                result.set(index++, LuaValue.valueOf(kind.name().toLowerCase(java.util.Locale.ROOT)));
             }
             return result;
          }
       });
       table.set("GetCategories", new VarArgFunction() {
          @Override public Varargs invoke(Varargs args) {
             service.endpointResolver.refresh(table);
             LuaTable result = new LuaTable();
             int index = 1;
             java.util.TreeSet<String> categories = new java.util.TreeSet<>(service.endpointResolver.categoryNames());
             service.dynamicRegistry.categories().forEach(category -> categories.add(category.name()));
             for (String category : categories) {
                result.set(index++, LuaValue.valueOf(category));
             }
             return result;
          }
       });
       table.set("GetCategory", new VarArgFunction() {
          @Override public Varargs invoke(Varargs args) {
             RegistryEndpoint endpoint = service.endpointResolver.resolve(args.checkjstring(2), "category", table);
             return table.get(endpoint.table()).checktable();
          }
       });
       table.set("Get", new VarArgFunction() {
          @Override public Varargs invoke(Varargs args) {
             String category = args.checkjstring(2);
             RegistryEndpoint endpoint = service.endpointResolver.resolve(category, "category", table);
             if (service.isGeneratedDataEndpoint(endpoint.table())) {
                return service.generatedDataResource(args.checkjstring(3), args.checkjstring(4));
             }
             if (endpoint.registryKind() != null) {
                return service.registryEntryFor(endpoint,
                   service.registryId(args.checkjstring(3)), table);
             }
             String id = service.registryId(args.checkjstring(3));
             if (service.dynamicRegistry.category(endpoint.table()) != null) {
                LuaTable definition = MLuaRegistryDefinition.create(
                   service.apiContext().mlua().registration(),
                   service.dynamicRegistry, endpoint.table(), id);
                return definition == null ? LuaValue.NIL : definition;
             }
             LuaTable target = table.get(endpoint.table()).checktable();
             return service.invokeCategoryQuery(target, endpoint.table(), "Get", LuaValue.valueOf(id));
          }
       });
       table.set("GetAll", new VarArgFunction() {
          @Override public Varargs invoke(Varargs args) {
             String category = args.checkjstring(2);
             RegistryEndpoint endpoint = service.endpointResolver.resolve(category, "category", table);
             if (service.isGeneratedDataEndpoint(endpoint.table())) {
                return service.generatedDataEntryMap(
                   args.checkjstring(3), service.optionalNamespace(args.arg(4)));
             }
             String namespace = service.optionalNamespace(args.arg(3));
             if (endpoint.registryKind() != null) {
                return service.registeredEntryMap(endpoint.registryKind(), namespace);
             }
             if (service.dynamicRegistry.category(endpoint.table()) != null) {
                return service.dynamicRegistryEntryMap(endpoint.table(), namespace);
             }
             LuaTable target = table.get(endpoint.table()).checktable();
             return service.invokeCategoryQuery(target, endpoint.table(), "GetAll",
                namespace == null ? LuaValue.NIL : LuaValue.valueOf(namespace));
          }
       });
       table.set("GetIds", new VarArgFunction() {
          @Override public Varargs invoke(Varargs args) {
             String category = args.checkjstring(2);
             RegistryEndpoint endpoint = service.endpointResolver.resolve(category, "category", table);
             if (service.isGeneratedDataEndpoint(endpoint.table())) {
                return service.stringArray(service.generatedDataIds(
                   args.checkjstring(3), service.optionalNamespace(args.arg(4))));
             }
             String namespace = service.optionalNamespace(args.arg(3));
             if (endpoint.registryKind() != null) {
                return service.stringArray(service.registeredIdList(endpoint.registryKind(), namespace));
             }
             if (service.dynamicRegistry.category(endpoint.table()) != null) {
                return service.stringArray(service.dynamicRegistry.ids(endpoint.table(), namespace));
             }
             LuaTable target = table.get(endpoint.table()).checktable();
             return service.invokeCategoryQuery(target, endpoint.table(), "GetIds",
                namespace == null ? LuaValue.NIL : LuaValue.valueOf(namespace));
          }
       });
       table.set("GetEntry", new VarArgFunction() {
          @Override public Varargs invoke(Varargs args) {
             RegistryEndpoint endpoint = service.endpointResolver.resolve(args.checkjstring(2), "category", table);
             if (service.isGeneratedDataEndpoint(endpoint.table())) {
                return service.generatedDataResource(args.checkjstring(3), args.checkjstring(4));
             }
             String id = service.registryId(args.checkjstring(3));
             return service.registryEntryFor(endpoint, id, table);
          }
       });
       table.set("Exists", new VarArgFunction() {
          @Override public Varargs invoke(Varargs args) {
             String category = args.checkjstring(2);
             RegistryEndpoint endpoint = service.endpointResolver.resolve(category, "category", table);
             if (service.isGeneratedDataEndpoint(endpoint.table())) {
                return LuaValue.valueOf(!service.generatedDataResource(
                   args.checkjstring(3), args.checkjstring(4)).isnil());
             }
             String id = service.registryId(args.checkjstring(3));
             if (endpoint.registryKind() != null) {
                LuaTable entry = MLuaRegistryEntry.create(service.apiContext().mlua().registration(),
                   endpoint.registryKind(), id);
                return entry.get("Exists").invoke(entry).arg1();
             }
             if (service.dynamicRegistry.category(endpoint.table()) != null) {
                return LuaValue.valueOf(service.dynamicRegistry.containsDefinition(endpoint.table(), id));
             }
             LuaTable target = table.get(endpoint.table()).checktable();
             return service.invokeCategoryQuery(target, endpoint.table(), "Exists", LuaValue.valueOf(id)).arg1();
          }
       });
    }
}
