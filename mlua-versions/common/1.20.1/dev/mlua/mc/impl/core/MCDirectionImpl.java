package dev.mlua.mc.impl.core;

import dev.mlua.mcapi.core.MCDirection;
import net.minecraft.core.Direction;
import java.util.EnumMap;

public final class MCDirectionImpl implements MCDirection {
    private static final EnumMap<Direction, MCDirectionImpl> CACHE = new EnumMap<>(Direction.class);

    public static final MCDirectionImpl NORTH = new MCDirectionImpl(Direction.NORTH);
    public static final MCDirectionImpl SOUTH = new MCDirectionImpl(Direction.SOUTH);
    public static final MCDirectionImpl EAST = new MCDirectionImpl(Direction.EAST);
    public static final MCDirectionImpl WEST = new MCDirectionImpl(Direction.WEST);
    public static final MCDirectionImpl UP = new MCDirectionImpl(Direction.UP);
    public static final MCDirectionImpl DOWN = new MCDirectionImpl(Direction.DOWN);

    private final Direction inner;

    private MCDirectionImpl(Direction inner) {
        this.inner = inner;
        CACHE.put(inner, this);
    }

    public static MCDirectionImpl fromReal(Direction inner) {
        if (inner == null) return null;
        return CACHE.computeIfAbsent(inner, MCDirectionImpl::new);
    }

    @Override
    public String getName() {
        return inner.getName();
    }

    @Override
    public MCDirection getOpposite() {
        return fromReal(inner.getOpposite());
    }

    @Override
    public String toString() {
        return "MCDirection." + inner.getName().toUpperCase();
    }
}
