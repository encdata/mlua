package dev.mlua.modules.coregui.service;

import dev.mlua.common.enums.Side;
import dev.mlua.core.MLuaRuntime;
import dev.mlua.core.scheduler.MLuaTaskSchedulerView;
import dev.mlua.minecraft.binding.MLuaNativeControl;
import dev.mlua.minecraft.binding.MLuaNativeScreen;
import dev.mlua.minecraft.ui.element.GuiButton;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/** Owns the boundary between the per-runtime GUI service and Minecraft's live screens. */
final class MLuaGuiNativeScreenLifecycle {
    private MLuaGuiNativeScreenLifecycle() { }

    static void applyLayouts(Object screen) {
        if (MLuaNativeScreenManager.isClientConnecting()) return;
        MLuaGuiService service = MLuaGuiService.getInstance();
        if (service == null) return;

        MLuaRuntime runtime = MLuaRuntime.get();
        service.nativeLayoutController.applyLayouts(screen);
        LuaTable controls = service.nativeControls(LuaValue.NIL, false, screen);
        LuaValue screenInstance = runtime == null || runtime.session() == null
            ? LuaValue.NIL : MLuaNativeScreen.instance(runtime, screen);
        if (runtime != null && runtime.session() != null) {
            service.state.screenOpened.fireInlineOnSession(runtime.session(), Side.CLIENT,
                LuaValue.varargsOf(new LuaValue[]{LuaValue.valueOf(screen.getClass().getName()), controls, screenInstance}));
        }
    }

    static void removed(Object screen) {
        if (screen == null) return;
        try {
            MLuaGuiService service = MLuaGuiService.getInstance();
            if (service == null) return;
            service.nativeLayoutController.restoreNativeLayouts(screen);
            if (MLuaNativeScreenManager.isClientConnecting()) return;
            MLuaRuntime runtime = MLuaRuntime.get();
            if (runtime == null || runtime.session() == null || service.state.screenClosed.isEmpty()) return;
            MLuaNativeScreenManager.runCloseCallback(screen, () -> {
                LuaTable controls = service.nativeControls(LuaValue.NIL, false, screen);
                LuaValue screenInstance = MLuaNativeScreen.instance(runtime, screen);
                service.state.screenClosed.fireInlineOnSession(runtime.session(), Side.CLIENT,
                    LuaValue.varargsOf(new LuaValue[]{LuaValue.valueOf(screen.getClass().getName()), controls, screenInstance}));
            });
        } finally {
            MLuaNativeControl.invalidateScreen(screen);
        }
    }

    static LuaTable controlsForCurrentRuntime(Object screen) {
        MLuaGuiService.requireNativeScreenClientThread("Gui:GetNativeControls");
        MLuaGuiService service = MLuaGuiService.getInstance();
        if (service == null) throw new LuaError("[MLua] Gui service is not active on the client");
        return service.nativeControls(LuaValue.NIL, false, screen);
    }

    static LuaTable controlsForScheduler(MLuaTaskSchedulerView scheduler, Object screen) {
        MLuaGuiService.requireNativeScreenClientThread("NativeScreen:GetControls");
        MLuaGuiService service = MLuaGuiService.getForScheduler(scheduler);
        if (service == null) throw new LuaError("[MLua] Gui service is not active for this client runtime");
        return service.nativeControls(LuaValue.NIL, false, screen);
    }

    static void setControlProperty(MLuaNativeControl.Backing backing, String name, LuaValue value) {
        MLuaGuiNativeControlProperties.setProperty(backing, name, value);
    }

    static void setControlProperties(MLuaNativeControl.Backing backing, LuaTable updates) {
        MLuaGuiNativeControlProperties.setProperties(backing, updates);
    }

    static void activateReplacement(GuiButton button, double mouseX, double mouseY) {
        MLuaNativeWidgetActivation.activate(button, mouseX, mouseY);
    }

    static Object currentScreen() {
        return MLuaNativeScreenManager.currentScreen();
    }

    static void requireClientThread(String operation) {
        MLuaNativeScreenManager.requireClientThread(operation);
    }

    static MLuaRuntime requireActiveClientRuntime() {
        MLuaRuntime runtime = MLuaRuntime.get();
        if (runtime == null || runtime.session() == null) {
            throw new LuaError("[MLua] Gui cannot create a native screen without an active client runtime.");
        }
        return runtime;
    }

    static void tickPendingScreens() {
        MLuaNativeScreenManager.tickPending();
    }

    static void openScreen(MLuaScriptScreen screen) {
        MLuaNativeScreenManager.queueOpen(screen);
    }

    static boolean isPending(Object screen) {
        return MLuaNativeScreenManager.isPending(screen);
    }

    static boolean isDisposed(Object screen) {
        return MLuaNativeScreenManager.isDisposed(screen);
    }

    static boolean isClosing(Object screen) {
        return MLuaNativeScreenManager.isClosing(screen);
    }

    static boolean closeScreen(Object expectedScreen) {
        return MLuaNativeScreenManager.close(expectedScreen);
    }
}
