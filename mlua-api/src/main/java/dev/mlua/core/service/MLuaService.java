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

/** A context-bound service that materializes the Lua API exposed to scripts. */
public interface MLuaService extends LuaBackedService {
   String name();

   public static record Context(MLuaContextView mlua) {
      public Context(MLuaContextView mlua) {
         this.mlua = Objects.requireNonNull(mlua, "mlua");
      }

      public MLuaContextView mlua() {
         return this.mlua;
      }

      /** Mod that owns the script requesting this service. */
      public String ownerModId() { return this.mlua.ownerModId(); }

      /** Game side on which the requesting script is running. */
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

      /** Returns a typed state value shared by this service on the current runtime side. */
      public <T> T sharedState(String serviceName, String key, Class<T> stateType,
                               Supplier<? extends T> factory) {
         return this.mlua.serviceState(serviceName, key, stateType, factory);
      }
   }
}
