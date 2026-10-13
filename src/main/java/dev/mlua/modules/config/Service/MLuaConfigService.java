package dev.mlua.modules.config.Service;

import dev.mlua.core.engine.MLuaLuaBindings;
import dev.mlua.core.service.MLuaReflectiveService;
import dev.mlua.runtime.config.MLuaConfigRegistry;
import java.util.Optional;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/** Reads and updates addon-owned configuration values through the shared reflective service base. */
public final class MLuaConfigService extends MLuaReflectiveService {
   public MLuaConfigService(Context context) {
      super(context);
   }

   @Override
   public String name() {
      return "Config";
   }

   @Override
   public LuaTable toLua() {
      LuaTable table = super.toLua();
      table.set("__side", "Both");
      return table;
   }

   public LuaValue Get(String path, Optional<String> requestedScope) {
      String scope = scope(requestedScope, path);
      Object value = MLuaConfigRegistry.get().getValue(this.context.ownerModId(), scope, path);
      return MLuaLuaBindings.toLuaValue(value);
   }

   public void Set(String path, LuaValue value, Optional<String> requestedScope) {
      String scope = scope(requestedScope, path);
      MLuaConfigRegistry.get().setValue(this.context.ownerModId(), scope, path,
         MLuaLuaBindings.javaValue(value));
      MLuaConfigRegistry.get().save(this.context.ownerModId(), scope);
   }

   public void Save(Optional<String> requestedScope) {
      if (requestedScope.isEmpty()) {
         saveAll();
      } else {
         String scope = MLuaConfigRegistry.scopeFromString(requestedScope.get());
         MLuaConfigRegistry.get().save(this.context.ownerModId(), scope);
      }
   }

   public boolean Exists(String path, Optional<String> requestedScope) {
      MLuaConfigRegistry registry = MLuaConfigRegistry.get();
      if (requestedScope.isPresent()) {
         String scope = MLuaConfigRegistry.scopeFromString(requestedScope.get());
         return registry.hasValue(this.context.ownerModId(), scope, path);
      }
      return registry.hasValue(this.context.ownerModId(), "common", path)
         || registry.hasValue(this.context.ownerModId(), "client", path)
         || registry.hasValue(this.context.ownerModId(), "server", path);
   }

   private String scope(Optional<String> requestedScope, String path) {
      return requestedScope.map(MLuaConfigRegistry::scopeFromString)
         .orElseGet(() -> MLuaConfigRegistry.get().defaultScopeNameFor(
            this.context.side(), this.context.ownerModId(), path));
   }

   private void saveAll() {
      MLuaConfigRegistry registry = MLuaConfigRegistry.get();
      String owner = this.context.ownerModId();
      for (String scope : new String[]{"common", "client", "server"}) {
         if (registry.hasConfig(owner, scope)) registry.save(owner, scope);
      }
   }
}
