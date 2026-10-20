package dev.mlua.modules.registry.service;

import dev.mlua.api.MLuaLuaErrors;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

import dev.mlua.modules.combat.MLuaDamageSourceRegistry;

/** Installs addon-owned damage source registration. */
final class MLuaRegistryDamageSourceApi {
   private MLuaRegistryDamageSourceApi() { }

   static void install(MLuaRegistryService service, LuaTable damageSource) {
      damageSource.set("Register", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            service.ensureRegistryPhase();
            Object runtimeOwner = service.apiContext().mlua().registration() instanceof dev.mlua.core.MLuaRuntime runtime ? runtime.session() : null;
            String owner = service.apiContext().mlua().ownerModId();
            String name = args.checkjstring(2);
            MLuaDamageSourceRegistry.DamageSourceDef definition;
            try {
               definition = MLuaDamageSourceRegistry.register(runtimeOwner, owner, name,
                  args.arg(3).opttable((LuaTable)null));
            } catch (IllegalArgumentException | IllegalStateException invalid) {
               throw MLuaLuaErrors.javaFailure("Registry damage source registration", invalid);
            }
            service.apiContext().mlua().registration().trackRollback(() ->
               MLuaDamageSourceRegistry.unregister(runtimeOwner, owner, name, definition));
            return LuaValue.NONE;
         }
      });
   }
}
