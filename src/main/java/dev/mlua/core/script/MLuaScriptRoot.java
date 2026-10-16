package dev.mlua.core.script;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;

public record MLuaScriptRoot(String modId, Path path) {
   public MLuaScriptRoot {
      if (modId == null || modId.isBlank()) {
         throw new IllegalArgumentException("MLua script root needs a non-empty addon id");
      }
      String normalizedModId = modId.trim().toLowerCase(Locale.ROOT);
      if (!normalizedModId.matches("[a-z0-9_.-]+")) {
         throw new IllegalArgumentException("Invalid MLua script root addon id '" + modId + "'");
      }
      modId = normalizedModId;
      Path suppliedPath = Objects.requireNonNull(path, "MLua script root path");
      if (suppliedPath.toString().isBlank()) {
         throw new IllegalArgumentException("MLua script root path must not be empty");
      }
      Path normalizedPath = suppliedPath.normalize();
      path = normalizedPath.toString().isBlank()
         ? suppliedPath.getFileSystem().getPath(".") : normalizedPath;
   }
}
