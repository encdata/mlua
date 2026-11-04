package dev.mlua.core;

import dev.mlua.common.enums.RegistrationPhase;
import dev.mlua.common.enums.ScriptType;
import dev.mlua.common.enums.Side;
import dev.mlua.common.mixin.MixinRuleEngine;
import dev.mlua.core.script.MLuaScript;
import dev.mlua.loaderapi.LoaderApi;
import dev.mlua.module.MLuaModuleLoader;
import java.nio.file.Path;
import java.util.Set;
import net.minecraft.server.MinecraftServer;

/** Owns the ordered reload pipeline for scripts supplied by resource packs. */
final class MLuaRuntimeResourceReload {
   private final MLuaRuntime runtime;
   private final MLuaRuntimeScriptCatalog scripts;

   MLuaRuntimeResourceReload(MLuaRuntime runtime, MLuaRuntimeScriptCatalog scripts) {
      this.runtime = runtime;
      this.scripts = scripts;
   }

   void reload(Set<Path> replacedScriptPaths, boolean nativeRegistryScriptsChanged) {
      this.runtime.advanceLifecycleEpoch();
      for (Path path : replacedScriptPaths) this.runtime.unregisterMixinScope(path);
      boolean nativeRegistriesFrozen = LoaderApi.registry().isFrozen();
      if (!nativeRegistriesFrozen) this.runtime.clearRegistryScriptRegistrations(replacedScriptPaths);

      MLuaRuntimeSession session = this.runtime.session();
      session.serverScheduler().removeScripts(replacedScriptPaths);
      session.clientScheduler().removeScripts(replacedScriptPaths);
      this.runtime.forceRefreshScripts();
      session.clearModuleCache(Side.BOTH);
      MLuaModuleLoader.get().clearRuntimeState(this.runtime, session, Side.BOTH);

      if (nativeRegistriesFrozen && nativeRegistryScriptsChanged) {
         MLuaLogger.LOGGER.warn("[MLua] Registry-phase scripts changed during resource reload, but Minecraft registries are frozen. Existing registrations remain active; restart the game to apply the new script definitions.");
      }
      this.rerunRegistryScripts(nativeRegistriesFrozen);
      this.rerunRuntimeScripts();
      this.rerunEntrypoints();

      MixinRuleEngine.triggerRetransform();
      this.runtime.generateAssetPack();
      MLuaDocumentation.generate(LoaderApi.platform().gameDir());
   }

   private void rerunRegistryScripts(boolean nativeRegistriesFrozen) {
      if (nativeRegistriesFrozen) return;
      for (MLuaScript script : this.scripts.resourceScripts()) {
         if (!MLuaRuntimeScriptCatalog.isRegistryPhaseScript(script)) continue;
         try {
            this.runtime.executeRegistryScript(script, script.type() != ScriptType.REGISTRY);
         } catch (Throwable failure) {
            this.runtime.reportRuntimeFailure("Registry script '" + script.displayName()
               + "' failed during reload", failure);
         }
      }
   }

   private void rerunRuntimeScripts() {
      MinecraftServer server = this.runtime.server();
      boolean client = LoaderApi.platform().isClient();
      for (MLuaScript script : this.scripts.resourceScripts()) {
         try {
            if (script.type() == ScriptType.REGISTRY) continue;
            if (script.type().isMixin()) {
               Side side = script.type().mixinSide();
               if (side == Side.CLIENT && !client) continue;
               this.runtime.executeRuntimeScript(script, side, RegistrationPhase.MIXIN, null);
            } else if (script.type() == ScriptType.SERVER && server != null) {
               this.runtime.executeRuntimeScript(script, Side.SERVER, RegistrationPhase.RUNTIME, server);
            } else if (script.type() == ScriptType.CLIENT && client) {
               this.runtime.executeRuntimeScript(script, Side.CLIENT, RegistrationPhase.RUNTIME, server);
            }
         } catch (Throwable failure) {
            if (script.type().isMixin()) this.runtime.unregisterMixinScope(script.path());
            this.runtime.reportRuntimeFailure("Resource-pack script '" + script.displayName()
               + "' failed during reload", failure);
         }
      }
   }

   private void rerunEntrypoints() {
      MinecraftServer server = this.runtime.server();
      if (server != null) {
         this.runtime.runCommandScripts(server.getCommands().getDispatcher());
         this.runtime.runEntrypointScripts(this.scripts.resourceScripts(), "run_server", Side.SERVER, server);
         this.runtime.reloadWorldLoadEntrypoints(server);
      }
      if (LoaderApi.platform().isClient()) {
         this.runtime.runClientCommandScripts();
         this.runtime.runEntrypointScripts(this.scripts.resourceScripts(), "run_client", Side.CLIENT, server);
         this.runtime.reloadClientWorldLoadEntrypoints(true);
      }
   }
}
