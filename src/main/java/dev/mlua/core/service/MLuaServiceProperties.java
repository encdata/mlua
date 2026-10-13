package dev.mlua.core.service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.IdentityHashMap;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

public final class MLuaServiceProperties {
   private static final LuaValue ATTACHED_KEY = LuaValue.valueOf("__mlua_service_properties_attached");
   private static final LuaValue BASE_INDEX_KEY = LuaValue.valueOf("__mlua_service_properties_base_index");
   private static final LuaValue BASE_NEWINDEX_KEY = LuaValue.valueOf("__mlua_service_properties_base_newindex");
   private static final LuaValue GETTERS_KEY = LuaValue.valueOf("__mlua_service_property_getters");
   private static final LuaValue SETTERS_KEY = LuaValue.valueOf("__mlua_service_property_setters");
   private static final LuaValue NONE_SENTINEL = LuaValue.FALSE;
   private static final LuaValue DOC_PROPERTIES_KEY = LuaValue.valueOf("__mlua_doc_properties");

   private MLuaServiceProperties() {
   }

   public static LuaTable attach(LuaTable table) {
      return attach(table, Map.of(), Map.of());
   }

   public static LuaTable attach(LuaTable table, Map<String, LuaValue> extraGetters, Map<String, LuaValue> extraSetters) {
      java.util.Objects.requireNonNull(table, "table");
      java.util.Objects.requireNonNull(extraGetters, "extraGetters");
      java.util.Objects.requireNonNull(extraSetters, "extraSetters");
      LuaValue existingMetaVal = table.getmetatable();
      LuaTable meta = existingMetaVal != null && existingMetaVal.istable() ? existingMetaVal.checktable() : new LuaTable();

      if (meta.rawget(ATTACHED_KEY).toboolean()) {
         LuaValue gettersValue = meta.rawget(GETTERS_KEY);
         LuaValue settersValue = meta.rawget(SETTERS_KEY);
         if (!gettersValue.istable() || !settersValue.istable()) {
            throw new IllegalStateException("Attached service property metadata is incomplete");
         }
         LuaTable getters = gettersValue.checktable();
         LuaTable setters = settersValue.checktable();
         Map<String, LuaValue> checkedGetters = validateProperties(extraGetters, "getter");
         Map<String, LuaValue> checkedSetters = validateProperties(extraSetters, "setter");
         validateBindings(getters, checkedGetters, "getter");
         validateBindings(setters, checkedSetters, "setter");
         checkedGetters.forEach(getters::rawset);
         checkedSetters.forEach(setters::rawset);
         publishProperties(table, meta, getters, setters);
         return table;
      }

      Map<String, LuaValue> getters = new HashMap<>();
      Map<String, LuaValue> setters = new HashMap<>();
      LuaValue accessorParameterCounts = table.rawget("__mlua_accessor_parameter_counts");
      collectAccessors(table, getters, setters, new IdentityHashMap<>(),
         accessorParameterCounts.istable() ? accessorParameterCounts.checktable() : null);

      LuaTable propertyGetters = new LuaTable();
      LuaTable propertySetters = new LuaTable();
      getters.forEach(propertyGetters::rawset);
      setters.forEach(propertySetters::rawset);
      Map<String, LuaValue> checkedGetters = validateProperties(extraGetters, "getter");
      Map<String, LuaValue> checkedSetters = validateProperties(extraSetters, "setter");
      validateBindings(propertyGetters, checkedGetters, "getter");
      validateBindings(propertySetters, checkedSetters, "setter");
      checkedGetters.forEach(propertyGetters::rawset);
      checkedSetters.forEach(propertySetters::rawset);
      meta.rawset(GETTERS_KEY, propertyGetters);
      meta.rawset(SETTERS_KEY, propertySetters);
      publishProperties(table, meta, propertyGetters, propertySetters);

      final LuaValue storedIndex = meta.rawget(BASE_INDEX_KEY);
      final LuaValue storedNewIndex = meta.rawget(BASE_NEWINDEX_KEY);
      final LuaValue existingIndex = storedIndex.isnil() ? meta.rawget("__index") : unwrapStored(storedIndex);
      final LuaValue existingNewIndex = storedNewIndex.isnil() ? meta.rawget("__newindex") : unwrapStored(storedNewIndex);
      meta.rawset("__index", new VarArgFunction() {
         @Override
         public Varargs invoke(Varargs args) {
            LuaValue self = args.arg(1);
            LuaValue key = args.arg(2);
            if (key.isstring()) {
               LuaValue getter = propertyGetters.rawget(key);
               if (getter.isfunction()) {
                  return getter.invoke(LuaValue.varargsOf(new LuaValue[]{self}));
               }
            }

            if (!existingIndex.isnil()) {
               return invokeIndex(existingIndex, self, key);
            }

            LuaValue raw = self.rawget(key);
            return raw.isnil() ? LuaValue.NIL : raw;
         }
      });
      meta.rawset("__newindex", new VarArgFunction() {
         @Override
         public Varargs invoke(Varargs args) {
            LuaValue self = args.arg(1);
            LuaValue key = args.arg(2);
            LuaValue value = args.arg(3);
            if (key.isstring()) {
               LuaValue setter = propertySetters.rawget(key);
               if (setter.isfunction()) {
                  setter.invoke(LuaValue.varargsOf(new LuaValue[]{self, value}));
                  return LuaValue.NONE;
               }
            }

            if (!existingNewIndex.isnil()) {
               return invokeNewIndex(existingNewIndex, self, key, value);
            }

            self.rawset(key, value);
            return LuaValue.NONE;
         }
      });
      meta.rawset(BASE_INDEX_KEY, storeBase(existingIndex));
      meta.rawset(BASE_NEWINDEX_KEY, storeBase(existingNewIndex));
      meta.rawset(ATTACHED_KEY, LuaValue.TRUE);
      table.setmetatable(meta);
      return table;
   }

   private static Map<String, LuaValue> validateProperties(Map<String, LuaValue> additions, String role) {
      Map<String, LuaValue> validated = new java.util.LinkedHashMap<>();
      additions.forEach((name, function) -> {
         if (name == null || name.isBlank() || function == null || !function.isfunction()) {
            throw new IllegalArgumentException("Service property " + role + " names and functions must be valid");
         }
         if (name.startsWith("__") || name.equals("ClassName")) {
            throw new IllegalArgumentException("Service property name is reserved for runtime metadata: " + name);
         }
         validated.put(name, function);
      });
      return validated;
   }

   private static void validateBindings(LuaTable existing, Map<String, LuaValue> additions, String role) {
      additions.forEach((name, function) -> {
         LuaValue current = existing.rawget(name);
         if (!current.isnil() && !current.raweq(function)) {
            throw new IllegalStateException("Service property " + role + " is already registered for '" + name + "'");
         }
      });
   }

   private static void publishProperties(LuaTable table, LuaTable meta, LuaTable getters, LuaTable setters) {
      Set<String> names = new HashSet<>();
      collectPropertyNames(getters, names);
      collectPropertyNames(setters, names);
      LuaTable documented = new LuaTable();
      for (String name : names) {
         if (table.rawget(name).isfunction()) {
            throw new IllegalStateException("Service member '" + name + "' cannot be both a property and a function");
         }
         LuaTable descriptor = new LuaTable();
         descriptor.set("readable", LuaValue.valueOf(getters.rawget(name).isfunction()));
         descriptor.set("writable", LuaValue.valueOf(setters.rawget(name).isfunction()));
         documented.set(name, descriptor);
         table.rawset(name, LuaValue.NIL);
      }
      meta.rawset(DOC_PROPERTIES_KEY, documented);
   }

   private static void collectPropertyNames(LuaTable properties, Set<String> names) {
      LuaValue key = LuaValue.NIL;
      while (true) {
         Varargs entry = properties.next(key);
         key = entry.arg1();
         if (key.isnil()) return;
         if (key.isstring() && entry.arg(2).isfunction()) names.add(key.tojstring());
      }
   }

   private static boolean isAccessorName(String name, String prefix) {
      return name.startsWith(prefix) && name.length() > prefix.length() && Character.isUpperCase(name.charAt(prefix.length()));
   }

   private static void collectAccessors(LuaTable table, Map<String, LuaValue> getters, Map<String, LuaValue> setters,
                                        IdentityHashMap<LuaTable, Boolean> seen, LuaTable accessorParameterCounts) {
      if (table == null || seen.put(table, Boolean.TRUE) != null) {
         return;
      }

      LuaValue key = LuaValue.NIL;
      while (true) {
         Varargs next = table.next(key);
         key = next.arg1();
         if (key.isnil()) {
            break;
         }
         if (!key.isstring()) {
            continue;
         }

         String name = key.tojstring();
         LuaValue value = next.arg(2);
         if (!value.isfunction()) {
            continue;
         }

         if (isAccessorName(name, "Get")) {
            LuaValue parameterCount = accessorParameterCounts == null ? LuaValue.NIL : accessorParameterCounts.rawget(key);
            if (accessorParameterCounts != null && (!parameterCount.isnumber() || parameterCount.toint() != 0)) continue;
            getters.putIfAbsent(name.substring(3), value);
         } else if (isAccessorName(name, "Set")) {
            LuaValue parameterCount = accessorParameterCounts == null ? LuaValue.NIL : accessorParameterCounts.rawget(key);
            if (accessorParameterCounts != null && (!parameterCount.isnumber() || parameterCount.toint() != 1)) continue;
            setters.putIfAbsent(name.substring(3), value);
         }
      }

      LuaValue metaValue = table.getmetatable();
      if (metaValue != null && metaValue.istable()) {
         LuaValue index = metaValue.checktable().rawget("__index");
         if (index.istable()) {
            collectAccessors(index.checktable(), getters, setters, seen, accessorParameterCounts);
         }
      }
   }

   private static Varargs invokeIndex(LuaValue existingIndex, LuaValue self, LuaValue key) {
      if (existingIndex.istable()) {
         return existingIndex.get(key);
      }
      return existingIndex.invoke(LuaValue.varargsOf(new LuaValue[]{self, key}));
   }

   private static Varargs invokeNewIndex(LuaValue existingNewIndex, LuaValue self, LuaValue key, LuaValue value) {
      if (existingNewIndex.istable()) {
         existingNewIndex.set(key, value);
         return LuaValue.NONE;
      }
      return existingNewIndex.invoke(LuaValue.varargsOf(new LuaValue[]{self, key, value}));
   }

   private static LuaValue storeBase(LuaValue value) {
      return value.isnil() ? NONE_SENTINEL : value;
   }

   private static LuaValue unwrapStored(LuaValue value) {
      return value.raweq(NONE_SENTINEL) ? LuaValue.NIL : value;
   }
}
