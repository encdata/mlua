package dev.mlua.mc.impl.client.gui;

import dev.mlua.mcapi.client.gui.MCGui;
import dev.mlua.mcapi.client.gui.MCGuiGraphics;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;

public class MCGuiImpl implements MCGui {
    private final Gui inner;

    public MCGuiImpl(Gui inner) {
        this.inner = inner;
    }

    public static MCGuiImpl fromReal(Gui inner) {
        return inner == null ? null : new MCGuiImpl(inner);
    }

    public Gui inner() {
        return inner;
    }

    @Override
    public void render(MCGuiGraphics graphics, float partialTick) {
        if (graphics instanceof MCGuiGraphicsImpl impl) {
            inner.render(impl.inner(), DeltaTracker.ZERO);
        }
    }

    @Override
    public Object getTitle() {
        return net.minecraft.network.chat.Component.literal("");
    }

    @Override
    public void clear() {
        inner.clear();
    }
}
