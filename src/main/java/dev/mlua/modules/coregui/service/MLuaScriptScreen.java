package dev.mlua.modules.coregui.service;

import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.lib.VarArgFunction;
import dev.mlua.core.MLuaLogger;
import dev.mlua.core.MLuaCallbackBudget;
import dev.mlua.core.engine.MLuaLuaBindings;
import dev.mlua.core.MLuaContext;
import dev.mlua.module.MLuaRegistration;
import dev.mlua.minecraft.binding.MLuaNativeControl;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.lang.ref.WeakReference;
import java.nio.file.Path;
import net.minecraft.network.chat.Component;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
//--version 26.1
import net.minecraft.client.gui.GuiGraphicsExtractor;
//--endversion
//--version 1.20.1,1.21.1
import net.minecraft.client.gui.GuiGraphics;
//--endversion

import dev.mlua.modules.coregui.service.MLuaGuiState;

final class MLuaScriptScreen extends Screen implements MLuaScriptScreenBuilder.Host {
    private LuaValue initialize;
    private LuaTable luaInstance;
    private MLuaRegistration registration;
    private String className;
    private LuaTable fields;
    final MLuaGuiState state;
    final String ownerModId;
    private final WeakReference<Object> runtimeOwner;
    private MLuaContext context;
    private final MLuaScriptScreenRenderer renderer = new MLuaScriptScreenRenderer();
    private final List<ScreenCallback> controlCallbacks = new CopyOnWriteArrayList<>();
    private final MLuaScriptScreenCallbacks classCallbacks = new MLuaScriptScreenCallbacks(this);
    private final MLuaScriptScreenInput input = new MLuaScriptScreenInput(this.classCallbacks);
    private LuaValue closed = LuaValue.NIL;
    private LuaValue tickCallback = LuaValue.NIL;
    private LuaValue renderCallback = LuaValue.NIL;
    private boolean classInitialized;
    private boolean initializationFailed;
    private volatile boolean activated;
    private volatile boolean disposed;
    private volatile boolean pendingOpen;
    private boolean closeNotified;
    private boolean closing;
    private boolean tickFailed;
    private boolean renderFailed;
    private int nextNativeControlIndex = 1;

    MLuaScriptScreen(Object title, LuaValue initialize, MLuaRegistration registration,
                             MLuaGuiState state, String ownerModId, MLuaContext context) {
        super(screenTitle(title));
        this.initialize = initialize;
        this.luaInstance = null;
        this.registration = registration;
        this.className = null;
        this.fields = null;
        this.state = state;
        this.ownerModId = ownerModId;
        this.runtimeOwner = new WeakReference<>(context == null ? null : context.session());
        this.context = context;
    }

    MLuaScriptScreen(Object title, LuaTable luaInstance, MLuaRegistration registration,
                             String className, LuaTable fields, MLuaGuiState state, String ownerModId,
                             MLuaContext context) {
        super(screenTitle(title));
        this.initialize = LuaValue.NIL;
        this.luaInstance = luaInstance;
        this.registration = registration;
        this.className = className;
        this.fields = fields;
        this.state = state;
        this.ownerModId = ownerModId;
        this.runtimeOwner = new WeakReference<>(context == null ? null : context.session());
        this.context = context;
    }

    private static Component screenTitle(Object title) {
        return title instanceof Component component ? component : Component.literal((String) title);
    }

    boolean markPendingOpen() {
        synchronized (this) {
            if (this.disposed) return false;
            this.pendingOpen = true;
            return true;
        }
    }

    boolean isPendingOpen() { return this.pendingOpen; }

    boolean isDisposed() { return this.disposed; }

    LuaTable luaInstance() { return this.luaInstance; }

    @Override protected void init() {
        super.init();
        this.pendingOpen = false;
        MLuaNativeScreenManager.forget(this);
        if (this.disposed || this.initializationFailed) return;
        this.clearControlCallbacks();
        this.renderer.clear();
        this.tickFailed = false;
        this.renderFailed = false;
        this.nextNativeControlIndex = 1;
        LuaTable builder = MLuaScriptScreenBuilder.create(this);
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
                    this.luaInstance.rawset("SetOnTick", builder.get("SetOnTick"));
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
            this.initializationFailed = true;
            this.renderer.clear();
            this.clearWidgets();
            MLuaLogger.LOGGER.error("Lua native screen initialization failed", failure);
        }
    }

    private static Object nativeWidget(LuaValue value) {
        LuaValue wrapped = value.istable() ? value.checktable().rawget("__javaObject") : LuaValue.NIL;
        if (!wrapped.isnil() && wrapped.isuserdata()) return wrapped.touserdata();
        if (value.isuserdata()) return value.touserdata();
        throw new LuaError("[MLua] Screen:AddRenderableWidget expects a live Java widget from Java:New or a Java method.");
    }

    @Override public void setOnClose(LuaValue callback) { this.closed = callback; }

    @Override public void setOnTick(LuaValue callback) {
        this.tickCallback = callback;
        this.tickFailed = false;
    }

    @Override public void setOnRender(LuaValue callback) { this.renderCallback = callback; }

    @Override public void addText(Object text, LuaTable position, int color) {
        this.renderer.addText(text, position, color, this.width, this.height);
    }

    @Override public LuaTable addButton(Object label, LuaTable position, LuaTable size, LuaValue callback) {
        return this.addNativeButton(label, position, size, callback);
    }

    @Override public LuaTable addEditBox(LuaTable position, LuaTable size, String initialText, LuaValue changed) {
        return this.addNativeEditBox(position, size, initialText, changed);
    }

    @Override public LuaTable addRenderableWidget(LuaValue value) {
        Object widget = nativeWidget(value);
        if (!(widget instanceof AbstractWidget abstractWidget)) {
            throw new LuaError("[MLua] Screen:AddRenderableWidget expects a Minecraft AbstractWidget instance.");
        }
        AbstractWidget added = this.addRenderableWidget(abstractWidget);
        int index = this.nextNativeControlIndex++;
        return MLuaNativeControl.instance(this.registration,
            new MLuaNativeControl.Backing(added, this, this.getClass().getName(), index,
                this.ownerModId, this.runtimeOwner.get()));
    }

    private LuaTable addNativeButton(Object label, LuaTable position, LuaTable size, LuaValue callback) {
        int x = MLuaGuiService.screenCoordinate(position, "X", this.width);
        int y = MLuaGuiService.screenCoordinate(position, "Y", this.height);
        int width = MLuaGuiService.screenCoordinate(size, "X", this.width);
        int height = MLuaGuiService.screenCoordinate(size, "Y", this.height);
        if (width < 1 || height < 1) throw new LuaError("[MLua] Screen button dimensions must be positive.");
        int buttonIndex = this.nextNativeControlIndex++;
        LuaTable[] controlRef = new LuaTable[1];
        ScreenCallback callbackRef = this.trackControlCallback(callback);
        Component component = label instanceof Component styled ? styled : Component.literal((String) label);
        Button button = Button.builder(component, clicked -> {
            LuaValue activeCallback = callbackRef.get();
            if (!activeCallback.isfunction()) return;
            try {
                this.invokeLua(activeCallback, LuaValue.varargsOf(new LuaValue[]{controlRef[0]}));
            }
            catch (RuntimeException failure) {
                callbackRef.clear();
                MLuaLogger.LOGGER.error("Lua screen button callback failed; callback disabled", failure);
            }
        }).bounds(x, y, width, height).build();
        this.addRenderableWidget(button);
        controlRef[0] = MLuaNativeControl.instance(this.registration,
            new MLuaNativeControl.Backing(button, this, this.getClass().getName(), buttonIndex,
                this.ownerModId, this.runtimeOwner.get()));
        return controlRef[0];
    }

    private LuaTable addNativeEditBox(LuaTable position, LuaTable size, String initialText, LuaValue changed) {
        int x = MLuaGuiService.screenCoordinate(position, "X", this.width);
        int y = MLuaGuiService.screenCoordinate(position, "Y", this.height);
        int width = MLuaGuiService.screenCoordinate(size, "X", this.width);
        int height = MLuaGuiService.screenCoordinate(size, "Y", this.height);
        if (width < 1 || height < 1) throw new LuaError("[MLua] Screen edit box dimensions must be positive.");
        int controlIndex = this.nextNativeControlIndex++;
        EditBox field = new EditBox(this.font, x, y, width, height, Component.literal(""));
        field.setValue(initialText);
        LuaTable control = MLuaNativeControl.instance(this.registration,
            new MLuaNativeControl.Backing(field, this, this.getClass().getName(), controlIndex,
                this.ownerModId, this.runtimeOwner.get()));
        if (changed.isfunction()) {
            ScreenCallback callbackRef = this.trackControlCallback(changed);
            field.setResponder(value -> {
                LuaValue activeCallback = callbackRef.get();
                if (!activeCallback.isfunction()) return;
                try { this.invokeLua(activeCallback, LuaValue.varargsOf(new LuaValue[]{LuaValue.valueOf(value), control})); }
                catch (RuntimeException failure) {
                    callbackRef.clear();
                    MLuaLogger.LOGGER.error("Lua screen edit box callback failed; callback disabled", failure);
                }
            });
        }
        this.addRenderableWidget(field);
        return control;
    }

    private void notifyClosed() {
        if (this.disposed || this.closeNotified) return;
        this.closeNotified = true;
        if (this.closed.isfunction()) {
            try { this.invokeLua(this.closed, LuaValue.NONE); }
            catch (RuntimeException failure) {
                this.closed = LuaValue.NIL;
                MLuaLogger.LOGGER.error("Lua native screen close callback failed; callback released", failure);
            }
        }
        this.classCallbacks.invokeLifecycle("OnClose");
    }

    private void releaseAfterRemoval(boolean notify) {
        if (this.disposed) return;
        this.closing = true;
        this.state.scriptScreens.remove(this);
        MLuaNativeScreenManager.forget(this);
        if (notify) this.notifyClosed();
        this.disposed = true;
        this.pendingOpen = false;
        synchronized (this.state.executionLock) {
            this.releaseLuaReferences();
        }
        this.renderer.clear();
        this.clearWidgets();
    }

    @Override public void removed() {
        if (!this.closing && !this.disposed) {
            if (this.activated) {
                this.releaseAfterRemoval(true);
            } else {
                MLuaNativeScreenManager.queueOpen(this);
            }
        }
        super.removed();
    }

    @Override public void onClose() {
        if (this.closing || this.disposed) return;
        this.closing = true;
        this.state.scriptScreens.remove(this);
        MLuaNativeScreenManager.forget(this);
        this.notifyClosed();
        super.onClose();
        this.disposed = true;
        this.pendingOpen = false;
        synchronized (this.state.executionLock) {
            this.releaseLuaReferences();
        }
        this.renderer.clear();
        this.clearWidgets();
    }

    @Override public void tick() {
        super.tick();
        if (this.disposed) return;
        this.activated = true;
        this.pendingOpen = false;
        if (this.luaInstance != null) {
            this.classCallbacks.invokeLifecycle("Tick");
        } else if (!this.tickFailed && this.tickCallback.isfunction()) {
            try { this.invokeLua(this.tickCallback, LuaValue.NONE); }
            catch (RuntimeException failure) {
                this.tickFailed = true;
                this.tickCallback = LuaValue.NIL;
                MLuaLogger.LOGGER.error("Lua native screen tick callback failed; further tick callbacks are paused for this screen", failure);
            }
        }
    }

    //--version 1.20.1,1.21.1
    @Override public void resize(Minecraft minecraft, int width, int height) {
        super.resize(minecraft, width, height);
        if (!this.disposed) {
            this.classCallbacks.invokeLifecycle("OnResize", LuaValue.valueOf(width), LuaValue.valueOf(height));
        }
    }
    //--endversion

    //--version 26.1
    @Override public void resize(int width, int height) {
        super.resize(width, height);
        if (!this.disposed) {
            this.classCallbacks.invokeLifecycle("OnResize", LuaValue.valueOf(width), LuaValue.valueOf(height));
        }
    }
    //--endversion

    @Override public boolean isPauseScreen() {
        return this.classCallbacks.isPauseScreen();
    }

    @Override public boolean shouldCloseOnEsc() {
        Boolean shouldClose = this.classCallbacks.invokeInputResult("ShouldCloseOnEsc");
        return shouldClose == null ? super.shouldCloseOnEsc() : shouldClose;
    }

    @Override public void onFilesDrop(List<Path> paths) {
        this.input.filesDrop(this.luaInstance, paths);
        super.onFilesDrop(paths);
    }

    //--version 1.20.1,1.21.1
    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.input.keyPressed(keyCode, scanCode, modifiers)) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (this.input.keyReleased(keyCode, scanCode, modifiers)) return true;
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override public boolean charTyped(char character, int modifiers) {
        if (this.input.charTyped(character, modifiers)) return true;
        return super.charTyped(character, modifiers);
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.input.mouseClicked(mouseX, mouseY, button)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (this.input.mouseReleased(mouseX, mouseY, button)) return true;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.input.mouseDragged(mouseX, mouseY, button, dragX, dragY)) return true;
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }
    //--endversion

    //--version 26.1
    @Override public boolean keyPressed(KeyEvent event) {
        if (this.input.keyPressed(event)) return true;
        return super.keyPressed(event);
    }

    @Override public boolean keyReleased(KeyEvent event) {
        if (this.input.keyReleased(event)) return true;
        return super.keyReleased(event);
    }

    @Override public boolean charTyped(CharacterEvent event) {
        if (this.input.charTyped(event)) return true;
        return super.charTyped(event);
    }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (this.input.mouseClicked(event, doubleClick)) return true;
        return super.mouseClicked(event, doubleClick);
    }

    @Override public boolean mouseReleased(MouseButtonEvent event) {
        if (this.input.mouseReleased(event)) return true;
        return super.mouseReleased(event);
    }

    @Override public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (this.input.mouseDragged(event, dragX, dragY)) return true;
        return super.mouseDragged(event, dragX, dragY);
    }
    //--endversion

    @Override public void mouseMoved(double mouseX, double mouseY) {
        this.input.mouseMoved(mouseX, mouseY);
        super.mouseMoved(mouseX, mouseY);
    }

    //--version 1.20.1
    @Override public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (this.input.mouseScrolled(mouseX, mouseY, delta)) return true;
        return super.mouseScrolled(mouseX, mouseY, delta);
    }
    //--endversion

    //--version 1.21.1,26.1
    @Override public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.input.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) return true;
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
    //--endversion

    private void renderLua(MLuaScriptScreenRenderer.Drawing drawing, int mouseX, int mouseY, float delta) {
        if (this.disposed || this.renderFailed) return;
        LuaValue classRender = this.luaInstance == null ? LuaValue.NIL : this.luaInstance.get("Render");
        boolean classLifecycle = classRender.isfunction();
        LuaValue callback = classLifecycle ? classRender : this.renderCallback;
        if (!callback.isfunction()) return;
        LuaTable canvas = this.renderer.createCanvas(drawing, this.width, this.height);
        LuaValue[] arguments = !classLifecycle
            ? new LuaValue[]{canvas, LuaValue.valueOf(mouseX), LuaValue.valueOf(mouseY), LuaValue.valueOf(delta)}
            : new LuaValue[]{this.luaInstance, canvas, LuaValue.valueOf(mouseX), LuaValue.valueOf(mouseY), LuaValue.valueOf(delta)};
        try { this.invokeLua(callback, LuaValue.varargsOf(arguments)); }
        catch (RuntimeException failure) {
            this.renderFailed = true;
            if (!classLifecycle) this.renderCallback = LuaValue.NIL;
            MLuaLogger.LOGGER.error("Lua native screen render callback failed; further render callbacks are paused for this screen", failure);
        }
    }

    void dispose() {
        synchronized (this) {
            if (this.disposed) return;
            this.disposed = true;
            this.pendingOpen = false;
        }
        MLuaNativeScreenManager.forget(this);
        this.renderer.clear();
        this.state.scriptScreens.remove(this);
        synchronized (this.state.executionLock) {
            this.releaseLuaReferences();
        }
        Minecraft minecraft = Minecraft.getInstance();
        Runnable cleanup = () -> {
            this.clearWidgets();
            if (MLuaNativeScreenManager.currentScreen() == this) minecraft.setScreen(null);
        };
        if (dev.mlua.runtime.client.MLuaClientThread.isClientThread()) cleanup.run();
        else minecraft.execute(cleanup);
    }

    private void releaseLuaReferences() {
        this.clearControlCallbacks();
        this.closed = LuaValue.NIL;
        this.tickCallback = LuaValue.NIL;
        this.renderCallback = LuaValue.NIL;
        this.initialize = LuaValue.NIL;
        if (this.luaInstance != null) {
            for (String name : List.of("AddText", "AddButton", "AddEditBox", "AddRenderableWidget",
                    "SetOnClose", "SetOnTick", "SetOnRender")) {
                this.luaInstance.rawset(name, LuaValue.NIL);
            }
        }
        this.luaInstance = null;
        this.registration = null;
        this.className = null;
        this.fields = null;
        this.context = null;
        this.classCallbacks.clear();
        MLuaNativeControl.invalidateScreen(this);
    }

    private ScreenCallback trackControlCallback(LuaValue callback) {
        ScreenCallback reference = new ScreenCallback(callback);
        this.controlCallbacks.add(reference);
        return reference;
    }

    private void clearControlCallbacks() {
        for (ScreenCallback callback : this.controlCallbacks) callback.clear();
        this.controlCallbacks.clear();
    }

    Varargs invokeLua(LuaValue function, Varargs arguments) {
        MLuaContext activeContext = this.context;
        if (activeContext == null || function == null || !function.isfunction()) return LuaValue.NONE;
        return MLuaLuaBindings.withContext(activeContext,
            () -> MLuaCallbackBudget.run(activeContext, () -> function.invoke(arguments)));
    }

    @Override
    //--version 26.1
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        MLuaScriptScreenRenderer.Drawing drawing = new MLuaScriptScreenRenderer.Drawing() {
            @Override public void fill(int left, int top, int right, int bottom, int color) { graphics.fill(left, top, right, bottom, color); }
            @Override public void text(Object text, int x, int y, int color, boolean shadow) { MLuaHudRenderer.drawText(graphics, text, x, y, color, shadow); }
        };
        this.renderer.drawText(drawing);
        this.renderLua(drawing, mouseX, mouseY, delta);
    }
    //--endversion

    //--version 1.20.1
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        this.renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, delta);
        MLuaScriptScreenRenderer.Drawing drawing = new MLuaScriptScreenRenderer.Drawing() {
            @Override public void fill(int left, int top, int right, int bottom, int color) { graphics.fill(left, top, right, bottom, color); }
            @Override public void text(Object text, int x, int y, int color, boolean shadow) { MLuaHudRenderer.drawText(graphics, text, x, y, color, shadow); }
        };
        this.renderer.drawText(drawing);
        this.renderLua(drawing, mouseX, mouseY, delta);
    }
    //--endversion

    //--version 1.21.1
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        this.renderBackground(graphics, mouseX, mouseY, delta);
        super.render(graphics, mouseX, mouseY, delta);
        MLuaScriptScreenRenderer.Drawing drawing = new MLuaScriptScreenRenderer.Drawing() {
            @Override public void fill(int left, int top, int right, int bottom, int color) { graphics.fill(left, top, right, bottom, color); }
            @Override public void text(Object text, int x, int y, int color, boolean shadow) { MLuaHudRenderer.drawText(graphics, text, x, y, color, shadow); }
        };
        this.renderer.drawText(drawing);
        this.renderLua(drawing, mouseX, mouseY, delta);
    }
    //--endversion

    /** Mutable indirection lets native widgets outlive a screen without retaining Lua closures. */
    private static final class ScreenCallback {
        private volatile LuaValue callback;

        private ScreenCallback(LuaValue callback) {
            this.callback = callback;
        }

        private LuaValue get() {
            LuaValue current = this.callback;
            return current == null ? LuaValue.NIL : current;
        }

        private void clear() {
            this.callback = LuaValue.NIL;
        }
    }
}
