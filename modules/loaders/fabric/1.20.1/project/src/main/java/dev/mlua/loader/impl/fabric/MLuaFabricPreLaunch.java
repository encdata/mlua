package dev.mlua.loader.impl.fabric;

import dev.mlua.agent.MLuaAgentBootstrap;
import dev.mlua.common.mixin.MLuaEarlyMixinBootstrap;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

public final class MLuaFabricPreLaunch implements PreLaunchEntrypoint {
   @Override
   public void onPreLaunch() {
      MLuaAgentBootstrap.ensureAttached();
      MLuaEarlyMixinBootstrap.bootstrap();
   }
}
