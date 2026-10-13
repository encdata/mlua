package dev.mlua.runtime.registry;

import dev.mlua.minecraft.MLuaResourceLocations;
import dev.mlua.runtime.client.MLuaVisualBlockEntity;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;

/** Dedicated 26.1 NeoForge block-property adapter. */
public final class MLuaBlockFactory {
    public Block create(Map<String, Object> properties, String registryName, Supplier<BlockEntityType<MLuaVisualBlockEntity>> type) { return new Block(makeProperties(properties)); }
    public BlockBehaviour.Properties makeProperties(Map<String, Object> properties) {
        BlockBehaviour.Properties p = copy(properties);
        Object strength = properties.get("strength");
        Object resistance = properties.get("explosionResistance");
        if (strength instanceof Number s && resistance instanceof Number r) p.strength(s.floatValue(), r.floatValue());
        else if (strength instanceof Number s) p.strength(s.floatValue());
        if (Boolean.FALSE.equals(properties.get("collision")) || Boolean.TRUE.equals(properties.get("noCollision"))) p.noCollision();
        if (Boolean.TRUE.equals(properties.get("noOcclusion"))) p.noOcclusion();
        if (Boolean.TRUE.equals(properties.get("randomTicks"))) p.randomTicks();
        if (properties.get("lightLevel") instanceof Number l) p.lightLevel(s -> Math.max(0, Math.min(15, l.intValue())));
        return p;
    }
    private BlockBehaviour.Properties copy(Map<String, Object> properties) {
        Object id = properties.get("copy");
        if (id instanceof String name) { Block block = BuiltInRegistries.BLOCK.getOptional(MLuaResourceLocations.parse(name)).orElse(null); if (block != null) return BlockBehaviour.Properties.ofFullCopy(block); }
        return BlockBehaviour.Properties.ofFullCopy(Blocks.STONE);
    }
}
