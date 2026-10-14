package dev.mlua.core.service;

import java.util.Objects;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.function.Supplier;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

public abstract class MLuaServiceBase implements MLuaService {
   protected final Context context;

   protected MLuaServiceBase(Context context) {
      this.context = Objects.requireNonNull(context, "context");
   }

   protected final <T> T sharedState(String key, Class<T> stateType, Supplier<? extends T> factory) {
      return this.context.sharedState(this.name(), key, stateType, factory);
   }

   /**
    * Returns the index of a logical service argument for both colon and dot calls.
    * For example, logical argument 1 is Lua argument 2 in {@code service:Call(value)}
    * and Lua argument 1 in {@code service.Call(value)}.
    */
   protected final int serviceArgumentIndex(Varargs args, LuaTable serviceTable, int logicalIndex) {
      if (logicalIndex < 1) throw new IllegalArgumentException("Logical service argument index must be positive");
      LuaValue receiver = args.arg1();
      if (receiver == serviceTable) return logicalIndex + 1;
      if (receiver.istable()) {
         LuaValue meta = receiver.getmetatable();
         if (meta != null && meta.istable()
            && this.name().equals(meta.checktable().rawget("__mlua_service_name").optjstring(""))) {
            return logicalIndex + 1;
         }
      }
      return logicalIndex;
   }

   /**
    * Normalizes Lua functions on a service table and its nested API tables so both
    * {@code service:Call(value)} and {@code service.Call(value)} reach the same
    * implementation argument layout.
    */
   protected final LuaTable supportColonAndDotCalls(LuaTable serviceTable) {
      Set<LuaTable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
      normalizeCalls(serviceTable, serviceTable, visited);
      return serviceTable;
   }

   private void normalizeCalls(LuaTable serviceTable, LuaTable table, Set<LuaTable> visited) {
      if (!visited.add(table)) return;
      LuaValue key = LuaValue.NIL;
      while (true) {
         Varargs entry = table.next(key);
         key = entry.arg1();
         if (key.isnil()) return;
         if (key.isstring() && key.tojstring().startsWith("__mlua")) continue;
         LuaValue value = entry.arg(2);
         if (value.istable()) {
            normalizeCalls(serviceTable, value.checktable(), visited);
         } else if (value.isfunction()) {
            LuaTable receiverTable = table;
            LuaValue implementation = value;
            table.rawset(key, new VarArgFunction() {
               @Override public Varargs invoke(Varargs args) {
                  if (serviceArgumentIndex(args, receiverTable, 1) == 2) {
                     return implementation.invoke(args);
                  }
                  LuaValue[] shifted = new LuaValue[args.narg() + 1];
                  shifted[0] = receiverTable;
                  for (int index = 1; index < shifted.length; index++) shifted[index] = args.arg(index);
                  return implementation.invoke(LuaValue.varargsOf(shifted));
               }
            });
         }
      }
   }
}
