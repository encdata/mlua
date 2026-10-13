package dev.mlua.core.service;

import java.util.Objects;
import java.util.function.Supplier;

/** Base for context-bound Java services that share state across scripts on one runtime side. */
public abstract class MLuaServiceBase implements MLuaService, LuaBackedService {
   protected final Context context;

   protected MLuaServiceBase(Context context) {
      this.context = Objects.requireNonNull(context, "context");
   }

   /** Returns one typed state object shared by this service name and key on this side. */
   protected final <T> T sharedState(String key, Class<T> stateType, Supplier<? extends T> factory) {
      return this.context.sharedState(this.name(), key, stateType, factory);
   }
}
