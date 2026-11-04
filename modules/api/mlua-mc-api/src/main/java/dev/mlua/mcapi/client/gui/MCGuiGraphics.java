package dev.mlua.mcapi.client.gui;

public interface MCGuiGraphics {
    void drawString(Object font, Object text, int x, int y, int color);
    void fill(int x1, int y1, int x2, int y2, int color);
    void blit(Object texture, int x, int y, int width, int height);
    void blit(Object texture, int x, int y, float uOffset, float vOffset, int width, int height, int textureWidth, int textureHeight);
    int guiWidth();
    int guiHeight();
    Object pose();
}
