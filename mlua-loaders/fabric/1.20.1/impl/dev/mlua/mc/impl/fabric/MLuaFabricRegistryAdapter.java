package dev.mlua.mc.impl.fabric;

import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;

public final class MLuaFabricRegistryAdapter {
    private MLuaFabricRegistryAdapter() {
    }

    public static <T> T register(Registry<T> registry, ResourceLocation id, T entry) {
        return Registry.register(registry, id, entry);
    }

    public static Item registerItem(ResourceLocation id, Item item) {
        return Registry.register(BuiltInRegistries.ITEM, id, item);
    }

    public static Block registerBlock(ResourceLocation id, Block block) {
        return Registry.register(BuiltInRegistries.BLOCK, id, block);
    }

    public static EntityType<?> registerEntityType(ResourceLocation id, EntityType<?> type) {
        return Registry.register(BuiltInRegistries.ENTITY_TYPE, id, type);
    }

    public static ParticleType<?> registerParticleType(ResourceLocation id, ParticleType<?> type) {
        return Registry.register(BuiltInRegistries.PARTICLE_TYPE, id, type);
    }

    public static MobEffect registerMobEffect(ResourceLocation id, MobEffect effect) {
        return Registry.register(BuiltInRegistries.MOB_EFFECT, id, effect);
    }

    public static Potion registerPotion(ResourceLocation id, Potion potion) {
        return Registry.register(BuiltInRegistries.POTION, id, potion);
    }

    public static CreativeModeTab registerCreativeModeTab(ResourceLocation id, CreativeModeTab tab) {
        return Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, id, tab);
    }

    public static BlockEntityType<?> registerBlockEntityType(ResourceLocation id, BlockEntityType<?> type) {
        return Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, id, type);
    }

    public static SoundEvent registerSoundEvent(ResourceLocation id, SoundEvent event) {
        return Registry.register(BuiltInRegistries.SOUND_EVENT, id, event);
    }

    public static Item getItem(ResourceLocation id) {
        return BuiltInRegistries.ITEM.get(id);
    }

    public static Block getBlock(ResourceLocation id) {
        return BuiltInRegistries.BLOCK.get(id);
    }

    public static EntityType<?> getEntityType(ResourceLocation id) {
        return BuiltInRegistries.ENTITY_TYPE.get(id);
    }

    public static boolean hasItem(ResourceLocation id) {
        return BuiltInRegistries.ITEM.containsKey(id);
    }

    public static boolean hasBlock(ResourceLocation id) {
        return BuiltInRegistries.BLOCK.containsKey(id);
    }

    public static boolean hasEntityType(ResourceLocation id) {
        return BuiltInRegistries.ENTITY_TYPE.containsKey(id);
    }
}
