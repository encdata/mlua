package dev.mlua.core.service;

import java.util.Objects;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import dev.mlua.module.MLuaRegistration;
import dev.mlua.common.enums.Side;
import java.util.Map;
import java.util.LinkedHashMap;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

public final class MLuaDescribedService implements MLuaService {
   private final MLuaService delegate;
   private final String description;
   private final Map<String, String> methodDescriptions;
   private final Side registeredSide;

   public MLuaDescribedService(MLuaService delegate, String description) {
      this(delegate, description, Map.of());
   }

   public MLuaDescribedService(MLuaService delegate, String description, Map<String, String> methodDescriptions) {
      this(delegate, description, methodDescriptions, false);
   }

   MLuaDescribedService(MLuaService delegate, String description, Map<String, String> methodDescriptions,
                        boolean allowMissingServiceDescription) {
      this(delegate, description, methodDescriptions, allowMissingServiceDescription, null);
   }

   MLuaDescribedService(MLuaService delegate, String description, Map<String, String> methodDescriptions,
                        boolean allowMissingServiceDescription, Side registeredSide) {
      this.delegate = Objects.requireNonNull(delegate, "delegate");
      this.description = description;
      this.registeredSide = registeredSide;
      this.methodDescriptions = Collections.unmodifiableMap(
         new LinkedHashMap<>(Objects.requireNonNull(methodDescriptions, "methodDescriptions")));
      if (!allowMissingServiceDescription && (description == null || description.isBlank())
         && this.methodDescriptions.isEmpty()) {
         throw new IllegalArgumentException("A described service needs a service or function description");
      }
      if (description != null && description.isBlank()) throw new IllegalArgumentException("Service description must be non-empty");
      this.methodDescriptions.forEach((name, value) -> {
         if (name.isBlank() || value.isBlank()) throw new IllegalArgumentException("Service function descriptions must be non-empty");
         if (MLuaRegistration.isReservedFunctionName(name)) {
            throw new IllegalArgumentException("Service function name is reserved for MLua metadata: " + name);
         }
      });
   }

   @Override
   public String name() {
      return delegate.name();
   }

   @Override
   public LuaTable toLua() {
      LuaTable table = delegate.toLua();
      if (table == null) throw new LuaError("[MLua] Service '" + this.name() + "' returned no Lua table");
      LuaValue side = table.rawget("__side");
      if (side.isnil() && registeredSide != null) {
         table.rawset("__side", LuaValue.valueOf(switch (registeredSide) {
            case CLIENT -> "Client";
            case SERVER -> "Server";
            case BOTH -> "Both";
         }));
      } else if (!side.isnil() && !side.isstring()) {
         throw new IllegalArgumentException("Service '" + this.name() + "' has invalid side metadata");
      } else if (side.isstring()) {
         String declaredSide = side.tojstring();
         String normalizedSide = switch (declaredSide.toUpperCase(java.util.Locale.ROOT)) {
            case "CLIENT" -> "Client";
            case "SERVER" -> "Server";
            case "BOTH" -> "Both";
            case "REGISTRY" -> "Registry";
            default -> throw new IllegalArgumentException("Service '" + this.name()
               + "' has unknown side metadata '" + declaredSide + "'");
         };
         if (!declaredSide.equals(normalizedSide)) table.rawset("__side", LuaValue.valueOf(normalizedSide));
      }
      LuaValue existingDescription = table.rawget("__mlua_doc_description");
      if (!existingDescription.isnil() && !existingDescription.isstring()) {
         throw new IllegalArgumentException("Service '" + this.name() + "' has invalid description metadata");
      }
      if (existingDescription.isstring() && existingDescription.tojstring().isBlank()) {
         throw new IllegalArgumentException("Service '" + this.name() + "' has an empty description");
      }
      if (description != null && existingDescription.isstring()
         && !description.equals(existingDescription.tojstring())) {
         throw new IllegalArgumentException("Service '" + this.name()
            + "' has a conflicting registered description");
      }
      LuaValue existingDocs = table.rawget("__mlua_doc_methods");
      if (!existingDocs.isnil() && !existingDocs.istable()) {
         throw new IllegalArgumentException("Service '" + this.name() + "' has invalid method descriptions");
      }
      LuaTable docs = new LuaTable();
      if (existingDocs.istable()) {
         LuaValue key = LuaValue.NIL;
         while (true) {
            Varargs entry = existingDocs.checktable().next(key);
            key = entry.arg1();
            if (key.isnil()) break;
            docs.rawset(key, entry.arg(2));
         }
      }
      mergeInheritedMethodDescriptions(table, docs, Collections.newSetFromMap(new IdentityHashMap<>()));
      validateMethodDescriptionEntries(table, docs);
      if (!methodDescriptions.isEmpty()) {
         methodDescriptions.forEach((name, value) -> {
            if (!table.get(name).isfunction()) {
                throw new IllegalArgumentException("Service '" + this.name() + "' has no exposed function named '" + name + "'");
            }
            LuaValue existing = docs.rawget(name);
            if (!existing.isnil() && (!existing.isstring() || !value.equals(existing.tojstring()))) {
               throw new IllegalArgumentException("Service '" + this.name() + "' function '" + name
                  + "' has a conflicting description");
            }
            docs.rawset(name, LuaValue.valueOf(value));
         });
      }
      validateFunctionDescriptions(table, docs, Collections.newSetFromMap(new IdentityHashMap<>()));
      if (description != null && !description.isBlank()) {
         table.rawset("__mlua_doc_description", LuaValue.valueOf(description));
      }
      if (!methodDescriptions.isEmpty()) table.rawset("__mlua_doc_methods", docs);
      return table;
   }

   private void validateFunctionDescriptions(LuaTable table, LuaTable docs, Set<LuaTable> visited) {
      if (!visited.add(table)) return;
      LuaValue key = LuaValue.NIL;
      while (true) {
         Varargs entry = table.next(key);
         key = entry.arg1();
         if (key.isnil()) break;
         if (!key.isstring() || !entry.arg(2).isfunction()
            || MLuaRegistration.isReservedFunctionName(key.tojstring())) continue;
         LuaValue methodDescription = docs.rawget(key);
         if (!methodDescription.isstring() || methodDescription.tojstring().isBlank()) {
            throw new IllegalStateException("Service '" + this.name() + "' function '"
               + key.tojstring() + "' needs an @MLuaDescription or a registered function description");
         }
      }

      LuaValue metatable = table.getmetatable();
      if (metatable == null || !metatable.istable()) return;
      LuaValue index = metatable.rawget("__index");
      if (index.istable()) validateFunctionDescriptions(index.checktable(), docs, visited);
   }

   private void mergeInheritedMethodDescriptions(LuaTable table, LuaTable docs, Set<LuaTable> visited) {
      if (!visited.add(table)) return;
      LuaValue classDocs = table.rawget("__mlua_doc_methods");
      if (classDocs.istable()) {
         LuaValue key = LuaValue.NIL;
         while (true) {
            Varargs entry = classDocs.checktable().next(key);
            key = entry.arg1();
            if (key.isnil()) break;
            LuaValue inherited = entry.arg(2);
            if (!key.isstring() || !inherited.isstring() || inherited.tojstring().isBlank()) continue;
            LuaValue existing = docs.rawget(key);
            if (existing.isnil()) docs.rawset(key, inherited);
         }
      }
      LuaValue metatable = table.getmetatable();
      if (metatable == null || !metatable.istable()) return;
      LuaValue index = metatable.rawget("__index");
      if (index.istable()) mergeInheritedMethodDescriptions(index.checktable(), docs, visited);
   }

   private void validateMethodDescriptionEntries(LuaTable service, LuaTable docs) {
      LuaValue key = LuaValue.NIL;
      while (true) {
         Varargs entry = docs.next(key);
         key = entry.arg1();
         if (key.isnil()) return;
         LuaValue value = entry.arg(2);
         if (!key.isstring() || key.tojstring().isBlank() || !value.isstring() || value.tojstring().isBlank()) {
            throw new IllegalArgumentException("Service '" + this.name()
               + "' has an invalid function description entry");
         }
         if (MLuaRegistration.isReservedFunctionName(key.tojstring())) {
            throw new IllegalArgumentException("Service function name is reserved for MLua metadata: "
               + key.tojstring());
         }
         if (!service.get(key.tojstring()).isfunction()) {
            throw new IllegalArgumentException("Service '" + this.name() + "' documents missing function '"
               + key.tojstring() + "'");
         }
      }
   }


}
