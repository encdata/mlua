package dev.mlua.modules.coregui.service;

import dev.mlua.common.enums.CoreGuiType;
import dev.mlua.modules.enums.MLuaEnumValue;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.LuaError;
import dev.mlua.core.service.MLuaService;
import dev.mlua.core.MLuaRuntime;
import dev.mlua.core.MLuaLogger;
import dev.mlua.core.scheduler.MLuaTaskSchedulerView;
import dev.mlua.core.engine.MLuaLuaBindings;
import dev.mlua.core.engine.MLuaLuauTypes;
import dev.mlua.common.enums.Side;
import dev.mlua.minecraft.ui.element.GuiBase;
import dev.mlua.minecraft.ui.element.GuiObject;
import dev.mlua.minecraft.ui.element.ScreenGui;
import dev.mlua.minecraft.ui.element.GuiButton;
import dev.mlua.minecraft.binding.MLuaNativeControl;
import dev.mlua.minecraft.binding.MLuaNativeScreen;
import dev.mlua.minecraft.binding.MLuaChatLine;
import dev.mlua.minecraft.binding.MLuaLuaArguments;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.network.chat.Component;
//--version 26.1
//--endversion
//--version 1.20.1,1.21.1
//--endversion

public final class MLuaGuiService implements MLuaService {
    private static final Map<MLuaTaskSchedulerView, MLuaGuiState> STATES = new WeakHashMap<>();
    final MLuaService.Context context;
    final MLuaGuiState state;
    private final MLuaGuiScreenRegistry screenRegistry;
    private final MLuaGuiNativeControlRegistry nativeControlRegistry;
    private final MLuaGuiScreenTargetRegistry screenTargetRegistry;
    final MLuaGuiNativeLayoutController nativeLayoutController;
    private static final AtomicLong NEXT_HUD_ELEMENT_ID = new AtomicLong(1);

    public MLuaGuiService(MLuaService.Context context) {
        this.context = context;
        this.state = stateFor(context.mlua().scheduler());
        this.screenRegistry = new MLuaGuiScreenRegistry(this, this.state);
        this.nativeControlRegistry = new MLuaGuiNativeControlRegistry(this);
        this.screenTargetRegistry = new MLuaGuiScreenTargetRegistry(this, this.state);
        this.nativeLayoutController = new MLuaGuiNativeLayoutController(this, this.state);
        this.state.executionLock = context.mlua().executionLock();
    }

    private MLuaGuiService(MLuaGuiState state) {
        this.context = null;
        this.state = state;
        this.screenRegistry = new MLuaGuiScreenRegistry(this, this.state);
        this.nativeControlRegistry = new MLuaGuiNativeControlRegistry(this);
        this.screenTargetRegistry = new MLuaGuiScreenTargetRegistry(this, this.state);
        this.nativeLayoutController = new MLuaGuiNativeLayoutController(this, this.state);
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
            MLuaGuiState state = STATES.get(scheduler);
            return state == null ? null : new MLuaGuiService(state);
        }
    }

    public static void renderHudElements(String position, Object graphics) {
        if (position == null || graphics == null) return;
        List<MLuaHudElement> elements = MLuaHudElementSnapshot.get(position, () -> {
            List<MLuaHudElement> registered = new ArrayList<>();
            synchronized (STATES) {
                for (MLuaGuiState state : STATES.values()) {
                    if (!state.enabled) continue;
                    for (MLuaHudElement element : state.hudElements.values()) {
                        if (position.equals(element.position)) registered.add(element);
                    }
                }
            }
            return registered;
        });
        MLuaHudRenderer.render(elements, graphics);
    }

    static Object nativeText(LuaValue value) {
        if (value.type() == LuaValue.TSTRING) return value.checkjstring();
        try {
            Object component = dev.mlua.api.MLuaLuaCoercions.javaValue(value, Component.class);
            if (component instanceof Component) return component;
        } catch (RuntimeException ignored) { }
        throw new LuaError("[MLua] Expected a string or Java Component value.");
    }

    static int readColor(LuaValue value, String method, String parameter) {
        return MLuaLuaArguments.wholeInt32Bits(value, method, parameter);
    }

    static int optionalColor(LuaValue value, int fallback, String method, String parameter) {
        return value.isnil() ? fallback : readColor(value, method, parameter);
    }

    public static boolean matchesRegisteredScreenTarget(MLuaTaskSchedulerView scheduler, String target, Object screen) {
        if (scheduler == null || target == null || screen == null) return false;
        MLuaGuiState state;
        synchronized (STATES) { state = STATES.get(scheduler); }
        if (state == null) return false;
        return MLuaGuiScreenTargetRegistry.matches(state, target, screen);
    }

    public static LuaTable getNativeControls(Object screen) {
        requireNativeScreenClientThread("Gui:GetNativeControls");
        MLuaGuiService service = getInstance();
        if (service == null) throw new LuaError("[MLua] Gui service is not active on the client");
        return service.nativeControls(LuaValue.NIL, false, screen);
    }

    public static LuaTable getNativeControls(MLuaTaskSchedulerView scheduler, Object screen) {
        requireNativeScreenClientThread("NativeScreen:GetControls");
        MLuaGuiService service = getForScheduler(scheduler);
        if (service == null) throw new LuaError("[MLua] Gui service is not active for this client runtime");
        return service.nativeControls(LuaValue.NIL, false, screen);
    }

    private static MLuaGuiState stateFor(MLuaTaskSchedulerView scheduler) {
        java.util.Objects.requireNonNull(scheduler, "scheduler");
        synchronized (STATES) {
            return STATES.computeIfAbsent(scheduler, ignored -> new MLuaGuiState());
        }
    }

    public static void clearForScheduler(MLuaTaskSchedulerView scheduler) {
        if (scheduler == null) return;
        MLuaGuiState state;
        synchronized (STATES) {
            state = STATES.get(scheduler);
        }
        if (state != null) state.clear();
    }

    public static void applyNativeLayouts(Object screen) {
        MLuaGuiService service = getInstance();
        if (service != null) {
            MLuaRuntime runtime = MLuaRuntime.get();
            service.nativeLayoutController.applyLayouts(screen);
            LuaTable event = service.nativeControls(LuaValue.NIL, false, screen);
            LuaValue screenInstance = runtime == null || runtime.session() == null ? LuaValue.NIL
                : MLuaNativeScreen.instance(runtime, screen);
            service.state.screenOpened.fireOnSide(Side.CLIENT,
                LuaValue.varargsOf(new LuaValue[]{LuaValue.valueOf(screen.getClass().getName()), event, screenInstance}));
        }
    }

    /** Restores and releases screen-specific native widget state when Minecraft discards a screen. */
    public static void nativeScreenRemoved(Object screen) {
        if (screen == null) return;
        try {
            MLuaGuiService service = getInstance();
            if (service == null) return;
            service.restoreNativeLayouts(screen);
            MLuaRuntime runtime = MLuaRuntime.get();
            if (runtime == null || runtime.session() == null || service.state.screenClosed.isEmpty()) return;
            MLuaNativeScreenManager.runCloseCallback(screen, () -> {
                LuaTable controls = service.nativeControls(LuaValue.NIL, false, screen);
                LuaValue screenInstance = MLuaNativeScreen.instance(runtime, screen);
                service.state.screenClosed.fireOnSide(Side.CLIENT,
                    LuaValue.varargsOf(new LuaValue[]{LuaValue.valueOf(screen.getClass().getName()), controls, screenInstance}));
            });
        } finally {
            MLuaNativeControl.invalidateScreen(screen);
        }
    }

    /** True only while synchronous ScreenClosed listeners are inspecting the removed screen. */
    public static boolean isNativeScreenInCloseCallback(Object screen) {
        return MLuaNativeScreenManager.isClosing(screen);
    }

    private void restoreNativeLayouts(Object screen) { this.nativeLayoutController.restoreNativeLayouts(screen); }

    public static Object currentScreen() {
        return MLuaNativeScreenManager.currentScreen();
    }

    /** Rejects direct widget access off-thread; screen and widget objects are owned by the Minecraft client thread. */
    public static void requireNativeScreenClientThread(String operation) {
        MLuaNativeScreenManager.requireClientThread(operation);
    }

    static MLuaRuntime requireActiveClientRuntime() {
        MLuaRuntime runtime = MLuaRuntime.get();
        if (runtime == null || runtime.session() == null) {
            throw new LuaError("[MLua] Gui cannot create a native screen without an active client runtime.");
        }
        return runtime;
    }

    /** Opens script-created screens once Minecraft's client window is available. */
    public static void tickPendingNativeScreens() {
        MLuaNativeScreenManager.tickPending();
    }

    /** Queues a constructed screen for the client tick; Lua client scripts may run on a worker. */
    static void openNativeScreen(MLuaScriptScreen screen) {
        MLuaNativeScreenManager.queueOpen(screen);
    }

    public static boolean isNativeScreenPending(Object screen) {
        return MLuaNativeScreenManager.isPending(screen);
    }

    public static boolean isNativeScreenDisposed(Object screen) {
        return MLuaNativeScreenManager.isDisposed(screen);
    }

    /** Closes only the currently active screen, scheduling the change when called from Lua workers. */
    public static boolean closeNativeScreen(Object expectedScreen) {
        return MLuaNativeScreenManager.close(expectedScreen);
    }

    public static void setNativeControlProperty(MLuaNativeControl.Backing backing, String name, LuaValue value) {
        MLuaGuiNativeControlProperties.setProperty(backing, name, value);
    }

    public static void setNativeControlProperties(MLuaNativeControl.Backing backing, LuaTable updates) {
        MLuaGuiNativeControlProperties.setProperties(backing, updates);
    }

    static LuaTable requireScreenUDim2(LuaValue value, String property) {
        if (!MLuaLuauTypes.isUDim2(value)) {
            throw new LuaError("[MLua] Screen " + property + " requires a UDim2 value.");
        }
        return value.checktable();
    }

    static int screenCoordinate(LuaTable dimensions, String axis, int extent) {
        LuaValue dimension = dimensions.get(axis);
        return (int) Math.round(dimension.get("Scale").todouble() * extent + dimension.get("Offset").toint());
    }

    MLuaNativeLayout findNativeLayout(String screen, int index, String owner) { return this.nativeLayoutController.findNativeLayout(screen, index, owner); }

    void syncLayoutObjects(MLuaNativeLayout layout) { this.nativeLayoutController.syncLayoutObjects(layout); }

    LuaTable nativeControls(LuaValue selector, boolean firstOnly) {
        requireNativeScreenClientThread(firstOnly ? "Gui:GetNativeControl" : "Gui:GetNativeControls");
        return nativeControls(selector, firstOnly, currentScreen());
    }

    LuaTable nativeControls(LuaValue selector, boolean firstOnly, Object screen) {
        return this.nativeControlRegistry.find(selector, firstOnly, screen);
    }

    MLuaNativeLayout addNativeLayout(String screen, LuaTable selector, LuaTable properties) { return this.nativeLayoutController.addNativeLayout(screen, selector, properties); }

    String ownerId() {
        return this.context == null ? "runtime" : this.context.mlua().ownerModId();
    }

    Object runtimeOwner() {
        if (this.context != null) return this.context.mlua().session();
        MLuaRuntime runtime = MLuaRuntime.get();
        return runtime == null ? null : runtime.session();
    }

    String registerScreenTarget(String alias, String className) {
        return this.screenTargetRegistry.register(alias, className);
    }

    boolean unregisterScreenTarget(String alias) {
        return this.screenTargetRegistry.unregister(alias);
    }

    boolean matchesRegisteredScreenTarget(String alias, Object screen) {
        return this.screenTargetRegistry.matches(alias, screen);
    }

    boolean removeNativeLayout(long id) { return this.nativeLayoutController.removeNativeLayout(id); }

    void removeNativeLayoutEntry(MLuaNativeLayout layout) { this.nativeLayoutController.removeNativeLayoutEntry(layout); }

    void recomposeLayouts(Object screen, MLuaNativeLayout changedLayout) { this.nativeLayoutController.recomposeLayouts(screen, changedLayout); }

    void recomposeTrackedScreens() { this.nativeLayoutController.recomposeTrackedScreens(); }

    private void applyLayouts(Object screen) { this.nativeLayoutController.applyLayouts(screen); }

    public static void activateNativeReplacement(GuiButton button, double mouseX, double mouseY) {
        Object nativeWidget;
        synchronized (MLuaGuiState.REPLACEMENT_ACTIONS) { nativeWidget = MLuaGuiState.REPLACEMENT_ACTIONS.get(button); }
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

    private boolean screenMatches(Object screen, String target) { return this.nativeLayoutController.screenMatches(screen, target); }



    public void addScreenGui(ScreenGui sg) { this.screenRegistry.add(sg); }
    public void removeScreenGui(ScreenGui sg) { this.screenRegistry.remove(sg); }
    public List<ScreenGui> getScreenGuis() { return this.screenRegistry.all(); }

    public ScreenGui getCoreGui(CoreGuiType type) {
        return this.screenRegistry.core(type);
    }

    public ScreenGui createCoreGui(CoreGuiType type, String name) {
        return this.screenRegistry.createCore(type, name);
    }

    public boolean isCoreGuiEnabled(CoreGuiType type) {
        return this.screenRegistry.isCoreEnabled(type);
    }

    public boolean isEnabled() { return this.state.enabled; }

    public void setEnabled(boolean enabled) {
        if (this.state.enabled == enabled) return;
        this.state.enabled = enabled;
        MLuaHudElementSnapshot.invalidate();
    }

    public void setCoreGuiEnabled(CoreGuiType type, boolean enabled) {
        this.screenRegistry.setCoreEnabled(type, enabled);
    }

    public void clear() {
        this.state.clear();
    }

    public int clearOwned(String ownerId) {
        if (ownerId == null || ownerId.isBlank()) return 0;
        int removed = 0;
        for (MLuaScriptScreen screen : List.copyOf(this.state.scriptScreens)) {
            if (!ownerId.equals(screen.ownerModId)) continue;
            screen.dispose();
            removed++;
        }
        synchronized (this.state) {
            for (Map.Entry<String, MLuaHudElement> entry : List.copyOf(this.state.hudElements.entrySet())) {
                if (ownerId.equals(entry.getValue().owner)
                    && this.state.hudElements.remove(entry.getKey(), entry.getValue())) removed++;
            }
            if (removed > 0) MLuaHudElementSnapshot.invalidate();
            boolean removedScreenTarget = false;
            for (Map.Entry<String, MLuaGuiState.ScreenTarget> entry : List.copyOf(this.state.screenTargets.entrySet())) {
                if (ownerId.equals(entry.getValue().owner())
                    && this.state.screenTargets.remove(entry.getKey(), entry.getValue())) {
                    removed++;
                    removedScreenTarget = true;
                }
            }
            if (removedScreenTarget) this.recomposeTrackedScreens();
            for (MLuaNativeLayout layout : List.copyOf(this.state.nativeLayouts)) {
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
            removed += this.screenRegistry.removeOwnedChildren(ownerId);
        }
        return removed;
    }

    public void Destroy() { clear(); }

    public String name() { return "Gui"; }

    public LuaTable toLua() {
        return MLuaGuiApi.build(this);
    }

    boolean removeScreen(LuaValue target) {
        return this.screenRegistry.remove(target);
    }

    static CoreGuiType requireCoreGuiType(LuaValue value, String method) {
        CoreGuiType type = MLuaEnumValue.toJava(value, CoreGuiType.class);
        if (type == null) throw new LuaError("[MLua] Gui:" + method + " expects an Enum.CoreGuiType value");
        return type;
    }

    static void requireNativeSelector(LuaTable selector) {
        MLuaGuiNativeControlRegistry.requireSelector(selector);
    }

    static void applyScreenProperties(ScreenGui screen, LuaTable definition) {
        screen.applyDefinitionProperties(definition);
    }

    void attachScriptOwner(GuiBase root) {
        if (this.context == null) return;
        root.setScheduler(this.context.mlua().scheduler());
        root.setRegistration(this.context.mlua().registration());
        root.setOwnerContext(this.context.mlua().ownerModId(), this.context.mlua().side(), this.context.mlua().globals());
    }

    String attachHudElement(String anchor, String id, boolean before, LuaValue callback, int priority) {
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
        MLuaHudElement element = new MLuaHudElement(key, owner, positionAnchor, before, position, callback,
            priority, NEXT_HUD_ELEMENT_ID.getAndIncrement(), this.context.mlua().executionLock(),
            MLuaLuaBindings.currentContext());
        this.state.hudElements.put(key, element);
        MLuaHudElementSnapshot.invalidate();
        return id;
    }

    boolean removeHudElement(String id) {
        if (this.context == null || id == null) return false;
        String key = this.context.mlua().ownerModId() + ":" + id;
        MLuaHudElement element = this.state.hudElements.get(key);
        if (element == null || !this.state.hudElements.remove(key, element)) return false;
        MLuaHudElementSnapshot.invalidate();
        return true;
    }

}
