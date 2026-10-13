package dev.mlua.core.script;

import dev.mlua.common.enums.ScriptType;
import java.nio.file.Path;

public record MLuaScript(String ownerModId, ScriptType type, Path root, Path path, String moduleId, String source) {
   public MLuaScript(String ownerModId, ScriptType type, Path root, Path path, String moduleId, String source) {
      this.ownerModId = ownerModId;
      this.type = type;
      this.root = root;
      this.path = path;
      this.moduleId = moduleId;
      this.source = source;
   }

   public String displayName() {
      return this.ownerModId + ":" + this.root.relativize(this.path).toString().replace(this.root.getFileSystem().getSeparator(), "/");
   }

   public String ownerModId() {
      return this.ownerModId;
   }

   public ScriptType type() {
      return this.type;
   }

   public Path root() {
      return this.root;
   }

   public Path path() {
      return this.path;
   }

   public String moduleId() {
      return this.moduleId;
   }

   public String source() {
      return this.source;
   }
}
