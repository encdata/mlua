package dev.mlua.mc.impl.block;

import dev.mlua.mcapi.block.MCBlockBehaviour;
import net.minecraft.world.level.block.state.BlockBehaviour;

public class MCBlockBehaviourImpl implements MCBlockBehaviour {
    private final BlockBehaviour inner;
    public MCBlockBehaviourImpl(BlockBehaviour inner) { this.inner = inner; }
    public static MCBlockBehaviourImpl fromReal(BlockBehaviour inner) { return inner == null ? null : new MCBlockBehaviourImpl(inner); }
    public BlockBehaviour inner() { return inner; }
    @Override public Properties getProperties() { return Properties.of(); }
    public static BlockBehaviour.Properties toForge(Properties properties) {
        BlockBehaviour.Properties result = BlockBehaviour.Properties.of();
        result.destroyTime(properties.destroyTime()).explosionResistance(properties.explosionResistance());
        if (properties.isNoOcclusion()) result.noOcclusion();
        if (properties.isInstabreak()) result.instabreak();
        if (properties.isRandomTicks()) result.randomTicks();
        if (properties.isRequiresCorrectToolForDrops()) result.requiresCorrectToolForDrops();
        if (properties.isNoCollision()) result.noCollision();
        if (properties.lightLevel() > 0) result.lightLevel(state -> properties.lightLevel());
        return result;
    }
}
