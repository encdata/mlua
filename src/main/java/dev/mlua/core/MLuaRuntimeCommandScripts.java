package dev.mlua.core;

import com.mojang.brigadier.CommandDispatcher;
import dev.mlua.common.enums.RegistrationPhase;
import dev.mlua.common.enums.ScriptType;
import dev.mlua.common.enums.Side;
import dev.mlua.core.script.MLuaScript;
import dev.mlua.module.MLuaModuleLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;

/** Owns command-script phase ordering for both the client and server runtimes. */
final class MLuaRuntimeCommandScripts {
   private final MLuaRuntime runtime;
   private final MLuaRuntimeSession session;
   private final MLuaRuntimeScriptCatalog scripts;
   private final MLuaRuntimeScriptExecutor executor;

   MLuaRuntimeCommandScripts(MLuaRuntime runtime, MLuaRuntimeSession session,
                             MLuaRuntimeScriptCatalog scripts, MLuaRuntimeScriptExecutor executor) {
      this.runtime = runtime;
      this.session = session;
      this.scripts = scripts;
      this.executor = executor;
   }

   void run(Side side, CommandDispatcher<CommandSourceStack> dispatcher) {
      if (side != Side.CLIENT && side != Side.SERVER) {
         throw new IllegalArgumentException("Command scripts require a client or server side");
      }

      MinecraftServer server = this.session.server();
      MLuaModuleLoader.get().beforeScriptPhase(this.runtime, this.session, side,
         RegistrationPhase.COMMANDS, dispatcher);
      this.runFiles(side, server);
      this.runEntrypoints(side, server);
      MLuaModuleLoader.get().afterScriptPhase(this.runtime, this.session, side,
         RegistrationPhase.COMMANDS, dispatcher);
   }

   private void runFiles(Side side, MinecraftServer server) {
      for (MLuaScript script : this.scripts.ofType(ScriptType.COMMANDS)) {
         try {
            this.executor.execute(script, side, RegistrationPhase.COMMANDS, server);
         } catch (RuntimeException failure) {
            String label = side == Side.CLIENT ? "Client command" : "Command";
            MLuaLogger.LOGGER.error("{} script '{}' failed and was skipped: {}",
               label, script.displayName(), failure.getMessage());
            this.runtime.reportRuntimeFailure(label + " script '" + script.displayName() + "' failed", failure);
         }
      }
   }

   private void runEntrypoints(Side side, MinecraftServer server) {
      for (MLuaScript script : this.scripts.withEntrypoint("run_commands", side)) {
         try {
            this.executor.executeEntrypoint(script, side, RegistrationPhase.COMMANDS, server);
         } catch (RuntimeException failure) {
            String label = side == Side.CLIENT ? "Client command entrypoint" : "Command entrypoint";
            MLuaLogger.LOGGER.error("{} script '{}' failed and was skipped: {}",
               label, script.displayName(), failure.getMessage());
            this.runtime.reportRuntimeFailure(label + " script '" + script.displayName() + "' failed", failure);
         }
      }
   }
}
