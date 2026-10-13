package dev.mlua.mc.impl.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

/** 26.x direct Minecraft binding. */
public final class MCBlockPropertiesBinding {
    private MCBlockPropertiesBinding() { }
    public static Object copy(Object block) {
        return BlockBehaviour.Properties.ofFullCopy((Block) block);
    }
}
