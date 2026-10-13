package dev.mlua.minecraft.scripted;

import dev.mlua.core.MLuaRuntime;
import dev.mlua.core.MLuaRuntimeSession;
import dev.mlua.module.MLuaRegistration;
import net.minecraft.world.level.Level;

public final class MLuaScriptRuntimeRegistration {
    private MLuaScriptRuntimeRegistration() { }

    public static MLuaRegistration forLevel(Level level) {
        if (level != null && !level.isClientSide() && level.getServer() != null) {
            MLuaRuntimeSession session = MLuaRuntimeSession.forServer(level.getServer());
            if (session != null && session.registration() != null) return session.registration();
        }
        return MLuaRuntime.get();
    }
}
