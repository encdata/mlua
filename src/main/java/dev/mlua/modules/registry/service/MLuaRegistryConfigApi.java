package dev.mlua.modules.registry.service;

import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

import dev.mlua.runtime.config.MLuaConfigRegistry;

/** Installs addon configuration registration methods. */
final class MLuaRegistryConfigApi {
   private MLuaRegistryConfigApi() { }

   static void install(MLuaRegistryService service, LuaTable config) {
      config.set("Register", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            service.ensureRegistryPhase();
            String scope = MLuaConfigRegistry.scopeFromString(args.checkjstring(2));
            String name = args.checkjstring(3);
            service.registerConfig(scope, name, args.checktable(4));
            return LuaValue.NONE;
         }
      });
      config.set("RegisterCommon", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            service.ensureRegistryPhase();
            service.registerConfig("common", args.checkjstring(2), args.checktable(3));
            return LuaValue.NONE;
         }
      });
      config.set("RegisterClient", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            service.ensureRegistryPhase();
            service.registerConfig("client", args.checkjstring(2), args.checktable(3));
            return LuaValue.NONE;
         }
      });
      config.set("RegisterServer", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            service.ensureRegistryPhase();
            service.registerConfig("server", args.checkjstring(2), args.checktable(3));
            return LuaValue.NONE;
         }
      });
   }
}
