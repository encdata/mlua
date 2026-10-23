package dev.mlua.runtime.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import dev.mlua.runtime.config.MLuaConfigRegistry.ConfigValue;
import dev.mlua.runtime.config.MLuaConfigRegistry.InstalledConfigSet;

/** Owns config JSON migration, validation, and atomic persistence for installed config sets. */
final class MLuaConfigStorage {
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

   private MLuaConfigStorage() { }

   static void loadOrCreate(InstalledConfigSet set) {
      Path filePath = set.filePath();
      try {
         Files.createDirectories(filePath.getParent());
         JsonObject data = new JsonObject();
         for (Map.Entry<String, ConfigValue> entry : set.values().entrySet()) {
            setJsonValue(data, entry.getKey(), entry.getValue().defaultValue());
         }

         if (Files.exists(filePath)) {
            try (Reader reader = Files.newBufferedReader(filePath)) {
               JsonObject existing = JsonParser.parseReader(reader).getAsJsonObject();
               mergeInto(data, existing);
            } catch (RuntimeException | IOException ignored) {
            }
         }

         for (Map.Entry<String, ConfigValue> entry : set.values().entrySet()) {
            Object loaded = resolveJsonValue(data, entry.getKey());
            if (loaded != null) {
               Object converted = coerceJsonValue(loaded, entry.getValue().typeName());
               if (converted != null && entry.getValue().isAllowedValue(converted)) {
                  entry.getValue().set(converted);
               }
            }
         }

         writeJson(set);
      } catch (IOException e) {
         throw new RuntimeException("[MLua] Failed to write config for '" + set.ownerModId() + "'", e);
      }
   }

   static void writeJson(InstalledConfigSet set) {
      Path filePath = set.filePath();
      try {
         Files.createDirectories(filePath.getParent());
         JsonObject data = new JsonObject();
         for (Map.Entry<String, ConfigValue> entry : set.values().entrySet()) {
            setJsonValue(data, entry.getKey(), entry.getValue().get());
         }

         Path temporaryFile = Files.createTempFile(filePath.getParent(), "mlua-config-", ".tmp");
         try {
            try (Writer writer = Files.newBufferedWriter(temporaryFile)) {
               GSON.toJson(data, writer);
            }
            try {
               Files.move(temporaryFile, filePath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException | FileAlreadyExistsException ignored) {
               Files.move(temporaryFile, filePath, StandardCopyOption.REPLACE_EXISTING);
            }
         } finally {
            Files.deleteIfExists(temporaryFile);
         }
      } catch (IOException e) {
         throw new RuntimeException("[MLua] Failed to write config for '" + set.ownerModId() + "'", e);
      }
   }

   private static void setJsonValue(JsonObject root, String path, Object value) {
      String[] parts = path.split("\\.");
      JsonObject parent = root;
      for (int i = 0; i < parts.length - 1; i++) {
         if (!parent.has(parts[i]) || !parent.get(parts[i]).isJsonObject()) {
            parent.add(parts[i], new JsonObject());
         }
         parent = parent.getAsJsonObject(parts[i]);
      }
      parent.add(parts[parts.length - 1], GSON.toJsonTree(value));
   }

   private static void mergeInto(JsonObject base, JsonObject overlay) {
      for (String key : overlay.keySet()) {
         JsonElement overlayValue = overlay.get(key);
         if (overlayValue.isJsonObject() && base.has(key) && base.get(key).isJsonObject()) {
            mergeInto(base.getAsJsonObject(key), overlayValue.getAsJsonObject());
         } else {
            base.add(key, overlayValue.deepCopy());
         }
      }
   }

   private static Object resolveJsonValue(JsonObject root, String path) {
      String[] parts = path.split("\\.");
      JsonObject current = root;
      for (int i = 0; i < parts.length - 1; i++) {
         if (!current.has(parts[i]) || !current.get(parts[i]).isJsonObject()) {
            return null;
         }
         current = current.getAsJsonObject(parts[i]);
      }

      JsonElement element = current.get(parts[parts.length - 1]);
      if (element == null || !element.isJsonPrimitive()) {
         return null;
      }

      var primitive = element.getAsJsonPrimitive();
      if (primitive.isBoolean()) {
         return primitive.getAsBoolean();
      } else if (primitive.isNumber()) {
         double number = primitive.getAsDouble();
         if (number == Math.floor(number) && !Double.isInfinite(number)) {
            long longValue = (long)number;
            return longValue >= Integer.MIN_VALUE && longValue <= Integer.MAX_VALUE ? (int)longValue : longValue;
         } else {
            return number;
         }
      } else {
         return primitive.isString() ? primitive.getAsString() : null;
      }
   }

   private static Object coerceJsonValue(Object value, String typeName) {
      if (value == null) {
         return null;
      } else {
         try {
            return switch (typeName) {
               case "boolean" -> value instanceof Boolean ? value : null;
               case "int" -> {
                  if (!(value instanceof Number number)) yield null;
                  double numeric = number.doubleValue();
                  yield Double.isFinite(numeric) && numeric == Math.rint(numeric)
                     && numeric >= Integer.MIN_VALUE && numeric <= Integer.MAX_VALUE ? (int)numeric : null;
               }
               case "long" -> {
                  if (!(value instanceof Number number)) yield null;
                  double numeric = number.doubleValue();
                  yield Double.isFinite(numeric) && numeric == Math.rint(numeric)
                     && numeric >= Long.MIN_VALUE && numeric < 0x1.0p63 ? (long)numeric : null;
               }
               case "double" -> {
                  if (!(value instanceof Number number)) yield null;
                  double numeric = number.doubleValue();
                  yield Double.isFinite(numeric) ? numeric : null;
               }
               case "string", "enum" -> value instanceof String ? value : null;
               default -> null;
            };
         } catch (Exception ignored) {
            return null;
         }
      }
   }
}
