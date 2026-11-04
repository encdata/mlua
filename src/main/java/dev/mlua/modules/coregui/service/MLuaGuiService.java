package dev.mlua.modules.coregui.service;

import dev.mlua.common.enums.CoreGuiType;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.LuaError;
import dev.mlua.core.service.MLuaService;
import dev.mlua.core.MLuaRuntime;
import dev.mlua.core.MLuaLogger;
import dev.mlua.core.scheduler.MLuaTaskSchedulerView;
import dev.mlua.core.engine.MLuaLuaBindings;
import dev.mlua.minecraft.ui.element.GuiBase;
import dev.mlua.minecraft.ui.element.ScreenGui;
import dev.mlua.minecraft.ui.element.GuiButton;
import dev.mlua.minecraft.binding.MLuaNativeControl;
import java.util.ArrayList;
import java.util.List;
//--version 26.1
//--endversion
//--version 1.20.1,1.21.1
//--endversion

public final class MLuaGuiService implements MLuaService {
    private static final MLuaGuiRuntimeStateRegistry RUNTIME_STATES = new MLuaGuiRuntimeStateRegistry();
    final MLuaService.Context context;
    final MLuaGuiState state;
    private final MLuaGuiScreenRegistry screenRegistry;
    private final MLuaGuiNativeControlRegistry nativeControlRegistry;
    private final MLuaGuiScreenTargetRegistry screenTargetRegistry;
    private final MLuaGuiHudRegistry hudRegistry;
    private final MLuaGuiOwnedResourceCleanup ownedResourceCleanup;
    final MLuaGuiNativeLayoutController nativeLayoutController;

    public MLuaGuiService(MLuaService.Context context) {
        this.context = context;
        this.state = RUNTIME_STATES.getOrCreate(context.mlua().scheduler());
        this.screenRegistry = new MLuaGuiScreenRegistry(this, this.state);
        this.nativeControlRegistry = new MLuaGuiNativeControlRegistry(this);
        this.screenTargetRegistry = new MLuaGuiScreenTargetRegistry(this, this.state);
        this.hudRegistry = new MLuaGuiHudRegistry(this.state, context.mlua().ownerModId(), context.mlua().executionLock());
        this.nativeLayoutController = new MLuaGuiNativeLayoutController(this, this.state);
        this.ownedResourceCleanup = new MLuaGuiOwnedResourceCleanup(this.state, this.screenRegistry, this.nativeLayoutController);
        this.state.executionLock = context.mlua().executionLock();
    }

    private MLuaGuiService(MLuaGuiState state) {
        this.context = null;
        this.state = state;
        this.screenRegistry = new MLuaGuiScreenRegistry(this, this.state);
        this.nativeControlRegistry = new MLuaGuiNativeControlRegistry(this);
        this.screenTargetRegistry = new MLuaGuiScreenTargetRegistry(this, this.state);
        this.hudRegistry = new MLuaGuiHudRegistry(this.state, "runtime", this.state.executionLock);
        this.nativeLayoutController = new MLuaGuiNativeLayoutController(this, this.state);
        this.ownedResourceCleanup = new MLuaGuiOwnedResourceCleanup(this.state, this.screenRegistry, this.nativeLayoutController);
    }

    static MLuaGuiService runtimeView(MLuaGuiState state) {
        return new MLuaGuiService(state);
    }

    public static MLuaGuiService getInstance() {
        MLuaRuntime runtime = MLuaRuntime.get();
        if (runtime == null || runtime.session() == null) return null;
        return getForScheduler(runtime.session().clientScheduler());
    }

    public static MLuaGuiService getForScheduler(MLuaTaskSchedulerView scheduler) {
        if (scheduler == null) return null;
        return RUNTIME_STATES.getService(scheduler);
    }

    public static void renderHudElements(String position, Object graphics) {
        if (position == null || graphics == null) return;
        List<MLuaHudElement> elements = MLuaHudElementSnapshot.get(position, () -> {
            List<MLuaHudElement> registered = new ArrayList<>();
            for (MLuaGuiState state : RUNTIME_STATES.snapshot()) {
                if (!state.enabled) continue;
                for (MLuaHudElement element : state.hudElements.values()) {
                    if (position.equals(element.position)) registered.add(element);
                }
            }
            return registered;
        });
        MLuaHudRenderer.render(elements, graphics);
    }

    public static boolean matchesRegisteredScreenTarget(MLuaTaskSchedulerView scheduler, String target, Object screen) {
        if (scheduler == null || target == null || screen == null) return false;
        MLuaGuiState state = RUNTIME_STATES.get(scheduler);
        if (state == null) return false;
        return MLuaGuiScreenTargetRegistry.matches(state, target, screen);
    }

    public static LuaTable getNativeControls(Object screen) {
        return MLuaGuiNativeScreenLifecycle.controlsForCurrentRuntime(screen);
    }

    public static LuaTable getNativeControls(MLuaTaskSchedulerView scheduler, Object screen) {
        return MLuaGuiNativeScreenLifecycle.controlsForScheduler(scheduler, screen);
    }

    public static void clearForScheduler(MLuaTaskSchedulerView scheduler) {
        if (scheduler == null) return;
        MLuaGuiState state = RUNTIME_STATES.get(scheduler);
        if (state != null) state.clear();
    }

    public static void applyNativeLayouts(Object screen) {
        MLuaGuiNativeScreenLifecycle.applyLayouts(screen);
    }

    /** Restores and releases screen-specific native widget state when Minecraft discards a screen. */
    public static void nativeScreenRemoved(Object screen) {
        MLuaGuiNativeScreenLifecycle.removed(screen);
    }

    /** True only while synchronous ScreenClosed listeners are inspecting the removed screen. */
    public static boolean isNativeScreenInCloseCallback(Object screen) {
        return MLuaGuiNativeScreenLifecycle.isClosing(screen);
    }

    public static Object currentScreen() {
        return MLuaGuiNativeScreenLifecycle.currentScreen();
    }

    /** Rejects direct widget access off-thread; screen and widget objects are owned by the Minecraft client thread. */
    public static void requireNativeScreenClientThread(String operation) {
        MLuaGuiNativeScreenLifecycle.requireClientThread(operation);
    }

    static MLuaRuntime requireActiveClientRuntime() {
        return MLuaGuiNativeScreenLifecycle.requireActiveClientRuntime();
    }

    /** Opens script-created screens once Minecraft's client window is available. */
    public static void tickPendingNativeScreens() {
        MLuaGuiNativeScreenLifecycle.tickPendingScreens();
    }

    /** Queues a constructed screen for the client tick; Lua client scripts may run on a worker. */
    static void openNativeScreen(MLuaScriptScreen screen) {
        MLuaGuiNativeScreenLifecycle.openScreen(screen);
    }

    public static boolean isNativeScreenPending(Object screen) {
        return MLuaGuiNativeScreenLifecycle.isPending(screen);
    }

    public static boolean isNativeScreenDisposed(Object screen) {
        return MLuaGuiNativeScreenLifecycle.isDisposed(screen);
    }

    /** Closes only the currently active screen, scheduling the change when called from Lua workers. */
    public static boolean closeNativeScreen(Object expectedScreen) {
        return MLuaGuiNativeScreenLifecycle.closeScreen(expectedScreen);
    }

    public static void setNativeControlProperty(MLuaNativeControl.Backing backing, String name, LuaValue value) {
        MLuaGuiNativeScreenLifecycle.setControlProperty(backing, name, value);
    }

    public static void setNativeControlProperties(MLuaNativeControl.Backing backing, LuaTable updates) {
        MLuaGuiNativeScreenLifecycle.setControlProperties(backing, updates);
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

    public static void activateNativeReplacement(GuiButton button, double mouseX, double mouseY) {
        MLuaGuiNativeScreenLifecycle.activateReplacement(button, mouseX, mouseY);
    }

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
        return this.ownedResourceCleanup.clear(ownerId);
    }

    public void Destroy() { clear(); }

    public String name() { return "Gui"; }

    public LuaTable toLua() {
        return MLuaGuiApi.build(this);
    }

    boolean removeScreen(LuaValue target) {
        return this.screenRegistry.remove(target);
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
        return this.hudRegistry.attach(anchor, id, before, callback, priority, MLuaLuaBindings.currentContext());
    }

    boolean removeHudElement(String id) {
        if (this.context == null || id == null) return false;
        return this.hudRegistry.remove(id);
    }

}
