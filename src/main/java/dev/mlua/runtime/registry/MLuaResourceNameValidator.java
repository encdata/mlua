package dev.mlua.runtime.registry;

import java.util.regex.Pattern;

public final class MLuaResourceNameValidator {
   private static final Pattern PART = Pattern.compile("[a-z0-9_./-]+");

   private MLuaResourceNameValidator() {
   }

   public static void validate(String name) {
      if (name == null || name.isBlank() || !PART.matcher(name).matches() || name.contains("..")) {
         throw new IllegalArgumentException("[MLua] Invalid resource name: " + name);
      }
   }
}
