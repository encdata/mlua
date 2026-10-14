package dev.mlua.mc.impl.fabric;

import dev.mlua.loader.impl.fabric.FabricRegistryBridge;
import dev.mlua.loaderapi.RegistryHandle;
import dev.mlua.loaderapi.RegistryKind;
import dev.mlua.runtime.registry.MLuaNamedBlockItem;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;

public final class FabricRegistryWiring {
    private FabricRegistryWiring() {
    }

    public static void wire(FabricRegistryBridge bridge) {
        bridge.flush(reg -> {
            ResourceLocation id = ResourceLocation.parse(reg.modId() + ":" + reg.name());
            switch (reg.kind()) {
                case ITEM -> {
                    Item item = (Item) reg.supplier().get();
                    Registry.register(BuiltInRegistries.ITEM, id, item);
                    bridge.bindHandle(reg.kind(), reg.key(), () -> item);
                }
                case BLOCK -> {
                    Block block = (Block) reg.supplier().get();
                    Registry.register(BuiltInRegistries.BLOCK, id, block);
                    bridge.bindHandle(reg.kind(), reg.key(), () -> block);
                }
                case BLOCK_ITEM -> {
                    Object item = reg.supplier() != null
                        ? reg.supplier().get()
                        : createBlockItem(bridge, reg.modId(), reg.name());
                    if (!(item instanceof BlockItem blockItem)) {
                        throw new IllegalStateException("[MLua] Block item for '" + reg.key()
                            + "' must be a BlockItem");
                    }
                    Registry.register(BuiltInRegistries.ITEM, id, blockItem);
                    bridge.bindHandle(reg.kind(), reg.key(), () -> blockItem);
                }
                case ENTITY -> {
                    EntityType<?> entityType = (EntityType<?>) reg.supplier().get();
                    Registry.register(BuiltInRegistries.ENTITY_TYPE, id, entityType);
                    if (reg.attributes() != null) {
                        Object attributes = reg.attributes().get();
                        if (!(attributes instanceof AttributeSupplier supplier)) {
                            throw new IllegalStateException("[MLua] Entity attributes for '" + reg.key()
                                + "' must return an AttributeSupplier");
                        }
                        FabricDefaultAttributeRegistry.register((EntityType) entityType, supplier);
                    }
                    bridge.bindHandle(reg.kind(), reg.key(), () -> entityType);
                }
                case PARTICLE -> {
                    ParticleType<?> particle = (ParticleType<?>) reg.supplier().get();
                    Registry.register(BuiltInRegistries.PARTICLE_TYPE, id, particle);
                    bridge.bindHandle(reg.kind(), reg.key(), () -> particle);
                }
                case POTION -> {
                    Potion potion = (Potion) reg.supplier().get();
                    Registry.register(BuiltInRegistries.POTION, id, potion);
                    bridge.bindHandle(reg.kind(), reg.key(), () -> potion);
                }
                case MOB_EFFECT -> {
                    MobEffect effect = (MobEffect) reg.supplier().get();
                    Registry.register(BuiltInRegistries.MOB_EFFECT, id, effect);
                    bridge.bindHandle(reg.kind(), reg.key(), () -> effect);
                }
                case CREATIVE_TAB -> {
                    CreativeModeTab tab = (CreativeModeTab) reg.supplier().get();
                    Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, id, tab);
                    bridge.bindHandle(reg.kind(), reg.key(), () -> tab);
                }
            }
        });
        bridge.registerPendingVisualBlockEntityTypes();
    }

    private static BlockItem createBlockItem(FabricRegistryBridge bridge, String modId, String name) {
        RegistryHandle<Block> blockHandle = bridge.get(RegistryKind.BLOCK, modId, name);
        Block block = blockHandle == null ? null : blockHandle.get();
        if (block == null) {
            throw new IllegalStateException("[MLua] Block item '" + modId + ":" + name + "' was registered before its block.");
        }
        return new MLuaNamedBlockItem(block, new Item.Properties(), modId + ":" + name);
    }
}
