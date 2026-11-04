package dev.mlua.core.require;

import dev.mlua.core.MLuaContext;
import dev.mlua.core.engine.MLuaEngine;
import dev.mlua.core.engine.MLuaValue;

public final class MLuaRequire {
   private final MLuaEngine engine;

   public MLuaRequire(MLuaEngine engine) {
      this.engine = engine;
   }

   public MLuaValue require(String path, MLuaContext context) {
      return this.engine.require(path, context);
   }
}
