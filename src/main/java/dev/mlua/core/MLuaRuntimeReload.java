package dev.mlua.core;

import dev.mlua.common.enums.RegistrationPhase;
import dev.mlua.common.enums.ScriptType;
import dev.mlua.common.enums.Side;
import dev.mlua.core.script.MLuaScript;
import dev.mlua.minecraft.network.MLuaNetworkManager;
import dev.mlua.module.MLuaModuleLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Runs server reloads and asks a player's client to reload its scripts. */
final class MLuaRuntimeReload {
   private final MLuaRuntime runtime;
   private final MLuaRuntimeSession session;
   private final MLuaRuntimeScriptCatalog scripts;
   private final MLuaRuntimeScriptExecutor executor;

   MLuaRuntimeReload(MLuaRuntime runtime, MLuaRuntimeSession session,
                     MLuaRuntimeScriptCatalog scripts, MLuaRuntimeScriptExecutor executor) {
      this.runtime = runtime;
      this.session = session;
      this.scripts = scripts;
      this.executor = executor;
   }

   void execute(CommandSourceStack source) {
      if (this.runtime.hasPermissionForReload(source)) {
         this.reloadServer(source);
         return;
      }
      ServerPlayer player = source.getPlayer();
      if (player == null) return;
      source.sendSuccess(() -> Component.literal("[MLua] Reloading client scripts..."), true);
      MLuaNetworkManager.get().sendClientReload(player);
   }

   private void reloadServer(CommandSourceStack source) {
      this.runtime.advanceLifecycleEpoch();
      source.sendSuccess(() -> Component.literal("[MLua] Reloading server scripts..."), true);
      MLuaModuleLoader modules = MLuaModuleLoader.get();
      modules.clearRuntimeState(this.runtime, this.session, Side.SERVER);
      this.session.clearScriptState(Side.SERVER);
      this.runtime.forceRefreshScripts();
      this.executor.resetSide(Side.SERVER);
      modules.onReload(Side.SERVER);

      for (MLuaScript script : this.scripts.ofType(ScriptType.SERVER)) {
         try {
            this.executor.execute(script, Side.SERVER, RegistrationPhase.RUNTIME, this.session.server());
         } catch (RuntimeException failure) {
            MLuaLogger.LOGGER.error("Server script '{}' failed during reload: {}",
               script.displayName(), failure.getMessage());
            this.runtime.reportRuntimeFailure(
               "Server script '" + script.displayName() + "' failed during reload", failure);
         }
      }

      for (MLuaScript script : this.scripts.withEntrypoint("run_server", Side.SERVER)) {
         try {
            this.executor.executeEntrypoint(script, Side.SERVER, RegistrationPhase.RUNTIME,
               this.session.server());
         } catch (RuntimeException failure) {
            MLuaLogger.LOGGER.error("Server entrypoint script '{}' failed during reload: {}",
               script.displayName(), failure.getMessage());
            this.runtime.reportRuntimeFailure(
               "Server entrypoint script '" + script.displayName() + "' failed during reload", failure);
         }
      }

      if (this.session.server() != null) {
         this.runtime.runCommandScripts(this.session.server().getCommands().getDispatcher());
         this.runtime.reloadWorldLoadEntrypoints(this.session.server());
      }
      MLuaNetworkManager.get().sendReloadToAll(this.session.server());
      source.sendSuccess(() -> Component.literal("[MLua] Server reload complete."), true);
   }
}
