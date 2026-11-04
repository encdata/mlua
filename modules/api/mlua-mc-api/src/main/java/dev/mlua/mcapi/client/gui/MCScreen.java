package dev.mlua.mcapi.client.gui;

import java.util.List;

public interface MCScreen {
    void render(MCGuiGraphics graphics, int mouseX, int mouseY, float partialTick);
    void init();
    Object getTitle();
    void onClose();
    boolean isPauseScreen();
    List<MCRenderable> getWidgets();
}
