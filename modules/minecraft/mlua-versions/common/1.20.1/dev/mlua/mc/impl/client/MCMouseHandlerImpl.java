package dev.mlua.mc.impl.client;

import dev.mlua.mcapi.client.MCMouseHandler;
import net.minecraft.client.MouseHandler;

public class MCMouseHandlerImpl implements MCMouseHandler {
    private final MouseHandler inner;

    public MCMouseHandlerImpl(MouseHandler inner) {
        this.inner = inner;
    }

    public static MCMouseHandlerImpl fromReal(MouseHandler inner) {
        return inner == null ? null : new MCMouseHandlerImpl(inner);
    }

    public MouseHandler inner() {
        return inner;
    }

    @Override
    public double xpos() {
        return inner.xpos();
    }

    @Override
    public double ypos() {
        return inner.ypos();
    }

    @Override
    public boolean isLeftPressed() {
        return inner.isLeftPressed();
    }
}
