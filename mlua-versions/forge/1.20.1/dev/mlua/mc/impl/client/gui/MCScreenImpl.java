package dev.mlua.mc.impl.client.gui;

import dev.mlua.mcapi.client.gui.MCGuiGraphics;
import dev.mlua.mcapi.client.gui.MCRenderable;
import dev.mlua.mcapi.client.gui.MCScreen;
import net.minecraft.client.gui.screens.Screen;

import java.util.ArrayList;
import java.util.List;

public class MCScreenImpl implements MCScreen {
    private final Screen inner;

    public MCScreenImpl(Screen inner) {
        this.inner = inner;
    }

    public static MCScreenImpl fromReal(Screen inner) {
        return inner == null ? null : new MCScreenImpl(inner);
    }

    public Screen inner() {
        return inner;
    }

    @Override
    public void render(MCGuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (graphics instanceof MCGuiGraphicsImpl impl) {
            inner.render(impl.inner(), mouseX, mouseY, partialTick);
        }
    }

    @Override
    public void init() {
    }

    @Override
    public Object getTitle() {
        return inner.getTitle();
    }

    @Override
    public void onClose() {
        inner.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return inner.isPauseScreen();
    }

    @Override
    public List<MCRenderable> getWidgets() {
        return new ArrayList<>();
    }
}
