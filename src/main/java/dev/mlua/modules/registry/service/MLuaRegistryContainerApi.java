package dev.mlua.modules.registry.service;

import dev.mlua.api.MLuaLuaErrors;
import java.util.List;
import java.util.Map;

import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

import dev.mlua.minecraft.binding.MLuaContainerType;
import dev.mlua.minecraft.binding.MLuaLuaArguments;
import dev.mlua.minecraft.scripted.MLuaBehaviorRegistry;
import dev.mlua.runtime.registry.MLuaRegistryNameHelper;

/** Installs scriptable container type registration and lookup endpoints. */
final class MLuaRegistryContainerApi {
   private MLuaRegistryContainerApi() { }

   static void install(MLuaRegistryService service, LuaTable containers) {
      containers.set("Register", new VarArgFunction() {
         @Override public Varargs invoke(Varargs args) {
            service.ensureRegistryPhase();
            String name = service.requiredLuaString(args, 2, "Registry.Containers:Register", "id");
            MLuaRegistryContentValidation.requireLocalRegistryName(service.apiContext().mlua().ownerModId(),
               name, "Registry.Containers:Register");
            LuaTable definition = args.checktable(3);
            int rows = MLuaLuaArguments.optionalWholeInt(MLuaRegistryContainerDefinitions.property(definition, "rows", "Rows"), 3,
               "Registry.Containers:Register", "Rows");
            LuaValue titleValue = MLuaRegistryContainerDefinitions.property(definition, "title", "Title");
            if (!titleValue.isnil() && titleValue.type() != LuaValue.TSTRING) {
               throw new LuaError("[MLua] Registry.Containers:Register title must be a string");
            }
            String title = titleValue.optjstring(MLuaRegistryNameHelper.displayName(name));
            String owner = service.apiContext().mlua().ownerModId();
            String key = owner + ":" + name;
            MLuaBehaviorRegistry.ContainerDefinition container;
            try {
               container = MLuaBehaviorRegistry.registerContainerType(key,
                  new MLuaBehaviorRegistry.ContainerDefinition(rows, title,
                     MLuaRegistryContainerDefinitions.allowedItems(owner, definition),
                     MLuaRegistryContainerDefinitions.slotAllowedItems(owner, definition, rows * 9)));
            } catch (IllegalArgumentException | IllegalStateException invalid) {
               throw MLuaLuaErrors.javaFailure("Registry container registration", invalid);
            }
            service.apiContext().mlua().registration().trackRollback(() ->
               MLuaBehaviorRegistry.unregisterContainerType(key, container));
            return MLuaContainerType.create(service.apiContext().mlua().registration(), key, container);
         }
      });
      containers.set("GetId", new VarArgFunction() {
         @Override public Varargs invoke(Varargs args) {
            return LuaValue.valueOf(service.registryId(
               service.requiredLuaString(args, 2, "Registry.Containers:GetId", "id")));
         }
      });
      containers.set("Get", new VarArgFunction() {
         @Override public Varargs invoke(Varargs args) {
            return service.containerType(
               service.registryId(
                  service.requiredLuaString(args, 2, "Registry.Containers:Get", "id")));
         }
      });
      containers.set("GetAll", new VarArgFunction() {
         @Override public Varargs invoke(Varargs args) {
            LuaTable result = new LuaTable();
            String namespace = service.optionalNamespace(args.arg(2));
            MLuaBehaviorRegistry.getContainerTypes().entrySet().stream()
               .filter(entry -> namespace == null || entry.getKey().startsWith(namespace + ":"))
               .sorted(Map.Entry.comparingByKey())
               .forEach(entry -> result.set(entry.getKey(), MLuaContainerType.create(
                  service.apiContext().mlua().registration(), entry.getKey(), entry.getValue())));
            return result;
         }
      });
      containers.set("GetIds", new VarArgFunction() {
         @Override public Varargs invoke(Varargs args) {
            String namespace = service.optionalNamespace(args.arg(2));
            List<String> ids = MLuaBehaviorRegistry.getContainerTypes().keySet().stream()
               .filter(id -> namespace == null || id.startsWith(namespace + ":"))
               .sorted().toList();
            return service.stringArray(ids);
         }
      });
      containers.set("Exists", new VarArgFunction() {
         @Override public Varargs invoke(Varargs args) {
            String id = service.registryId(args.checkjstring(2));
            return LuaValue.valueOf(MLuaBehaviorRegistry.getContainerType(id) != null);
         }
      });
   }
}
