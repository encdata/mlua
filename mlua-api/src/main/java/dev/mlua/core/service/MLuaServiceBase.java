package dev.mlua.core.service;

import java.util.Objects;
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
}
