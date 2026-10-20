package dev.mlua.runtime.registry;

import dev.mlua.minecraft.MLuaResourceLocations;
import net.minecraft.resources.ResourceLocation;

public final class MLuaAssetResolver {
   private MLuaAssetResolver() {
   }

   public static ResourceLocation resolveTexture(String ownerModId, String value, String defaultFolder) {
      return resolve(ownerModId, value, defaultFolder);
   }

   public static ResourceLocation resolveModel(String ownerModId, String value, String defaultFolder) {
      return resolve(ownerModId, value, defaultFolder);
   }

   public static String resolveTextureString(String ownerModId, String value, String defaultFolder) {
      return resolveTexture(ownerModId, value, defaultFolder).toString();
   }

   public static String resolveModelString(String ownerModId, String value, String defaultFolder) {
      return resolveModel(ownerModId, value, defaultFolder).toString();
   }

   public static ResourceLocation resolveTextureFile(String ownerModId, String value, String defaultFolder) {
      ResourceLocation logical = resolveTexture(ownerModId, value, defaultFolder);
      String namespace = MLuaResourceLocations.namespace(logical);
      String path = MLuaResourceLocations.path(logical);
      String suffix = path.endsWith(".png") ? "" : ".png";
      if (path.startsWith("textures/")) {
         return MLuaResourceLocations.build(namespace, path + suffix);
      }
      if (path.startsWith("item/") || path.startsWith("block/")) {
         return MLuaResourceLocations.build(namespace, "textures/" + path + suffix);
      }
      return MLuaResourceLocations.build(namespace, "textures/" + defaultFolder + "/" + path + suffix);
   }

   private static ResourceLocation resolve(String ownerModId, String value, String defaultFolder) {
      if (ownerModId == null || ownerModId.isBlank()) {
         throw new IllegalArgumentException("[MLua] owner mod id is required for asset resolution");
      }
      if (value == null || value.isBlank()) {
         throw new IllegalArgumentException("[MLua] asset value is required");
      }
      String trimmed = value.trim();
      int colon = trimmed.indexOf(':');
      String namespace = colon >= 0 ? trimmed.substring(0, colon) : ownerModId;
      String path = colon >= 0 ? trimmed.substring(colon + 1) : trimmed;
      if (namespace.isBlank() || path.isBlank()) {
         throw new IllegalArgumentException("[MLua] invalid asset location: " + value);
      }
      if (!path.contains("/")) path = defaultFolder + "/" + path;
      return MLuaResourceLocations.build(namespace, path);
   }
}
