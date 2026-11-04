package dev.mlua.mc.impl.item;

import dev.mlua.mcapi.block.MCBlock;
import dev.mlua.mcapi.item.MCBlockItem;
import dev.mlua.mc.impl.block.MCBlockImpl;
import net.minecraft.world.item.BlockItem;

public class MCBlockItemImpl extends MCItemImpl implements MCBlockItem {
    private final BlockItem inner;

    public MCBlockItemImpl(BlockItem inner) {
        super(inner);
        this.inner = inner;
    }

    @Override
    public MCBlock getBlock() {
        return MCBlockImpl.fromReal(inner.getBlock());
    }
}
