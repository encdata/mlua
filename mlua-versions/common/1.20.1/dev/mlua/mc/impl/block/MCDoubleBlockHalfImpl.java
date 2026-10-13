package dev.mlua.mc.impl.block;

import dev.mlua.mcapi.block.MCDoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

public final class MCDoubleBlockHalfImpl {
    private MCDoubleBlockHalfImpl() {}

    public static MCDoubleBlockHalf fromReal(DoubleBlockHalf half) {
        if (half == null) return MCDoubleBlockHalf.LOWER;
        return switch (half) {
            case UPPER -> MCDoubleBlockHalf.UPPER;
            case LOWER -> MCDoubleBlockHalf.LOWER;
        };
    }

    public static DoubleBlockHalf toReal(MCDoubleBlockHalf half) {
        if (half == null) return DoubleBlockHalf.LOWER;
        return switch (half) {
            case UPPER -> DoubleBlockHalf.UPPER;
            case LOWER -> DoubleBlockHalf.LOWER;
        };
    }
}
