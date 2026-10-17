package dev.mlua.modules.coregui.Service;

import dev.mlua.common.enums.CoreGuiType;
import dev.mlua.modules.enums.MLuaEnumValue;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.lib.VarArgFunction;
import dev.mlua.core.service.MLuaService;
import dev.mlua.core.service.MLuaServiceBase;
import dev.mlua.core.MLuaRuntime;
import dev.mlua.core.MLuaLogger;
import dev.mlua.core.scheduler.MLuaTaskSchedulerView;
import dev.mlua.core.engine.MLuaLuaBindings;
import dev.mlua.core.MLuaContext;
import dev.mlua.core.engine.MLuaLuauTypes;
import dev.mlua.core.signal.MLuaSignal;
import dev.mlua.common.enums.Side;
import dev.mlua.module.MLuaRegistration;
import dev.mlua.minecraft.ui.element.GuiBase;
import dev.mlua.minecraft.ui.element.GuiObject;
import dev.mlua.minecraft.ui.element.MLuaTableRegistry;
import dev.mlua.minecraft.ui.element.ScreenGui;
import dev.mlua.minecraft.ui.element.GuiButton;
import dev.mlua.minecraft.binding.MLuaNativeControl;
import dev.mlua.minecraft.binding.MLuaNativeScreen;
import dev.mlua.minecraft.binding.MLuaChatLine;
import dev.mlua.minecraft.binding.MLuaLuaArguments;
import dev.mlua.minecraft.ui.UDim;
import dev.mlua.minecraft.ui.UDim2;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.WeakHashMap;
import java.util.EnumSet;
import java.util.Set;
import java.util.Collections;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicLong;
import java.util.IdentityHashMap;
import java.nio.file.Path;
import net.minecraft.network.chat.Component;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.Font;
//--version 26.1
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.MouseButtonEvent;
//--endversion
//--version 1.20.1,1.21.1
import net.minecraft.client.gui.GuiGraphics;
//--endversion

public final class MLuaGuiService implements MLuaService {
    private static final Map<MLuaTaskSchedulerView, GuiState> STATES = new WeakHashMap<>();
    private final MLuaService.Context context;
    private final GuiState state;
    private static final AtomicLong NEXT_LAYOUT_ID = new AtomicLong(1);
    private static final AtomicLong NEXT_HUD_ELEMENT_ID = new AtomicLong(1);
    private static final Map<GuiButton, Object> REPLACEMENT_ACTIONS = new WeakHashMap<>();

    public MLuaGuiService(MLuaService.Context context) {
        this.context = context;
        this.state = stateFor(context.mlua().scheduler());
        this.state.executionLock = context.mlua().executionLock();
    }

    private MLuaGuiService(GuiState state) {
        this.context = null;
        this.state = state;
    }

    public static MLuaGuiService getInstance() {
        MLuaRuntime runtime = MLuaRuntime.get();
        if (runtime == null || runtime.session() == null) return null;
        return getForScheduler(runtime.session().clientScheduler());
    }

    public static Component beforeChatLineAdded(Component message) {
        MLuaGuiService service = getInstance();
        MLuaRuntime runtime = MLuaRuntime.get();
        if (service == null || runtime == null || runtime.session() == null || service.state.chatLineAdding.isEmpty()) return message;
        MLuaChatLine.Backing backing = new MLuaChatLine.Backing(message);
        LuaTable instance = MLuaChatLine.instance(runtime, backing);
        try {
            service.state.chatLineAdding.fireOnSide(Side.CLIENT, LuaValue.varargsOf(new LuaValue[]{instance}));
            return backing.result();
        } finally {
            backing.expire();
        }
    }

    public static MLuaGuiService getForScheduler(MLuaTaskSchedulerView scheduler) {
        if (scheduler == null) return null;
        synchronized (STATES) {
            GuiState state = STATES.get(scheduler);
            return state == null ? null : new MLuaGuiService(state);
        }
    }

    public static void renderHudElements(String position, Object graphics) {
        if (position == null || graphics == null) return;
        List<HudElement> elements = new java.util.ArrayList<>();
        synchronized (STATES) {
            for (GuiState state : STATES.values()) {
                if (state.enabled) elements.addAll(state.hudElements.values());
            }
        }
        elements.sort(java.util.Comparator.comparingInt((HudElement element) -> element.priority)
            .thenComparingLong(element -> element.order));
        for (HudElement element : elements) {
            if (element.position.equals(position) && !element.failed) renderHudElement(element, graphics);
        }
    }

    private static void renderHudElement(HudElement element, Object graphics) {
        Object window = Minecraft.getInstance() == null ? null : Minecraft.getInstance().getWindow();
        synchronized (element.executionLock) {
            element.graphics = graphics;
            element.canvas.set("Width", LuaValue.valueOf(readNumber(window, "getGuiScaledWidth", "guiScaledWidth")));
            element.canvas.set("Height", LuaValue.valueOf(readNumber(window, "getGuiScaledHeight", "guiScaledHeight")));
            element.canvas.set("Anchor", LuaValue.valueOf(element.anchor));
            element.canvas.set("Phase", LuaValue.valueOf(element.before ? "Before" : "After"));
            try {
                MLuaLuaBindings.withContext(element.context, () -> {
                    element.callback.invoke(element.canvas);
                    return null;
                });
            } catch (RuntimeException failure) {
                element.failed = true;
                MLuaLogger.LOGGER.error("Lua HUD element '{}' failed; rendering is paused until it is registered again", element.id, failure);
            } finally {
                element.graphics = null;
            }
        }
    }

    private static LuaTable createHudCanvas(HudElement element) {
        LuaTable canvas = new LuaTable();
        canvas.set("Width", LuaValue.ZERO);
        canvas.set("Height", LuaValue.ZERO);
        canvas.set("Anchor", LuaValue.valueOf(element.anchor));
        canvas.set("Phase", LuaValue.valueOf(element.before ? "Before" : "After"));
        canvas.set("Fill", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                drawHudFill(requireHudGraphics(element), MLuaLuaArguments.wholeInt(args, 2, "HudCanvas:Fill", "left"),
                    MLuaLuaArguments.wholeInt(args, 3, "HudCanvas:Fill", "top"),
                    MLuaLuaArguments.wholeInt(args, 4, "HudCanvas:Fill", "right"),
                    MLuaLuaArguments.wholeInt(args, 5, "HudCanvas:Fill", "bottom"),
                    readColor(args.arg(6), "HudCanvas:Fill", "color"));
                return LuaValue.NONE;
            }
        });
        canvas.set("DrawText", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                drawHudText(requireHudGraphics(element), nativeText(args.arg(2)),
                    MLuaLuaArguments.wholeInt(args, 3, "HudCanvas:DrawText", "x"),
                    MLuaLuaArguments.wholeInt(args, 4, "HudCanvas:DrawText", "y"),
                    optionalColor(args.arg(5), 0xFFFFFF, "HudCanvas:DrawText", "color"),
                    args.arg(6).optboolean(true));
                return LuaValue.NONE;
            }
        });
        return canvas;
    }

    private static Object requireHudGraphics(HudElement element) {
        Object graphics = element.graphics;
        if (graphics == null) throw new LuaError("[MLua] HUD canvas drawing methods are only available during the render callback.");
        return graphics;
    }

    private static int readColor(LuaValue value, String method, String parameter) {
        return MLuaLuaArguments.wholeInt32Bits(value, method, parameter);
    }

    private static int optionalColor(LuaValue value, int fallback, String method, String parameter) {
        return value.isnil() ? fallback : readColor(value, method, parameter);
    }

    private static int readNumber(Object target, String... methodNames) {
        if (target == null) return 0;
        for (String name : methodNames) {
            try {
                Object value = target.getClass().getMethod(name).invoke(target);
                if (value instanceof Number number) return number.intValue();
            } catch (ReflectiveOperationException ignored) { }
        }
        return 0;
    }

    private static void drawHudFill(Object graphics, int left, int top, int right, int bottom, int color) {
        try {
            graphics.getClass().getMethod("fill", int.class, int.class, int.class, int.class, int.class)
                .invoke(graphics, left, top, right, bottom, color);
        } catch (ReflectiveOperationException failure) {
            throw new LuaError("[MLua] This Minecraft HUD graphics context does not support Fill.");
        }
    }

    private static Object nativeText(LuaValue value) {
        if (value.type() == LuaValue.TSTRING) return value.checkjstring();
        try {
            Object component = dev.mlua.api.MLuaLuaCoercions.javaValue(value, Component.class);
            if (component instanceof Component) return component;
        } catch (RuntimeException ignored) { }
        throw new LuaError("[MLua] Expected a string or Java Component value.");
    }

    private static void drawHudText(Object graphics, Object text, int x, int y, int color, boolean shadow) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) throw new LuaError("[MLua] Minecraft client is not available.");
        Class<?> textType = text instanceof Component ? Component.class : String.class;
        for (String name : List.of("text", "drawString")) {
            try {
                graphics.getClass().getMethod(name, Font.class, textType, int.class, int.class, int.class, boolean.class)
                    .invoke(graphics, minecraft.font, text, x, y, color, shadow);
                return;
            } catch (ReflectiveOperationException ignored) { }
        }
        throw new LuaError("[MLua] This Minecraft HUD graphics context does not support DrawText.");
    }

    public static boolean matchesRegisteredScreenTarget(MLuaTaskSchedulerView scheduler, String target, Object screen) {
        if (scheduler == null || target == null || screen == null) return false;
        GuiState state;
        synchronized (STATES) { state = STATES.get(scheduler); }
        if (state == null) return false;
        ScreenTarget registered = state.screenTargets.get(target.toLowerCase(java.util.Locale.ROOT));
        return registered != null && ScreenGui.matchesScreenClass(screen.getClass(), registered.className());
    }

    public static LuaTable getNativeControls(Object screen) {
        MLuaGuiService service = getInstance();
        if (service == null) throw new LuaError("[MLua] Gui service is not active on the client");
        return service.nativeControls(LuaValue.NIL, false, screen);
    }

    public static LuaTable getNativeControls(MLuaTaskSchedulerView scheduler, Object screen) {
        MLuaGuiService service = getForScheduler(scheduler);
        if (service == null) throw new LuaError("[MLua] Gui service is not active for this client runtime");
        return service.nativeControls(LuaValue.NIL, false, screen);
    }

    private static GuiState stateFor(MLuaTaskSchedulerView scheduler) {
        java.util.Objects.requireNonNull(scheduler, "scheduler");
        synchronized (STATES) {
            return STATES.computeIfAbsent(scheduler, ignored -> new GuiState());
        }
    }

    public static void clearForScheduler(MLuaTaskSchedulerView scheduler) {
        synchronized (STATES) {
            GuiState state = STATES.get(scheduler);
            if (state != null) state.clear();
        }
    }

    public static void applyNativeLayouts(Object screen) {
        MLuaGuiService service = getInstance();
        if (service != null) {
            MLuaRuntime runtime = MLuaRuntime.get();
            service.applyLayouts(screen);
            LuaTable event = service.nativeControls(LuaValue.NIL, false, screen);
            LuaValue screenInstance = runtime == null ? LuaValue.NIL
                : MLuaNativeScreen.instance(runtime, screen);
            service.state.screenOpened.fireOnSide(Side.CLIENT,
                LuaValue.varargsOf(new LuaValue[]{LuaValue.valueOf(screen.getClass().getName()), event, screenInstance}));
        }
    }

    public static Object currentScreen() {
        try {
            Class<?> minecraft = Class.forName("net.minecraft.client.Minecraft");
            Object instance = minecraft.getMethod("getInstance").invoke(null);
            Field screen = minecraft.getField("screen");
            return screen.get(instance);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    public static void setNativeControlProperty(MLuaNativeControl.Backing backing, String name, LuaValue value) {
        if (value.isnil() && Set.of("Replacement", "Before", "After").contains(name)) {
            LuaTable update = new LuaTable();
            update.set("__Clear" + name, LuaValue.TRUE);
            setNativeControlProperties(backing, update);
            if ("Replacement".equals(name)) backing.setReplacement(value);
            return;
        }
        LuaTable properties = new LuaTable();
        properties.set(name, value);
        setNativeControlProperties(backing, properties);
    }

    public static void setNativeControlProperties(MLuaNativeControl.Backing backing, LuaTable updates) {
        backing.requireCurrent();
        MLuaGuiService service = getInstance();
        if (service == null) throw new LuaError("[MLua] Gui service is not active on the client");
        synchronized (service.state) {
            NativeLayout layout = service.findNativeLayout(backing.screenName, backing.index);
            boolean createdLayout = false;
            LuaTable previousProperties = null;
            boolean clearsObjects = updates.rawget("__ClearReplacement").toboolean()
                || updates.rawget("__ClearBefore").toboolean() || updates.rawget("__ClearAfter").toboolean();
            if (layout == null && clearsObjects) return;
            if (layout == null) {
                LuaTable selector = new LuaTable();
                selector.set("Index", backing.index);
                LuaTable initial = copyProperties(updates);
                layout = service.addNativeLayout(backing.screenName, selector, initial);
                createdLayout = true;
            } else {
                previousProperties = copyProperties(layout.properties());
                boolean clearReplacement = updates.rawget("__ClearReplacement").toboolean();
                boolean clearBefore = updates.rawget("__ClearBefore").toboolean();
                boolean clearAfter = updates.rawget("__ClearAfter").toboolean();
                LuaTable nextProperties = copyProperties(layout.properties());
                for (String property : List.of("Replacement", "Before", "After")) {
                    LuaValue value = updates.get(property);
                    if (!value.isnil()) requireGuiObject(value, property);
                }
                for (LuaValue key : updates.keys()) {
                    if (key.type() == LuaValue.TSTRING && !key.tojstring().startsWith("__Clear")) {
                        nextProperties.set(key, updates.get(key));
                    }
                }
                if (clearReplacement) nextProperties.set("Replacement", LuaValue.NIL);
                if (clearBefore) nextProperties.set("Before", LuaValue.NIL);
                if (clearAfter) nextProperties.set("After", LuaValue.NIL);
                validateLayoutObjects(nextProperties);
                replaceProperties(layout.properties(), nextProperties);
                if (clearReplacement || clearBefore || clearAfter || updates.get("Replacement") != LuaValue.NIL
                    || updates.get("Before") != LuaValue.NIL || updates.get("After") != LuaValue.NIL) {
                    service.syncLayoutObjects(layout);
                }
            }
            try {
                service.recomposeLayouts(backing.screen, layout);
            } catch (RuntimeException | Error failure) {
                if (createdLayout) service.removeNativeLayout(layout.id());
                else if (previousProperties != null) {
                    try {
                        replaceProperties(layout.properties(), previousProperties);
                        service.syncLayoutObjects(layout);
                        service.recomposeLayouts(backing.screen, layout);
                    } catch (RuntimeException | Error rollbackFailure) {
                        failure.addSuppressed(rollbackFailure);
                    }
                }
                throw failure;
            }
        }
    }

    private static LuaTable copyProperties(LuaTable source) {
        LuaTable copy = new LuaTable();
        for (LuaValue key : source.keys()) copy.set(key, source.get(key));
        return copy;
    }

    private static LuaTable requireScreenUDim2(LuaValue value, String property) {
        if (!MLuaLuauTypes.isUDim2(value)) {
            throw new LuaError("[MLua] Screen " + property + " requires a UDim2 value.");
        }
        return value.checktable();
    }

    private static int screenCoordinate(LuaTable dimensions, String axis, int extent) {
        LuaValue dimension = dimensions.get(axis);
        return (int) Math.round(dimension.get("Scale").todouble() * extent + dimension.get("Offset").toint());
    }

    private interface ScreenDrawing {
        void fill(int left, int top, int right, int bottom, int color);
        void text(Object text, int x, int y, int color, boolean shadow);
    }

    private record ScreenText(Object text, int x, int y, int color) { }

    private static void replaceProperties(LuaTable target, LuaTable source) {
        for (LuaValue key : target.keys()) target.set(key, LuaValue.NIL);
        for (LuaValue key : source.keys()) target.set(key, source.get(key));
    }

    private NativeLayout findNativeLayout(String screen, int index) {
        for (NativeLayout layout : this.state.nativeLayouts) {
            if (!layout.screen().equals(screen)) continue;
            LuaValue selectedIndex = layout.selector().get("Index");
            if (selectedIndex.isint() && selectedIndex.checkint() == index) return layout;
        }
        return null;
    }

    private void syncLayoutObjects(NativeLayout layout) {
        GuiObject replacement = guiObjectProperty(layout.properties(), "Replacement");
        GuiObject before = guiObjectProperty(layout.properties(), "Before");
        GuiObject after = guiObjectProperty(layout.properties(), "After");
        validateLayoutObjects(layout.properties());
        ScreenGui overlay = layout.overlay();
        if (overlay != null) {
            for (GuiObject old : new GuiObject[]{layout.replacement(), layout.before(), layout.after()}) {
                if (old != null) {
                    overlay.removeChild(old);
                    if (old instanceof GuiButton button) synchronized (REPLACEMENT_ACTIONS) { REPLACEMENT_ACTIONS.remove(button); }
                }
            }
            this.removeScreen(overlay.toLua());
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
        this.attachScriptOwner(overlay);
        if (before != null) overlay.addChild(before);
        if (replacement != null) overlay.addChild(replacement);
        if (after != null) overlay.addChild(after);
        this.addScreenGui(overlay);
        layout.setOverlay(overlay);
    }

    private static GuiObject guiObjectProperty(LuaTable properties, String name) {
        LuaValue value = properties.get(name);
        if (value.isnil()) return null;
        return requireGuiObject(value, name);
    }

    private static void validateLayoutObjects(LuaTable properties) {
        GuiObject replacement = guiObjectProperty(properties, "Replacement");
        GuiObject before = guiObjectProperty(properties, "Before");
        GuiObject after = guiObjectProperty(properties, "After");
        if (replacement != null && (replacement == before || replacement == after) || before != null && before == after) {
            throw new LuaError("[MLua] NativeControl Replacement, Before, and After must use separate GuiObject instances");
        }
    }

    private static GuiObject requireGuiObject(LuaValue value, String name) {
        if (!value.istable() || !(MLuaTableRegistry.getElement(value.checktable()) instanceof GuiObject object)) {
            throw new LuaError("[MLua] NativeControl." + name + " expects a live GuiObject instance or nil");
        }
        return object;
    }

    private LuaTable nativeControls(LuaValue selector, boolean firstOnly) {
        return nativeControls(selector, firstOnly, currentScreen());
    }

    private LuaTable nativeControls(LuaValue selector, boolean firstOnly, Object screen) {
        LuaTable result = new LuaTable();
        if (screen == null) return result;
        try {
            Object childrenValue = screen.getClass().getMethod("children").invoke(screen);
            if (!(childrenValue instanceof Iterable<?> children)) return result;
            MLuaRegistration registration = this.context == null ? MLuaRuntime.get() : this.context.mlua().registration();
            String screenName = screen.getClass().getName();
            int index = 0;
            int output = 1;
            for (Object widget : children) {
                if (widget == null || !isNativeControl(widget)) continue;
                index++;
                if (selector.istable() && !matchesWidget(widget, index, selector.checktable())) continue;
                MLuaNativeControl.Backing backing = new MLuaNativeControl.Backing(widget, screen, screenName, index);
                NativeLayout layout = this.findNativeLayout(screenName, index);
                if (layout != null) {
                    backing.setReplacement(layout.properties().get("Replacement"));
                    backing.setBefore(layout.properties().get("Before"));
                    backing.setAfter(layout.properties().get("After"));
                }
                result.set(output++, MLuaNativeControl.instance(registration, backing));
                if (firstOnly) break;
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return result;
    }

    private NativeLayout addNativeLayout(String screen, LuaTable selector, LuaTable properties) {
        long id = NEXT_LAYOUT_ID.getAndIncrement();
        validateLayoutObjects(properties);
        NativeLayout layout = new NativeLayout(id, this.ownerId(), screen, selector, properties, null, null);
        this.state.nativeLayouts.add(layout);
        this.syncLayoutObjects(layout);
        return layout;
    }

    private String ownerId() {
        return this.context == null ? "runtime" : this.context.mlua().ownerModId();
    }

    private String registerScreenTarget(String alias, String className) {
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
        ScreenTarget target = new ScreenTarget(this.ownerId(), alias, className);
        ScreenTarget existing = this.state.screenTargets.putIfAbsent(key, target);
        if (existing != null && (!existing.owner().equals(target.owner())
            || !existing.className().equals(target.className()))) {
            throw new LuaError("[MLua] Screen target alias '" + alias + "' is already registered.");
        }
        this.recomposeTrackedScreens();
        return alias;
    }

    private boolean unregisterScreenTarget(String alias) {
        if (alias == null || alias.isBlank()) return false;
        String key = alias.toLowerCase(java.util.Locale.ROOT);
        ScreenTarget target = this.state.screenTargets.get(key);
        boolean removed = target != null && target.owner().equals(this.ownerId())
            && this.state.screenTargets.remove(key, target);
        if (removed) this.recomposeTrackedScreens();
        return removed;
    }

    private boolean matchesRegisteredScreenTarget(String alias, Object screen) {
        if (alias == null || screen == null) return false;
        ScreenTarget target = this.state.screenTargets.get(alias.toLowerCase(java.util.Locale.ROOT));
        return target != null && ScreenGui.matchesScreenClass(screen.getClass(), target.className());
    }

    private boolean removeNativeLayout(long id) {
        boolean removed = false;
        for (NativeLayout layout : this.state.nativeLayouts) {
            if (layout.id() != id || !layout.owner().equals(this.ownerId())) continue;
            this.removeNativeLayoutEntry(layout);
            removed = true;
        }
        return removed;
    }

    private void removeNativeLayoutEntry(NativeLayout layout) {
        List<NativeLayout> previousLayouts = List.copyOf(this.state.nativeLayouts);
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
            this.removeScreen(layout.overlay().toLua());
        }
        if (layout.replacement() instanceof GuiButton button) synchronized (REPLACEMENT_ACTIONS) { REPLACEMENT_ACTIONS.remove(button); }
        for (Object screen : affectedScreens) this.applyLayouts(screen);
    }

    private void recomposeLayouts(Object screen, NativeLayout changedLayout) {
        if (screen == null) return;
        List<NativeLayout> layouts = List.copyOf(this.state.nativeLayouts);
        for (int index = layouts.size() - 1; index >= 0; index--) layouts.get(index).restore(screen);
        int changedIndex = layouts.indexOf(changedLayout);
        for (int index = Math.max(0, changedIndex); index < layouts.size(); index++) {
            layouts.get(index).clearSnapshot(screen);
        }
        this.applyLayouts(screen);
    }

    private void recomposeTrackedScreens() {
        Set<Object> screens = Collections.newSetFromMap(new IdentityHashMap<>());
        Object current = currentScreen();
        if (current != null) screens.add(current);
        for (NativeLayout layout : this.state.nativeLayouts) screens.addAll(layout.appliedScreens());
        for (Object screen : screens) this.recomposeLayouts(screen, null);
    }

    private void applyLayouts(Object screen) {
        for (NativeLayout layout : this.state.nativeLayouts) {
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

    public static void activateNativeReplacement(GuiButton button, double mouseX, double mouseY) {
        Object nativeWidget;
        synchronized (REPLACEMENT_ACTIONS) { nativeWidget = REPLACEMENT_ACTIONS.get(button); }
        if (nativeWidget == null) return;
        for (Class<?> type = nativeWidget.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Method method = type.getDeclaredMethod("onPress");
                invokeNativeActivation(method, nativeWidget);
                return;
            } catch (NoSuchMethodException ignored) {
            }
            try {
                Method method = type.getDeclaredMethod("onClick", double.class, double.class);
                invokeNativeActivation(method, nativeWidget, mouseX, mouseY);
                return;
            } catch (NoSuchMethodException ignored) {
            }
        }
    }

    private static void invokeNativeActivation(Method method, Object nativeWidget, Object... arguments) {
        try {
            method.setAccessible(true);
            method.invoke(nativeWidget, arguments);
        } catch (ReflectiveOperationException | RuntimeException failure) {
            Throwable cause = failure instanceof java.lang.reflect.InvocationTargetException invocation
                && invocation.getCause() != null ? invocation.getCause() : failure;
            throw new LuaError(new RuntimeException("[MLua] Could not forward activation to native widget '"
                + nativeWidget.getClass().getName() + "' through " + method.getName() + "()", cause));
        }
    }

    private boolean screenMatches(Object screen, String target) {
        if (screen == null || target == null) return false;
        return target.equalsIgnoreCase("Any")
            || ScreenGui.matchesScreenClass(screen.getClass(), target)
            || this.matchesRegisteredScreenTarget(target, screen);
    }

    private static void applyLayoutToWidgets(Object screen, NativeLayout layout) {
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
                if (!isNativeControl(widget)) continue;
                int currentIndex = ++widgetIndex;
                if (!matchesWidget(widget, currentIndex, selector)) continue;
                layout.capture(screen, widget, properties);
                applyWidgetProperties(widget, properties, screen);
                if (layout.replacement() != null) {
                    int x = readInt(widget, "getX", 0);
                    int y = readInt(widget, "getY", 0);
                    int width = readInt(widget, "getWidth", 0);
                    int height = readInt(widget, "getHeight", 0);
                    layout.replacement().setPosition(new UDim2(new UDim(0, x), new UDim(0, y)));
                    layout.replacement().setSize(new UDim2(new UDim(0, width), new UDim(0, height)));
                    if (layout.replacement() instanceof GuiButton button) synchronized (REPLACEMENT_ACTIONS) { REPLACEMENT_ACTIONS.put(button, widget); }
                    if (!setWidgetField(widget, "visible", false)) {
                        throw new LuaError("[MLua] NativeControl.Replacement requires a writable visibility flag on "
                            + widget.getClass().getSimpleName());
                    }
                    LuaValue visible = properties.get("Visible");
                    if (visible.isboolean()) layout.replacement().setVisible(visible.checkboolean());
                }
                positionAdjacent(layout.before(), widget, screen, true);
                positionAdjacent(layout.after(), widget, screen, false);
            }
        } catch (ReflectiveOperationException failure) {
            layout.reportUnsupportedScreen(screen.getClass(), "could not read child widgets: " + failure.getMessage());
        }
    }

    private static void positionAdjacent(GuiObject element, Object widget, Object screen, boolean before) {
        if (element == null) return;
        int x = readInt(widget, "getX", 0);
        int y = readInt(widget, "getY", 0);
        int width = readInt(widget, "getWidth", 0);
        int height = readInt(widget, "getHeight", 0);
        int elementWidth = element.computeAbsoluteWidth(readDimension(screen, "getWidth", "width", 0));
        int elementHeight = element.computeAbsoluteHeight(readDimension(screen, "getHeight", "height", 0));
        int elementX = before ? x - elementWidth : x + width;
        int elementY = y + (height - elementHeight) / 2;
        element.setPosition(new UDim2(new UDim(0, elementX), new UDim(0, elementY)));
    }

    private static boolean isNativeControl(Object widget) {
        try {
            widget.getClass().getMethod("getX");
            widget.getClass().getMethod("getY");
            widget.getClass().getMethod("getWidth");
            widget.getClass().getMethod("getHeight");
            return true;
        } catch (NoSuchMethodException ignored) {
            return false;
        }
    }

    private static boolean matchesWidget(Object widget, int index, LuaTable selector) {
        LuaValue targetIndex = selector.get("Index");
        if (targetIndex.isint() && targetIndex.checkint() != index) return false;
        LuaValue className = selector.get("Class");
        if (className.type() == LuaValue.TSTRING && !matchesWidgetType(widget.getClass(), className.checkjstring(), new java.util.HashSet<>())) return false;
        LuaValue text = selector.get("Text");
        if (text.type() == LuaValue.TSTRING) {
            try {
                Object message = widget.getClass().getMethod("getMessage").invoke(widget);
                Object value = message.getClass().getMethod("getString").invoke(message);
                if (!text.checkjstring().equals(value)) return false;
            } catch (ReflectiveOperationException ignored) {
                return false;
            }
        }
        for (LuaValue key : selector.keys()) {
            if (key.type() != LuaValue.TSTRING) continue;
            String property = key.tojstring();
            if (Set.of("Index", "Class", "Text").contains(property)) continue;
            Object actual;
            try {
                actual = MLuaNativeControl.readWidgetProperty(widget, property);
            } catch (LuaError missingProperty) {
                return false;
            }
            if (!matchesWidgetProperty(actual, selector.get(key))) return false;
        }
        return targetIndex.isint() || className.type() == LuaValue.TSTRING
            || text.type() == LuaValue.TSTRING || selector.keys().length > 0;
    }

    private static boolean matchesWidgetProperty(Object actual, LuaValue expected) {
        if (actual == null || expected.isnil()) return false;
        if (actual instanceof Component component) actual = component.getString();
        else if (actual instanceof Enum<?> value) actual = value.name();
        if (actual instanceof Boolean value) return expected.isboolean() && value == expected.toboolean();
        if (actual instanceof Number value) return expected.type() == LuaValue.TNUMBER
            && Double.compare(value.doubleValue(), expected.todouble()) == 0;
        if (actual instanceof String value) return expected.type() == LuaValue.TSTRING && value.equals(expected.tojstring());
        return false;
    }

    private static boolean matchesWidgetType(Class<?> type, String selector, Set<Class<?>> visited) {
        if (type == null || !visited.add(type)) return false;
        if (selector.equals(type.getSimpleName()) || selector.equals(type.getName())) return true;
        for (Class<?> contract : type.getInterfaces()) {
            if (matchesWidgetType(contract, selector, visited)) return true;
        }
        return matchesWidgetType(type.getSuperclass(), selector, visited);
    }

    private static void applyWidgetProperties(Object widget, LuaTable properties, Object screen) {
        LuaValue position = properties.get("Position");
        if (!position.isnil()) {
            if (!MLuaLuauTypes.isUDim2(position)) {
                throw new LuaError("[MLua] NativeControl.Position requires a UDim2 value created by UDim2.new, UDim2.fromScale, or UDim2.fromOffset");
            }
            LuaTable dimensions = position.checktable();
            LuaValue x = dimensions.get("X");
            LuaValue y = dimensions.get("Y");
            int currentX = readInt(widget, "getX", 0);
            int currentY = readInt(widget, "getY", 0);
            int screenWidth = readDimension(screen, "getWidth", "width", 0);
            int screenHeight = readDimension(screen, "getHeight", "height", 0);
            int nextX = uiDimension(x, currentX, screenWidth);
            int nextY = uiDimension(y, currentY, screenHeight);
            if (!invokeIfPresent(widget, "setPosition", new Class<?>[]{int.class, int.class}, nextX, nextY)
                && (!setIntField(widget, "x", nextX) || !setIntField(widget, "y", nextY))) {
                throw new LuaError("[MLua] NativeControl position is not writable for " + widget.getClass().getSimpleName());
            }
        }
        LuaValue size = properties.get("Size");
        if (!size.isnil()) {
            if (!MLuaLuauTypes.isUDim2(size)) {
                throw new LuaError("[MLua] NativeControl.Size requires a UDim2 value created by UDim2.new, UDim2.fromScale, or UDim2.fromOffset");
            }
            LuaTable dimensions = size.checktable();
            int nextWidth = uiDimension(dimensions.get("X"), readInt(widget, "getWidth", 0),
                readDimension(screen, "getWidth", "width", 0));
            if (!invokeIfPresent(widget, "setWidth", new Class<?>[]{int.class}, nextWidth) && !setIntField(widget, "width", nextWidth)) {
                throw new LuaError("[MLua] NativeControl size is not writable for " + widget.getClass().getSimpleName());
            }
            int nextHeight = uiDimension(dimensions.get("Y"), readInt(widget, "getHeight", 0),
                readDimension(screen, "getHeight", "height", 0));
            if (!invokeIfPresent(widget, "setHeight", new Class<?>[]{int.class}, nextHeight) && !setIntField(widget, "height", nextHeight)) {
                throw new LuaError("[MLua] NativeControl size is not writable for " + widget.getClass().getSimpleName());
            }
        }
        LuaValue visible = properties.get("Visible");
        if (visible.isboolean() && !setWidgetField(widget, "visible", visible.checkboolean())) {
            throw new LuaError("[MLua] NativeControl.Visible is not writable for " + widget.getClass().getSimpleName());
        }
        LuaValue text = properties.get("Text");
        if (text.type() == LuaValue.TSTRING && !setWidgetText(widget, text.checkjstring())) {
            throw new LuaError("[MLua] NativeControl.Text is not writable for " + widget.getClass().getSimpleName());
        }
        for (LuaValue key : properties.keys()) {
            if (key.type() != LuaValue.TSTRING) continue;
            String name = key.tojstring();
            if (Set.of("Position", "Size", "Visible", "Text", "Replacement", "Before", "After")
                .contains(name)) continue;
            MLuaNativeControl.setWidgetProperty(widget, name, properties.get(key));
        }
    }

    private static int uiDimension(LuaValue value, int current, int parentSize) {
        if (!MLuaLuauTypes.isUDim(value)) throw new LuaError("[MLua] UDim2 axes must be UDim values");
        return (int) Math.round(parentSize * value.get("Scale").todouble() + value.get("Offset").toint());
    }

    private static int readDimension(Object target, String methodName, String fieldName, int fallback) {
        int value = readInt(target, methodName, Integer.MIN_VALUE);
        if (value != Integer.MIN_VALUE) return value;
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.getInt(target);
            } catch (NoSuchFieldException ignored) {
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private static Object readWidgetText(Object widget) {
        try {
            return widget.getClass().getMethod("getMessage").invoke(widget);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static boolean setWidgetText(Object widget, String text) {
        try {
            for (Method setter : widget.getClass().getMethods()) {
                if (!setter.getName().equals("setMessage") || setter.getParameterCount() != 1) continue;
                Class<?> componentType = setter.getParameterTypes()[0];
                Method literal = componentType.getMethod("literal", String.class);
                setter.invoke(widget, literal.invoke(null, text));
                return true;
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) { }
        return false;
    }

    private static int readInt(Object target, String methodName, int fallback) {
        try {
            Object value = target.getClass().getMethod(methodName).invoke(target);
            return value instanceof Number number ? number.intValue() : fallback;
        } catch (ReflectiveOperationException ignored) {
            return fallback;
        }
    }

    private static boolean invokeIfPresent(Object target, String methodName, Class<?>[] parameterTypes, Object... args) {
        try {
            target.getClass().getMethod(methodName, parameterTypes).invoke(target, args);
            return true;
        } catch (NoSuchMethodException ignored) {
            return false;
        } catch (ReflectiveOperationException | RuntimeException failure) {
            throw new LuaError("[MLua] Failed to update native widget using " + methodName + ": " + failure.getMessage());
        }
    }

    private static boolean setWidgetField(Object widget, String name, boolean value) {
        for (Class<?> type = widget.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                field.setBoolean(widget, value);
                return true;
            } catch (NoSuchFieldException ignored) {
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                return false;
            }
        }
        return false;
    }

    private static boolean setIntField(Object widget, String name, int value) {
        for (Class<?> type = widget.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                field.setInt(widget, value);
                return true;
            } catch (NoSuchFieldException ignored) {
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                return false;
            }
        }
        return false;
    }

    private static boolean readWidgetField(Object widget, String name, boolean fallback) {
        for (Class<?> type = widget.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.getBoolean(widget);
            } catch (NoSuchFieldException ignored) {
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    public void addScreenGui(ScreenGui sg) {
        java.util.Objects.requireNonNull(sg, "screenGui");
        synchronized (this.state) {
            if (!this.state.screens.contains(sg)) this.state.screens.add(sg);
        }
    }
    public void removeScreenGui(ScreenGui sg) { this.state.screens.remove(sg); }
    public List<ScreenGui> getScreenGuis() { return List.copyOf(this.state.screens); }

    public ScreenGui getCoreGui(CoreGuiType type) {
        for (ScreenGui screen : this.state.screens) {
            if (screen.getCoreGuiType() == type) return screen;
        }
        return null;
    }

    public ScreenGui createCoreGui(CoreGuiType type, String name) {
        if (type == null || type == CoreGuiType.All) {
            throw new IllegalArgumentException("A specific CoreGuiType is required for a core overlay");
        }
        synchronized (this.state) {
            ScreenGui existing = this.getCoreGui(type);
            if (existing != null) return existing;
            ScreenGui screen = new ScreenGui(name == null || name.isBlank() ? type.name() + "Overlay" : name);
            screen.setCoreGuiType(type);
            this.attachScriptOwner(screen);
            this.addScreenGui(screen);
            return screen;
        }
    }

    public boolean isCoreGuiEnabled(CoreGuiType type) {
        if (type == CoreGuiType.All) return true;
        return !this.state.disabledTypes.contains(type);
    }

    public boolean isEnabled() { return this.state.enabled; }

    public void setEnabled(boolean enabled) { this.state.enabled = enabled; }

    public void setCoreGuiEnabled(CoreGuiType type, boolean enabled) {
        if (type == CoreGuiType.All) {
            if (enabled) this.state.disabledTypes.clear();
            else for (CoreGuiType t : CoreGuiType.values()) if (t != CoreGuiType.All) this.state.disabledTypes.add(t);
        } else {
            if (enabled) this.state.disabledTypes.remove(type);
            else this.state.disabledTypes.add(type);
        }
    }

    public void clear() {
        this.state.clear();
    }

    public int clearOwned(String ownerId) {
        if (ownerId == null || ownerId.isBlank()) return 0;
        int removed = 0;
        synchronized (this.state) {
            for (Map.Entry<String, HudElement> entry : List.copyOf(this.state.hudElements.entrySet())) {
                if (ownerId.equals(entry.getValue().owner)
                    && this.state.hudElements.remove(entry.getKey(), entry.getValue())) removed++;
            }
            for (MLuaScriptScreen screen : List.copyOf(this.state.scriptScreens)) {
                if (ownerId.equals(screen.ownerModId)) {
                    screen.dispose();
                    removed++;
                }
            }
            boolean removedScreenTarget = false;
            for (Map.Entry<String, ScreenTarget> entry : List.copyOf(this.state.screenTargets.entrySet())) {
                if (ownerId.equals(entry.getValue().owner())
                    && this.state.screenTargets.remove(entry.getKey(), entry.getValue())) {
                    removed++;
                    removedScreenTarget = true;
                }
            }
            if (removedScreenTarget) this.recomposeTrackedScreens();
            for (NativeLayout layout : List.copyOf(this.state.nativeLayouts)) {
                if (!ownerId.equals(layout.owner())) continue;
                GuiObject[] ownedObjects = {layout.replacement(), layout.before(), layout.after()};
                this.removeNativeLayoutEntry(layout);
                for (GuiObject object : ownedObjects) {
                    if (object != null && ownerId.equals(object.ownerModId())) {
                        object.destroyPreservingForeign(ownerId);
                    }
                }
                removed++;
            }
            for (ScreenGui screen : List.copyOf(this.state.screens)) {
                removed += screen.removeOwnedChildren(ownerId);
                if (ownerId.equals(screen.ownerModId()) && screen.getChildren().isEmpty()
                    && this.state.screens.remove(screen)) {
                    screen.destroy();
                    removed++;
                }
            }
        }
        return removed;
    }

    public void Destroy() { clear(); }

    public String name() { return "Gui"; }

    public LuaTable toLua() {
        LuaTable table = new LuaTable();
        table.set("GetEnabled", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                return LuaValue.valueOf(MLuaGuiService.this.isEnabled());
            }
        });
        table.set("SetEnabled", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                MLuaGuiService.this.setEnabled(args.checkboolean(2));
                return LuaValue.NONE;
            }
        });
        table.set("RegisterScreenTarget", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                return LuaValue.valueOf(MLuaGuiService.this.registerScreenTarget(
                    args.arg(2).checkjstring(), args.arg(3).checkjstring()));
            }
        });
        table.set("UnregisterScreenTarget", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                return LuaValue.valueOf(MLuaGuiService.this.unregisterScreenTarget(args.arg(2).checkjstring()));
            }
        });
        LuaTable vanillaHudElements = new LuaTable();
        vanillaHudElements.set("Hud", LuaValue.valueOf("Hud"));
        vanillaHudElements.set("Chat", LuaValue.valueOf("Chat"));
        vanillaHudElements.set("Crosshair", LuaValue.valueOf("Crosshair"));
        vanillaHudElements.set("Hotbar", LuaValue.valueOf("Hotbar"));
        vanillaHudElements.set("Health", LuaValue.valueOf("Health"));
        vanillaHudElements.set("Experience", LuaValue.valueOf("Experience"));
        vanillaHudElements.set("Effects", LuaValue.valueOf("Effects"));
        //--version 1.21.1
        vanillaHudElements.set("Food", LuaValue.valueOf("Food"));
        //--endversion
        //--version 1.21.1,26.1
        vanillaHudElements.set("OverlayMessage", LuaValue.valueOf("OverlayMessage"));
        vanillaHudElements.set("Title", LuaValue.valueOf("Title"));
        vanillaHudElements.set("Scoreboard", LuaValue.valueOf("Scoreboard"));
        vanillaHudElements.set("PlayerList", LuaValue.valueOf("PlayerList"));
        //--endversion
        table.set("VanillaHudElements", vanillaHudElements);
        table.set("AttachElementBefore", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                return LuaValue.valueOf(MLuaGuiService.this.attachHudElement(
                    args.arg(2).checkjstring(), args.arg(3).checkjstring(), true, args.arg(4),
                    MLuaLuaArguments.optionalWholeInt(args, 5, 0, "Gui:AttachElementBefore", "priority")));
            }
        });
        table.set("AttachElementAfter", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                return LuaValue.valueOf(MLuaGuiService.this.attachHudElement(
                    args.arg(2).checkjstring(), args.arg(3).checkjstring(), false, args.arg(4),
                    MLuaLuaArguments.optionalWholeInt(args, 5, 0, "Gui:AttachElementAfter", "priority")));
            }
        });
        table.set("RemoveHudElement", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                if (MLuaGuiService.this.context == null) return LuaValue.FALSE;
                String key = MLuaGuiService.this.context.mlua().ownerModId() + ":" + args.arg(2).checkjstring();
                HudElement element = MLuaGuiService.this.state.hudElements.get(key);
                return LuaValue.valueOf(element != null
                    && MLuaGuiService.this.state.hudElements.remove(key, element));
            }
        });
        if (this.context != null) {
            table.set("ScreenOpened", MLuaLuaBindings.signalInstance(this.state.screenOpened,
                this.context.mlua().globals(), this.context.mlua().scheduler(), this.context.mlua().ownerModId(),
                this.context.mlua().side()));
            table.set("ChatLineAdding", MLuaLuaBindings.signalInstance(this.state.chatLineAdding,
                this.context.mlua().globals(), this.context.mlua().scheduler(), this.context.mlua().ownerModId(),
                this.context.mlua().side()));
        }

        table.set("GetScreens", new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                LuaTable list = new LuaTable();
                int i = 1;
                for (ScreenGui sg : MLuaGuiService.this.state.screens) {
                    if (sg.scheduler() == null && MLuaGuiService.this.context != null) sg.setScheduler(MLuaGuiService.this.context.mlua().scheduler());
                    if (sg.registration() == null && MLuaGuiService.this.context != null) sg.setRegistration(MLuaGuiService.this.context.mlua().registration());
                    list.set(i++, sg.toLua());
                }
                return list;
            }
        });

        table.set("CreateScreen", new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                String name = args.arg(2).optjstring("Screen");
                ScreenGui sg = new ScreenGui(name);
                MLuaGuiService.this.attachScriptOwner(sg);
                if (args.arg(3).istable()) applyScreenProperties(sg, args.arg(3).checktable());
                MLuaGuiService.this.addScreenGui(sg);
                return sg.toLua();
            }
        });

        table.set("OpenNativeScreen", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                Object title = nativeText(args.arg(2));
                LuaValue initialize = args.arg(3);
                if (!initialize.isfunction()) throw new LuaError("[MLua] Gui:OpenNativeScreen expects an initialization function.");
                Minecraft minecraft = Minecraft.getInstance();
                if (minecraft == null) throw new LuaError("[MLua] Minecraft client is not available.");
                MLuaScriptScreen screen = new MLuaScriptScreen(title, initialize,
                    MLuaGuiService.this.context.mlua().registration(), MLuaGuiService.this.state,
                    MLuaGuiService.this.context.mlua().ownerModId(), MLuaLuaBindings.currentContext());
                MLuaGuiService.this.state.scriptScreens.add(screen);
                minecraft.setScreen(screen);
                return MLuaNativeScreen.instance(MLuaRuntime.get(), screen);
            }
        });

        table.set("OpenScreen", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                if (MLuaGuiService.this.context == null) throw new LuaError("[MLua] Gui service context is unavailable.");
                String className = args.arg(2).checkjstring();
                Object title = nativeText(args.arg(3));
                LuaValue fieldsValue = args.arg(4);
                if (!fieldsValue.isnil() && !fieldsValue.istable()) throw new LuaError("[MLua] Gui:OpenScreen fields must be a table.");
                if (fieldsValue.istable()) {
                    Set<String> reserved = Set.of("NativeScreen", "Close", "AddText", "AddButton", "AddEditBox", "AddRenderableWidget", "SetOnClose", "SetOnRender");
                    for (LuaValue key : fieldsValue.checktable().keys()) {
                        if (key.type() == LuaValue.TSTRING && reserved.contains(key.tojstring())) {
                            throw new LuaError("[MLua] Gui:OpenScreen fields cannot replace the native screen member '" + key.tojstring() + "'.");
                        }
                    }
                }
                MLuaRegistration registration = MLuaGuiService.this.context.mlua().registration();
                if (!registration.isSubclassOf(className, "Screen")) {
                    throw new LuaError("[MLua] Gui:OpenScreen requires a registered class that extends Screen.");
                }
                LuaTable instance;
                try { instance = registration.attachClassInstance(new LuaTable(), className, null); }
                catch (RuntimeException failure) { throw new LuaError("[MLua] Could not create screen class '" + className + "': " + failure.getMessage()); }
                MLuaScriptScreen screen = new MLuaScriptScreen(title, instance, registration, className,
                    fieldsValue.istable() ? fieldsValue.checktable() : null, MLuaGuiService.this.state,
                    MLuaGuiService.this.context.mlua().ownerModId(), MLuaLuaBindings.currentContext());
                MLuaGuiService.this.state.scriptScreens.add(screen);
                instance.set("NativeScreen", MLuaNativeScreen.instance(MLuaRuntime.get(), screen));
                instance.rawset("Close", new VarArgFunction() {
                    @Override public Varargs invoke(Varargs closeArgs) {
                        if (MLuaGuiService.currentScreen() == screen) {
                            Minecraft.getInstance().setScreen(null);
                            return LuaValue.TRUE;
                        }
                        return LuaValue.FALSE;
                    }
                });
                Minecraft.getInstance().setScreen(screen);
                return instance;
            }
        });

        table.set("RemoveScreen", new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                return LuaValue.valueOf(MLuaGuiService.this.removeScreen(args.arg(2)));
            }
        });

        table.set("SetCoreGuiEnabled", new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                LuaValue typeVal = args.arg(2);
                boolean enabled = args.arg(3).optboolean(true);
                CoreGuiType type = requireCoreGuiType(typeVal, "SetCoreGuiEnabled");
                MLuaGuiService.this.setCoreGuiEnabled(type, enabled);
                return LuaValue.NONE;
            }
        });

        table.set("GetCoreGuiEnabled", new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                LuaValue typeVal = args.arg(2);
                CoreGuiType type = requireCoreGuiType(typeVal, "GetCoreGuiEnabled");
                return LuaValue.valueOf(MLuaGuiService.this.isCoreGuiEnabled(type));
            }
        });

        table.set("AddScreenGui", new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                LuaTable t = args.checktable(2);
                GuiBase existing = MLuaTableRegistry.getElement(t);
                if (existing != null) {
                    if (!(existing instanceof ScreenGui screen)) {
                        throw new LuaError("[MLua] Gui:AddScreenGui expects a ScreenGui instance or a screen definition table");
                    }
                    MLuaGuiService.this.attachScriptOwner(screen);
                    MLuaGuiService.this.addScreenGui(screen);
                    return screen.toLua();
                }
                String name = t.get("Name").optjstring("Screen");
                ScreenGui sg = new ScreenGui(name);
                MLuaGuiService.this.attachScriptOwner(sg);
                applyScreenProperties(sg, t);

                processChildren(t, sg);

                MLuaGuiService.this.addScreenGui(sg);
                return sg.toLua();
            }
        });

        table.set("RemoveScreenGui", new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                return LuaValue.valueOf(MLuaGuiService.this.removeScreen(args.arg(2)));
            }
        });

        table.set("GetScreenGuis", new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                LuaTable list = new LuaTable();
                int i = 1;
                for (ScreenGui sg : MLuaGuiService.this.state.screens) {
                    if (sg.scheduler() == null && MLuaGuiService.this.context != null) sg.setScheduler(MLuaGuiService.this.context.mlua().scheduler());
                    if (sg.registration() == null && MLuaGuiService.this.context != null) sg.setRegistration(MLuaGuiService.this.context.mlua().registration());
                    list.set(i++, sg.toLua());
                }
                return list;
            }
        });

        table.set("GetCoreGui", new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                CoreGuiType type = requireCoreGuiType(args.arg(2), "GetCoreGui");
                ScreenGui screen = MLuaGuiService.this.getCoreGui(type);
                return screen == null ? LuaValue.NIL : screen.toLua();
            }
        });

        table.set("CreateCoreGui", new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                CoreGuiType type = requireCoreGuiType(args.arg(2), "CreateCoreGui");
                if (type == CoreGuiType.All) {
                    throw new LuaError("[MLua] Gui:CreateCoreGui requires one specific CoreGuiType, not All");
                }
                String name = args.arg(3).optjstring(null);
                return MLuaGuiService.this.createCoreGui(type, name).toLua();
            }
        });

        table.set("GetNativeControls", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                LuaValue selector = args.arg(2);
                if (!selector.isnil() && !selector.istable()) throw new LuaError("[MLua] Gui:GetNativeControls selector must be a table");
                if (selector.istable()) requireNativeSelector(selector.checktable());
                return MLuaGuiService.this.nativeControls(selector, false);
            }
        });

        table.set("GetNativeControl", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                LuaTable selector = args.checktable(2);
                requireNativeSelector(selector);
                return MLuaGuiService.this.nativeControls(selector, true).get(1);
            }
        });

        table.set("SetNativeLayout", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                String target = args.checkjstring(2);
                if (target.isBlank()) throw new LuaError("[MLua] Gui:SetNativeLayout requires a screen class name or 'Any'");
                LuaTable selector = args.checktable(3);
                LuaTable properties = args.checktable(4);
                requireNativeSelector(selector);
                if (properties.keys().length == 0) throw new LuaError("[MLua] Native layout needs at least one property");
                for (LuaValue key : properties.keys()) {
                    if (key.type() != LuaValue.TSTRING || key.tojstring().isBlank() || !Character.isLetter(key.tojstring().charAt(0))
                        || key.tojstring().startsWith("__")) {
                        throw new LuaError("[MLua] Native layout property names must start with a letter and cannot be runtime metadata");
                    }
                }
                if (!properties.get("X").isnil() || !properties.get("Y").isnil()
                    || !properties.get("Width").isnil() || !properties.get("Height").isnil()) {
                    throw new LuaError("[MLua] Native layout uses Position and Size UDim2 values, not X/Y/Width/Height fields");
                }
                if (!properties.get("Position").isnil() && !MLuaLuauTypes.isUDim2(properties.get("Position"))
                    || !properties.get("Size").isnil() && !MLuaLuauTypes.isUDim2(properties.get("Size"))
                    || !properties.get("Visible").isnil() && !properties.get("Visible").isboolean()
                    || !properties.get("Text").isnil() && properties.get("Text").type() != LuaValue.TSTRING) {
                    throw new LuaError("[MLua] Native layout Position and Size require UDim2 values; Visible must be boolean and Text must be a string");
                }
                for (LuaValue key : properties.keys()) {
                    if (key.type() != LuaValue.TSTRING || Set.of("Position", "Size", "Visible", "Text", "Replacement", "Before", "After").contains(key.tojstring())) continue;
                    LuaValue value = properties.get(key);
                    if (value.type() != LuaValue.TSTRING && value.type() != LuaValue.TNUMBER
                        && value.type() != LuaValue.TBOOLEAN) {
                        throw new LuaError("[MLua] Native layout property '" + key
                            + "' must use a string, number, boolean, enum name, or text value");
                    }
                }
                if (!properties.get("Replacement").isnil()
                    && ((!selector.get("Index").isint() && selector.get("Text").type() != LuaValue.TSTRING)
                        || properties.get("Visible").isboolean() && properties.get("Visible").checkboolean())) {
                    throw new LuaError("[MLua] A replacement requires a specific Index or Text selector and cannot set Visible=true");
                }
                if ((!properties.get("Before").isnil() || !properties.get("After").isnil()) && !selector.get("Index").isint()) {
                    throw new LuaError("[MLua] NativeControl.Before and After require a specific Index selector");
                }
                NativeLayout layout = MLuaGuiService.this.addNativeLayout(target, selector, properties);
                try {
                    MLuaGuiService.this.recomposeLayouts(currentScreen(), layout);
                } catch (RuntimeException | Error failure) {
                    MLuaGuiService.this.removeNativeLayout(layout.id());
                    throw failure;
                }
                return LuaValue.valueOf(layout.id());
            }
        });

        table.set("RemoveNativeLayout", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                return LuaValue.valueOf(MLuaGuiService.this.removeNativeLayout(args.checklong(2)));
            }
        });

        return MLuaServiceBase.normalizeServiceCalls(table, this.name());
    }

    private boolean removeScreen(LuaValue target) {
        ScreenGui found = null;
        if (target.istable()) {
            GuiBase instance = MLuaTableRegistry.getElement(target.checktable());
            if (instance instanceof ScreenGui screen && this.state.screens.contains(screen)) found = screen;
        } else if (target.type() == LuaValue.TSTRING) {
            String name = target.checkjstring();
            for (ScreenGui screen : this.state.screens) {
                if (name.equals(screen.getName())) {
                    found = screen;
                    break;
                }
            }
        } else {
            throw new LuaError("[MLua] Gui:RemoveScreenGui expects a ScreenGui instance or name");
        }
        if (found == null) return false;
        this.removeScreenGui(found);
        found.destroy();
        return true;
    }

    private static CoreGuiType requireCoreGuiType(LuaValue value, String method) {
        CoreGuiType type = MLuaEnumValue.toJava(value, CoreGuiType.class);
        if (type == null) throw new LuaError("[MLua] Gui:" + method + " expects an Enum.CoreGuiType value");
        return type;
    }

    private static void requireNativeSelector(LuaTable selector) {
        for (LuaValue key : selector.keys()) {
            if (key.type() != LuaValue.TSTRING) throw new LuaError("[MLua] Native widget selector fields must be strings");
            String name = key.tojstring();
            if (name.isBlank() || name.startsWith("__") || !Character.isLetter(name.charAt(0))) {
                throw new LuaError("[MLua] Invalid native widget selector field '" + name + "'");
            }
        }
        LuaValue index = selector.get("Index");
        LuaValue className = selector.get("Class");
        LuaValue text = selector.get("Text");
        if (!index.isnil() && (!index.isint() || index.checkint() < 1)) {
            throw new LuaError("[MLua] Native widget Index must be a positive one-based integer");
        }
        if (!className.isnil() && (className.type() != LuaValue.TSTRING || className.checkjstring().isBlank())) {
            throw new LuaError("[MLua] Native widget Class must be a non-empty Java class or interface name");
        }
        if (!text.isnil() && text.type() != LuaValue.TSTRING) {
            throw new LuaError("[MLua] Native widget Text must be a string");
        }
        for (LuaValue key : selector.keys()) {
            String name = key.tojstring();
            if (Set.of("Index", "Class", "Text").contains(name)) continue;
            LuaValue value = selector.get(key);
            if (value.type() != LuaValue.TSTRING && value.type() != LuaValue.TNUMBER
                && value.type() != LuaValue.TBOOLEAN) {
                throw new LuaError("[MLua] Native widget property selector '" + name
                    + "' must use a string, number, or boolean");
            }
        }
        if (index.isnil() && className.isnil() && text.isnil()) {
            if (selector.keys().length == 0) {
                throw new LuaError("[MLua] Native widget selector requires Index, Class, Text, or a readable property");
            }
        }
    }

    private static void applyScreenProperties(ScreenGui screen, LuaTable definition) {
        screen.applyDefinitionProperties(definition);
    }

    private void attachScriptOwner(GuiBase root) {
        if (this.context == null) return;
        root.setScheduler(this.context.mlua().scheduler());
        root.setRegistration(this.context.mlua().registration());
        root.setOwnerContext(this.context.mlua().ownerModId(), this.context.mlua().side(), this.context.mlua().globals());
    }

    private String attachHudElement(String anchor, String id, boolean before, LuaValue callback, int priority) {
        if (this.context == null) throw new LuaError("[MLua] Gui service context is unavailable.");
        if (id == null || id.isBlank()) throw new LuaError("[MLua] HUD element id must not be empty.");
        String normalizedAnchor = anchor == null ? "" : anchor.trim();
        String positionAnchor = switch (normalizedAnchor.toLowerCase(java.util.Locale.ROOT)) {
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
        String owner = this.context.mlua().ownerModId();
        String key = owner + ":" + id;
        String position = (before ? "Before" : "After") + positionAnchor;
        HudElement element = new HudElement(key, owner, positionAnchor, before, position, callback,
            priority, NEXT_HUD_ELEMENT_ID.getAndIncrement(), this.context.mlua().executionLock(),
            MLuaLuaBindings.currentContext());
        this.state.hudElements.put(key, element);
        return id;
    }

    private static void processChildren(LuaTable parentTable, GuiBase parent) {
        LuaValue children = parentTable.get("Children");
        if (children.isnil()) return;
        if (!children.istable()) throw new LuaError("[MLua] UI Children must be a dense array of child definition tables.");
        LuaTable childList = children.checktable();
        int childCount = requireDenseArrayLength(childList, "UI Children");
        for (int index = 1; index <= childCount; index++) {
            LuaValue value = childList.rawget(index);
            if (!value.istable()) {
                throw new LuaError("[MLua] UI Children entry " + index + " must be a definition table or live UI instance.");
            }
            GuiObject child = createFromTable(value.checktable(), parent.ownerModId(), parent.ownerSide(), parent.ownerGlobals(), parent.registration(), parent.scheduler());
            parent.addChild(child);
        }
    }

    private static int requireDenseArrayLength(LuaTable array, String label) {
        Set<Integer> indices = new TreeSet<>();
        LuaValue key = LuaValue.NIL;
        while (true) {
            Varargs entry = array.next(key);
            key = entry.arg1();
            if (key.isnil()) break;
            if (key.type() != LuaValue.TNUMBER) {
                throw new LuaError("[MLua] " + label + " must use only positive integer array keys.");
            }
            double numericIndex = key.todouble();
            int index = key.toint();
            if (index < 1 || numericIndex != index || !indices.add(index)) {
                throw new LuaError("[MLua] " + label + " must use only positive integer array keys.");
            }
        }
        int expected = 1;
        for (int index : indices) {
            if (index != expected++) {
                throw new LuaError("[MLua] " + label + " must be a dense array without gaps.");
            }
        }
        return indices.size();
    }

    private static GuiObject createFromTable(LuaTable t, String ownerModId, dev.mlua.common.enums.Side side, org.luaj.vm2.Globals globals,
            dev.mlua.module.MLuaRegistration registration, dev.mlua.core.scheduler.MLuaTaskSchedulerView scheduler) {
        String type = t.get("__type").optjstring("");
        if (type.isBlank() && MLuaTableRegistry.getElement(t) == null) {
            throw new LuaError("[MLua] UI child definition requires a registered class in __type");
        }
        GuiBase element = MLuaTableRegistry.getElement(t);
        if (!(element instanceof GuiObject)) {
            LuaTable classTable = registration != null ? registration.classTable(type) : null;
            if (classTable == null) throw new LuaError("[MLua] UI class '" + type + "' is not registered in this addon runtime");
            LuaValue constructor = classTable.get("new");
            if (!constructor.isfunction()) throw new LuaError("[MLua] UI class '" + type + "' has no constructor");
            LuaValue created = constructor.call(classTable);
            if (!created.istable()) throw new LuaError("[MLua] UI class '" + type + "' constructor must return a live instance");
            element = MLuaTableRegistry.getElement(created.checktable());
            if (element != null && registration != null
                && !type.equals(created.checktable().rawget("__type").optjstring(""))) {
                registration.attachClassInstance(created.checktable(), type, null);
            }
        }
        if (!(element instanceof GuiObject obj)) throw new LuaError("[MLua] UI class '" + type + "' must create a GuiObject instance");
        obj.setRegistration(registration);
        obj.setScheduler(scheduler);
        obj.setOwnerContext(ownerModId, side, globals);
        applyProperties(obj, t);
        processChildren(t, obj);
        return obj;
    }

    private static void applyProperties(GuiObject obj, LuaTable definition) {
        LuaValue value = definition.get("MouseEnter");
        if (value.isfunction()) obj.getMouseEnter().connectOwned(value, obj.ownerModId(), obj.ownerSide(), obj.ownerGlobals(), obj.scheduler(), MLuaLuaBindings.boundContext(obj.ownerGlobals()));
        value = definition.get("MouseLeave");
        if (value.isfunction()) obj.getMouseLeave().connectOwned(value, obj.ownerModId(), obj.ownerSide(), obj.ownerGlobals(), obj.scheduler(), MLuaLuaBindings.boundContext(obj.ownerGlobals()));
        value = definition.get("MouseMoved");
        if (value.isfunction()) obj.getMouseMoved().connectOwned(value, obj.ownerModId(), obj.ownerSide(), obj.ownerGlobals(), obj.scheduler(), MLuaLuaBindings.boundContext(obj.ownerGlobals()));
        value = definition.get("InputBegan");
        if (value.isfunction()) obj.getInputBegan().connectOwned(value, obj.ownerModId(), obj.ownerSide(), obj.ownerGlobals(), obj.scheduler(), MLuaLuaBindings.boundContext(obj.ownerGlobals()));
        value = definition.get("InputChanged");
        if (value.isfunction()) obj.getInputChanged().connectOwned(value, obj.ownerModId(), obj.ownerSide(), obj.ownerGlobals(), obj.scheduler(), MLuaLuaBindings.boundContext(obj.ownerGlobals()));
        value = definition.get("InputEnded");
        if (value.isfunction()) obj.getInputEnded().connectOwned(value, obj.ownerModId(), obj.ownerSide(), obj.ownerGlobals(), obj.scheduler(), MLuaLuaBindings.boundContext(obj.ownerGlobals()));
        obj.applyDefinitionProperties(definition);
    }

    private static final class MLuaScriptScreen extends Screen {
        private final LuaValue initialize;
        private final LuaTable luaInstance;
        private final MLuaRegistration registration;
        private final String className;
        private final LuaTable fields;
        private final GuiState state;
        private final String ownerModId;
        private final MLuaContext context;
        private final List<ScreenText> textLines = new CopyOnWriteArrayList<>();
        private final Set<String> failedInputMethods = ConcurrentHashMap.newKeySet();
        private LuaValue closed = LuaValue.NIL;
        private LuaValue renderCallback = LuaValue.NIL;
        private boolean classInitialized;
        private boolean disposed;
        private boolean renderFailed;
        private int nextNativeControlIndex = 1;

        private MLuaScriptScreen(Object title, LuaValue initialize, MLuaRegistration registration,
                                 GuiState state, String ownerModId, MLuaContext context) {
            super(screenTitle(title));
            this.initialize = initialize;
            this.luaInstance = null;
            this.registration = registration;
            this.className = null;
            this.fields = null;
            this.state = state;
            this.ownerModId = ownerModId;
            this.context = context;
        }

        private MLuaScriptScreen(Object title, LuaTable luaInstance, MLuaRegistration registration,
                                 String className, LuaTable fields, GuiState state, String ownerModId,
                                 MLuaContext context) {
            super(screenTitle(title));
            this.initialize = LuaValue.NIL;
            this.luaInstance = luaInstance;
            this.registration = registration;
            this.className = className;
            this.fields = fields;
            this.state = state;
            this.ownerModId = ownerModId;
            this.context = context;
        }

        private static Component screenTitle(Object title) {
            return title instanceof Component component ? component : Component.literal((String) title);
        }

        @Override protected void init() {
            super.init();
            this.textLines.clear();
            this.renderFailed = false;
            this.nextNativeControlIndex = 1;
            LuaTable builder = new LuaTable();
            builder.set("SetOnClose", new VarArgFunction() {
                @Override public Varargs invoke(Varargs args) {
                    LuaValue callback = args.arg(2);
                    if (!callback.isfunction()) throw new LuaError("[MLua] Screen:SetOnClose expects a callback function.");
                    MLuaScriptScreen.this.closed = callback;
                    return LuaValue.NONE;
                }
            });
            builder.set("SetOnRender", new VarArgFunction() {
                @Override public Varargs invoke(Varargs args) {
                    LuaValue callback = args.arg(2);
                    if (!callback.isfunction()) throw new LuaError("[MLua] Screen:SetOnRender expects a callback function.");
                    MLuaScriptScreen.this.renderCallback = callback;
                    return LuaValue.NONE;
                }
            });
            builder.set("AddText", new VarArgFunction() {
                @Override public Varargs invoke(Varargs args) {
                    Object text = nativeText(args.arg(2));
                    LuaTable position = requireScreenUDim2(args.arg(3), "Position");
                    int color = optionalColor(args.arg(4), 0xFFFFFF, "NativeScreen:AddText", "color");
                    MLuaScriptScreen.this.textLines.add(new ScreenText(text,
                        screenCoordinate(position, "X", MLuaScriptScreen.this.width),
                        screenCoordinate(position, "Y", MLuaScriptScreen.this.height), color));
                    return LuaValue.NONE;
                }
            });
            builder.set("AddButton", new VarArgFunction() {
                @Override public Varargs invoke(Varargs args) {
                    Object label = nativeText(args.arg(2));
                    LuaTable position = requireScreenUDim2(args.arg(3), "Position");
                    LuaTable size = requireScreenUDim2(args.arg(4), "Size");
                    LuaValue callback = args.arg(5);
                    if (!callback.isfunction()) throw new LuaError("[MLua] Screen:AddButton expects a callback function.");
                    return MLuaScriptScreen.this.addNativeButton(label, position, size, callback);
                }
            });
            builder.set("AddEditBox", new VarArgFunction() {
                @Override public Varargs invoke(Varargs args) {
                    LuaTable position = requireScreenUDim2(args.arg(2), "Position");
                    LuaTable size = requireScreenUDim2(args.arg(3), "Size");
                    String initialText = args.arg(4).optjstring("");
                    LuaValue changed = args.arg(5);
                    if (!changed.isnil() && !changed.isfunction()) {
                        throw new LuaError("[MLua] Screen:AddEditBox onChanged must be a callback function.");
                    }
                    return MLuaScriptScreen.this.addNativeEditBox(position, size, initialText, changed);
                }
            });
            builder.set("AddRenderableWidget", new VarArgFunction() {
                @Override public Varargs invoke(Varargs args) {
                    Object widget = nativeWidget(args.arg(2));
                    if (!(widget instanceof AbstractWidget abstractWidget)) {
                        throw new LuaError("[MLua] Screen:AddRenderableWidget expects a Minecraft AbstractWidget instance.");
                    }
                    AbstractWidget added = MLuaScriptScreen.this.addRenderableWidget(abstractWidget);
                    int index = MLuaScriptScreen.this.nextNativeControlIndex++;
                    return MLuaNativeControl.instance(MLuaScriptScreen.this.registration,
                        new MLuaNativeControl.Backing(added, MLuaScriptScreen.this,
                            MLuaScriptScreen.this.getClass().getName(), index));
                }
            });
            try {
                synchronized (this.state.executionLock) {
                    if (this.luaInstance == null) {
                        this.invokeLua(this.initialize, LuaValue.varargsOf(new LuaValue[]{builder}));
                    } else {
                        this.luaInstance.rawset("AddText", builder.get("AddText"));
                        this.luaInstance.rawset("AddButton", builder.get("AddButton"));
                        this.luaInstance.rawset("AddEditBox", builder.get("AddEditBox"));
                        this.luaInstance.rawset("AddRenderableWidget", builder.get("AddRenderableWidget"));
                        this.luaInstance.rawset("SetOnClose", builder.get("SetOnClose"));
                        this.luaInstance.rawset("SetOnRender", builder.get("SetOnRender"));
                        if (!this.classInitialized) {
                            this.registration.initializeClassInstance(this.luaInstance, this.className, this.fields);
                            this.classInitialized = true;
                        } else {
                            LuaValue init = this.luaInstance.get("Init");
                            if (init.isfunction()) this.invokeLua(init, LuaValue.varargsOf(new LuaValue[]{this.luaInstance,
                                this.fields == null ? LuaValue.NIL : this.fields}));
                        }
                    }
                }
            } catch (RuntimeException failure) {
                MLuaLogger.LOGGER.error("Lua native screen initialization failed", failure);
            }
        }

        private static Object nativeWidget(LuaValue value) {
            LuaValue wrapped = value.istable() ? value.checktable().rawget("__javaObject") : LuaValue.NIL;
            if (!wrapped.isnil() && wrapped.isuserdata()) return wrapped.touserdata();
            if (value.isuserdata()) return value.touserdata();
            throw new LuaError("[MLua] Screen:AddRenderableWidget expects a live Java widget from Java:New or a Java method.");
        }

        private LuaTable addNativeButton(Object label, LuaTable position, LuaTable size, LuaValue callback) {
            int x = screenCoordinate(position, "X", this.width);
            int y = screenCoordinate(position, "Y", this.height);
            int width = screenCoordinate(size, "X", this.width);
            int height = screenCoordinate(size, "Y", this.height);
            if (width < 1 || height < 1) throw new LuaError("[MLua] Screen button dimensions must be positive.");
            int buttonIndex = this.nextNativeControlIndex++;
            LuaTable[] controlRef = new LuaTable[1];
            Component component = label instanceof Component styled ? styled : Component.literal((String) label);
            Button button = Button.builder(component, clicked -> {
                try {
                    this.invokeLua(callback, LuaValue.varargsOf(new LuaValue[]{controlRef[0]}));
                }
                catch (RuntimeException failure) { MLuaLogger.LOGGER.error("Lua screen button callback failed", failure); }
            }).bounds(x, y, width, height).build();
            this.addRenderableWidget(button);
            controlRef[0] = MLuaNativeControl.instance(this.registration,
                new MLuaNativeControl.Backing(button, this, this.getClass().getName(), buttonIndex));
            return controlRef[0];
        }

        private LuaTable addNativeEditBox(LuaTable position, LuaTable size, String initialText, LuaValue changed) {
            int x = screenCoordinate(position, "X", this.width);
            int y = screenCoordinate(position, "Y", this.height);
            int width = screenCoordinate(size, "X", this.width);
            int height = screenCoordinate(size, "Y", this.height);
            if (width < 1 || height < 1) throw new LuaError("[MLua] Screen edit box dimensions must be positive.");
            int controlIndex = this.nextNativeControlIndex++;
            EditBox field = new EditBox(this.font, x, y, width, height, Component.literal(""));
            field.setValue(initialText);
            LuaTable control = MLuaNativeControl.instance(this.registration,
                new MLuaNativeControl.Backing(field, this, this.getClass().getName(), controlIndex));
            if (changed.isfunction()) {
                field.setResponder(value -> {
                    try { this.invokeLua(changed, LuaValue.varargsOf(new LuaValue[]{LuaValue.valueOf(value), control})); }
                    catch (RuntimeException failure) { MLuaLogger.LOGGER.error("Lua screen edit box callback failed", failure); }
                });
            }
            this.addRenderableWidget(field);
            return control;
        }

        @Override public void onClose() {
            this.state.scriptScreens.remove(this);
            if (!this.disposed) {
                if (this.closed.isfunction()) {
                    try { this.invokeLua(this.closed, LuaValue.NONE); }
                    catch (RuntimeException failure) { MLuaLogger.LOGGER.error("Lua native screen close callback failed", failure); }
                }
                this.invokeClassMethod("OnClose");
            }
            super.onClose();
        }

        @Override public void tick() {
            super.tick();
            if (!this.disposed) this.invokeClassMethod("Tick");
        }

        //--version 1.20.1,1.21.1
        @Override public void resize(Minecraft minecraft, int width, int height) {
            super.resize(minecraft, width, height);
            if (!this.disposed) {
                this.invokeClassMethod("OnResize", LuaValue.valueOf(width), LuaValue.valueOf(height));
            }
        }
        //--endversion

        //--version 26.1
        @Override public void resize(int width, int height) {
            super.resize(width, height);
            if (!this.disposed) {
                this.invokeClassMethod("OnResize", LuaValue.valueOf(width), LuaValue.valueOf(height));
            }
        }
        //--endversion

        @Override public boolean isPauseScreen() {
            if (this.disposed || this.luaInstance == null || this.failedInputMethods.contains("IsPauseScreen")) return true;
            LuaValue method = this.luaInstance.get("IsPauseScreen");
            if (!method.isfunction()) return true;
            try {
                LuaValue result = this.invokeLua(method, LuaValue.varargsOf(new LuaValue[]{this.luaInstance})).arg1();
                return result.isnil() || result.toboolean();
            } catch (RuntimeException failure) {
                this.failedInputMethods.add("IsPauseScreen");
                MLuaLogger.LOGGER.error("Lua screen method 'IsPauseScreen' failed; using Minecraft's pause-screen default", failure);
                return true;
            }
        }

        @Override public boolean shouldCloseOnEsc() {
            if (this.disposed || this.luaInstance == null || this.failedInputMethods.contains("ShouldCloseOnEsc")) {
                return super.shouldCloseOnEsc();
            }
            LuaValue method = this.luaInstance.get("ShouldCloseOnEsc");
            if (!method.isfunction()) return super.shouldCloseOnEsc();
            boolean shouldClose = this.invokeClassInputMethod("ShouldCloseOnEsc");
            return this.failedInputMethods.contains("ShouldCloseOnEsc") ? super.shouldCloseOnEsc() : shouldClose;
        }

        @Override public void onFilesDrop(List<Path> paths) {
            if (this.luaInstance != null && paths != null) {
                LuaTable droppedPaths = new LuaTable();
                for (int index = 0; index < paths.size(); index++) {
                    droppedPaths.set(index + 1, LuaValue.valueOf(paths.get(index).toString()));
                }
                this.invokeClassInputMethod("OnFilesDrop", droppedPaths);
            }
            super.onFilesDrop(paths);
        }

        private void invokeClassMethod(String methodName, LuaValue... parameters) {
            if (this.luaInstance == null) return;
            LuaValue method = this.luaInstance.get(methodName);
            if (!method.isfunction()) return;
            if (parameters.length == 0) {
                try { this.invokeLua(method, LuaValue.varargsOf(new LuaValue[]{this.luaInstance})); }
                catch (RuntimeException failure) {
                    MLuaLogger.LOGGER.error("Lua screen lifecycle method '{}' failed", methodName, failure);
                }
                return;
            }
            LuaValue[] arguments = new LuaValue[parameters.length + 1];
            arguments[0] = this.luaInstance;
            System.arraycopy(parameters, 0, arguments, 1, parameters.length);
            try { this.invokeLua(method, LuaValue.varargsOf(arguments)); }
            catch (RuntimeException failure) {
                MLuaLogger.LOGGER.error("Lua screen lifecycle method '{}' failed", methodName, failure);
            }
        }

        private boolean invokeClassInputMethod(String methodName, LuaValue... parameters) {
            if (this.disposed || this.luaInstance == null || this.failedInputMethods.contains(methodName)) return false;
            LuaValue method = this.luaInstance.get(methodName);
            if (!method.isfunction()) return false;
            LuaValue[] arguments = new LuaValue[parameters.length + 1];
            arguments[0] = this.luaInstance;
            System.arraycopy(parameters, 0, arguments, 1, parameters.length);
            try { return this.invokeLua(method, LuaValue.varargsOf(arguments)).arg1().toboolean(); }
            catch (RuntimeException failure) {
                this.failedInputMethods.add(methodName);
                MLuaLogger.LOGGER.error("Lua screen input method '{}' failed", methodName, failure);
                return false;
            }
        }

        //--version 1.20.1,1.21.1
        @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (this.invokeClassInputMethod("KeyPressed", LuaValue.valueOf(keyCode), LuaValue.valueOf(scanCode),
                LuaValue.valueOf(modifiers))) return true;
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        @Override public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
            if (this.invokeClassInputMethod("KeyReleased", LuaValue.valueOf(keyCode), LuaValue.valueOf(scanCode),
                LuaValue.valueOf(modifiers))) return true;
            return super.keyReleased(keyCode, scanCode, modifiers);
        }

        @Override public boolean charTyped(char character, int modifiers) {
            if (this.invokeClassInputMethod("CharTyped", LuaValue.valueOf(String.valueOf(character)),
                LuaValue.valueOf(modifiers))) return true;
            return super.charTyped(character, modifiers);
        }

        @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (this.invokeClassInputMethod("MouseClicked", LuaValue.valueOf(mouseX), LuaValue.valueOf(mouseY),
                LuaValue.valueOf(button), LuaValue.FALSE)) return true;
            return super.mouseClicked(mouseX, mouseY, button);
        }

        @Override public boolean mouseReleased(double mouseX, double mouseY, int button) {
            if (this.invokeClassInputMethod("MouseReleased", LuaValue.valueOf(mouseX), LuaValue.valueOf(mouseY),
                LuaValue.valueOf(button))) return true;
            return super.mouseReleased(mouseX, mouseY, button);
        }

        @Override public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
            if (this.invokeClassInputMethod("MouseDragged", LuaValue.valueOf(mouseX), LuaValue.valueOf(mouseY),
                LuaValue.valueOf(button), LuaValue.valueOf(dragX), LuaValue.valueOf(dragY))) return true;
            return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        }
        //--endversion

        //--version 26.1
        @Override public boolean keyPressed(KeyEvent event) {
            if (this.invokeClassInputMethod("KeyPressed", LuaValue.valueOf(event.key()),
                LuaValue.valueOf(event.scancode()), LuaValue.valueOf(event.modifiers()))) return true;
            return super.keyPressed(event);
        }

        @Override public boolean keyReleased(KeyEvent event) {
            if (this.invokeClassInputMethod("KeyReleased", LuaValue.valueOf(event.key()),
                LuaValue.valueOf(event.scancode()), LuaValue.valueOf(event.modifiers()))) return true;
            return super.keyReleased(event);
        }

        @Override public boolean charTyped(CharacterEvent event) {
            if (this.invokeClassInputMethod("CharTyped", LuaValue.valueOf(event.codepointAsString()),
                LuaValue.ZERO)) return true;
            return super.charTyped(event);
        }

        @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            if (this.invokeClassInputMethod("MouseClicked", LuaValue.valueOf(event.x()), LuaValue.valueOf(event.y()),
                LuaValue.valueOf(event.button()), LuaValue.valueOf(doubleClick))) return true;
            return super.mouseClicked(event, doubleClick);
        }

        @Override public boolean mouseReleased(MouseButtonEvent event) {
            if (this.invokeClassInputMethod("MouseReleased", LuaValue.valueOf(event.x()), LuaValue.valueOf(event.y()),
                LuaValue.valueOf(event.button()))) return true;
            return super.mouseReleased(event);
        }

        @Override public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
            if (this.invokeClassInputMethod("MouseDragged", LuaValue.valueOf(event.x()), LuaValue.valueOf(event.y()),
                LuaValue.valueOf(event.button()), LuaValue.valueOf(dragX), LuaValue.valueOf(dragY))) return true;
            return super.mouseDragged(event, dragX, dragY);
        }
        //--endversion

        @Override public void mouseMoved(double mouseX, double mouseY) {
            this.invokeClassInputMethod("MouseMoved", LuaValue.valueOf(mouseX), LuaValue.valueOf(mouseY));
            super.mouseMoved(mouseX, mouseY);
        }

        //--version 1.20.1
        @Override public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
            if (this.invokeClassInputMethod("MouseScrolled", LuaValue.valueOf(mouseX), LuaValue.valueOf(mouseY),
                LuaValue.ZERO, LuaValue.valueOf(delta))) return true;
            return super.mouseScrolled(mouseX, mouseY, delta);
        }
        //--endversion

        //--version 1.21.1,26.1
        @Override public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
            if (this.invokeClassInputMethod("MouseScrolled", LuaValue.valueOf(mouseX), LuaValue.valueOf(mouseY),
                LuaValue.valueOf(scrollX), LuaValue.valueOf(scrollY))) return true;
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        //--endversion

        private void renderLua(ScreenDrawing drawing, int mouseX, int mouseY, float delta) {
            if (this.disposed || this.renderFailed) return;
            LuaValue classRender = this.luaInstance == null ? LuaValue.NIL : this.luaInstance.get("Render");
            boolean classLifecycle = classRender.isfunction();
            LuaValue callback = classLifecycle ? classRender : this.renderCallback;
            if (!callback.isfunction()) return;
            LuaTable canvas = new LuaTable();
            canvas.set("Width", LuaValue.valueOf(this.width));
            canvas.set("Height", LuaValue.valueOf(this.height));
            canvas.set("Fill", new VarArgFunction() {
                @Override public Varargs invoke(Varargs args) {
                    drawing.fill(MLuaLuaArguments.wholeInt(args, 2, "ScreenCanvas:Fill", "left"),
                        MLuaLuaArguments.wholeInt(args, 3, "ScreenCanvas:Fill", "top"),
                        MLuaLuaArguments.wholeInt(args, 4, "ScreenCanvas:Fill", "right"),
                        MLuaLuaArguments.wholeInt(args, 5, "ScreenCanvas:Fill", "bottom"),
                        readColor(args.arg(6), "ScreenCanvas:Fill", "color"));
                    return LuaValue.NONE;
                }
            });
            canvas.set("DrawText", new VarArgFunction() {
                @Override public Varargs invoke(Varargs args) {
                    drawing.text(nativeText(args.arg(2)),
                        MLuaLuaArguments.wholeInt(args, 3, "ScreenCanvas:DrawText", "x"),
                        MLuaLuaArguments.wholeInt(args, 4, "ScreenCanvas:DrawText", "y"),
                        optionalColor(args.arg(5), 0xFFFFFF, "ScreenCanvas:DrawText", "color"),
                        args.arg(6).optboolean(true));
                    return LuaValue.NONE;
                }
            });
            LuaValue[] arguments = !classLifecycle
                ? new LuaValue[]{canvas, LuaValue.valueOf(mouseX), LuaValue.valueOf(mouseY), LuaValue.valueOf(delta)}
                : new LuaValue[]{this.luaInstance, canvas, LuaValue.valueOf(mouseX), LuaValue.valueOf(mouseY), LuaValue.valueOf(delta)};
            try { this.invokeLua(callback, LuaValue.varargsOf(arguments)); }
            catch (RuntimeException failure) {
                this.renderFailed = true;
                MLuaLogger.LOGGER.error("Lua native screen render callback failed; further render callbacks are paused for this screen", failure);
            }
        }

        private void dispose() {
            this.disposed = true;
            this.textLines.clear();
            this.state.scriptScreens.remove(this);
            if (MLuaGuiService.currentScreen() == this) Minecraft.getInstance().setScreen(null);
        }

        private Varargs invokeLua(LuaValue function, Varargs arguments) {
            return MLuaLuaBindings.withContext(this.context, () -> function.invoke(arguments));
        }

        @Override
        //--version 26.1
        public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
            this.extractBackground(graphics, mouseX, mouseY, delta);
            super.extractRenderState(graphics, mouseX, mouseY, delta);
            for (ScreenText line : this.textLines) drawHudText(graphics, line.text(), line.x(), line.y(), line.color(), true);
            this.renderLua(new ScreenDrawing() {
                @Override public void fill(int left, int top, int right, int bottom, int color) { graphics.fill(left, top, right, bottom, color); }
                @Override public void text(Object text, int x, int y, int color, boolean shadow) { drawHudText(graphics, text, x, y, color, shadow); }
            }, mouseX, mouseY, delta);
        }
        //--endversion

        //--version 1.20.1
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
            this.renderBackground(graphics);
            super.render(graphics, mouseX, mouseY, delta);
            for (ScreenText line : this.textLines) drawHudText(graphics, line.text(), line.x(), line.y(), line.color(), true);
            this.renderLua(new ScreenDrawing() {
                @Override public void fill(int left, int top, int right, int bottom, int color) { graphics.fill(left, top, right, bottom, color); }
                @Override public void text(Object text, int x, int y, int color, boolean shadow) { drawHudText(graphics, text, x, y, color, shadow); }
            }, mouseX, mouseY, delta);
        }
        //--endversion

        //--version 1.21.1
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
            this.renderBackground(graphics, mouseX, mouseY, delta);
            super.render(graphics, mouseX, mouseY, delta);
            for (ScreenText line : this.textLines) drawHudText(graphics, line.text(), line.x(), line.y(), line.color(), true);
            this.renderLua(new ScreenDrawing() {
                @Override public void fill(int left, int top, int right, int bottom, int color) { graphics.fill(left, top, right, bottom, color); }
                @Override public void text(Object text, int x, int y, int color, boolean shadow) { drawHudText(graphics, text, x, y, color, shadow); }
            }, mouseX, mouseY, delta);
        }
        //--endversion
    }

    private static final class GuiState {
        private final List<ScreenGui> screens = new CopyOnWriteArrayList<>();
        private final List<MLuaScriptScreen> scriptScreens = new CopyOnWriteArrayList<>();
        private final Map<String, HudElement> hudElements = new ConcurrentHashMap<>();
        private final Set<CoreGuiType> disabledTypes = EnumSet.noneOf(CoreGuiType.class);
        private final List<NativeLayout> nativeLayouts = new CopyOnWriteArrayList<>();
        private final Map<String, ScreenTarget> screenTargets = new ConcurrentHashMap<>();
        private final MLuaSignal screenOpened = new MLuaSignal();
        private final MLuaSignal chatLineAdding = new MLuaSignal();
        private volatile Object executionLock = this;
        private volatile boolean enabled = true;

        private void clear() {
            this.hudElements.clear();
            for (MLuaScriptScreen screen : List.copyOf(this.scriptScreens)) screen.dispose();
            List<NativeLayout> layouts = List.copyOf(this.nativeLayouts);
            for (int index = layouts.size() - 1; index >= 0; index--) {
                NativeLayout layout = layouts.get(index);
                layout.restore();
                if (layout.replacement() instanceof GuiButton button) synchronized (REPLACEMENT_ACTIONS) { REPLACEMENT_ACTIONS.remove(button); }
            }
            for (ScreenGui screen : this.screens) screen.destroy();
            this.screens.clear();
            this.disabledTypes.clear();
            this.enabled = true;
            this.nativeLayouts.clear();
            this.screenTargets.clear();
            this.screenOpened.Destroy();
            this.chatLineAdding.Destroy();
        }
    }

    private static final class HudElement {
        private final String id;
        private final String owner;
        private final String anchor;
        private final boolean before;
        private final String position;
        private final int priority;
        private final LuaValue callback;
        private final long order;
        private final Object executionLock;
        private final MLuaContext context;
        private final LuaTable canvas;
        private volatile Object graphics;
        private volatile boolean failed;

        private HudElement(String id, String owner, String anchor, boolean before, String position,
                           LuaValue callback, int priority, long order, Object executionLock, MLuaContext context) {
            this.id = id;
            this.owner = owner;
            this.anchor = anchor;
            this.before = before;
            this.position = position;
            this.priority = priority;
            this.callback = callback;
            this.order = order;
            this.executionLock = executionLock;
            this.context = context;
            this.canvas = createHudCanvas(this);
        }
    }

    private record ScreenTarget(String owner, String alias, String className) { }

    private static final class NativeLayout {
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

        private NativeLayout(long id, String owner, String screen, LuaTable selector, LuaTable properties,
                GuiObject replacement, ScreenGui overlay) {
            this.id = id;
            this.owner = owner;
            this.screen = screen;
            this.selector = selector;
            this.properties = properties;
            this.replacement = replacement;
            this.overlay = overlay;
            this.before = guiObjectProperty(properties, "Before");
            this.after = guiObjectProperty(properties, "After");
        }

        private long id() { return id; }
        private String owner() { return owner; }
        private String screen() { return screen; }
        private LuaTable selector() { return selector; }
        private LuaTable properties() { return properties; }
        private GuiObject replacement() { return replacement; }
        private GuiObject before() { return before; }
        private GuiObject after() { return after; }
        private ScreenGui overlay() { return overlay; }
        private synchronized boolean failed() { return failed; }
        private synchronized void fail(Class<?> screenClass, RuntimeException failure) {
            if (failed) return;
            failed = true;
            MLuaLogger.LOGGER.warn("Disabling native UI layout {} for '{}' after it failed on screen {}: {}",
                id, screen, screenClass.getName(), failure.getMessage());
        }
        private synchronized void reportUnsupportedScreen(Class<?> screenClass, String reason) {
            if (unsupportedScreenReported) return;
            unsupportedScreenReported = true;
            MLuaLogger.LOGGER.warn("Native UI layout {} for '{}' could not inspect screen {}: {}",
                id, screen, screenClass.getName(), reason);
        }
        private void setReplacement(GuiObject replacement) { this.replacement = replacement; }
        private void setBefore(GuiObject before) { this.before = before; }
        private void setAfter(GuiObject after) { this.after = after; }
        private void setOverlay(ScreenGui overlay) { this.overlay = overlay; }

        private synchronized List<Object> appliedScreens() {
            return List.copyOf(originals.keySet());
        }

        private synchronized void capture(Object screen, Object widget, LuaTable layoutProperties) {
            Map<Object, WidgetState> screenOriginals = originals.computeIfAbsent(screen,
                ignored -> new IdentityHashMap<>());
            WidgetState state = screenOriginals.computeIfAbsent(widget, value -> new WidgetState(
                readInt(value, "getX", 0), readInt(value, "getY", 0), readInt(value, "getWidth", 0),
                readInt(value, "getHeight", 0), readWidgetField(value, "visible", true),
                readWidgetText(value), new java.util.LinkedHashMap<>()));
            for (LuaValue key : layoutProperties.keys()) {
                if (key.type() != LuaValue.TSTRING) continue;
                String name = key.tojstring();
                if (Set.of("X", "Y", "Width", "Height", "Visible", "Text", "Replacement", "Before", "After")
                    .contains(name) || state.properties().containsKey(name)) continue;
                state.properties().put(name, MLuaNativeControl.readWidgetProperty(widget, name));
            }
        }

        private synchronized void restore() {
            for (Object screen : List.copyOf(originals.keySet())) restore(screen);
        }

        private synchronized void restore(Object screen) {
            Map<Object, WidgetState> screenOriginals = originals.remove(screen);
            if (screenOriginals == null) return;
            try {
                screenOriginals.forEach((widget, state) -> {
                    try {
                        if (!invokeIfPresent(widget, "setPosition", new Class<?>[]{int.class, int.class}, state.x(), state.y())) {
                            setIntField(widget, "x", state.x());
                            setIntField(widget, "y", state.y());
                        }
                        if (!invokeIfPresent(widget, "setWidth", new Class<?>[]{int.class}, state.width())) setIntField(widget, "width", state.width());
                        if (!invokeIfPresent(widget, "setHeight", new Class<?>[]{int.class}, state.height())) setIntField(widget, "height", state.height());
                        setWidgetField(widget, "visible", state.visible());
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

        private synchronized void clearSnapshot(Object screen) {
            Map<Object, WidgetState> screenOriginals = originals.remove(screen);
            if (screenOriginals != null) screenOriginals.clear();
        }
    }

    private record WidgetState(int x, int y, int width, int height, boolean visible, Object text,
                               Map<String, Object> properties) { }
}
