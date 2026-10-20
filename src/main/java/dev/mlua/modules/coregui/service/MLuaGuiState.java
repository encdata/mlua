package dev.mlua.modules.coregui.service;

import dev.mlua.common.enums.CoreGuiType;
import dev.mlua.core.signal.MLuaSignal;
import dev.mlua.minecraft.ui.element.GuiButton;
import dev.mlua.minecraft.ui.element.ScreenGui;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** Mutable GUI state owned by one MLua client scheduler. */
final class MLuaGuiState {
    static final Map<GuiButton, Object> REPLACEMENT_ACTIONS = new WeakHashMap<>();

    final List<ScreenGui> screens = new CopyOnWriteArrayList<>();
    final List<MLuaScriptScreen> scriptScreens = new CopyOnWriteArrayList<>();
    final Map<String, MLuaHudElement> hudElements = new ConcurrentHashMap<>();
    final Set<CoreGuiType> disabledTypes = EnumSet.noneOf(CoreGuiType.class);
    final List<MLuaNativeLayout> nativeLayouts = new CopyOnWriteArrayList<>();
    final Map<String, ScreenTarget> screenTargets = new ConcurrentHashMap<>();
    final MLuaSignal screenOpened = new MLuaSignal();
    final MLuaSignal screenClosed = new MLuaSignal();
    final MLuaSignal chatLineAdding = new MLuaSignal();
    volatile Object executionLock = this;
    volatile boolean enabled = true;

    void clear() {
        this.hudElements.clear();
        MLuaHudElementSnapshot.invalidate();
        for (MLuaScriptScreen screen : List.copyOf(this.scriptScreens)) screen.dispose();
        List<MLuaNativeLayout> layouts = List.copyOf(this.nativeLayouts);
        for (int index = layouts.size() - 1; index >= 0; index--) {
            MLuaNativeLayout layout = layouts.get(index);
            layout.restore();
            if (layout.replacement() instanceof GuiButton button) {
                synchronized (REPLACEMENT_ACTIONS) { REPLACEMENT_ACTIONS.remove(button); }
            }
        }
        for (ScreenGui screen : this.screens) screen.destroy();
        this.screens.clear();
        this.disabledTypes.clear();
        this.enabled = true;
        this.nativeLayouts.clear();
        this.screenTargets.clear();
        this.screenOpened.Destroy();
        this.screenClosed.Destroy();
        this.chatLineAdding.Destroy();
    }

    record ScreenTarget(String owner, String alias, String className) { }
}
