package dev.mlua.core;

import dev.mlua.common.enums.RegistrationPhase;
import dev.mlua.common.enums.Side;
import dev.mlua.common.mixin.MixinRuleEngine;
import dev.mlua.core.script.MLuaScript;
import dev.mlua.loaderapi.LoaderApi;
import org.luaj.vm2.LuaError;

/** Runs mixin scripts before Minecraft starts loading classes. */
final class MLuaRuntimeMixinScripts {
   private final MLuaRuntime runtime;
   private final MLuaRuntimeScriptCatalog scripts;
   private final MLuaRuntimeScriptExecutor executor;

   MLuaRuntimeMixinScripts(MLuaRuntime runtime, MLuaRuntimeScriptCatalog scripts,
                           MLuaRuntimeScriptExecutor executor) {
      this.runtime = runtime;
      this.scripts = scripts;
      this.executor = executor;
   }

   void run() {
      for (MLuaScript script : this.scripts.mixinScripts()) {
         Side side = script.type().mixinSide();
         if (side == Side.CLIENT && !LoaderApi.platform().isClient()) continue;
         this.execute(script, side, false,
            () -> this.executor.execute(script, side, RegistrationPhase.MIXIN, null));
      }
      for (MLuaScript script : this.scripts.withEntrypoint("run_mixin", Side.SERVER)) {
         this.execute(script, Side.SERVER, true,
            () -> this.executor.executeEntrypoint(script, Side.SERVER, RegistrationPhase.MIXIN, null));
      }
      MixinRuleEngine.markEarlyBootstrapDone();
   }

   private void execute(MLuaScript script, Side side, boolean entrypoint, Runnable run) {
      this.runtime.unregisterMixinScope(script.path());
      try {
         run.run();
      } catch (LuaError failure) {
         this.runtime.unregisterMixinScope(script.path());
         if (entrypoint) {
            MLuaLogger.LOGGER.error("Mixin entrypoint script '{}' failed and was skipped: {}",
               script.displayName(), failure.getMessage());
         } else {
            String sideName = side == Side.CLIENT ? "Client" : "Server";
            MLuaLogger.LOGGER.error("{} mixin script '{}' failed and was skipped: {}",
               sideName, script.displayName(), failure.getMessage());
         }
         String summary = entrypoint
            ? "Mixin entrypoint script '" + script.displayName() + "' failed"
            : "Mixin script '" + script.displayName() + "' failed";
         this.runtime.reportRuntimeFailure(summary, failure);
      } catch (RuntimeException | Error failure) {
         this.runtime.unregisterMixinScope(script.path());
         throw failure;
      }
   }
}
