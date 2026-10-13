package dev.mlua.mc.impl.block.entity;

import dev.mlua.mcapi.block.MCBlockState;
import dev.mlua.mcapi.block.entity.MCBlockEntity;
import dev.mlua.mcapi.block.entity.MCBlockEntityType;
import dev.mlua.mcapi.core.MCBlockPos;
import dev.mlua.mcapi.core.MCResourceLocation;
import dev.mlua.mc.impl.block.MCBlockStateImpl;
import dev.mlua.mc.impl.core.MCResourceLocationImpl;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;

public class MCBlockEntityTypeImpl implements MCBlockEntityType {
    private final BlockEntityType<?> inner;

    public MCBlockEntityTypeImpl(BlockEntityType<?> inner) {
        this.inner = inner;
    }

    public BlockEntityType<?> inner() {
        return inner;
    }

    @Override
    public MCResourceLocation getRegistryName() {
        var key = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(inner);
        return key == null ? null : MCResourceLocationImpl.fromReal(key);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends MCBlockEntity> T create(MCBlockPos pos, MCBlockState state) {
        var be = inner.create(new BlockPos(pos.getX(), pos.getY(), pos.getZ()),
            ((MCBlockStateImpl) state).inner());
        return (T) (be == null ? null : new MCBlockEntityImpl(be));
    }
}
