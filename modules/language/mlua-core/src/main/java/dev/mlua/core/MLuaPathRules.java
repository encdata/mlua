package dev.mlua.core;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;

public final class MLuaPathRules {
   private MLuaPathRules() {
   }

   public static boolean isModDataPath(String path) {
      if (path != null && !path.isBlank() && path.indexOf(0) < 0 && !path.contains(":")) {
         try {
            Path candidate = Path.of(path);
            if (candidate.isAbsolute()) {
               return false;
            } else {
               Path normalized = candidate.normalize();
               return !normalized.startsWith("..") && !normalized.toString().equals("..");
            }
         } catch (InvalidPathException var3) {
            return false;
         }
      } else {
         return false;
      }
   }
}
