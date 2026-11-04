package dev.mlua.core.engine;

import dev.mlua.core.MLuaContext;
import dev.mlua.core.script.MLuaScript;
import dev.mlua.common.enums.Side;
import java.util.List;
import org.luaj.vm2.LuaValue;

public interface MLuaEngine {
   MLuaValue execute(MLuaScript var1, MLuaContext var2);

   MLuaValue executeResumable(MLuaScript var1, MLuaContext var2);

   boolean executeEntrypoint(MLuaScript var1, MLuaContext var2, String var3);

   default boolean executeEntrypoint(MLuaScript script, MLuaContext context, String entrypointName,
                                     LuaValue event) {
      return executeEntrypoint(script, context, entrypointName);
   }

   MLuaValue require(String var1, MLuaContext var2);

   void replaceScripts(List<MLuaScript> scripts);

   void resetSide(Side side);
}
