package dev.mlua.mc.impl.block;

import dev.mlua.mcapi.block.MCBlockState;
import dev.mlua.mcapi.block.MCDoorBlock;
import dev.mlua.mcapi.core.MCBlockPos;
import dev.mlua.mcapi.world.MCLevel;
import dev.mlua.mc.impl.world.MCLevelImpl;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;

public class MCDoorBlockImpl extends MCBlockImpl implements MCDoorBlock {
    private final DoorBlock inner;

    public MCDoorBlockImpl(DoorBlock inner) {
        super(inner);
        this.inner = inner;
    }

    @Override
    public boolean isOpen(MCBlockState state) {
        return ((MCBlockStateImpl) state).inner().getValue(DoorBlock.OPEN);
    }

    @Override
    public void setOpen(MCLevel level, MCBlockState state, MCBlockPos pos, boolean open) {
        Level l = ((MCLevelImpl) level).inner();
        BlockPos bp = new BlockPos(pos.getX(), pos.getY(), pos.getZ());
        inner.setOpen(null, l, ((MCBlockStateImpl) state).inner(), bp, open);
    }
}
