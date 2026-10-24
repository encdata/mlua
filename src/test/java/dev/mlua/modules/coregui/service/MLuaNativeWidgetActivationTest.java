package dev.mlua.modules.coregui.service;

import dev.mlua.minecraft.ui.element.GuiButton;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class MLuaNativeWidgetActivationTest {
    private GuiButton boundButton;

    @AfterEach
    void clearReplacementActions() {
        if (this.boundButton == null) return;
        synchronized (MLuaGuiState.REPLACEMENT_ACTIONS) {
            MLuaGuiState.REPLACEMENT_ACTIONS.remove(this.boundButton);
        }
    }

    @Test
    void prefersPressHandlerAtEachClassLevelForRepeatedActivations() {
        TestButton button = new TestButton();
        PressWidget widget = new PressWidget();
        bind(button, widget);

        MLuaNativeWidgetActivation.activate(button, 12, 34);
        MLuaNativeWidgetActivation.activate(button, 56, 78);

        assertEquals(2, widget.presses);
        assertEquals(0, widget.clicks);
    }

    @Test
    void resolvesMouseClickWhenPressHandlerIsAbsent() {
        TestButton button = new TestButton();
        ClickWidget widget = new ClickWidget();
        bind(button, widget);

        MLuaNativeWidgetActivation.activate(button, 12, 34);

        assertEquals(12, widget.mouseX);
        assertEquals(34, widget.mouseY);
    }

    @Test
    void subclassClickHandlerTakesPrecedenceOverParentPressHandler() {
        TestButton button = new TestButton();
        ChildWidget widget = new ChildWidget();
        bind(button, widget);

        MLuaNativeWidgetActivation.activate(button, 4, 9);

        assertEquals(0, widget.parentPresses);
        assertEquals(4, widget.mouseX);
        assertEquals(9, widget.mouseY);
    }

    private void bind(GuiButton button, Object widget) {
        this.boundButton = button;
        synchronized (MLuaGuiState.REPLACEMENT_ACTIONS) {
            MLuaGuiState.REPLACEMENT_ACTIONS.put(button, widget);
        }
    }

    private static final class TestButton extends GuiButton { }

    private static final class PressWidget {
        private int presses;
        private int clicks;
        private void onPress() { this.presses++; }
        private void onClick(double mouseX, double mouseY) { this.clicks++; }
    }

    private static final class ClickWidget {
        private double mouseX;
        private double mouseY;
        private void onClick(double mouseX, double mouseY) {
            this.mouseX = mouseX;
            this.mouseY = mouseY;
        }
    }

    private static class ParentWidget {
        private int parentPresses;
        private void onPress() { this.parentPresses++; }
    }

    private static final class ChildWidget extends ParentWidget {
        private double mouseX;
        private double mouseY;
        private void onClick(double mouseX, double mouseY) {
            this.mouseX = mouseX;
            this.mouseY = mouseY;
        }
    }
}
