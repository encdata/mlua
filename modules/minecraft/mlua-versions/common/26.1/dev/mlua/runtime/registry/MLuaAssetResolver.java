package dev.mlua.runtime.registry;

import java.util.Set;
import dev.mlua.minecraft.MLuaResourceLocations;
import net.minecraft.resources.Identifier;

/** Resolves addon asset names to the identifier type used by Minecraft 26.1. */
public final class MLuaAssetResolver {
   private static final Set<String> LEGACY_TEXTURE_FOLDERS = Set.of(
      "item", "block", "gui", "entity", "models");

   private MLuaAssetResolver() { }

   public static Identifier resolveTexture(String ownerModId, String value, String defaultFolder) {
      return resolve(ownerModId, value, defaultFolder);
   }

   public static Identifier resolveModel(String ownerModId, String value, String defaultFolder) {
      return resolve(ownerModId, value, defaultFolder);
   }

   public static String resolveTextureString(String ownerModId, String value, String defaultFolder) {
      return resolveTexture(ownerModId, value, defaultFolder).toString();
   }

   public static String resolveModelString(String ownerModId, String value, String defaultFolder) {
      return resolveModel(ownerModId, value, defaultFolder).toString();
   }

   public static Identifier resolveTextureFile(String ownerModId, String value, String defaultFolder) {
      if (ownerModId == null || ownerModId.isBlank()) {
         throw new IllegalArgumentException("[MLua] owner mod id is required for asset resolution");
      }
      if (value == null || value.isBlank()) {
         throw new IllegalArgumentException("[MLua] asset value is required");
      }
      TextureReference textureReference = parseTextureUri(ownerModId, value.trim());
      if (textureReference != null) {
         String namespace = MLuaResourceLocations.namespace(textureReference.location());
         String path = MLuaResourceLocations.path(textureReference.location());
         if (!path.startsWith("textures/")) path = "textures/" + path;
         return MLuaResourceLocations.build(namespace, path + (path.endsWith(".png") ? "" : ".png"));
      }
      String trimmed = value.trim();
      Identifier logical = resolve(ownerModId, trimmed, defaultFolder);
      String namespace = MLuaResourceLocations.namespace(logical);
      String path = MLuaResourceLocations.path(logical);
      String suffix = path.endsWith(".png") ? "" : ".png";
      if (path.startsWith("textures/")) {
         return MLuaResourceLocations.build(namespace, path + suffix);
      }
      if (path.startsWith("item/") || path.startsWith("block/")
            || path.startsWith(defaultFolder + "/")) {
         return MLuaResourceLocations.build(namespace, "textures/" + path + suffix);
      }
      return MLuaResourceLocations.build(namespace, "textures/" + defaultFolder + "/" + path + suffix);
   }

   public static boolean isTextureUri(String value) {
      if (value == null) return false;
      int marker = value.indexOf(":/");
      return marker > 0 && value.substring(0, marker).matches("[a-zA-Z0-9_.-]+");
   }

   private static Identifier resolve(String ownerModId, String value, String defaultFolder) {
      if (ownerModId == null || ownerModId.isBlank()) {
         throw new IllegalArgumentException("[MLua] owner mod id is required for asset resolution");
      }
      if (value == null || value.isBlank()) {
         throw new IllegalArgumentException("[MLua] asset value is required");
      }
      String trimmed = value.trim();
      Identifier textureReference = resolveTextureUri(ownerModId, trimmed);
      if (textureReference != null) return textureReference;
      int colon = trimmed.indexOf(':');
      String namespace = colon >= 0 ? trimmed.substring(0, colon) : ownerModId;
      String path = colon >= 0 ? trimmed.substring(colon + 1) : trimmed;
      if (namespace.isBlank() || path.isBlank()) {
         throw new IllegalArgumentException("[MLua] invalid asset location: " + value);
      }
      if (!path.contains("/")) path = defaultFolder + "/" + path;
      return MLuaResourceLocations.build(namespace, path);
   }

   /** Resolves namespaced texture paths and legacy texture-folder references. */
   private static Identifier resolveTextureUri(String ownerModId, String value) {
      TextureReference reference = parseTextureUri(ownerModId, value);
      return reference == null ? null : reference.location();
   }

   private static TextureReference parseTextureUri(String ownerModId, String value) {
      int marker = value.indexOf(":/");
      if (marker <= 0) return null;
      String folder = value.substring(0, marker);
      if (!folder.matches("[a-zA-Z0-9_.-]+")) return null;
      String reference = value.substring(marker + 2);
      if (reference.isBlank()) {
         throw new IllegalArgumentException("[MLua] texture reference needs a path: " + value);
      }

      reference = reference.replace('\\', '/');
      while (reference.startsWith("/")) reference = reference.substring(1);
      if (!LEGACY_TEXTURE_FOLDERS.contains(folder)) {
         return new TextureReference(MLuaResourceLocations.build(folder, reference));
      }

      int colon = reference.indexOf(':');
      String namespace = colon >= 0 ? reference.substring(0, colon) : ownerModId;
      String path = colon >= 0 ? reference.substring(colon + 1) : reference;
      if (namespace.isBlank() || path.isBlank()) {
         throw new IllegalArgumentException("[MLua] invalid texture folder reference: " + value);
      }
      if (path.startsWith("textures/")) {
         return new TextureReference(MLuaResourceLocations.build(namespace, path));
      }
      String folderPath = path.startsWith(folder + "/") ? path : folder + "/" + path;
      return new TextureReference(MLuaResourceLocations.build(namespace, folderPath));
   }

   private record TextureReference(Identifier location) { }
}
