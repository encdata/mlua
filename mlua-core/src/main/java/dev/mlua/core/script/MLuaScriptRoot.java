package dev.mlua.core.script;

import java.nio.file.Path;

public record MLuaScriptRoot(String modId, Path path) {
   public MLuaScriptRoot(String modId, Path path) {
      this.modId = modId;
      this.path = path;
   }

   public String modId() {
      return this.modId;
   }

   public Path path() {
      return this.path;
   }
}
