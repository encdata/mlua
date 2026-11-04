package dev.mlua.runtime.config;

import dev.mlua.common.enums.Side;
import dev.mlua.loaderapi.LoaderApi;
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

public final class MLuaConfigRegistry {
   private static final MLuaConfigRegistry INSTANCE = new MLuaConfigRegistry();
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
         String normalizedScope = scopeFromString(scope);
         Map<String, PendingConfigSet> byScope = this.pending.computeIfAbsent(ownerModId, ignored -> new LinkedHashMap());
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
         if (pendingSet.isEmpty()) byScope.remove(handle.scope());
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

   public synchronized Map<String, Object> getValues(String ownerModId, String scope) {
      InstalledConfigSet set = this.requiredSet(ownerModId, scopeFromString(scope));
      Map<String, Object> snapshot = new LinkedHashMap<>();
      set.values().forEach((path, value) -> snapshot.put(path, value.get()));
      return java.util.Collections.unmodifiableMap(snapshot);
   }

   public synchronized void setValue(String ownerModId, String scope, String path, Object value) {
      InstalledConfigSet set = this.requiredSet(ownerModId, scopeFromString(scope));
      ConfigValue configValue = set.values().get(path);
      if (configValue == null) {
         throw new LuaError("[MLua] Unknown " + scope + " config value '" + path + "' for mod '" + ownerModId + "'.");
      } else {
         configValue.set(coerceValue(path, configValue, value));
      }
   }

   /** Validates every requested value before applying any change in the selected scope. */
   public synchronized int setValues(String ownerModId, String scope, Map<String, Object> values) {
      String normalizedScope = scopeFromString(scope);
      InstalledConfigSet set = this.requiredSet(ownerModId, normalizedScope);
      Map<ConfigValue, Object> updates = new LinkedHashMap<>();
      for (Map.Entry<String, Object> entry : values.entrySet()) {
         ConfigValue configValue = set.values().get(entry.getKey());
         if (configValue == null) {
            throw new LuaError("[MLua] Unknown " + normalizedScope + " config value '" + entry.getKey()
               + "' for mod '" + ownerModId + "'.");
         }
         updates.put(configValue, coerceValue(entry.getKey(), configValue, entry.getValue()));
      }
      updates.forEach(ConfigValue::set);
      return updates.size();
   }

   public synchronized void setValue(String ownerModId, ModConfig.Type type, String path, Object value) {
      this.setValue(ownerModId, scopeName(type), path, value);
   }

   public synchronized void save(String ownerModId, String scope) {
      InstalledConfigSet set = this.installedSet(ownerModId, scopeFromString(scope));
      if (set != null) {
         MLuaConfigStorage.writeJson(set);
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
         MLuaConfigStorage.loadOrCreate(installedSet);
      }
   }

   private static boolean removeExpectedValues(Map<String, ConfigValue> target, Map<String, ConfigValue> expected) {
      boolean removed = false;
      for (Map.Entry<String, ConfigValue> entry : expected.entrySet()) {
         removed |= target.remove(entry.getKey(), entry.getValue());
      }
      return removed;
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

   private static Object coerceValue(String path, ConfigValue configValue, Object value) {
      Object defaultValue = configValue.defaultValue();
      Object converted = null;
      if (defaultValue instanceof Boolean) {
         if (value instanceof Boolean bool) {
            converted = bool;
         }
      } else if (defaultValue instanceof Integer) {
         if (value instanceof Number number) {
            double numeric = number.doubleValue();
            if (Double.isFinite(numeric) && numeric == Math.rint(numeric)
                  && numeric >= Integer.MIN_VALUE && numeric <= Integer.MAX_VALUE) {
               converted = (int)numeric;
            }
         }
      } else if (defaultValue instanceof Double) {
         if (value instanceof Number number) {
            double numeric = number.doubleValue();
            if (Double.isFinite(numeric)) converted = numeric;
         }
      } else if (defaultValue instanceof Long) {
         if (value instanceof Number number) {
            double numeric = number.doubleValue();
            if (Double.isFinite(numeric) && numeric == Math.rint(numeric)
                  && numeric >= Long.MIN_VALUE && numeric < 0x1.0p63) {
               converted = (long)numeric;
            }
         }
      } else if (defaultValue instanceof String && value instanceof String string) {
         converted = string;
      }
      if (converted == null) {
         throw new LuaError("[MLua] Config '" + path + "' expects a " + valueTypeName(defaultValue) + ".");
      }
      if (defaultValue instanceof Number && !Double.isFinite(((Number)converted).doubleValue())) {
         throw new LuaError("[MLua] Config '" + path + "' must be finite.");
      }
      if (!configValue.isAllowedValue(converted)) {
         throw new LuaError("[MLua] Config '" + path + "' must be one of: "
            + String.join(", ", configValue.options()) + ".");
      }
      return configValue.clampToRange(converted);
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

   static boolean isWithinRange(Object value, Double minimum, Double maximum) {
      if (!(value instanceof Number number)) return minimum == null && maximum == null;
      double numeric = number.doubleValue();
      return Double.isFinite(numeric) && (minimum == null || numeric >= minimum)
         && (maximum == null || numeric <= maximum);
   }

   static String rangeDescription(Double minimum, Double maximum) {
      if (minimum != null && maximum != null) return "between " + minimum + " and " + maximum;
      if (minimum != null) return "at least " + minimum;
      if (maximum != null) return "at most " + maximum;
      return "within the supported range";
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
         default -> type.name().toLowerCase(Locale.ROOT);
      };
   }

   public static final class ConfigValue {
      private final String typeName;
      private final Object defaultValue;
      private final Double minimum;
      private final Double maximum;
      private final List<String> options;
      private final String description;
      private Object value;

      ConfigValue(String typeName, Object defaultValue, Double minimum, Double maximum,
                  List<String> options, String description) {
         this.typeName = typeName;
         this.defaultValue = defaultValue;
         this.minimum = minimum;
         this.maximum = maximum;
         this.options = options;
         this.description = description;
         this.value = defaultValue;
      }

      public String typeName() {
         return this.typeName;
      }

      public Object defaultValue() {
         return this.defaultValue;
      }

      public Double minimum() { return this.minimum; }

      public Double maximum() { return this.maximum; }

      public List<String> options() { return this.options; }

      public String description() { return this.description; }

      public boolean isWithinRange(Object candidate) {
         return MLuaConfigRegistry.isWithinRange(candidate, this.minimum, this.maximum);
      }

      public Object clampToRange(Object candidate) {
         if (!(candidate instanceof Number number)) return candidate;
         double numeric = number.doubleValue();
         if (!Double.isFinite(numeric)) throw new LuaError("[MLua] Config numeric values must be finite.");
         if (this.minimum != null) numeric = Math.max(numeric, this.minimum);
         if (this.maximum != null) numeric = Math.min(numeric, this.maximum);
         if (this.defaultValue instanceof Integer) return (int)numeric;
         if (this.defaultValue instanceof Long) return (long)numeric;
         return numeric;
      }

      public boolean isAllowedValue(Object candidate) {
         return this.options.isEmpty() || candidate instanceof String text && this.options.contains(text);
      }

      public String rangeDescription() {
         if (this.minimum != null && this.maximum != null) return "between " + this.minimum + " and " + this.maximum;
         if (this.minimum != null) return "at least " + this.minimum;
         if (this.maximum != null) return "at most " + this.maximum;
         return "within the supported range";
      }

      public Object get() {
         return this.value;
      }

      public void set(Object value) {
         if (!this.isAllowedValue(value)) {
            throw new LuaError("[MLua] Config value must be one of: " + String.join(", ", this.options) + ".");
         }
         this.value = this.clampToRange(value);
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
         MLuaConfigSchema.validateConfigTable(section, new java.util.IdentityHashMap<>(),
            new java.util.IdentityHashMap<>(), 1);
         Map<String, ConfigValue> staged = new LinkedHashMap<>();
         MLuaConfigSchema.defineEntries(staged, List.of(sectionName), section);
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
