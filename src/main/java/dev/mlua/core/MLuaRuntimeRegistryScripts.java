package dev.mlua.core;

import dev.mlua.common.enums.RegistrationPhase;
import dev.mlua.common.enums.ScriptType;
import dev.mlua.common.enums.Side;
import dev.mlua.common.mixin.MixinRuleEngine;
import dev.mlua.common.mixin.MLuaMixinCallbackRegistry;
import dev.mlua.core.script.MLuaScript;
import dev.mlua.loaderapi.LoaderApi;
import java.nio.file.Path;
import java.util.Set;
import org.luaj.vm2.LuaError;

/** Loads registry scripts once and keeps their registrations ready for rollback. */
final class MLuaRuntimeRegistryScripts {
   private final MLuaRuntime runtime;
   private final MLuaRuntimeScriptCatalog scripts;
   private final MLuaRuntimeScriptExecutor executor;
   private final MLuaRegistryScriptLifecycle lifecycle;
   private volatile boolean hasRun;

   MLuaRuntimeRegistryScripts(MLuaRuntime runtime, MLuaRuntimeScriptCatalog scripts,
                              MLuaRuntimeScriptExecutor executor,
                              MLuaRegistryScriptLifecycle lifecycle) {
      this.runtime = runtime;
      this.scripts = scripts;
      this.executor = executor;
      this.lifecycle = lifecycle;
   }

   boolean hasRun() {
      return this.hasRun;
   }

   synchronized void run() {
      if (this.hasRun) {
         throw new IllegalStateException("[MLua] Registry scripts already ran for this runtime. Native Minecraft registrations cannot be rolled back or hot-reloaded; restart the game to apply registry script changes.");
      }
      this.runtime.ensureScriptsCurrent();
      // Registry submissions cannot be undone after a script starts.
      this.hasRun = true;
      this.clearAllRegistrations();
      this.scripts.ofType(ScriptType.REGISTRY).forEach(script -> runScript(script, false));
      this.scripts.withEntrypoint("run_registry", Side.SERVER)
         .forEach(script -> runScript(script, true));

      this.runtime.generateAssetPack();
      MixinRuleEngine.triggerRetransform();
      MLuaDocumentation.generate(LoaderApi.platform().gameDir());
   }

   private void runScript(MLuaScript script, boolean entrypoint) {
      try {
         this.execute(script, entrypoint);
      } catch (RuntimeException | Error failure) {
         String kind = entrypoint ? "Registry entrypoint script" : "Registry script";
         MLuaLogger.LOGGER.error("{} '{}' failed and was skipped: {}", kind,
            script.displayName(), failure.getMessage());
         this.runtime.reportRuntimeFailure(kind + " '" + script.displayName() + "' failed", failure);
      }
   }

   void execute(MLuaScript script, boolean entrypoint) {
      this.lifecycle.execute(script.path(), this::unregisterMixinScope,
         registration -> this.executor.executeRegistryScript(script, entrypoint, registration));
   }

   void clearAllRegistrations() {
      this.lifecycle.clearAll(this::unregisterMixinScope);
   }

   void clearRegistrations(Set<Path> scriptPaths) {
      this.lifecycle.clearPaths(scriptPaths, this::unregisterMixinScope);
   }

   void unregisterMixinScope(Path path) {
      if (path != null) {
         MixinRuleEngine.get().unregisterScope(MLuaMixinCallbackRegistry.scriptScope(path.toString()));
      }
   }
}
