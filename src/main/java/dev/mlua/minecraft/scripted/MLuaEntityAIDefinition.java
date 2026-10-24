package dev.mlua.minecraft.scripted;

import java.util.IdentityHashMap;
import java.util.List;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

/** Validates and snapshots the data portion of a scripted entity AI definition. */
public final class MLuaEntityAIDefinition {
   public static final int MAX_STATES = 256;
   public static final int MAX_TABLE_ENTRIES = 16_384;
   public static final int MAX_TABLE_DEPTH = 64;
   private static final int MAX_STATE_NAME_LENGTH = 128;

   private MLuaEntityAIDefinition() { }

   /** Deep-copies AI state data while retaining Lua callback functions and shared table references. */
   public static LuaTable snapshot(LuaTable source) {
      if (source == null) throw new IllegalArgumentException("Entity AI definition cannot be null");
      validate(source);
      return copyTable(source, new IdentityHashMap<>(), new IdentityHashMap<>(), new int[]{0}, 0);
   }

   /** Validates the raw table structure accepted for state-based AI. */
   public static void validate(LuaValue ai) {
      if (ai == null || ai.isnil() || ai.isfunction()) return;
      if (!ai.istable()) throw new LuaError("[MLua] Entity AI must be a function or a state table.");

      LuaTable definition = ai.checktable();
      validateDataTable(definition, new IdentityHashMap<>(), new int[]{0}, 0);
      LuaValue initial = definition.rawget("initial");
      LuaValue statesValue = definition.rawget("states");
      if (initial.type() != LuaValue.TSTRING || initial.tojstring().isBlank() || !statesValue.istable()) {
         throw new LuaError("[MLua] State-based AI requires raw 'initial' and 'states' table entries.");
      }
      if (initial.tojstring().length() > MAX_STATE_NAME_LENGTH) {
         throw new LuaError("[MLua] Entity AI initial state name exceeds the maximum length of "
            + MAX_STATE_NAME_LENGTH + " characters.");
      }

      LuaTable states = statesValue.checktable();
      int stateCount = 0;
      LuaValue key = LuaValue.NIL;
      while (true) {
         Varargs entry = states.next(key);
         key = entry.arg1();
         if (key.isnil()) break;
         if (++stateCount > MAX_STATES) {
            throw new LuaError("[MLua] Entity AI exceeds the maximum of " + MAX_STATES + " states per definition.");
         }
      }
      if (states.rawget(initial).isnil()) {
         throw new LuaError("[MLua] State-based AI initial state '" + initial.tojstring() + "' is not defined.");
      }

      key = LuaValue.NIL;
      while (true) {
         Varargs entry = states.next(key);
         key = entry.arg1();
         if (key.isnil()) break;
         LuaValue state = entry.arg(2);
         if (key.type() != LuaValue.TSTRING || key.tojstring().isBlank()
               || key.tojstring().length() > MAX_STATE_NAME_LENGTH
               || !state.isfunction() && !state.istable()) {
            throw new LuaError("[MLua] Each AI state must have a name and be a function or state table.");
         }
         if (state.istable()) validateStateCallbacks(key.tojstring(), state.checktable());
      }
   }

   private static void validateStateCallbacks(String stateName, LuaTable callbacks) {
      for (String callbackName : List.of("onEnter", "update", "onExit")) {
         LuaValue callback = callbacks.rawget(callbackName);
         if (!callback.isnil() && !callback.isfunction()) {
            throw new LuaError("[MLua] AI state '" + stateName + "' callback '" + callbackName
               + "' must be a raw function entry.");
         }
      }
   }

   private static void validateDataTable(LuaTable table, IdentityHashMap<LuaTable, Boolean> active,
         int[] entries, int depth) {
      if (depth >= MAX_TABLE_DEPTH) {
         throw new LuaError("[MLua] Entity AI definition cannot be nested more than "
            + MAX_TABLE_DEPTH + " tables deep.");
      }
      if (table.getmetatable() != null) {
         throw new LuaError("[MLua] Entity AI definition tables cannot have metatables; AI definitions are snapshotted as data.");
      }
      if (active.put(table, Boolean.TRUE) != null) {
         throw new LuaError("[MLua] Entity AI definition cannot contain cyclic tables.");
      }
      try {
         LuaValue key = LuaValue.NIL;
         while (true) {
            Varargs entry = table.next(key);
            key = entry.arg1();
            if (key.isnil()) return;
            if (++entries[0] > MAX_TABLE_ENTRIES) {
               throw new LuaError("[MLua] Entity AI definition cannot contain more than "
                  + MAX_TABLE_ENTRIES + " table entries.");
            }
            if (key.istable()) validateDataTable(key.checktable(), active, entries, depth + 1);
            LuaValue value = entry.arg(2);
            if (value.istable()) validateDataTable(value.checktable(), active, entries, depth + 1);
         }
      } finally {
         active.remove(table);
      }
   }

   private static LuaTable copyTable(LuaTable source, IdentityHashMap<LuaTable, LuaTable> copies,
         IdentityHashMap<LuaTable, Boolean> active, int[] entries, int depth) {
      if (depth >= MAX_TABLE_DEPTH) {
         throw new LuaError("[MLua] Entity AI definition cannot be nested more than "
            + MAX_TABLE_DEPTH + " tables deep.");
      }
      if (active.containsKey(source)) {
         throw new LuaError("[MLua] Entity AI definition cannot contain cyclic tables.");
      }
      LuaTable existing = copies.get(source);
      if (existing != null) return existing;

      LuaTable copy = new LuaTable();
      copies.put(source, copy);
      active.put(source, Boolean.TRUE);
      LuaValue key = LuaValue.NIL;
      while (true) {
         Varargs entry = source.next(key);
         key = entry.arg1();
         if (key.isnil()) break;
         if (++entries[0] > MAX_TABLE_ENTRIES) {
            throw new LuaError("[MLua] Entity AI definition cannot contain more than "
               + MAX_TABLE_ENTRIES + " table entries.");
         }
         LuaValue value = entry.arg(2);
         LuaValue copiedKey = key.istable()
            ? copyTable(key.checktable(), copies, active, entries, depth + 1) : key;
         LuaValue copiedValue = value.istable()
            ? copyTable(value.checktable(), copies, active, entries, depth + 1) : value;
         copy.rawset(copiedKey, copiedValue);
      }
      active.remove(source);
      return copy;
   }
}
