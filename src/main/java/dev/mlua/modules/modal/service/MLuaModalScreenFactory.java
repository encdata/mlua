package dev.mlua.modules.modal.service;

import dev.mlua.core.signal.MLuaSignal;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.luaj.vm2.LuaValue;

/** Creates the native Minecraft screens used by the Modal service. */
final class MLuaModalScreenFactory {
    private MLuaModalScreenFactory() { }

    static Screen messageBox(String title, String message, List<String> buttons,
                             MLuaSignal result, Consumer<Screen> onClose) {
        final String titleText = title;
        return new ModalScreen(titleText) {
            private boolean closed;

            @Override
            protected void init() {
                super.init();
                int availableWidth = Math.max(20, this.width - (buttons.size() + 1) * 10);
                int buttonWidth = Math.max(20, Math.min(200, availableWidth / buttons.size()));
                int totalWidth = buttons.size() * buttonWidth + (buttons.size() - 1) * 10;
                int startX = (this.width - totalWidth) / 2;
                int y = this.height / 2 + 20;
                for (int i = 0; i < buttons.size(); i++) {
                    int index = i + 1;
                    this.addRenderableWidget(Button.builder(Component.literal(buttons.get(i)), button -> {
                        if (this.closed) return;
                        this.closed = true;
                        result.fire(LuaValue.valueOf(index));
                        closeCurrentScreen();
                    }).bounds(startX + i * (buttonWidth + 10), y, buttonWidth, 20).build());
                }
            }

            @Override
            public void onClose() {
                onClose.accept(this);
                if (!this.closed) {
                    this.closed = true;
                    result.fire(LuaValue.NIL);
                }
                super.onClose();
            }

            @Override
            //--version 26.1
            public void extractRenderState(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            //--endversion
            //--version 1.20.1,1.21.1
            public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            //--endversion
                this.renderBase(graphics, mouseX, mouseY, partialTick);
                this.drawCentered(graphics, titleText, this.height / 2 - 40, 0xFFFFFF);
                if (message == null || message.isEmpty()) return;
                int lineY = this.height / 2 - 20;
                for (String line : message.split("\n")) {
                    this.drawCentered(graphics, line, lineY, 0xAAAAAA);
                    lineY += 12;
                }
            }
        };
    }

    static Screen prompt(String title, String message, String defaultText,
                         MLuaSignal result, Consumer<Screen> onClose) {
        final String titleText = title;
        return new ModalScreen(titleText) {
            private EditBox textBox;
            private boolean closed;

            @Override
            protected void init() {
                super.init();
                int centerX = this.width / 2;
                int centerY = this.height / 2;
                this.textBox = new EditBox(this.font, centerX - 100, centerY - 10, 200, 20, Component.literal(""));
                this.textBox.setValue(defaultText == null ? "" : defaultText);
                this.textBox.setFocused(true);
                this.addRenderableWidget(this.textBox);
                this.setFocused(this.textBox);
                this.addRenderableWidget(Button.builder(Component.literal("OK"), button -> {
                    if (this.closed) return;
                    this.closed = true;
                    result.fire(LuaValue.valueOf(this.textBox.getValue()));
                    closeCurrentScreen();
                }).bounds(centerX - 110, centerY + 30, 100, 20).build());
                this.addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> {
                    if (this.closed) return;
                    this.closed = true;
                    result.fire(LuaValue.NIL);
                    closeCurrentScreen();
                }).bounds(centerX + 10, centerY + 30, 100, 20).build());
            }

            @Override
            //--version 26.1
            public void extractRenderState(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            //--endversion
            //--version 1.20.1,1.21.1
            public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            //--endversion
                this.renderBase(graphics, mouseX, mouseY, partialTick);
                this.drawCentered(graphics, titleText, this.height / 2 - 50, 0xFFFFFF);
                if (message != null && !message.isEmpty()) {
                    this.drawCentered(graphics, message, this.height / 2 - 30, 0xAAAAAA);
                }
            }

            @Override
            public void onClose() {
                onClose.accept(this);
                if (!this.closed) {
                    this.closed = true;
                    result.fire(LuaValue.NIL);
                }
                super.onClose();
            }
        };
    }

    static Screen confirmDialog(String title, String message,
                                MLuaSignal result, Consumer<Screen> onClose) {
        final String titleText = title;
        return new ModalScreen(titleText) {
            private boolean closed;

            @Override
            protected void init() {
                super.init();
                int centerX = this.width / 2;
                int centerY = this.height / 2;
                this.addRenderableWidget(Button.builder(Component.literal("Yes"), button -> {
                    if (this.closed) return;
                    this.closed = true;
                    result.fire(LuaValue.TRUE);
                    closeCurrentScreen();
                }).bounds(centerX - 110, centerY + 20, 100, 20).build());
                this.addRenderableWidget(Button.builder(Component.literal("No"), button -> {
                    if (this.closed) return;
                    this.closed = true;
                    result.fire(LuaValue.FALSE);
                    closeCurrentScreen();
                }).bounds(centerX + 10, centerY + 20, 100, 20).build());
            }

            @Override
            //--version 26.1
            public void extractRenderState(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            //--endversion
            //--version 1.20.1,1.21.1
            public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            //--endversion
                this.renderBase(graphics, mouseX, mouseY, partialTick);
                //--version 26.1
                this.drawCentered(graphics, titleText, this.height / 2 - 40, 0xFFFFFF);
                //--endversion
                //--version 1.20.1,1.21.1
                this.drawCentered(graphics, titleText, this.height / 2 - 40, 0xFFFFFF);
                //--endversion
                if (message == null || message.isEmpty()) return;
                int lineY = this.height / 2 - 20;
                for (String line : message.split("\n")) {
                    this.drawCentered(graphics, line, lineY, 0xAAAAAA);
                    lineY += 12;
                }
            }

            @Override
            public void onClose() {
                onClose.accept(this);
                if (!this.closed) {
                    this.closed = true;
                    result.fire(LuaValue.FALSE);
                }
                super.onClose();
            }
        };
    }

    private static void closeCurrentScreen() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null) minecraft.setScreen(null);
    }

    private abstract static class ModalScreen extends Screen {
        ModalScreen(String title) {
            super(Component.literal(title));
        }

        final void renderBase(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            //--version 26.1
            this.extractBackground(graphics, mouseX, mouseY, partialTick);
            super.extractRenderState(graphics, mouseX, mouseY, partialTick);
            //--endversion
            //--version 1.21.1
            this.renderBackground(graphics, mouseX, mouseY, partialTick);
            super.render(graphics, mouseX, mouseY, partialTick);
            //--endversion
            //--version 1.20.1
            this.renderBackground(graphics);
            super.render(graphics, mouseX, mouseY, partialTick);
            //--endversion
        }

        final void drawCentered(GuiGraphics graphics, String text, int y, int color) {
            //--version 26.1
            graphics.centeredText(this.font, text, this.width / 2, y, color);
            //--endversion
            //--version 1.20.1,1.21.1
            graphics.drawCenteredString(this.font, text, this.width / 2, y, color);
            //--endversion
        }
    }
}
