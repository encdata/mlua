package dev.mlua.core;

import dev.mlua.module.MLuaModuleLoader;
import dev.mlua.core.engine.MLuaLuaApiDocSnapshot;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class MLuaDocGenerator {
   private MLuaDocGenerator() {
   }

   public static void main(String[] args) {
      MLuaModuleLoader.get().loadForDocumentation();
      Path gameDir = Paths.get("run");
      MLuaDocumentation.generate(gameDir);
      MLuaLuaApiDocSnapshot.DocumentationSnapshot snapshot = MLuaLuaApiDocSnapshot.generate();
      MLuaDocumentation.writeGeneratedLuaApi(gameDir, snapshot.inventoryHtml());
   }
}
