package dev.mlua.modules.coregui.service;

import dev.mlua.core.MLuaLogger;
import dev.mlua.minecraft.binding.MLuaNativeControl;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/** Owns native Minecraft widget creation and callback lifetime for a script screen. */
final class MLuaScriptScreenControls {
    private final MLuaScriptScreen screen;
    private final List<CallbackReference> callbacks = new CopyOnWriteArrayList<>();
    private int nextControlIndex = 1;

    MLuaScriptScreenControls(MLuaScriptScreen screen) {
        this.screen = screen;
    }

    void reset() {
        this.clear();
        this.nextControlIndex = 1;
    }

    LuaTable addButton(Object label, LuaTable position, LuaTable size, LuaValue callback) {
        int x = MLuaGuiValueParser.screenCoordinate(position, "X", this.screen.screenWidth());
        int y = MLuaGuiValueParser.screenCoordinate(position, "Y", this.screen.screenHeight());
        int width = MLuaGuiValueParser.screenCoordinate(size, "X", this.screen.screenWidth());
        int height = MLuaGuiValueParser.screenCoordinate(size, "Y", this.screen.screenHeight());
        if (width < 1 || height < 1) throw new LuaError("[MLua] Screen button dimensions must be positive.");

        int controlIndex = this.nextControlIndex++;
        LuaTable[] controlRef = new LuaTable[1];
        CallbackReference callbackRef = this.track(callback);
        Component component = label instanceof Component styled ? styled : Component.literal((String) label);
        Button button = Button.builder(component, clicked -> {
            LuaValue activeCallback = callbackRef.get();
            if (!activeCallback.isfunction()) return;
            try {
                this.screen.invokeLua(activeCallback, LuaValue.varargsOf(new LuaValue[]{controlRef[0]}));
            } catch (RuntimeException failure) {
                callbackRef.clear();
                MLuaLogger.LOGGER.error("Lua screen button callback failed; callback disabled", failure);
            }
        }).bounds(x, y, width, height).build();
        controlRef[0] = this.screen.addNativeWidget(button, controlIndex);
        return controlRef[0];
    }

    LuaTable addEditBox(LuaTable position, LuaTable size, String initialText, LuaValue changed) {
        int x = MLuaGuiValueParser.screenCoordinate(position, "X", this.screen.screenWidth());
        int y = MLuaGuiValueParser.screenCoordinate(position, "Y", this.screen.screenHeight());
        int width = MLuaGuiValueParser.screenCoordinate(size, "X", this.screen.screenWidth());
        int height = MLuaGuiValueParser.screenCoordinate(size, "Y", this.screen.screenHeight());
        if (width < 1 || height < 1) throw new LuaError("[MLua] Screen edit box dimensions must be positive.");

        int controlIndex = this.nextControlIndex++;
        EditBox field = new EditBox(this.screen.screenFont(), x, y, width, height, Component.literal(""));
        field.setValue(initialText);
        LuaTable control = this.screen.addNativeWidget(field, controlIndex);
        if (changed.isfunction()) {
            CallbackReference callbackRef = this.track(changed);
            field.setResponder(value -> {
                LuaValue activeCallback = callbackRef.get();
                if (!activeCallback.isfunction()) return;
                try {
                    this.screen.invokeLua(activeCallback,
                        LuaValue.varargsOf(new LuaValue[]{LuaValue.valueOf(value), control}));
                } catch (RuntimeException failure) {
                    callbackRef.clear();
                    MLuaLogger.LOGGER.error("Lua screen edit box callback failed; callback disabled", failure);
                }
            });
        }
        return control;
    }

    LuaTable addRenderableWidget(LuaValue value) {
        Object widget = nativeWidget(value);
        if (!(widget instanceof AbstractWidget abstractWidget)) {
            throw new LuaError("[MLua] Screen:AddRenderableWidget expects a Minecraft AbstractWidget instance.");
        }
        return this.screen.addNativeWidget(abstractWidget, this.nextControlIndex++);
    }

    private CallbackReference track(LuaValue callback) {
        CallbackReference reference = new CallbackReference(callback);
        this.callbacks.add(reference);
        return reference;
    }

    private static Object nativeWidget(LuaValue value) {
        LuaValue wrapped = value.istable() ? value.checktable().rawget("__javaObject") : LuaValue.NIL;
        if (!wrapped.isnil() && wrapped.isuserdata()) return wrapped.touserdata();
        if (value.isuserdata()) return value.touserdata();
        throw new LuaError("[MLua] Screen:AddRenderableWidget expects a live Java widget from Java:New or a Java method.");
    }

    void clear() {
        for (CallbackReference callback : this.callbacks) callback.clear();
        this.callbacks.clear();
    }

    /** Mutable indirection lets native widgets outlive a screen without retaining Lua closures. */
    private static final class CallbackReference {
        private volatile LuaValue callback;

        private CallbackReference(LuaValue callback) {
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
