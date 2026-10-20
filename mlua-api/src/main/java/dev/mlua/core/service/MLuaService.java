package dev.mlua.core.service;

import dev.mlua.core.MLuaContextView;
import dev.mlua.common.enums.RegistrationPhase;
import dev.mlua.common.enums.ScriptType;
import dev.mlua.common.enums.Side;
import dev.mlua.module.MLuaRegistration;
import dev.mlua.core.scheduler.MLuaTaskSchedulerView;
import java.util.Objects;
import java.util.function.Supplier;
import java.nio.file.Path;
import net.minecraft.server.MinecraftServer;

public interface MLuaService extends LuaBackedService {
   String name();

   record Context(MLuaContextView mlua) {
      public Context {
         Objects.requireNonNull(mlua, "mlua");
      }

      public String ownerModId() { return this.mlua.ownerModId(); }

      public Side side() { return this.mlua.side(); }

      public RegistrationPhase phase() { return this.mlua.phase(); }

      public MLuaTaskSchedulerView scheduler() { return this.mlua.scheduler(); }

      public MLuaRegistration registration() { return this.mlua.registration(); }

      public MinecraftServer server() { return this.mlua.server(); }

      public Path filesRoot() { return this.mlua.filesRoot(); }

      public boolean isClient() { return this.mlua.isClient(); }

      public boolean isServer() { return this.mlua.isServer(); }

      public boolean isRegistration() { return this.mlua.isRegistration(); }

      public boolean isPhase(RegistrationPhase phase) { return this.mlua.isPhase(phase); }

      public boolean isScriptType(ScriptType type) { return this.mlua.isScriptType(type); }

      public <T> T sharedState(String serviceName, String key, Class<T> stateType,
                               Supplier<? extends T> factory) {
         return this.mlua.serviceState(serviceName, key, stateType, factory);
      }
   }
}
