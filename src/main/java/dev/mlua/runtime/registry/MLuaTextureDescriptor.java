package dev.mlua.runtime.registry;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

public record MLuaTextureDescriptor(Map<String, String> faces) {
   public MLuaTextureDescriptor {
      Objects.requireNonNull(faces, "faces");
      faces = Collections.unmodifiableMap(new LinkedHashMap<>(faces));
   }

   public static MLuaTextureDescriptor empty() {
      return new MLuaTextureDescriptor(Map.of());
   }

   public static MLuaTextureDescriptor all(String texture) {
      return texture != null && !texture.isBlank() ? single("all", texture) : empty();
   }

   public static MLuaTextureDescriptor fromLua(LuaValue value) {
      if (value.isnil()) return empty();
      if (value.isstring()) return single("all", value.checkjstring());
      if (!value.istable()) {
         throw new IllegalArgumentException("[MLua] Texture property must be a string or table");
      }

      LuaTable table = value.checktable();
      Map<String, String> faces = new LinkedHashMap<>();
      LuaValue key = LuaValue.NIL;
      while (true) {
         Varargs next = table.next(key);
         key = next.arg1();
         if (key.isnil()) break;
         if (!key.isstring() || !next.arg(2).isstring()) {
            throw new IllegalArgumentException("[MLua] Texture faces must map names to texture ids");
         }
         faces.put(normalizeFace(key.checkjstring()), next.arg(2).checkjstring());
      }
      return new MLuaTextureDescriptor(faces);
   }

   public static MLuaTextureDescriptor fromProperties(Map<String, Object> properties, String... keys) {
      Objects.requireNonNull(properties, "properties");
      for (String key : keys) {
         Object value = properties.get(key);
         if (value == null) continue;
         if (value instanceof String texture) return single("all", texture);
         if (value instanceof Map<?, ?> map) {
            Map<String, String> faces = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
               if (!(entry.getKey() instanceof String face) || !(entry.getValue() instanceof String texture)) {
                  throw new IllegalArgumentException("[MLua] Texture faces must map names to texture ids");
               }
               faces.put(normalizeFace(face), texture);
            }
            return new MLuaTextureDescriptor(faces);
         }
         throw new IllegalArgumentException("[MLua] Texture property '"+key+"' must be a string or map");
      }
      return empty();
   }

   public boolean isEmpty() {
      return faces.isEmpty();
   }

   public String all() {
      return faces.get("all");
   }

   public MLuaTextureDescriptor resolve(String ownerModId, String defaultFolder) {
      Map<String, String> resolved = new LinkedHashMap<>();
      faces.forEach((face, texture) -> resolved.put(face,
         MLuaAssetResolver.resolveTextureString(ownerModId, texture, defaultFolder)));
      return resolved.isEmpty() ? empty() : new MLuaTextureDescriptor(resolved);
   }

   private static MLuaTextureDescriptor single(String face, String texture) {
      return new MLuaTextureDescriptor(Map.of(face, texture));
   }

   private static String normalizeFace(String face) {
      return switch (face.toLowerCase(Locale.ROOT)) {
         case "all", "texture", "item", "layer0" -> "all";
         case "front", "north" -> "north";
         case "back", "south" -> "south";
         case "left", "west" -> "west";
         case "right", "east" -> "east";
         case "top", "up" -> "up";
         case "bottom", "down" -> "down";
         case "particle" -> "particle";
         default -> face.toLowerCase(Locale.ROOT);
      };
   }
}
