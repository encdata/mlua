package dev.mlua.modules.registry.service;

import dev.mlua.core.engine.MLuaLuaBindings;
import dev.mlua.minecraft.MLuaResourceLocations;
import dev.mlua.modules.combat.MLuaProjectileDefinition;
import net.minecraft.resources.ResourceLocation;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/** Installs the runtime-owned projectile definition registry API. */
final class MLuaRegistryProjectileApi {
    private MLuaRegistryProjectileApi() { }

    static void install(MLuaRegistryService service, LuaTable projectiles) {
        projectiles.set("Register", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                service.ensureRegistryPhase();
                String name = args.checkjstring(2);
                String ownerModId = service.apiContext().mlua().ownerModId();
                MLuaRegistryDefinitions.requireLocalRegistryName(ownerModId, name,
                    "Registry.Projectiles:Register");
                LuaTable properties = args.arg(3).opttable(null);
                if (properties == null) {
                    throw new LuaError("[MLua] Registry.Projectiles:Register requires a properties table");
                }

                Object runtimeOwner = service.projectileRuntimeOwner();
                var scriptContext = MLuaLuaBindings.boundContext(service.apiContext().mlua().globals());
                MLuaProjectileDefinition.ProjectileDef definition = MLuaProjectileDefinition.register(
                    runtimeOwner, ownerModId, name, properties, scriptContext);
                service.apiContext().mlua().registration().trackRollback(() ->
                    MLuaProjectileDefinition.unregister(runtimeOwner, ownerModId, name, definition));
                return MLuaProjectileDefinition.createTypeLuaTable(runtimeOwner, ownerModId, name,
                    service.apiContext().mlua().registration());
            }
        });

        projectiles.set("Get", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                return service.projectileType(service.registryId(args.checkjstring(2)));
            }
        });
        projectiles.set("GetId", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                return LuaValue.valueOf(service.registryId(args.checkjstring(2)));
            }
        });
        projectiles.set("GetAll", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                LuaTable result = new LuaTable();
                Object runtimeOwner = service.projectileRuntimeOwner();
                String namespace = MLuaRegistryService.optionalNamespace(args.arg(2));
                for (String id : MLuaProjectileDefinition.registeredIds(runtimeOwner, namespace)) {
                    result.set(id, service.projectileType(id));
                }
                return result;
            }
        });
        projectiles.set("GetIds", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                return MLuaRegistryService.stringArray(MLuaProjectileDefinition.registeredIds(
                    service.projectileRuntimeOwner(), MLuaRegistryService.optionalNamespace(args.arg(2))));
            }
        });
        projectiles.set("Exists", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                String id = service.registryId(args.checkjstring(2));
                ResourceLocation location = MLuaResourceLocations.tryParseOrNull(id);
                return LuaValue.valueOf(location != null && MLuaProjectileDefinition.get(
                    service.projectileRuntimeOwner(), MLuaResourceLocations.namespace(location),
                    MLuaResourceLocations.path(location)) != null);
            }
        });
    }
}
