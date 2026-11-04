package dev.mlua.runtime.client;

import dev.mlua.core.MLuaRuntime;
import dev.mlua.module.MLuaModuleLoader;
import net.minecraft.client.Minecraft;

public final class MLuaClientEventHandlers {
    private MLuaClientEventHandlers() {
    }

   public static void onRenderTickStart(MLuaRuntime runtime, double deltaTime) {
      MLuaRenderCallbacks.setFrameDeltaTime(deltaTime);
      if (MLuaClientThread.isPumpingClientEventLoopForLua()) return;
      if (isConnectingWithoutPlayer()) return;
      MLuaGuiRenderer.onRenderFrame(deltaTime);
      var session = runtime.session();
      MLuaModuleLoader.get().onRenderFrame(runtime, session, deltaTime);
      MLuaModuleLoader.get().afterRenderFrame(runtime, session, deltaTime);
   }

   public static void onRenderTickEnd(double deltaTime) {
      if (MLuaClientThread.isPumpingClientEventLoopForLua() || isConnectingWithoutPlayer()) return;
      MLuaRuntime runtime = MLuaRuntime.get();
      if (runtime == null || runtime.session() == null) return;
      MLuaModuleLoader.get().onRenderFrameEnd(runtime, runtime.session(), deltaTime);
   }

   /** Avoid pumping login packets reentrantly while a render callback's Lua coroutine is resumed. */
   public static boolean isConnectingWithoutPlayer() {
      Minecraft minecraft = Minecraft.getInstance();
      if (minecraft == null) return false;
      boolean localPlayerMissing = minecraft.player == null;
      if (!localPlayerMissing) return false;
      boolean connectionActive = minecraft.getConnection() != null;
      boolean connectScreenOpen = minecraft.screen != null
         && "ConnectScreen".equals(minecraft.screen.getClass().getSimpleName());
      return connectionActive || connectScreenOpen;
   }
}
