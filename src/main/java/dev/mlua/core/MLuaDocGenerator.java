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
      MLuaModuleLoader.get().registerClasses(MLuaRuntime.get());
      Path gameDir = Paths.get("run");
      MLuaDocumentation.generate(gameDir);
      MLuaLuaApiDocSnapshot.DocumentationSnapshot snapshot = MLuaLuaApiDocSnapshot.generate();
      MLuaDocumentation.writeGeneratedLuaApi(gameDir, snapshot.inventoryHtml(), snapshot.pages());
   }
}
