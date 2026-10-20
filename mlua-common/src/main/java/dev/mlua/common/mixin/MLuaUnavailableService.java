package dev.mlua.common.mixin;

import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Service handle for APIs that cannot run during early mixin bootstrap. */
public final class MLuaUnavailableService {
   private static final Logger LOGGER = LoggerFactory.getLogger("MLua");

   private MLuaUnavailableService() { }

   public static LuaTable create(String serviceName) {
      LOGGER.warn("[MLua] GetService(\"{}\") is unavailable until runtime script execution", serviceName);
      return new LuaTable() {
         @Override
         public LuaValue get(LuaValue key) {
            return new VarArgFunction() {
               @Override
               public Varargs invoke(Varargs args) {
                  throw new LuaError("[MLua] Service '" + serviceName + "' method '" + key
                     + "' cannot run during early mixin bootstrap");
               }
            };
         }
      };
   }
}
