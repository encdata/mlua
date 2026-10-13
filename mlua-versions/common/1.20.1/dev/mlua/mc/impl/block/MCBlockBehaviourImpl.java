package dev.mlua.mc.impl.block;

import dev.mlua.mcapi.block.MCBlockBehaviour;
import net.minecraft.world.level.block.state.BlockBehaviour;

public class MCBlockBehaviourImpl implements MCBlockBehaviour {
    private final BlockBehaviour inner;

    public MCBlockBehaviourImpl(BlockBehaviour inner) {
        this.inner = inner;
    }

    public static MCBlockBehaviourImpl fromReal(BlockBehaviour inner) {
        return inner == null ? null : new MCBlockBehaviourImpl(inner);
    }

    public BlockBehaviour inner() {
        return inner;
    }

    @Override
    public Properties getProperties() {
        return Properties.of();
    }

    public static BlockBehaviour.Properties toForge(Properties properties) {
        BlockBehaviour.Properties bbp = BlockBehaviour.Properties.of();
        bbp.destroyTime(properties.destroyTime());
        bbp.explosionResistance(properties.explosionResistance());
        if (properties.sound() != null) {
            bbp.sound(((MCSoundTypeImpl) properties.sound()).inner());
        }
        if (properties.mapColor() != null) {
            bbp.mapColor(((MCMapColorImpl) properties.mapColor()).inner());
        }
        if (properties.isNoOcclusion()) {
            bbp.noOcclusion();
        }
        if (properties.isInstabreak()) {
            bbp.instabreak();
        }
        if (properties.isRandomTicks()) {
            bbp.randomTicks();
        }
        if (properties.isRequiresCorrectToolForDrops()) {
            bbp.requiresCorrectToolForDrops();
        }
        if (properties.isNoCollision()) {
            bbp.noCollission();
        }
        if (properties.lightLevel() > 0) {
            bbp.lightLevel(state -> properties.lightLevel());
        }
        if (properties.friction() != 0.6f) {
            bbp.friction(properties.friction());
        }
        if (properties.speedFactor() != 1.0f) {
            bbp.speedFactor(properties.speedFactor());
        }
        if (properties.jumpFactor() != 1.0f) {
            bbp.jumpFactor(properties.jumpFactor());
        }
        return bbp;
    }
}
