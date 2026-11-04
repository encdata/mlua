package dev.mlua.modules.coregui.service;

import dev.mlua.minecraft.ui.element.ScreenGui;
import org.luaj.vm2.LuaError;

/** Owns addon-defined screen aliases for one GUI runtime. */
final class MLuaGuiScreenTargetRegistry {
    private final MLuaGuiService service;
    private final MLuaGuiState state;

    MLuaGuiScreenTargetRegistry(MLuaGuiService service, MLuaGuiState state) {
        this.service = service;
        this.state = state;
    }

    String register(String alias, String className) {
        if (alias == null || !alias.matches("[A-Za-z][A-Za-z0-9_.-]*")) {
            throw new LuaError("[MLua] Gui:RegisterScreenTarget alias must start with a letter and contain only letters, digits, '.', '_' or '-'.");
        }
        if (ScreenGui.isBuiltInScreenTarget(alias)) {
            throw new LuaError("[MLua] Screen target alias '" + alias + "' is reserved by MLua.");
        }
        if (className == null || className.isBlank()) {
            throw new LuaError("[MLua] Gui:RegisterScreenTarget requires a Java screen class or interface name.");
        }
        String key = alias.toLowerCase(java.util.Locale.ROOT);
        MLuaGuiState.ScreenTarget target = new MLuaGuiState.ScreenTarget(service.ownerId(), alias, className);
        MLuaGuiState.ScreenTarget existing = state.screenTargets.putIfAbsent(key, target);
        if (existing != null && (!existing.owner().equals(target.owner())
            || !existing.className().equals(target.className()))) {
            throw new LuaError("[MLua] Screen target alias '" + alias + "' is already registered.");
        }
        service.recomposeTrackedScreens();
        return alias;
    }

    boolean unregister(String alias) {
        if (alias == null || alias.isBlank()) return false;
        String key = alias.toLowerCase(java.util.Locale.ROOT);
        MLuaGuiState.ScreenTarget target = state.screenTargets.get(key);
        boolean removed = target != null && target.owner().equals(service.ownerId())
            && state.screenTargets.remove(key, target);
        if (removed) service.recomposeTrackedScreens();
        return removed;
    }

    boolean matches(String alias, Object screen) {
        if (alias == null || screen == null) return false;
        MLuaGuiState.ScreenTarget target = state.screenTargets.get(alias.toLowerCase(java.util.Locale.ROOT));
        return target != null && ScreenGui.matchesScreenClass(screen.getClass(), target.className());
    }

    static boolean matches(MLuaGuiState state, String target, Object screen) {
        if (state == null || target == null || screen == null) return false;
        MLuaGuiState.ScreenTarget registered = state.screenTargets.get(target.toLowerCase(java.util.Locale.ROOT));
        return registered != null && ScreenGui.matchesScreenClass(screen.getClass(), registered.className());
    }
}
