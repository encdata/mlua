package dev.mlua.core.service;

import java.util.Objects;
import java.util.function.Supplier;

public abstract class MLuaServiceBase implements MLuaService {
   protected final Context context;

   protected MLuaServiceBase(Context context) {
      this.context = Objects.requireNonNull(context, "context");
   }

   protected final <T> T sharedState(String key, Class<T> stateType, Supplier<? extends T> factory) {
      return this.context.sharedState(this.name(), key, stateType, factory);
   }
}
