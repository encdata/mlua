package dev.mlua.common.mixin;

import java.util.function.Supplier;
import org.luaj.vm2.LuaTable;

/** Compatibility entry point for bootstrap code; the Lua API is shared with runtime scripts. */
public final class MLuaEarlyMixinApi {
   private MLuaEarlyMixinApi() { }

   public static LuaTable create(Supplier<String> activeScope) {
      return MLuaMixinLuaApi.create(activeScope);
   }
}
