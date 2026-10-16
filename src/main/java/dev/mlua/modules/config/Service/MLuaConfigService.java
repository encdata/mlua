package dev.mlua.modules.config.Service;

import dev.mlua.core.engine.MLuaLuaBindings;
import dev.mlua.api.MLuaDescription;
import dev.mlua.core.service.MLuaReflectiveService;
import dev.mlua.runtime.config.MLuaConfigRegistry;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

@MLuaDescription("Reads and updates registered MLua configuration values.")
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

   @MLuaDescription("Returns the current value for a registered configuration key.")
   public LuaValue Get(String path, Optional<String> requestedScope) {
      String scope = scope(requestedScope, path);
      Object value = MLuaConfigRegistry.get().getValue(this.context.ownerModId(), scope, path);
      return MLuaLuaBindings.toLuaValue(value);
   }

   @MLuaDescription("Returns a snapshot of every registered value in a configuration scope, keyed by path.")
   public LuaValue GetAll(String scope) {
      return MLuaLuaBindings.toLuaValue(
         MLuaConfigRegistry.get().getValues(this.context.ownerModId(), scope));
   }

   @MLuaDescription("Updates a configuration value for the requested scope.")
   public void Set(String path, LuaValue value, Optional<String> requestedScope) {
      String scope = scope(requestedScope, path);
      MLuaConfigRegistry.get().setValue(this.context.ownerModId(), scope, path,
         MLuaLuaBindings.javaValue(value));
      MLuaConfigRegistry.get().save(this.context.ownerModId(), scope);
   }

   @MLuaDescription("Validates and updates several values in one config scope, then writes the config file once. No values change if a key or value is invalid.")
   public int SetMany(String requestedScope, LuaTable values) {
      String scope = MLuaConfigRegistry.scopeFromString(requestedScope);
      Map<String, Object> updates = new LinkedHashMap<>();
      LuaValue key = LuaValue.NIL;
      while (true) {
         Varargs entry = values.next(key);
         key = entry.arg1();
         if (key.isnil()) break;
         if (!key.isstring()) throw new LuaError("[MLua] Config:SetMany expects string config paths as table keys.");
         updates.put(key.tojstring(), MLuaLuaBindings.javaValue(entry.arg(2)));
      }
      if (updates.isEmpty()) return 0;
      int changed = MLuaConfigRegistry.get().setValues(this.context.ownerModId(), scope, updates);
      MLuaConfigRegistry.get().save(this.context.ownerModId(), scope);
      return changed;
   }

   @MLuaDescription("Persists the current configuration values to disk.")
   public void Save(Optional<String> requestedScope) {
      if (requestedScope.isEmpty()) {
         saveAll();
      } else {
         String scope = MLuaConfigRegistry.scopeFromString(requestedScope.get());
         MLuaConfigRegistry.get().save(this.context.ownerModId(), scope);
      }
   }

   @MLuaDescription("Returns whether a configuration key is registered.")
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
