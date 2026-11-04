package dev.mlua.modules.coregui.service;

import java.nio.file.Path;
import java.util.List;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
//--version 26.1
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.MouseButtonEvent;
//--endversion

/** Translates each supported Minecraft input signature into the stable Lua screen callback contract. */
final class MLuaScriptScreenInput {
    private final MLuaScriptScreenCallbacks callbacks;

    MLuaScriptScreenInput(MLuaScriptScreenCallbacks callbacks) {
        this.callbacks = callbacks;
    }

    void filesDrop(LuaTable instance, List<Path> paths) {
        if (instance == null || paths == null) return;
        LuaTable droppedPaths = new LuaTable();
        for (int index = 0; index < paths.size(); index++) {
            droppedPaths.set(index + 1, LuaValue.valueOf(paths.get(index).toString()));
        }
        this.callbacks.invokeInputMethod("OnFilesDrop", droppedPaths);
    }

    //--version 1.20.1,1.21.1
    boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return this.callbacks.invokeInputMethod("KeyPressed", LuaValue.valueOf(keyCode),
            LuaValue.valueOf(scanCode), LuaValue.valueOf(modifiers));
    }

    boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        return this.callbacks.invokeInputMethod("KeyReleased", LuaValue.valueOf(keyCode),
            LuaValue.valueOf(scanCode), LuaValue.valueOf(modifiers));
    }

    boolean charTyped(char character, int modifiers) {
        return this.callbacks.invokeInputMethod("CharTyped", LuaValue.valueOf(String.valueOf(character)),
            LuaValue.valueOf(modifiers));
    }

    boolean mouseClicked(double mouseX, double mouseY, int button) {
        return this.callbacks.invokeInputMethod("MouseClicked", LuaValue.valueOf(mouseX), LuaValue.valueOf(mouseY),
            LuaValue.valueOf(button), LuaValue.FALSE);
    }

    boolean mouseReleased(double mouseX, double mouseY, int button) {
        return this.callbacks.invokeInputMethod("MouseReleased", LuaValue.valueOf(mouseX), LuaValue.valueOf(mouseY),
            LuaValue.valueOf(button));
    }

    boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return this.callbacks.invokeInputMethod("MouseDragged", LuaValue.valueOf(mouseX), LuaValue.valueOf(mouseY),
            LuaValue.valueOf(button), LuaValue.valueOf(dragX), LuaValue.valueOf(dragY));
    }
    //--endversion

    //--version 26.1
    boolean keyPressed(KeyEvent event) {
        return this.callbacks.invokeInputMethod("KeyPressed", LuaValue.valueOf(event.key()),
            LuaValue.valueOf(event.scancode()), LuaValue.valueOf(event.modifiers()));
    }

    boolean keyReleased(KeyEvent event) {
        return this.callbacks.invokeInputMethod("KeyReleased", LuaValue.valueOf(event.key()),
            LuaValue.valueOf(event.scancode()), LuaValue.valueOf(event.modifiers()));
    }

    boolean charTyped(CharacterEvent event) {
        return this.callbacks.invokeInputMethod("CharTyped", LuaValue.valueOf(event.codepointAsString()),
            LuaValue.ZERO);
    }

    boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return this.callbacks.invokeInputMethod("MouseClicked", LuaValue.valueOf(event.x()), LuaValue.valueOf(event.y()),
            LuaValue.valueOf(event.button()), LuaValue.valueOf(doubleClick));
    }

    boolean mouseReleased(MouseButtonEvent event) {
        return this.callbacks.invokeInputMethod("MouseReleased", LuaValue.valueOf(event.x()), LuaValue.valueOf(event.y()),
            LuaValue.valueOf(event.button()));
    }

    boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        return this.callbacks.invokeInputMethod("MouseDragged", LuaValue.valueOf(event.x()), LuaValue.valueOf(event.y()),
            LuaValue.valueOf(event.button()), LuaValue.valueOf(dragX), LuaValue.valueOf(dragY));
    }
    //--endversion

    void mouseMoved(double mouseX, double mouseY) {
        this.callbacks.invokeInputMethod("MouseMoved", LuaValue.valueOf(mouseX), LuaValue.valueOf(mouseY));
    }

    //--version 1.20.1
    boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        return this.callbacks.invokeInputMethod("MouseScrolled", LuaValue.valueOf(mouseX), LuaValue.valueOf(mouseY),
            LuaValue.ZERO, LuaValue.valueOf(delta));
    }
    //--endversion

    //--version 1.21.1,26.1
    boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return this.callbacks.invokeInputMethod("MouseScrolled", LuaValue.valueOf(mouseX), LuaValue.valueOf(mouseY),
            LuaValue.valueOf(scrollX), LuaValue.valueOf(scrollY));
    }
    //--endversion
}
