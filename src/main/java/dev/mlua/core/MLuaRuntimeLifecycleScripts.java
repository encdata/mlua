package dev.mlua.core;

import dev.mlua.common.enums.RegistrationPhase;
import dev.mlua.common.enums.ScriptType;
import dev.mlua.common.enums.Side;
import dev.mlua.core.script.MLuaScript;
import dev.mlua.minecraft.binding.MLuaLevelWrapper;
import dev.mlua.module.MLuaModuleLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/** Owns server and world script lifecycle callbacks for one runtime session. */
final class MLuaRuntimeLifecycleScripts {
   private final MLuaRuntime runtime;
   private final MLuaRuntimeSession session;
   private final MLuaRuntimeScriptCatalog scripts;
   private final MLuaRuntimeScriptExecutor executor;
   private final MLuaRuntimeWorldLifecycle worlds;
   private boolean serverRan;

   MLuaRuntimeLifecycleScripts(MLuaRuntime runtime, MLuaRuntimeSession session,
                               MLuaRuntimeScriptCatalog scripts, MLuaRuntimeScriptExecutor executor,
                               MLuaRuntimeWorldLifecycle worlds) {
      this.runtime = runtime;
      this.session = session;
      this.scripts = scripts;
      this.executor = executor;
      this.worlds = worlds;
   }

   void runServerScripts(MinecraftServer server) {
      if (this.serverRan) return;
      this.session.attachServer(server);
      this.serverRan = true;
      this.runtime.ensureScriptsCurrent();
      for (MLuaScript script : this.scripts.ofType(ScriptType.SERVER)) {
         try {
            this.executor.execute(script, Side.SERVER, RegistrationPhase.RUNTIME, server);
         } catch (RuntimeException failure) {
            MLuaLogger.LOGGER.error("Server script '{}' failed and was skipped: {}",
               script.displayName(), failure.getMessage());
            this.runtime.reportRuntimeFailure("Server script '" + script.displayName() + "' failed", failure);
         }
      }
      for (MLuaScript script : this.scripts.withEntrypoint("run_server", Side.SERVER)) {
         try {
            this.executor.executeEntrypoint(script, Side.SERVER, RegistrationPhase.RUNTIME, server);
         } catch (RuntimeException failure) {
            MLuaLogger.LOGGER.error("Server entrypoint script '{}' failed and was skipped: {}",
               script.displayName(), failure.getMessage());
            this.runtime.reportRuntimeFailure("Server entrypoint script '" + script.displayName() + "' failed",
               failure);
         }
      }
      for (Level level : server.getAllLevels()) this.runWorldLifecycleEntrypoint(level, true);
   }

   void runWorldLifecycleEntrypoint(Level level, boolean loaded) {
      if (level == null) return;
      Side side = level.isClientSide() ? Side.CLIENT : Side.SERVER;
      if (side == Side.SERVER && (!this.serverRan || this.session.server() != level.getServer())) return;
      this.runtime.ensureScriptsCurrent();
      String name = loaded ? "run_world_load" : "run_world_unload";
      if (!this.worlds.markDispatched(level, loaded)) return;
      MinecraftServer server = side == Side.SERVER ? level.getServer() : this.session.server();
      for (MLuaScript script : this.scripts.withEntrypoint(name, side)) {
         try {
            LuaTable event = new LuaTable();
            event.set("Level", MLuaLevelWrapper.levelTable(level, MLuaRuntime.registrationForLevel(level)));
            event.set("Dimension", LuaValue.valueOf(dev.mlua.minecraft.MLuaDimensionId.from(level)));
            event.set("Side", LuaValue.valueOf(side == Side.CLIENT ? "Client" : "Server"));
            this.executor.executeEntrypoint(script, side, RegistrationPhase.RUNTIME,
               server, name, event, this.runtime);
         } catch (RuntimeException failure) {
            MLuaLogger.LOGGER.error("World lifecycle entrypoint '{}' in '{}' failed: {}",
               name, script.displayName(), failure.getMessage());
            this.runtime.reportRuntimeFailure("World lifecycle entrypoint '" + name + "' failed in "
               + script.displayName(), failure);
         }
      }
   }

   boolean ownsServer(MinecraftServer server) {
      return this.session.server() == server;
   }

   void onServerStopping(MinecraftServer server) {
      for (Level level : server.getAllLevels()) this.runWorldLifecycleEntrypoint(level, false);
      this.runServerStoppingEntrypoints(server);
      MLuaModuleLoader.get().clearRuntimeState(this.runtime, this.session, Side.SERVER);
      this.session.clearScriptState(Side.SERVER);
      this.executor.resetSide(Side.SERVER);
      MLuaModuleLoader.get().onReload(Side.SERVER);
      this.session.detachServer(server);
      this.worlds.removeServerWorlds(server);
      this.serverRan = false;
   }

   void reloadWorldLoadEntrypoints(MinecraftServer server) {
      for (Level level : server.getAllLevels()) {
         this.worlds.forget(level);
         this.runWorldLifecycleEntrypoint(level, true);
      }
   }

   private void runServerStoppingEntrypoints(MinecraftServer server) {
      this.runtime.ensureScriptsCurrent();
      for (MLuaScript script : this.scripts.withEntrypoint("run_server_stopping", Side.SERVER)) {
         try {
            this.executor.executeEntrypoint(script, Side.SERVER, RegistrationPhase.RUNTIME,
               server, "run_server_stopping", null, this.runtime);
         } catch (RuntimeException failure) {
            MLuaLogger.LOGGER.error("Server stopping entrypoint in '{}' failed: {}",
               script.displayName(), failure.getMessage());
            this.runtime.reportRuntimeFailure("Server stopping entrypoint failed in "
               + script.displayName(), failure);
         }
      }
   }
}
