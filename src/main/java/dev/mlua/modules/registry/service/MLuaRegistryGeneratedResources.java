package dev.mlua.modules.registry.service;

import dev.mlua.api.MLuaLuaErrors;
import com.google.gson.JsonObject;
import dev.mlua.core.service.MLuaService;
import dev.mlua.loaderapi.GeneratedAssetKind;
import dev.mlua.runtime.registry.MLuaLoaderRegistry;
import dev.mlua.minecraft.MLuaResourceLocations;
import dev.mlua.minecraft.binding.MLuaGeneratedAssetResource;
import dev.mlua.minecraft.binding.MLuaGeneratedDataResource;
import java.util.List;
import java.util.TreeSet;
import java.util.function.Function;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/** Owns generated asset and data-pack registration, lookup, and rollback. */
final class MLuaRegistryGeneratedResources {
   private final MLuaService.Context context;
   private final Function<String, String> registryId;

   MLuaRegistryGeneratedResources(MLuaService.Context context, Function<String, String> registryId) {
      this.context = context;
      this.registryId = registryId;
   }

   void registerData(String owner, String resourceType, String id, JsonObject definition) {
      validateName(owner, id, "data pack " + resourceType);
      JsonObject contribution = definition.deepCopy();
      MLuaLoaderRegistry.registerGeneratedDataResource(owner, resourceType, id, contribution);
      this.context.mlua().registration().trackRollback(() ->
         MLuaLoaderRegistry.unregisterGeneratedDataResource(owner, resourceType, id, contribution));
   }

   void registerAssetResource(String owner, String path, JsonObject definition) {
      JsonObject contribution = definition.deepCopy();
      MLuaLoaderRegistry.registerGeneratedAssetResource(owner, path, contribution);
      this.context.mlua().registration().trackRollback(() ->
         MLuaLoaderRegistry.unregisterGeneratedAssetResource(owner, path, contribution));
   }

   void registerAsset(GeneratedAssetKind kind, String owner, String name, JsonObject definition) {
      validateName(owner, name, kind.name().toLowerCase(java.util.Locale.ROOT));
      JsonObject snapshot = definition.deepCopy();
      MLuaLoaderRegistry.registerGeneratedAsset(kind, owner, name, snapshot);
      this.context.mlua().registration().trackRollback(() ->
         MLuaLoaderRegistry.unregisterGeneratedAsset(kind, owner, name, snapshot));
   }

   LuaValue dataResource(String resourceType, String id) {
      String type = requireDataResourceType(resourceType);
      LuaTable entry = MLuaGeneratedDataResource.create(this.context.mlua().registration(), type, this.registryId.apply(id));
      return entry == null ? LuaValue.NIL : entry;
   }

   List<String> dataIds(String resourceType, String namespace) {
      String type = requireDataResourceType(resourceType);
      try {
         return MLuaLoaderRegistry.getGeneratedDataResourceIds(type, namespace);
      } catch (IllegalArgumentException error) {
         throw MLuaLuaErrors.javaFailure("Generated data resource lookup", error);
      }
   }

   LuaTable dataEntries(String resourceType, String namespace, boolean keyed) {
      String type = requireDataResourceType(resourceType);
      LuaTable result = new LuaTable();
      int index = 1;
      for (String id : this.dataIds(resourceType, namespace)) {
         LuaTable entry = MLuaGeneratedDataResource.create(this.context.mlua().registration(),
            type, id);
         if (entry != null) {
            if (keyed) result.set(id, entry);
            else result.set(index++, entry);
         }
      }
      return result;
   }

   LuaValue assetResource(GeneratedAssetKind kind, String id) {
      String normalizedId = this.registryId.apply(id);
      if (!MLuaLoaderRegistry.getGeneratedAssets(kind).containsKey(normalizedId)) return LuaValue.NIL;
      return MLuaGeneratedAssetResource.create(this.context.mlua().registration(), kind, normalizedId);
   }

   List<String> assetIds(GeneratedAssetKind kind, String namespace) {
      TreeSet<String> result = new TreeSet<>();
      for (String id : MLuaLoaderRegistry.getGeneratedAssets(kind).keySet()) {
         if (matchesNamespace(id, namespace)) result.add(id);
      }
      return List.copyOf(result);
   }

   LuaTable assetEntries(GeneratedAssetKind kind, String namespace, boolean keyed) {
      LuaTable result = new LuaTable();
      int index = 1;
      for (String id : this.assetIds(kind, namespace)) {
         LuaTable handle = MLuaGeneratedAssetResource.create(this.context.mlua().registration(), kind, id);
         if (keyed) result.set(id, handle);
         else result.set(index++, handle);
      }
      return result;
   }

   void installAssetLookupApi(LuaTable target, GeneratedAssetKind kind) {
      target.set("Get", new VarArgFunction() {
         @Override public Varargs invoke(Varargs args) {
            return MLuaRegistryGeneratedResources.this.assetResource(kind, args.checkjstring(2));
         }
      });
      target.set("GetEntry", target.get("Get"));
      target.set("GetIds", new VarArgFunction() {
         @Override public Varargs invoke(Varargs args) {
            return MLuaRegistryLuaValues.stringArray(MLuaRegistryGeneratedResources.this.assetIds(
               kind, MLuaRegistryLuaValues.optionalNamespace(args.arg(2))));
         }
      });
      target.set("GetEntries", new VarArgFunction() {
         @Override public Varargs invoke(Varargs args) {
            return MLuaRegistryGeneratedResources.this.assetEntries(
               kind, MLuaRegistryLuaValues.optionalNamespace(args.arg(2)), false);
         }
      });
      target.set("GetAll", new VarArgFunction() {
         @Override public Varargs invoke(Varargs args) {
            return MLuaRegistryGeneratedResources.this.assetEntries(
               kind, MLuaRegistryLuaValues.optionalNamespace(args.arg(2)), true);
         }
      });
      target.set("Exists", new VarArgFunction() {
         @Override public Varargs invoke(Varargs args) {
            return LuaValue.valueOf(!MLuaRegistryGeneratedResources.this.assetResource(
               kind, args.checkjstring(2)).isnil());
         }
      });
   }

   void installDataLookupApi(LuaTable target) {
      target.set("Get", new VarArgFunction() {
         @Override public Varargs invoke(Varargs args) {
            return MLuaRegistryGeneratedResources.this.dataResource(args.checkjstring(2), args.checkjstring(3));
         }
      });
      target.set("GetEntry", target.get("Get"));
      target.set("GetIds", new VarArgFunction() {
         @Override public Varargs invoke(Varargs args) {
            return MLuaRegistryLuaValues.stringArray(MLuaRegistryGeneratedResources.this.dataIds(
               args.checkjstring(2), MLuaRegistryLuaValues.optionalNamespace(args.arg(3))));
         }
      });
      target.set("GetEntries", new VarArgFunction() {
         @Override public Varargs invoke(Varargs args) {
            return MLuaRegistryGeneratedResources.this.dataEntries(
               args.checkjstring(2), MLuaRegistryLuaValues.optionalNamespace(args.arg(3)), false);
         }
      });
      target.set("GetAll", new VarArgFunction() {
         @Override public Varargs invoke(Varargs args) {
            return MLuaRegistryGeneratedResources.this.dataEntries(
               args.checkjstring(2), MLuaRegistryLuaValues.optionalNamespace(args.arg(3)), true);
         }
      });
      target.set("Exists", new VarArgFunction() {
         @Override public Varargs invoke(Varargs args) {
            return LuaValue.valueOf(!MLuaRegistryGeneratedResources.this.dataResource(
               args.checkjstring(2), args.checkjstring(3)).isnil());
         }
      });
   }

   private void validateName(String owner, String name, String resourceKind) {
      try {
         dev.mlua.runtime.registry.MLuaResourceNameValidator.validate(owner);
         dev.mlua.runtime.registry.MLuaResourceNameValidator.validate(name);
         var id = MLuaResourceLocations.tryParseOrNull(owner + ":" + name);
         if (id == null || !owner.equals(MLuaResourceLocations.namespace(id)) || !name.equals(MLuaResourceLocations.path(id))) {
            throw new IllegalArgumentException("Invalid lowercase namespaced registry path: " + name);
         }
      } catch (IllegalArgumentException invalid) {
         throw new LuaError("[MLua] Registry generated " + resourceKind
            + " name must be a valid lowercase addon-local resource path: " + invalid.getMessage());
      }
   }

   private static boolean matchesNamespace(String id, String namespace) {
      int separator = id.indexOf(':');
      return separator > 0 && (namespace == null || namespace.equals(id.substring(0, separator)));
   }

   private static String requireDataResourceType(String value) {
      String type = value == null ? "" : value.trim();
      if (type.isEmpty() || type.startsWith("/") || type.endsWith("/") || type.contains("\\")
          || !type.matches("[a-z0-9_./-]+")) {
         throw new LuaError("[MLua] Data resource type must be a lowercase relative path, such as worldgen/biome");
      }
      for (String segment : type.split("/")) {
         if (segment.isBlank() || segment.equals(".") || segment.equals("..")) {
            throw new LuaError("[MLua] Data resource type contains an invalid path segment");
         }
      }
      return type;
   }

}
