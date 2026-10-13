package dev.mlua.minecraft;

import net.minecraft.resources.Identifier;

public final class MLuaResourceLocations {
   private MLuaResourceLocations() {}

   public static Identifier parse(String id) {
      Identifier location = tryParseOrNull(id);
      if (location == null) throw new IllegalArgumentException("Invalid resource location: " + id);
      return location;
   }

   public static Identifier build(String namespace, String path) {
      return Identifier.fromNamespaceAndPath(namespace, path);
   }

   public static Identifier tryParseOrNull(String id) {
      if (id == null || id.trim().isEmpty()) return null;
      try {
         String value = id.trim();
         return Identifier.parse(value.indexOf(':') < 0 ? "minecraft:" + value : value);
      } catch (RuntimeException ignored) {
         return null;
      }
   }

   public static String namespace(Identifier id) { return id.getNamespace(); }
   public static String path(Identifier id) { return id.getPath(); }
}
