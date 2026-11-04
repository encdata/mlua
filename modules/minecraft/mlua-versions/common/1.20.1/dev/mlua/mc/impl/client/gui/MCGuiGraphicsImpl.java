package dev.mlua.mc.impl.client.gui;

import dev.mlua.mcapi.client.gui.MCGuiGraphics;
import net.minecraft.client.gui.GuiGraphics;

public class MCGuiGraphicsImpl implements MCGuiGraphics {
    private final GuiGraphics inner;

    public MCGuiGraphicsImpl(GuiGraphics inner) {
        this.inner = inner;
    }

    public static MCGuiGraphicsImpl fromReal(GuiGraphics inner) {
        return inner == null ? null : new MCGuiGraphicsImpl(inner);
    }

    public GuiGraphics inner() {
        return inner;
    }

    @Override
    public void drawString(Object font, Object text, int x, int y, int color) {
        if (font instanceof net.minecraft.client.gui.Font f && text instanceof net.minecraft.network.chat.Component c) {
            inner.drawString(f, c, x, y, color);
        }
    }

    @Override
    public void fill(int x1, int y1, int x2, int y2, int color) {
        inner.fill(x1, y1, x2, y2, color);
    }

    @Override
    public void blit(Object texture, int x, int y, int width, int height) {
        if (texture instanceof net.minecraft.resources.ResourceLocation rl) {
            inner.blit(rl, x, y, 0, 0.0F, 0.0F, width, height, width, height);
        }
    }

    @Override
    public void blit(Object texture, int x, int y, float uOffset, float vOffset, int width, int height, int textureWidth, int textureHeight) {
        if (texture instanceof net.minecraft.resources.ResourceLocation rl) {
            inner.blit(rl, x, y, uOffset, vOffset, width, height, textureWidth, textureHeight);
        }
    }

    @Override
    public int guiWidth() {
        return inner.guiWidth();
    }

    @Override
    public int guiHeight() {
        return inner.guiHeight();
    }

    @Override
    public Object pose() {
        return inner.pose();
    }
}
