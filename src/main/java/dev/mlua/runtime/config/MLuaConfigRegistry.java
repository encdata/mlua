package dev.mlua.runtime.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.mlua.common.enums.Side;
import dev.mlua.loaderapi.LoaderApi;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraftforge.fml.config.ModConfig;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

public final class MLuaConfigRegistry {
   private static final MLuaConfigRegistry INSTANCE = new MLuaConfigRegistry();
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   private final Map<String, Map<String, PendingConfigSet>> pending = new LinkedHashMap();
   private final Map<String, Map<String, InstalledConfigSet>> installedRegistry = new LinkedHashMap();
   private final Map<String, List<InstalledConfigSet>> byAttachedContainer = new LinkedHashMap();
   private boolean installed;

   public static MLuaConfigRegistry get() {
      return INSTANCE;
   }

   public synchronized ConfigSectionHandle register(String ownerModId, String scope, String sectionName, LuaTable values) {
      if (this.installed) {
         throw new IllegalStateException("[MLua] Cannot register config '" + sectionName + "': configs are already installed.");
      } else if (sectionName != null && !sectionName.isBlank()) {
         Map<String, PendingConfigSet> byScope = this.pending.computeIfAbsent(ownerModId, ignored -> new LinkedHashMap());
         String normalizedScope = scopeFromString(scope);
         PendingConfigSet set = byScope.computeIfAbsent(normalizedScope,
            ignored -> new PendingConfigSet(ownerModId, normalizedScope));
         try {
            Map<String, ConfigValue> sectionValues = set.registerSection(sectionName, values);
            return new ConfigSectionHandle(ownerModId, normalizedScope, sectionName, sectionValues);
         } catch (RuntimeException | Error failure) {
            if (set.isEmpty()) byScope.remove(normalizedScope, set);
            if (byScope.isEmpty()) this.pending.remove(ownerModId, byScope);
            throw failure;
         }
      } else {
         throw new LuaError("[MLua] Config section name must not be blank.");
      }
   }

   public synchronized ConfigSectionHandle register(String ownerModId, ModConfig.Type type, String sectionName, LuaTable values) {
      return this.register(ownerModId, scopeName(type), sectionName, values);
   }

   public synchronized boolean unregister(ConfigSectionHandle handle) {
      if (handle == null) return false;
      boolean removed = false;
      Map<String, PendingConfigSet> byScope = this.pending.get(handle.ownerModId());
      PendingConfigSet pendingSet = byScope == null ? null : byScope.get(handle.scope());
      if (pendingSet != null) {
         removed |= pendingSet.remove(handle);
         if (pendingSet.values().isEmpty()) byScope.remove(handle.scope());
         if (byScope.isEmpty()) this.pending.remove(handle.ownerModId());
      }
      InstalledConfigSet installedSet = this.installedSet(handle.ownerModId(), handle.scope());
      if (installedSet != null) {
         removed |= removeExpectedValues(installedSet.values(), handle.values());
         if (installedSet.values().isEmpty()) {
            Map<String, InstalledConfigSet> byInstalledScope = this.installedRegistry.get(handle.ownerModId());
            if (byInstalledScope != null) {
               byInstalledScope.remove(handle.scope(), installedSet);
               if (byInstalledScope.isEmpty()) this.installedRegistry.remove(handle.ownerModId());
            }
            List<InstalledConfigSet> attached = this.byAttachedContainer.get(installedSet.attachedModId());
            if (attached != null) {
               attached.remove(installedSet);
               if (attached.isEmpty()) this.byAttachedContainer.remove(installedSet.attachedModId());
            }
         }
      }
      return removed;
   }

   public synchronized void install() {
      if (!this.installed) {
         this.pending.values().forEach(byScope -> byScope.values().forEach(this::installSet));
         this.pending.clear();
         this.installed = true;
      }
   }

   public synchronized boolean hasConfig(String ownerModId, String scope) {
      InstalledConfigSet set = this.installedSet(ownerModId, scopeFromString(scope));
      return set != null && !set.values().isEmpty();
   }

   public synchronized boolean hasConfig(String ownerModId, ModConfig.Type type) {
      return this.hasConfig(ownerModId, scopeName(type));
   }

   public synchronized boolean hasValue(String ownerModId, String scope, String path) {
      InstalledConfigSet set = this.installedSet(ownerModId, scopeFromString(scope));
      return set != null && set.values().containsKey(path);
   }

   public synchronized boolean hasValue(String ownerModId, ModConfig.Type type, String path) {
      return this.hasValue(ownerModId, scopeName(type), path);
   }

   public synchronized Object getValue(String ownerModId, String scope, String path) {
      InstalledConfigSet set = this.requiredSet(ownerModId, scopeFromString(scope));
      ConfigValue value = set.values().get(path);
      if (value == null) {
         throw new LuaError("[MLua] Unknown " + scope + " config value '" + path + "' for mod '" + ownerModId + "'.");
      } else {
         return value.get();
      }
   }

   public synchronized Object getValue(String ownerModId, ModConfig.Type type, String path) {
      return this.getValue(ownerModId, scopeName(type), path);
   }

   public synchronized void setValue(String ownerModId, String scope, String path, Object value) {
      InstalledConfigSet set = this.requiredSet(ownerModId, scopeFromString(scope));
      ConfigValue configValue = set.values().get(path);
      if (configValue == null) {
         throw new LuaError("[MLua] Unknown " + scope + " config value '" + path + "' for mod '" + ownerModId + "'.");
      } else {
         configValue.set(coerceValue(path, configValue.defaultValue(), value));
      }
   }

   public synchronized void setValue(String ownerModId, ModConfig.Type type, String path, Object value) {
      this.setValue(ownerModId, scopeName(type), path, value);
   }

   public synchronized void save(String ownerModId, String scope) {
      InstalledConfigSet set = this.installedSet(ownerModId, scopeFromString(scope));
      if (set != null) {
         this.writeJson(set);
      }
   }

   public synchronized void save(String ownerModId, ModConfig.Type type) {
      this.save(ownerModId, scopeName(type));
   }

   public synchronized List<InstalledConfigSet> attachedTo(String attachedModId) {
      return List.copyOf(this.byAttachedContainer.getOrDefault(attachedModId, List.of()));
   }

   public synchronized List<String> describeForContainer(String attachedModId) {
      List<String> lines = new ArrayList();
      List<InstalledConfigSet> sets = this.attachedTo(attachedModId);
      if (sets.isEmpty()) {
         lines.add("No MLua configs attached.");
         return lines;
      } else {
         sets.stream().sorted(Comparator.comparing(InstalledConfigSet::ownerModId).thenComparing(InstalledConfigSet::scope)).forEach(set -> {
            lines.add("Owner: " + set.ownerModId() + "  Scope: " + set.scope());
            lines.add("File: " + set.filePath());
            lines.add("Loaded: " + set.loaded());
            set.values().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
               lines.add("  " + entry.getKey() + " = " + String.valueOf(entry.getValue().get()));
            });
            lines.add("");
         });
         return lines;
      }
   }

   private void installSet(PendingConfigSet pendingSet) {
      if (!pendingSet.values().isEmpty()) {
         String ownerModId = pendingSet.ownerModId();
         String scope = pendingSet.scope();
         String attachedModId = this.resolveAttachedModId(ownerModId);
         InstalledConfigSet installedSet = new InstalledConfigSet(ownerModId, attachedModId, scope, new LinkedHashMap(pendingSet.values()), this.configFile(ownerModId), true);
         this.installedRegistry.computeIfAbsent(ownerModId, ignored -> new LinkedHashMap()).put(scope, installedSet);
         this.byAttachedContainer.computeIfAbsent(attachedModId, ignored -> new ArrayList()).add(installedSet);
         this.loadOrCreateJson(installedSet);
      }
   }

   private static boolean removeExpectedValues(Map<String, ConfigValue> target, Map<String, ConfigValue> expected) {
      boolean removed = false;
      for (Map.Entry<String, ConfigValue> entry : expected.entrySet()) {
         removed |= target.remove(entry.getKey(), entry.getValue());
      }
      return removed;
   }

   private void loadOrCreateJson(InstalledConfigSet set) {
      Path filePath = set.filePath();
      try {
         Files.createDirectories(filePath.getParent());
         JsonObject data = new JsonObject();
         for (Map.Entry<String, ConfigValue> entry : set.values().entrySet()) {
            this.setJsonValue(data, entry.getKey(), entry.getValue().defaultValue());
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
               if (converted != null) {
                  entry.getValue().set(converted);
               }
            }
         }

         this.writeJson(set);
      } catch (IOException e) {
         throw new RuntimeException("[MLua] Failed to write config for '" + set.ownerModId() + "'", e);
      }
   }

   private void writeJson(InstalledConfigSet set) {
      Path filePath = set.filePath();
      try {
         Files.createDirectories(filePath.getParent());
         JsonObject data = new JsonObject();
         for (Map.Entry<String, ConfigValue> entry : set.values().entrySet()) {
            this.setJsonValue(data, entry.getKey(), entry.getValue().get());
         }

         try (Writer writer = Files.newBufferedWriter(filePath)) {
            GSON.toJson(data, writer);
         }
      } catch (IOException e) {
         throw new RuntimeException("[MLua] Failed to write config for '" + set.ownerModId() + "'", e);
      }
   }

   private InstalledConfigSet installedSet(String ownerModId, String scope) {
      Map<String, InstalledConfigSet> byScope = this.installedRegistry.get(ownerModId);
      return byScope == null ? null : byScope.get(scope);
   }

   private InstalledConfigSet requiredSet(String ownerModId, String scope) {
      InstalledConfigSet set = this.installedSet(ownerModId, scope);
      if (set == null) {
         throw new LuaError("[MLua] No " + scope + " config registered for mod '" + ownerModId + "'.");
      } else {
         return set;
      }
   }

   private String resolveAttachedModId(String ownerModId) {
      if (this.modExists(ownerModId)) {
         return ownerModId;
      }
      return this.modExists("mlua") ? "mlua" : ownerModId;
   }

   private boolean modExists(String modId) {
      try {
         return LoaderApi.platform().hasMod(modId);
      } catch (Throwable ignored) {
         return false;
      }
   }

   private Path configFile(String ownerModId) {
      return this.configDir().resolve(ownerModId).resolve("mlua.json");
   }

   private Path configDir() {
      try {
         return LoaderApi.platform().configDir();
      } catch (Throwable ignored) {
         return Path.of("config");
      }
   }

   private void setJsonValue(JsonObject root, String path, Object value) {
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
               case "int" -> value instanceof Number ? ((Number)value).intValue() : null;
               case "long" -> value instanceof Number ? ((Number)value).longValue() : null;
               case "double" -> value instanceof Number ? ((Number)value).doubleValue() : null;
               case "string" -> value instanceof String ? value : null;
               default -> null;
            };
         } catch (Exception ignored) {
            return null;
         }
      }
   }

   private static Object coerceValue(String path, Object defaultValue, Object value) {
      if (defaultValue instanceof Boolean) {
         if (value instanceof Boolean bool) {
            return bool;
         }
      } else if (defaultValue instanceof Integer) {
         if (value instanceof Number number) {
            return number.intValue();
         }
      } else if (defaultValue instanceof Double) {
         if (value instanceof Number number) {
            return number.doubleValue();
         }
      } else if (defaultValue instanceof Long) {
         if (value instanceof Number number) {
            return number.longValue();
         }
      } else if (defaultValue instanceof String && value instanceof String string) {
         return string;
      }

      throw new LuaError("[MLua] Config '" + path + "' expects a " + valueTypeName(defaultValue) + ".");
   }

   private static String valueTypeName(Object defaultValue) {
      if (defaultValue instanceof Boolean) {
         return "boolean";
      } else if (defaultValue instanceof Integer) {
         return "integer";
      } else if (defaultValue instanceof Long) {
         return "long";
      } else if (defaultValue instanceof Number) {
         return "number";
      } else {
         return defaultValue instanceof String ? "string" : "supported value";
      }
   }

   private static void defineEntries(Map<String, ConfigValue> values, List<String> path, LuaTable table) {
      LuaValue key = LuaValue.NIL;

      while(true) {
         org.luaj.vm2.Varargs next = table.next(key);
         key = next.arg1();
         if (key.isnil()) {
            return;
         }

         LuaValue value = next.arg(2);
         String name = key.checkjstring();
         ArrayList<String> childPath = new ArrayList(path);
         childPath.add(name);
         if (value.istable() && !isValueDescriptor(value.checktable())) {
            defineEntries(values, childPath, value.checktable());
         } else {
            ConfigDescriptor descriptor = descriptorFor(value, childPath);
            values.put(String.join(".", childPath), new ConfigValue(descriptor.typeName(), descriptor.defaultValue()));
         }
      }
   }

   private static void validateConfigTable(LuaTable table,
                                          java.util.IdentityHashMap<LuaTable, Boolean> active,
                                          java.util.IdentityHashMap<LuaTable, Boolean> visited,
                                          int depth) {
      if (depth > 64) throw new LuaError("[MLua] Config definitions cannot be nested more than 64 tables deep.");
      if (active.containsKey(table)) throw new LuaError("[MLua] Config definitions cannot contain cyclic tables.");
      if (visited.containsKey(table)) return;
      active.put(table, Boolean.TRUE);
      LuaValue key = LuaValue.NIL;
      while (true) {
         org.luaj.vm2.Varargs entry = table.next(key);
         key = entry.arg1();
         if (key.isnil()) break;
         LuaValue value = entry.arg(2);
         if (value.istable()) {
            validateConfigTable(value.checktable(), active, visited, depth + 1);
         }
      }
      active.remove(table);
      visited.put(table, Boolean.TRUE);
   }

   private static boolean isValueDescriptor(LuaTable table) {
      return !table.get("default").isnil() || !table.get("Default").isnil() || !table.get("value").isnil() || !table.get("Value").isnil();
   }

   private static ConfigDescriptor descriptorFor(LuaValue value, List<String> path) {
      if (value.istable() && isValueDescriptor(value.checktable())) {
         LuaTable table = value.checktable();
         LuaValue defaultValue = first(table, "default", "Default", "value", "Value");
         if (defaultValue.isnil()) {
            throw new LuaError("[MLua] Config '" + String.join(".", path) + "' is missing a default value.");
         } else {
            String typeName = readTypeName(table, defaultValue, path);
            Object converted = convertDefault(defaultValue, path, typeName);
            Double min = readOptionalNumber(table, "min", "Min");
            Double max = readOptionalNumber(table, "max", "Max");
            validateRangeUsage(path, typeName, min, max);
            return new ConfigDescriptor(path, typeName, converted);
         }
      } else {
         String typeName = inferTypeName(value, path);
         return new ConfigDescriptor(path, typeName, convertDefault(value, path, typeName));
      }
   }

   private static void validateRangeUsage(List<String> path, String typeName, Double min, Double max) {
      if ((min != null || max != null) && ("boolean".equals(typeName) || "string".equals(typeName))) {
         throw new LuaError("[MLua] Config '" + String.join(".", path) + "' only supports Min/Max on numeric types.");
      }
   }

   private static LuaValue first(LuaTable table, String... keys) {
      for (String key : keys) {
         LuaValue value = table.get(key);
         if (!value.isnil()) {
            return value;
         }
      }

      return LuaValue.NIL;
   }

   private static Double readOptionalNumber(LuaTable table, String... keys) {
      LuaValue value = first(table, keys);
      return value.isnil() ? null : value.checkdouble();
   }

   private static Object convertDefault(LuaValue value, List<String> path, String typeName) {
      return switch (typeName) {
         case "boolean" -> {
            if (!value.isboolean()) {
               throw new LuaError("[MLua] Config '" + String.join(".", path) + "' expects a boolean default.");
            }
            yield value.toboolean();
         }
         case "int" -> {
            if (!value.isnumber()) {
               throw new LuaError("[MLua] Config '" + String.join(".", path) + "' expects a numeric default.");
            }
            yield value.toint();
         }
         case "long" -> {
            if (!value.isnumber()) {
               throw new LuaError("[MLua] Config '" + String.join(".", path) + "' expects a numeric default.");
            }
            yield value.tolong();
         }
         case "double" -> {
            if (!value.isnumber()) {
               throw new LuaError("[MLua] Config '" + String.join(".", path) + "' expects a numeric default.");
            }
            yield value.todouble();
         }
         case "string" -> {
            if (!value.isstring()) {
               throw new LuaError("[MLua] Config '" + String.join(".", path) + "' expects a string default.");
            }
            yield value.tojstring();
         }
         default -> throw new LuaError("[MLua] Unsupported config type '" + typeName + "' for '" + String.join(".", path) + "'.");
      };
   }

   private static String readTypeName(LuaTable table, LuaValue defaultValue, List<String> path) {
      LuaValue explicit = first(table, "type", "Type");
      return explicit.isnil() ? inferTypeName(defaultValue, path) : normalizeTypeName(explicit.checkjstring(), path);
   }

   private static String inferTypeName(LuaValue value, List<String> path) {
      if (value.isboolean()) {
         return "boolean";
      } else if (value.isnumber()) {
         double number = value.todouble();
         return Math.rint(number) == number && number >= Integer.MIN_VALUE && number <= Integer.MAX_VALUE ? "int" : "double";
      } else if (value.isstring()) {
         return "string";
      } else {
         throw new LuaError("[MLua] Config '" + String.join(".", path) + "' must use boolean, number, or string defaults.");
      }
   }

   private static String normalizeTypeName(String raw, List<String> path) {
      String normalized = raw.toLowerCase(Locale.ROOT);
      return switch (normalized) {
         case "bool", "boolean" -> "boolean";
         case "int", "integer" -> "int";
         case "long" -> "long";
         case "float", "double", "number" -> "double";
         case "string" -> "string";
         default -> throw new LuaError("[MLua] Config '" + String.join(".", path) + "' has unknown Type '" + raw + "'.");
      };
   }

   public static String scopeFromString(String scope) {
      String normalized = scope.toLowerCase(Locale.ROOT);
      return switch (normalized) {
         case "client" -> "client";
         case "common" -> "common";
         case "server" -> "server";
         default -> throw new LuaError("[MLua] Unknown config scope '" + scope + "'. Use client, common, or server.");
      };
   }

   public static ModConfig.Type typeFromScope(String scope) {
      return switch (scopeFromString(scope)) {
         case "client" -> ModConfig.Type.CLIENT;
         case "common" -> ModConfig.Type.COMMON;
         case "server" -> ModConfig.Type.SERVER;
         default -> throw new LuaError("[MLua] Unknown config scope '" + scope + "'.");
      };
   }

   public ModConfig.Type defaultScopeFor(Side side, String ownerModId, String path) {
      return typeFromScope(this.defaultScopeNameFor(side, ownerModId, path));
   }

   public String defaultScopeNameFor(Side side, String ownerModId, String path) {
      synchronized(this) {
         if (side == Side.CLIENT && this.hasValue(ownerModId, "client", path)) {
            return "client";
         } else if (side == Side.SERVER && this.hasValue(ownerModId, "server", path)) {
            return "server";
         } else if (this.hasValue(ownerModId, "common", path)) {
            return "common";
         } else if (this.hasValue(ownerModId, "client", path)) {
            return "client";
         } else if (this.hasValue(ownerModId, "server", path)) {
            return "server";
         } else {
            throw new LuaError("[MLua] Unknown config value '" + path + "' for mod '" + ownerModId + "'.");
         }
      }
   }

   public static String scopeName(ModConfig.Type type) {
      return switch (type) {
         case CLIENT -> "client";
         case COMMON -> "common";
         case SERVER -> "server";
      };
   }

   private static record ConfigDescriptor(List<String> path, String typeName, Object defaultValue) {
   }

   public static final class ConfigValue {
      private final String typeName;
      private final Object defaultValue;
      private Object value;

      ConfigValue(String typeName, Object defaultValue) {
         this.typeName = typeName;
         this.defaultValue = defaultValue;
         this.value = defaultValue;
      }

      public String typeName() {
         return this.typeName;
      }

      public Object defaultValue() {
         return this.defaultValue;
      }

      public Object get() {
         return this.value;
      }

      public void set(Object value) {
         this.value = value;
      }
   }

   public static record InstalledConfigSet(String ownerModId, String attachedModId, String scope, Map<String, ConfigValue> values, Path filePath, boolean loaded) {
      public ModConfig.Type type() {
         return MLuaConfigRegistry.typeFromScope(this.scope);
      }
   }

   public static record ConfigSectionHandle(String ownerModId, String scope, String sectionName,
                                           Map<String, ConfigValue> values) { }

   private static final class PendingConfigSet {
      private final String ownerModId;
      private final String scope;
      private final Map<String, ConfigValue> values = new LinkedHashMap();
      private final Map<String, Map<String, ConfigValue>> sections = new LinkedHashMap();

      private PendingConfigSet(String ownerModId, String scope) {
         this.ownerModId = ownerModId;
         this.scope = scope;
      }

      private Map<String, ConfigValue> registerSection(String sectionName, LuaTable section) {
         for (String existing : this.sections.keySet()) {
            if (pathsOverlap(existing, sectionName)) {
               throw new LuaError("[MLua] Config section '" + sectionName + "' overlaps section '"
                  + existing + "' for mod '" + this.ownerModId + "' (" + this.scope + ").");
            }
         }
         MLuaConfigRegistry.validateConfigTable(section, new java.util.IdentityHashMap<>(),
            new java.util.IdentityHashMap<>(), 1);
         Map<String, ConfigValue> staged = new LinkedHashMap<>();
         MLuaConfigRegistry.defineEntries(staged, List.of(sectionName), section);
         for (String candidate : staged.keySet()) {
            for (String existing : this.values.keySet()) {
               if (pathsOverlap(existing, candidate)) {
                  throw new LuaError("[MLua] Config value '" + candidate + "' overlaps existing value '"
                     + existing + "' for mod '" + this.ownerModId + "' (" + this.scope + ").");
               }
            }
         }
         Map<String, ConfigValue> sectionValues = java.util.Collections.unmodifiableMap(staged);
         this.values.putAll(sectionValues);
         this.sections.put(sectionName, sectionValues);
         return sectionValues;
      }

      private boolean remove(ConfigSectionHandle handle) {
         Map<String, ConfigValue> registered = this.sections.get(handle.sectionName());
         if (registered != handle.values()) return false;
         this.sections.remove(handle.sectionName());
         removeExpectedValues(this.values, handle.values());
         return true;
      }

      private boolean isEmpty() {
         return this.sections.isEmpty() && this.values.isEmpty();
      }

      public String ownerModId() {
         return this.ownerModId;
      }

      public String scope() {
         return this.scope;
      }

      public Map<String, ConfigValue> values() {
         return this.values;
      }
   }

   private static boolean pathsOverlap(String first, String second) {
      return first.equals(second) || first.startsWith(second + ".") || second.startsWith(first + ".");
   }
}
