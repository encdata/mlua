package dev.mlua.mcapi.client.gui;

public interface MCGui {
    void render(MCGuiGraphics graphics, float partialTick);
    Object getTitle();
    void clear();
}
