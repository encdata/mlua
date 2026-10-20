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
import dev.mlua.minecraft.binding.MLuaLuaArguments;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;
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
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.MouseButtonEvent;
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
    private final List<ScreenText> textLines = new CopyOnWriteArrayList<>();
    private final List<ScreenCallback> controlCallbacks = new CopyOnWriteArrayList<>();
    private final Set<String> failedInputMethods = ConcurrentHashMap.newKeySet();
    private final Set<String> failedLifecycleMethods = ConcurrentHashMap.newKeySet();
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

    @Override protected void init() {
        super.init();
        this.pendingOpen = false;
        MLuaNativeScreenManager.forget(this);
        if (this.disposed || this.initializationFailed) return;
        this.clearControlCallbacks();
        this.textLines.clear();
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
            this.textLines.clear();
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
        this.textLines.add(new ScreenText(text,
            MLuaGuiService.screenCoordinate(position, "X", this.width),
            MLuaGuiService.screenCoordinate(position, "Y", this.height), color));
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
        this.invokeClassMethod("OnClose");
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
        this.textLines.clear();
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
        this.textLines.clear();
        this.clearWidgets();
    }

    @Override public void tick() {
        super.tick();
        if (this.disposed) return;
        this.activated = true;
        this.pendingOpen = false;
        if (this.luaInstance != null) {
            this.invokeClassMethod("Tick");
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
            return optionalBooleanResult(result, "IsPauseScreen", true);
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
        Boolean shouldClose = this.invokeClassInputMethodResult("ShouldCloseOnEsc");
        return shouldClose == null ? super.shouldCloseOnEsc() : shouldClose;
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
        if (this.luaInstance == null || this.failedLifecycleMethods.contains(methodName)) return;
        LuaValue method = this.luaInstance.get(methodName);
        if (!method.isfunction()) return;
        if (parameters.length == 0) {
            try { this.invokeLua(method, LuaValue.varargsOf(new LuaValue[]{this.luaInstance})); }
            catch (RuntimeException failure) {
                this.failedLifecycleMethods.add(methodName);
                MLuaLogger.LOGGER.error("Lua screen lifecycle method '{}' failed", methodName, failure);
            }
            return;
        }
        LuaValue[] arguments = new LuaValue[parameters.length + 1];
        arguments[0] = this.luaInstance;
        System.arraycopy(parameters, 0, arguments, 1, parameters.length);
        try { this.invokeLua(method, LuaValue.varargsOf(arguments)); }
        catch (RuntimeException failure) {
            this.failedLifecycleMethods.add(methodName);
            MLuaLogger.LOGGER.error("Lua screen lifecycle method '{}' failed", methodName, failure);
        }
    }

    private boolean invokeClassInputMethod(String methodName, LuaValue... parameters) {
        return Boolean.TRUE.equals(this.invokeClassInputMethodResult(methodName, parameters));
    }

    /** Returns null when Minecraft should handle the input normally. */
    private Boolean invokeClassInputMethodResult(String methodName, LuaValue... parameters) {
        if (this.disposed || this.luaInstance == null || this.failedInputMethods.contains(methodName)) return null;
        LuaValue method = this.luaInstance.get(methodName);
        if (!method.isfunction()) return null;
        LuaValue[] arguments = new LuaValue[parameters.length + 1];
        arguments[0] = this.luaInstance;
        System.arraycopy(parameters, 0, arguments, 1, parameters.length);
        try {
            LuaValue result = this.invokeLua(method, LuaValue.varargsOf(arguments)).arg1();
            return result.isnil() ? null : optionalBooleanResult(result, methodName, false);
        }
        catch (RuntimeException failure) {
            this.failedInputMethods.add(methodName);
            MLuaLogger.LOGGER.error("Lua screen input method '{}' failed", methodName, failure);
            return null;
        }
    }

    private static boolean optionalBooleanResult(LuaValue result, String methodName, boolean nilValue) {
        if (result.isnil()) return nilValue;
        if (!result.isboolean()) {
            throw new LuaError("[MLua] Screen:" + methodName + " must return true, false, or nil.");
        }
        return result.toboolean();
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
                    MLuaGuiService.readColor(args.arg(6), "ScreenCanvas:Fill", "color"));
                return LuaValue.NONE;
            }
        });
        canvas.set("DrawText", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                drawing.text(MLuaGuiService.nativeText(args.arg(2)),
                    MLuaLuaArguments.wholeInt(args, 3, "ScreenCanvas:DrawText", "x"),
                    MLuaLuaArguments.wholeInt(args, 4, "ScreenCanvas:DrawText", "y"),
                    MLuaGuiService.optionalColor(args.arg(5), 0xFFFFFF, "ScreenCanvas:DrawText", "color"),
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
        this.textLines.clear();
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
        this.failedInputMethods.clear();
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

    private Varargs invokeLua(LuaValue function, Varargs arguments) {
        MLuaContext activeContext = this.context;
        if (activeContext == null || function == null || !function.isfunction()) return LuaValue.NONE;
        return MLuaLuaBindings.withContext(activeContext,
            () -> MLuaCallbackBudget.run(activeContext, () -> function.invoke(arguments)));
    }

    @Override
    //--version 26.1
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        for (ScreenText line : this.textLines) MLuaHudRenderer.drawText(graphics, line.text(), line.x(), line.y(), line.color(), true);
        this.renderLua(new ScreenDrawing() {
            @Override public void fill(int left, int top, int right, int bottom, int color) { graphics.fill(left, top, right, bottom, color); }
            @Override public void text(Object text, int x, int y, int color, boolean shadow) { MLuaHudRenderer.drawText(graphics, text, x, y, color, shadow); }
        }, mouseX, mouseY, delta);
    }
    //--endversion

    //--version 1.20.1
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        this.renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, delta);
        for (ScreenText line : this.textLines) MLuaHudRenderer.drawText(graphics, line.text(), line.x(), line.y(), line.color(), true);
        this.renderLua(new ScreenDrawing() {
            @Override public void fill(int left, int top, int right, int bottom, int color) { graphics.fill(left, top, right, bottom, color); }
            @Override public void text(Object text, int x, int y, int color, boolean shadow) { MLuaHudRenderer.drawText(graphics, text, x, y, color, shadow); }
        }, mouseX, mouseY, delta);
    }
    //--endversion

    //--version 1.21.1
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        this.renderBackground(graphics, mouseX, mouseY, delta);
        super.render(graphics, mouseX, mouseY, delta);
        for (ScreenText line : this.textLines) MLuaHudRenderer.drawText(graphics, line.text(), line.x(), line.y(), line.color(), true);
        this.renderLua(new ScreenDrawing() {
            @Override public void fill(int left, int top, int right, int bottom, int color) { graphics.fill(left, top, right, bottom, color); }
            @Override public void text(Object text, int x, int y, int color, boolean shadow) { MLuaHudRenderer.drawText(graphics, text, x, y, color, shadow); }
        }, mouseX, mouseY, delta);
    }
    //--endversion

    private interface ScreenDrawing {
        void fill(int left, int top, int right, int bottom, int color);
        void text(Object text, int x, int y, int color, boolean shadow);
    }

    private record ScreenText(Object text, int x, int y, int color) { }

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
