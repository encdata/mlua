package dev.mlua.mc.impl.client.gui;

import dev.mlua.mcapi.client.gui.MCGuiGraphics;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

public class MCGuiGraphicsImpl implements MCGuiGraphics {
    private final GuiGraphicsExtractor inner;
    public MCGuiGraphicsImpl(GuiGraphicsExtractor inner) { this.inner = inner; }
    public static MCGuiGraphicsImpl fromReal(GuiGraphicsExtractor inner) { return inner == null ? null : new MCGuiGraphicsImpl(inner); }
    public GuiGraphicsExtractor inner() { return inner; }
    @Override public void drawString(Object font, Object text, int x, int y, int color) { if (font instanceof net.minecraft.client.gui.Font f && text instanceof net.minecraft.network.chat.Component c) inner.text(f, c, x, y, color); }
    @Override public void fill(int x1, int y1, int x2, int y2, int color) { inner.fill(x1, y1, x2, y2, color); }
    @Override public void blit(Object texture, int x, int y, int width, int height) { if (texture instanceof Identifier id) inner.blit(id, x, y, width, height, 0, 0, width, height); }
    @Override public void blit(Object texture, int x, int y, float uOffset, float vOffset, int width, int height, int textureWidth, int textureHeight) { if (texture instanceof Identifier id) inner.blit(id, x, y, width, height, uOffset, vOffset, textureWidth, textureHeight); }
    @Override public int guiWidth() { return inner.guiWidth(); }
    @Override public int guiHeight() { return inner.guiHeight(); }
    @Override public Object pose() { return inner.pose(); }
}
