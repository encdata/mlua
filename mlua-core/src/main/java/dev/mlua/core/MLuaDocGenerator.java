package dev.mlua.core;

import dev.mlua.module.MLuaModuleLoader;
import java.nio.file.Paths;

public final class MLuaDocGenerator {
   private MLuaDocGenerator() {
   }

   public static void main(String[] args) {
      MLuaModuleLoader.get().loadAll();
      MLuaDocumentation.generate(Paths.get("run"));
   }
}
