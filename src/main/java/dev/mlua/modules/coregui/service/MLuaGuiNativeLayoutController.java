package dev.mlua.modules.coregui.service;

import dev.mlua.minecraft.ui.UDim;
import dev.mlua.minecraft.ui.UDim2;
import dev.mlua.minecraft.ui.element.GuiButton;
import dev.mlua.minecraft.ui.element.GuiObject;
import dev.mlua.minecraft.ui.element.ScreenGui;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/** Applies addon-owned layouts to native Minecraft screens and restores their widget state. */
final class MLuaGuiNativeLayoutController {
    private static final AtomicLong NEXT_LAYOUT_ID = new AtomicLong(1);
    private final MLuaGuiService service;
    private final MLuaGuiState state;

    MLuaGuiNativeLayoutController(MLuaGuiService service, MLuaGuiState state) {
        this.service = service;
        this.state = state;
    }

    void restoreNativeLayouts(Object screen) {
        for (MLuaNativeLayout layout : this.state.nativeLayouts) layout.restore(screen);
        Set<Object> widgets = Collections.newSetFromMap(new IdentityHashMap<>());
        try {
            Object children = screen.getClass().getMethod("children").invoke(screen);
            if (children instanceof Iterable<?> iterable) iterable.forEach(widgets::add);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
        if (!widgets.isEmpty()) {
            synchronized (MLuaGuiState.REPLACEMENT_ACTIONS) {
                MLuaGuiState.REPLACEMENT_ACTIONS.entrySet().removeIf(entry -> widgets.contains(entry.getValue()));
            }
        }
    }

    MLuaNativeLayout findNativeLayout(String screen, int index, String owner) {
        List<MLuaNativeLayout> layouts = service.state.nativeLayouts;
        for (int i = layouts.size() - 1; i >= 0; i--) {
            MLuaNativeLayout layout = layouts.get(i);
            if (!layout.screen().equals(screen) || !layout.owner().equals(owner)) continue;
            LuaValue selectedIndex = layout.selector().get("Index");
            if (selectedIndex.isint() && selectedIndex.checkint() == index) return layout;
        }
        return null;
    }

    void syncLayoutObjects(MLuaNativeLayout layout) {
        GuiObject replacement = MLuaGuiNativeControlProperties.guiObjectProperty(layout.properties(), "Replacement");
        GuiObject before = MLuaGuiNativeControlProperties.guiObjectProperty(layout.properties(), "Before");
        GuiObject after = MLuaGuiNativeControlProperties.guiObjectProperty(layout.properties(), "After");
        MLuaGuiNativeControlProperties.validateLayoutObjects(layout.properties());
        ScreenGui overlay = layout.overlay();
        if (overlay != null) {
            for (GuiObject old : new GuiObject[]{layout.replacement(), layout.before(), layout.after()}) {
                if (old != null) {
                    overlay.removeChild(old);
                    if (old instanceof GuiButton button) synchronized (MLuaGuiState.REPLACEMENT_ACTIONS) { MLuaGuiState.REPLACEMENT_ACTIONS.remove(button); }
                }
            }
            service.removeScreen(overlay.toLua());
            overlay = null;
        }
        layout.setReplacement(replacement);
        layout.setBefore(before);
        layout.setAfter(after);
        if (replacement == null && before == null && after == null) {
            layout.setOverlay(null);
            return;
        }
        overlay = new ScreenGui("NativeControl" + layout.id());
        overlay.setTargetScreen(layout.screen());
        service.attachScriptOwner(overlay);
        if (before != null) overlay.addChild(before);
        if (replacement != null) overlay.addChild(replacement);
        if (after != null) overlay.addChild(after);
        service.addScreenGui(overlay);
        layout.setOverlay(overlay);
    }

    MLuaNativeLayout addNativeLayout(String screen, LuaTable selector, LuaTable properties) {
        long id = NEXT_LAYOUT_ID.getAndIncrement();
        MLuaGuiNativeControlProperties.validateLayoutObjects(properties);
        MLuaNativeLayout layout = new MLuaNativeLayout(id, service.ownerId(), screen, selector, properties, null,
            MLuaGuiNativeControlProperties.guiObjectProperty(properties, "Before"),
            MLuaGuiNativeControlProperties.guiObjectProperty(properties, "After"), null);
        this.state.nativeLayouts.add(layout);
        this.syncLayoutObjects(layout);
        return layout;
    }

    boolean removeNativeLayout(long id) {
        boolean removed = false;
        for (MLuaNativeLayout layout : this.state.nativeLayouts) {
            if (layout.id() != id || !layout.owner().equals(service.ownerId())) continue;
            this.removeNativeLayoutEntry(layout);
            removed = true;
        }
        return removed;
    }

    void removeNativeLayoutEntry(MLuaNativeLayout layout) {
        List<MLuaNativeLayout> previousLayouts = List.copyOf(this.state.nativeLayouts);
        List<Object> affectedScreens = layout.appliedScreens();
        int removedIndex = previousLayouts.indexOf(layout);
        this.state.nativeLayouts.remove(layout);
        for (int screenIndex = affectedScreens.size() - 1; screenIndex >= 0; screenIndex--) {
            Object screen = affectedScreens.get(screenIndex);
            for (int layoutIndex = previousLayouts.size() - 1; layoutIndex >= 0; layoutIndex--) {
                previousLayouts.get(layoutIndex).restore(screen);
            }
            for (int layoutIndex = removedIndex + 1; layoutIndex < previousLayouts.size(); layoutIndex++) {
                previousLayouts.get(layoutIndex).clearSnapshot(screen);
            }
        }
        if (layout.overlay() != null) {
            for (GuiObject object : new GuiObject[]{layout.replacement(), layout.before(), layout.after()}) {
                if (object != null) layout.overlay().removeChild(object);
            }
            service.removeScreen(layout.overlay().toLua());
        }
        if (layout.replacement() instanceof GuiButton button) synchronized (MLuaGuiState.REPLACEMENT_ACTIONS) { MLuaGuiState.REPLACEMENT_ACTIONS.remove(button); }
        for (Object screen : affectedScreens) this.applyLayouts(screen);
    }

    void recomposeLayouts(Object screen, MLuaNativeLayout changedLayout) {
        if (screen == null) return;
        List<MLuaNativeLayout> layouts = List.copyOf(this.state.nativeLayouts);
        for (int index = layouts.size() - 1; index >= 0; index--) layouts.get(index).restore(screen);
        int changedIndex = layouts.indexOf(changedLayout);
        for (int index = Math.max(0, changedIndex); index < layouts.size(); index++) {
            layouts.get(index).clearSnapshot(screen);
        }
        this.applyLayouts(screen);
    }

    void recomposeTrackedScreens() {
        Set<Object> screens = Collections.newSetFromMap(new IdentityHashMap<>());
        Object current = MLuaGuiService.currentScreen();
        if (current != null) screens.add(current);
        for (MLuaNativeLayout layout : this.state.nativeLayouts) screens.addAll(layout.appliedScreens());
        for (Object screen : screens) this.recomposeLayouts(screen, null);
    }

    void applyLayouts(Object screen) {
        for (MLuaNativeLayout layout : this.state.nativeLayouts) {
            if (layout.failed() || !screenMatches(screen, layout.screen())) continue;
            try {
                applyLayoutToWidgets(screen, layout);
            } catch (RuntimeException failure) {
                try {
                    this.removeNativeLayoutEntry(layout);
                } catch (RuntimeException cleanupFailure) {
                    failure.addSuppressed(cleanupFailure);
                }
                layout.fail(screen.getClass(), failure);
            }
        }
    }

    boolean screenMatches(Object screen, String target) {
        if (screen == null || target == null) return false;
        return target.equalsIgnoreCase("Any")
            || ScreenGui.matchesScreenClass(screen.getClass(), target)
            || service.matchesRegisteredScreenTarget(target, screen);
    }

    private static void applyLayoutToWidgets(Object screen, MLuaNativeLayout layout) {
        LuaTable selector = layout.selector();
        LuaTable properties = layout.properties();
        try {
            Object childrenValue = screen.getClass().getMethod("children").invoke(screen);
            if (!(childrenValue instanceof Iterable<?> children)) {
                layout.reportUnsupportedScreen(screen.getClass(), "children() did not return an iterable");
                return;
            }
            int widgetIndex = 0;
            for (Object widget : children) {
                if (widget == null) continue;
                if (!MLuaNativeWidgetAccess.isNativeControl(widget)) continue;
                int currentIndex = ++widgetIndex;
                if (!MLuaNativeWidgetAccess.matchesWidget(widget, currentIndex, selector)) continue;
                layout.capture(screen, widget, properties);
                MLuaNativeWidgetAccess.applyWidgetProperties(widget, properties, screen);
                if (layout.replacement() != null) {
                    int x = MLuaNativeWidgetAccess.readInt(widget, "getX", 0);
                    int y = MLuaNativeWidgetAccess.readInt(widget, "getY", 0);
                    int width = MLuaNativeWidgetAccess.readInt(widget, "getWidth", 0);
                    int height = MLuaNativeWidgetAccess.readInt(widget, "getHeight", 0);
                    layout.replacement().setPosition(new UDim2(new UDim(0, x), new UDim(0, y)));
                    layout.replacement().setSize(new UDim2(new UDim(0, width), new UDim(0, height)));
                    if (layout.replacement() instanceof GuiButton button) synchronized (MLuaGuiState.REPLACEMENT_ACTIONS) { MLuaGuiState.REPLACEMENT_ACTIONS.put(button, widget); }
                    if (!MLuaNativeWidgetAccess.setWidgetField(widget, "visible", false)) {
                        throw new LuaError("[MLua] NativeControl.Replacement requires a writable visibility flag on "
                            + widget.getClass().getSimpleName());
                    }
                    LuaValue visible = properties.get("Visible");
                    if (visible.isboolean()) layout.replacement().setVisible(visible.checkboolean());
                }
                MLuaNativeWidgetAccess.positionAdjacent(layout.before(), widget, screen, true);
                MLuaNativeWidgetAccess.positionAdjacent(layout.after(), widget, screen, false);
            }
        } catch (ReflectiveOperationException failure) {
            layout.reportUnsupportedScreen(screen.getClass(), "could not read child widgets: " + failure.getMessage());
        }
    }
}
