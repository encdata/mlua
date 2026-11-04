package dev.mlua.minecraft;

import net.minecraft.resources.ResourceLocation;

public final class MLuaResourceLocations {
   private MLuaResourceLocations() {
   }

   public static ResourceLocation parse(String id) {
      ResourceLocation location = tryParseOrNull(id);
      if (location == null) {
         throw new IllegalArgumentException("Invalid resource location: " + id);
      } else {
         return location;
      }
   }

   public static ResourceLocation build(String namespace, String path) {
      return parse(namespace + ":" + path);
   }

   public static ResourceLocation tryParseOrNull(String id) {
      if (id == null) {
         return null;
      }

      String value = id.trim();
      if (value.isEmpty()) {
         return null;
      }

      try {
         int split = value.indexOf(58);
         if (split < 0) {
            return ResourceLocation.fromNamespaceAndPath("minecraft", value);
         }

         String namespace = value.substring(0, split);
         String path = value.substring(split + 1);
         if (namespace.isEmpty() || path.isEmpty()) {
            return null;
         }

         return ResourceLocation.fromNamespaceAndPath(namespace, path);
      } catch (RuntimeException ignored) {
         return null;
      }
   }

   public static String namespace(ResourceLocation id) {
      String value = id.toString();
      int split = value.indexOf(58);
      return split >= 0 ? value.substring(0, split) : "minecraft";
   }

   public static String path(ResourceLocation id) {
      String value = id.toString();
      int split = value.indexOf(58);
      return split >= 0 ? value.substring(split + 1) : value;
   }
}
