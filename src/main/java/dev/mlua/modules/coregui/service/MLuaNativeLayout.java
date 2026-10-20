package dev.mlua.modules.coregui.service;

import dev.mlua.core.MLuaLogger;
import dev.mlua.minecraft.binding.MLuaNativeControl;
import dev.mlua.minecraft.ui.element.GuiObject;
import dev.mlua.minecraft.ui.element.ScreenGui;
import java.lang.reflect.Method;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

final class MLuaNativeLayout {
    private final long id;
    private final String owner;
    private final String screen;
    private final LuaTable selector;
    private final LuaTable properties;
    private GuiObject replacement;
    private GuiObject before;
    private GuiObject after;
    private ScreenGui overlay;
    private boolean unsupportedScreenReported;
    private boolean failed;
    private boolean restoreFailureReported;
    private final Map<Object, Map<Object, WidgetState>> originals = new IdentityHashMap<>();

    MLuaNativeLayout(long id, String owner, String screen, LuaTable selector, LuaTable properties,
            GuiObject replacement, GuiObject before, GuiObject after, ScreenGui overlay) {
        this.id = id;
        this.owner = owner;
        this.screen = screen;
        this.selector = selector;
        this.properties = properties;
        this.replacement = replacement;
        this.overlay = overlay;
        this.before = before;
        this.after = after;
    }

    long id() { return id; }
    String owner() { return owner; }
    String screen() { return screen; }
    LuaTable selector() { return selector; }
    LuaTable properties() { return properties; }
    GuiObject replacement() { return replacement; }
    GuiObject before() { return before; }
    GuiObject after() { return after; }
    ScreenGui overlay() { return overlay; }
    synchronized boolean failed() { return failed; }
    synchronized void fail(Class<?> screenClass, RuntimeException failure) {
        if (failed) return;
        failed = true;
        MLuaLogger.LOGGER.warn("Disabling native UI layout {} for '{}' after it failed on screen {}: {}",
            id, screen, screenClass.getName(), failure.getMessage());
    }
    synchronized void reportUnsupportedScreen(Class<?> screenClass, String reason) {
        if (unsupportedScreenReported) return;
        unsupportedScreenReported = true;
        MLuaLogger.LOGGER.warn("Native UI layout {} for '{}' could not inspect screen {}: {}",
            id, screen, screenClass.getName(), reason);
    }
    void setReplacement(GuiObject replacement) { this.replacement = replacement; }
    void setBefore(GuiObject before) { this.before = before; }
    void setAfter(GuiObject after) { this.after = after; }
    void setOverlay(ScreenGui overlay) { this.overlay = overlay; }

    synchronized List<Object> appliedScreens() {
        return List.copyOf(originals.keySet());
    }

    synchronized void capture(Object screen, Object widget, LuaTable layoutProperties) {
        Map<Object, WidgetState> screenOriginals = originals.computeIfAbsent(screen,
            ignored -> new IdentityHashMap<>());
        WidgetState state = screenOriginals.computeIfAbsent(widget, value -> new WidgetState(
            MLuaNativeWidgetAccess.readInt(value, "getX", 0), MLuaNativeWidgetAccess.readInt(value, "getY", 0), MLuaNativeWidgetAccess.readInt(value, "getWidth", 0),
            MLuaNativeWidgetAccess.readInt(value, "getHeight", 0), MLuaNativeWidgetAccess.readWidgetField(value, "visible", true),
            MLuaNativeWidgetAccess.readWidgetText(value), new java.util.LinkedHashMap<>()));
        for (LuaValue key : layoutProperties.keys()) {
            if (key.type() != LuaValue.TSTRING) continue;
            String name = key.tojstring();
            if (Set.of("X", "Y", "Width", "Height", "Visible", "Text", "Replacement", "Before", "After")
                .contains(name) || state.properties().containsKey(name)) continue;
            state.properties().put(name, MLuaNativeControl.readWidgetProperty(widget, name));
        }
    }

    synchronized void restore() {
        for (Object screen : List.copyOf(originals.keySet())) restore(screen);
    }

    synchronized void restore(Object screen) {
        Map<Object, WidgetState> screenOriginals = originals.remove(screen);
        if (screenOriginals == null) return;
        try {
            screenOriginals.forEach((widget, state) -> {
                try {
                    if (!MLuaNativeWidgetAccess.invokeIfPresent(widget, "setPosition", new Class<?>[]{int.class, int.class}, state.x(), state.y())) {
                        MLuaNativeWidgetAccess.setIntField(widget, "x", state.x());
                        MLuaNativeWidgetAccess.setIntField(widget, "y", state.y());
                    }
                    if (!MLuaNativeWidgetAccess.invokeIfPresent(widget, "setWidth", new Class<?>[]{int.class}, state.width())) MLuaNativeWidgetAccess.setIntField(widget, "width", state.width());
                    if (!MLuaNativeWidgetAccess.invokeIfPresent(widget, "setHeight", new Class<?>[]{int.class}, state.height())) MLuaNativeWidgetAccess.setIntField(widget, "height", state.height());
                    MLuaNativeWidgetAccess.setWidgetField(widget, "visible", state.visible());
                    if (state.text() != null) {
                        for (Method setter : widget.getClass().getMethods()) {
                            if (setter.getName().equals("setMessage") && setter.getParameterCount() == 1) {
                                try { setter.invoke(widget, state.text()); } catch (ReflectiveOperationException ignored) { }
                                break;
                            }
                        }
                    }
                    state.properties().forEach((name, value) -> MLuaNativeControl.restoreWidgetProperty(widget, name, value));
                } catch (RuntimeException failure) {
                    if (!restoreFailureReported) {
                        restoreFailureReported = true;
                        MLuaLogger.LOGGER.warn("Native UI layout {} for '{}' could not restore a widget after removal: {}",
                            id, screen, failure.getMessage());
                    }
                }
            });
        } finally {
            screenOriginals.clear();
        }
    }

    synchronized void clearSnapshot(Object screen) {
        Map<Object, WidgetState> screenOriginals = originals.remove(screen);
        if (screenOriginals != null) screenOriginals.clear();
    }

    private record WidgetState(int x, int y, int width, int height, boolean visible, Object text,
                               Map<String, Object> properties) { }
}
