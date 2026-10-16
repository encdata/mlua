package dev.mlua.runtime.client;

import dev.mlua.core.MLuaRuntime;
import dev.mlua.module.MLuaModuleLoader;

public final class MLuaClientEventHandlers {
    private MLuaClientEventHandlers() {
    }

   public static void onRenderTickStart(MLuaRuntime runtime, double deltaTime) {
      MLuaRenderCallbacks.setFrameDeltaTime(deltaTime);
      var session = runtime.session();
      MLuaModuleLoader.get().onRenderFrame(runtime, session, deltaTime);
      MLuaModuleLoader.get().afterRenderFrame(runtime, session, deltaTime);
   }

   public static void onRenderTickEnd() {
      MLuaRuntime runtime = MLuaRuntime.get();
      MLuaModuleLoader.get().onRenderFrameEnd(runtime, runtime.session());
   }
}
