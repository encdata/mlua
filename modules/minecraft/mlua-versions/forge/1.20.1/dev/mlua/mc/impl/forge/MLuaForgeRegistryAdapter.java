package dev.mlua.mc.impl.forge;

import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

public final class MLuaForgeRegistryAdapter {

    private MLuaForgeRegistryAdapter() {}

    public static DeferredRegister<Item> createItemRegister(String modId) {
        return DeferredRegister.create(ForgeRegistries.ITEMS, modId);
    }

    public static DeferredRegister<Block> createBlockRegister(String modId) {
        return DeferredRegister.create(ForgeRegistries.BLOCKS, modId);
    }

    public static DeferredRegister<EntityType<?>> createEntityTypeRegister(String modId) {
        return DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, modId);
    }

    public static DeferredRegister<ParticleType<?>> createParticleTypeRegister(String modId) {
        return DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, modId);
    }

    public static DeferredRegister<MobEffect> createMobEffectRegister(String modId) {
        return DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, modId);
    }

    public static DeferredRegister<Potion> createPotionRegister(String modId) {
        return DeferredRegister.create(ForgeRegistries.POTIONS, modId);
    }

    public static DeferredRegister<BlockEntityType<?>> createBlockEntityTypeRegister(String modId) {
        return DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, modId);
    }

    public static DeferredRegister<SoundEvent> createSoundEventRegister(String modId) {
        return DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, modId);
    }

    public static <T> DeferredRegister<T> createRegister(String modId, ResourceKey<? extends Registry<T>> registryKey) {
        return DeferredRegister.create(registryKey, modId);
    }

    public static Item getItem(ResourceLocation id) {
        return ForgeRegistries.ITEMS.getValue(id);
    }

    public static Block getBlock(ResourceLocation id) {
        return ForgeRegistries.BLOCKS.getValue(id);
    }

    public static SoundEvent getSoundEvent(ResourceLocation id) {
        return ForgeRegistries.SOUND_EVENTS.getValue(id);
    }

    public static EntityType<?> getEntityType(ResourceLocation id) {
        return ForgeRegistries.ENTITY_TYPES.getValue(id);
    }

    public static boolean hasItem(ResourceLocation id) {
        return ForgeRegistries.ITEMS.containsKey(id);
    }

    public static boolean hasBlock(ResourceLocation id) {
        return ForgeRegistries.BLOCKS.containsKey(id);
    }

    public static boolean hasEntityType(ResourceLocation id) {
        return ForgeRegistries.ENTITY_TYPES.containsKey(id);
    }
}
