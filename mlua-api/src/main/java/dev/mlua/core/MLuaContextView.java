package dev.mlua.core;

import dev.mlua.common.enums.RegistrationPhase;
import dev.mlua.common.enums.ScriptType;
import dev.mlua.common.enums.Side;
import dev.mlua.module.MLuaRegistration;
import dev.mlua.core.scheduler.MLuaTaskSchedulerView;
import java.nio.file.Path;
import java.util.function.Supplier;
import net.minecraft.server.MinecraftServer;
import org.luaj.vm2.Globals;

public interface MLuaContextView {
   String ownerModId();

   Side side();

   RegistrationPhase phase();

   MLuaTaskSchedulerView scheduler();

   /** Returns the globals bound to this context, or throws when the implementation does not expose them. */
   default Globals globals() {
      throw new UnsupportedOperationException("This MLua context does not expose Lua globals");
   }

   /** Returns the runtime registration surface, or throws when the implementation does not expose it. */
   default MLuaRegistration registration() {
      throw new UnsupportedOperationException("This MLua context does not expose runtime registration");
   }

   /** Runtime/session owner for resources created through this context. */
   default Object session() { return this; }

   /**
    * Returns state shared by instances of one Java service on this runtime side.
    * Context implementations without a session fall back to a fresh value.
    */
   default <T> T serviceState(String serviceName, String key, Class<T> stateType,
                              Supplier<? extends T> factory) {
      return factory.get();
   }

   MinecraftServer server();

   Path filesRoot();

   boolean isClient();

   boolean isServer();

   boolean isRegistration();

   boolean isPhase(RegistrationPhase phase);

   boolean isScriptType(ScriptType type);
}
