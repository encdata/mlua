package dev.mlua.mc.impl.block;

import dev.mlua.mcapi.block.MCMapColor;
import net.minecraft.world.level.material.MapColor;

public class MCMapColorImpl implements MCMapColor {
    private final MapColor inner;

    public MCMapColorImpl(MapColor inner) {
        this.inner = inner;
    }

    public static MCMapColorImpl fromReal(MapColor inner) {
        return inner == null ? null : new MCMapColorImpl(inner);
    }

    public MapColor inner() {
        return inner;
    }

    @Override
    public int getId() {
        return inner.id;
    }

    @Override
    public int getRGB() {
        return inner.col;
    }
}
