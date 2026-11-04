package dev.mlua.modules.coregui.service;

import dev.mlua.core.MLuaContext;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

/** Owns script HUD anchor validation, registration, and removal for one GUI runtime. */
final class MLuaGuiHudRegistry {
    private static final AtomicLong NEXT_ID = new AtomicLong(1);

    private final MLuaGuiState state;
    private final String ownerModId;
    private final Object executionLock;

    MLuaGuiHudRegistry(MLuaGuiState state, String ownerModId, Object executionLock) {
        this.state = state;
        this.ownerModId = ownerModId;
        this.executionLock = executionLock;
    }

    String attach(String anchor, String id, boolean before, LuaValue callback, int priority, MLuaContext luaContext) {
        if (id == null || id.isBlank()) throw new LuaError("[MLua] HUD element id must not be empty.");
        String normalizedAnchor = anchor == null ? "" : anchor.trim();
        String positionAnchor = switch (normalizedAnchor.toLowerCase(Locale.ROOT)) {
            case "hud" -> "Hud";
            case "chat" -> "Chat";
            case "crosshair" -> "Crosshair";
            case "hotbar" -> "Hotbar";
            case "health" -> "Health";
            case "experience" -> "Experience";
            case "effects" -> "Effects";
            //--version 1.21.1
            case "food" -> "Food";
            //--endversion
            //--version 1.21.1,26.1
            case "overlaymessage", "overlay_message" -> "OverlayMessage";
            case "title" -> "Title";
            case "scoreboard" -> "Scoreboard";
            case "playerlist", "player_list", "tablist", "tab_list" -> "PlayerList";
            //--endversion
            default -> throw new LuaError("[MLua] Unsupported HUD anchor '" + anchor + "' for this Minecraft version.");
        };
        if (!callback.isfunction()) throw new LuaError("[MLua] HUD element callback must be a function.");

        String key = this.ownerModId + ":" + id;
        String position = (before ? "Before" : "After") + positionAnchor;
        MLuaHudElement element = new MLuaHudElement(key, this.ownerModId, positionAnchor, before, position, callback,
            priority, NEXT_ID.getAndIncrement(), this.executionLock, luaContext);
        this.state.hudElements.put(key, element);
        MLuaHudElementSnapshot.invalidate();
        return id;
    }

    boolean remove(String id) {
        if (id == null) return false;
        String key = this.ownerModId + ":" + id;
        MLuaHudElement element = this.state.hudElements.get(key);
        if (element == null || !this.state.hudElements.remove(key, element)) return false;
        MLuaHudElementSnapshot.invalidate();
        return true;
    }

}
