package dev.mlua.core;

import dev.mlua.common.enums.RegistrationPhase;
import dev.mlua.common.enums.ScriptType;
import dev.mlua.common.enums.Side;
import dev.mlua.core.script.MLuaScript;
import dev.mlua.module.MLuaModuleLoader;
import dev.mlua.loaderapi.LoaderApi;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.level.Level;

/** Owns client script startup, reload, hot reload, and the common-side client bridge. */
final class MLuaRuntimeClientScripts {
   private static final ConcurrentHashMap<HookKey, Method> CLIENT_HOOKS = new ConcurrentHashMap<>();
   private final MLuaRuntime runtime;
   private final MLuaRuntimeSession session;
   private final MLuaRuntimeScriptCatalog scripts;
   private final MLuaRuntimeScriptExecutor executor;
   private final MLuaClientScriptWatcher watcher = new MLuaClientScriptWatcher();
   private final MLuaRuntimeCommandScripts commands;
   private final MLuaRuntimeWorldLifecycle worlds;
   private boolean clientRan;

   MLuaRuntimeClientScripts(MLuaRuntime runtime, MLuaRuntimeSession session,
                            MLuaRuntimeScriptCatalog scripts, MLuaRuntimeScriptExecutor executor,
                            MLuaRuntimeCommandScripts commands, MLuaRuntimeWorldLifecycle worlds) {
      this.runtime = runtime;
      this.session = session;
      this.scripts = scripts;
      this.executor = executor;
      this.commands = commands;
      this.worlds = worlds;
   }

   void runOnce() {
      if (this.clientRan) return;
      this.clientRan = true;
      this.commands.run(Side.CLIENT, null);
      for (MLuaScript script : this.scripts.ofType(ScriptType.CLIENT)) {
         try {
            this.executor.execute(script, Side.CLIENT, RegistrationPhase.RUNTIME, this.session.server());
         } catch (RuntimeException failure) {
            MLuaLogger.LOGGER.error("Client script '{}' failed and was skipped: {}",
               script.displayName(), failure.getMessage());
            this.runtime.reportRuntimeFailure("Client script '" + script.displayName() + "' failed", failure);
         }
      }
      for (MLuaScript script : this.scripts.withEntrypoint("run_client", Side.CLIENT)) {
         try {
            this.executor.executeEntrypoint(script, Side.CLIENT, RegistrationPhase.RUNTIME, this.session.server());
         } catch (RuntimeException failure) {
            MLuaLogger.LOGGER.error("Client entrypoint script '{}' failed and was skipped: {}",
               script.displayName(), failure.getMessage());
            this.runtime.reportRuntimeFailure("Client entrypoint script '" + script.displayName() + "' failed",
               failure);
         }
      }
      this.reloadWorldLoadEntrypoints(false);
   }

   void runCommandScripts() {
      this.commands.run(Side.CLIENT, null);
   }

   void reload() {
      MLuaModuleLoader.get().clearRuntimeState(this.runtime, this.session, Side.CLIENT);
      this.session.clearScriptState(Side.CLIENT);
      this.runtime.forceRefreshScripts();
      this.executor.resetSide(Side.CLIENT);
      MLuaModuleLoader.get().onReload(Side.CLIENT);
      this.commands.run(Side.CLIENT, null);

      for (MLuaScript script : this.scripts.ofType(ScriptType.CLIENT)) {
         try {
            this.executor.execute(script, Side.CLIENT, RegistrationPhase.RUNTIME, this.session.server());
         } catch (RuntimeException failure) {
            MLuaLogger.LOGGER.error("Client script '{}' failed during reload: {}",
               script.displayName(), failure.getMessage());
            this.runtime.reportRuntimeFailure("Client script '" + script.displayName()
               + "' failed during reload", failure);
         }
      }
      for (MLuaScript script : this.scripts.withEntrypoint("run_client", Side.CLIENT)) {
         try {
            this.executor.executeEntrypoint(script, Side.CLIENT, RegistrationPhase.RUNTIME, this.session.server());
         } catch (RuntimeException failure) {
            MLuaLogger.LOGGER.error("Client entrypoint script '{}' failed during reload: {}",
               script.displayName(), failure.getMessage());
            this.runtime.reportRuntimeFailure("Client entrypoint script '" + script.displayName()
               + "' failed during reload", failure);
         }
      }
      this.reloadWorldLoadEntrypoints(true);
   }

   void pollHotReload() {
      long now = System.currentTimeMillis();
      if (this.watcher.poll(this.scripts.roots(), now, this::isClientWindowActive)) {
         MLuaLogger.LOGGER.info("[MLua] Script changes detected. Reloading client scripts.");
         this.runtime.reloadClientOnly();
         this.runtime.recordClientMessage("[MLua] Script changes detected. Client scripts reloaded.");
      }
   }

   void reloadWorldLoadEntrypoints(boolean forceReload) {
      if (!LoaderApi.platform().isClient()) return;
      Level level;
      try {
         level = (Level) invokeClientHook("currentLevel", new Class<?>[0]);
      } catch (RuntimeException unavailable) {
         return;
      }
      if (level == null || !level.isClientSide()) return;
      if (forceReload) this.worlds.forget(level);
      this.runtime.runWorldLifecycleEntrypoint(level, true);
   }

   void generateAssets() {
      if (LoaderApi.platform().isClient()) invokeClientHook("generateAssets", new Class<?>[0]);
   }

   private boolean isClientWindowActive() {
      boolean lastWindowActive = this.watcher.lastWindowActive();
      if (!LoaderApi.platform().isClient()) return lastWindowActive;
      try {
         return (boolean) invokeClientHook("isWindowActive", new Class<?>[]{boolean.class}, lastWindowActive);
      } catch (Throwable ignored) {
         return lastWindowActive;
      }
   }

   static Object invokeClientHook(String methodName, Class<?>[] parameterTypes, Object... arguments) {
      try {
         HookKey key = new HookKey(methodName, List.of(parameterTypes));
         return CLIENT_HOOKS.computeIfAbsent(key, MLuaRuntimeClientScripts::resolveClientHook)
            .invoke(null, arguments);
      } catch (InvocationTargetException failure) {
         Throwable cause = failure.getCause() == null ? failure : failure.getCause();
         if (cause instanceof RuntimeException runtimeFailure) throw runtimeFailure;
         if (cause instanceof Error error) throw error;
         throw new IllegalStateException("MLua client runtime hook '" + methodName + "' failed", cause);
      } catch (IllegalAccessException failure) {
         throw new IllegalStateException("MLua client runtime hook '" + methodName + "' is inaccessible", failure);
      }
   }

   private static Method resolveClientHook(HookKey key) {
      try {
         Class<?> hooks = Class.forName("dev.mlua.runtime.client.MLuaClientRuntimeHooks", true,
            MLuaRuntimeClientScripts.class.getClassLoader());
         return hooks.getMethod(key.name(), key.parameterTypes().toArray(new Class<?>[0]));
      } catch (ReflectiveOperationException failure) {
         throw new IllegalStateException("MLua client runtime hook '" + key.name() + "' is unavailable", failure);
      }
   }

   private record HookKey(String name, List<Class<?>> parameterTypes) {
      private HookKey {
         parameterTypes = List.copyOf(parameterTypes);
      }
   }
}
