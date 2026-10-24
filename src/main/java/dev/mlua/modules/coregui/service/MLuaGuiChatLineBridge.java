package dev.mlua.modules.coregui.service;

import dev.mlua.common.enums.Side;
import dev.mlua.core.MLuaLogger;
import dev.mlua.core.MLuaRuntime;
import dev.mlua.minecraft.binding.MLuaChatLine;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.network.chat.Component;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/** Bridges Minecraft's synchronous chat insertion hook to the short-lived Lua chat-line event. */
public final class MLuaGuiChatLineBridge {
    private static final boolean PLAYER_TEST = Boolean.parseBoolean(System.getenv("MLUA_CI_PLAYER_TEST"));
    private static final AtomicBoolean REPORTED_BRIDGE = new AtomicBoolean();

    private MLuaGuiChatLineBridge() { }

    public static Component beforeChatLineAdded(Component message) {
        if (PLAYER_TEST && REPORTED_BRIDGE.compareAndSet(false, true)) {
            MLuaLogger.LOGGER.info("MLUA_CI_CHAT_LINE_BRIDGE_ENTERED");
        }

        MLuaGuiService service = MLuaGuiService.getInstance();
        MLuaRuntime runtime = MLuaRuntime.get();
        if (service == null || runtime == null || runtime.session() == null
                || service.state.chatLineAdding.isEmpty()) {
            return message;
        }

        MLuaChatLine.Backing backing = new MLuaChatLine.Backing(message);
        LuaTable instance = MLuaChatLine.instance(runtime, backing);
        try {
            // Minecraft needs the transformed component before this method returns, so these
            // listeners must remain synchronous on the caller's client thread.
            service.state.chatLineAdding.fireInlineOnSession(runtime.session(), Side.CLIENT,
                    LuaValue.varargsOf(new LuaValue[]{instance}));
            return backing.result();
        } finally {
            backing.expire();
        }
    }
}
