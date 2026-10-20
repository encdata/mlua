package dev.mlua.runtime.client;

import dev.mlua.core.MLuaLogger;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;

/** Guards APIs that expose frame-owned Minecraft renderer objects to Lua. */
final class MLuaClientRenderThread {
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    private MLuaClientRenderThread() { }

    static boolean require(String operation) {
        if (MLuaClientThread.isClientThread()) return true;
        if (WARNED.add(operation)) {
            MLuaLogger.LOGGER.error("[MLua] Skipping {} because the loader invoked it outside Minecraft's client render thread",
                operation);
        }
        return false;
    }
}
