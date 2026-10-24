package dev.mlua.minecraft.scripted;

import dev.mlua.core.MLuaContext;

/** Shared addon ownership rules for scripted definition limits and teardown. */
final class MLuaScriptedOwnerPolicy {
   private MLuaScriptedOwnerPolicy() { }

   static boolean sameOwner(MLuaContext left, String leftKey, MLuaContext right, String rightKey) {
      return ownerOf(left, leftKey).equals(ownerOf(right, rightKey));
   }

   static String ownerOf(MLuaContext context, String key) {
      if (context != null && context.ownerModId() != null && !context.ownerModId().isBlank()) {
         return context.ownerModId();
      }
      return ownerFromKey(key);
   }

   private static String ownerFromKey(String key) {
      int separator = key == null ? -1 : key.indexOf(':');
      return separator > 0 ? key.substring(0, separator) : "";
   }
}
