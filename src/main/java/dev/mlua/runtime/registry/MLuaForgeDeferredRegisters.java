package dev.mlua.runtime.registry;

import java.util.HashMap;
import java.util.HashSet;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.Set;
import dev.mlua.minecraft.MLuaResourceLocations;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

/** Owns Forge's per-mod deferred registers and event-bus attachment lifecycle. */
final class MLuaForgeDeferredRegisters {
    private static final net.minecraft.resources.ResourceKey<net.minecraft.core.Registry<CreativeModeTab>>
            CREATIVE_MODE_TAB_REGISTRY = resolveCreativeModeTabRegistryKey();

    private final Map<String, DeferredRegister<Item>> items = new HashMap<>();
    private final Map<String, DeferredRegister<Block>> blocks = new HashMap<>();
    private final Map<String, DeferredRegister<CreativeModeTab>> tabs = new HashMap<>();
    private final Map<String, DeferredRegister<EntityType<?>>> entities = new HashMap<>();
    private final Map<String, DeferredRegister<ParticleType<?>>> particles = new HashMap<>();
    private final Map<String, DeferredRegister<MobEffect>> mobEffects = new HashMap<>();
    private final Map<String, DeferredRegister<Potion>> potions = new HashMap<>();
    private final Map<String, DeferredRegister<BlockEntityType<?>>> blockEntities = new HashMap<>();
    private final Map<String, IEventBus> attachedBuses = new HashMap<>();
    private final Set<String> attachedModIds = new HashSet<>();

    void attach(String modId, IEventBus bus) {
        attachedBuses.put(modId, bus);
        if (!attachedModIds.add(modId)) return;
        registerIfPresent(items.get(modId), bus);
        registerIfPresent(blocks.get(modId), bus);
        registerIfPresent(tabs.get(modId), bus);
        registerIfPresent(entities.get(modId), bus);
        registerIfPresent(particles.get(modId), bus);
        registerIfPresent(mobEffects.get(modId), bus);
        registerIfPresent(potions.get(modId), bus);
        registerIfPresent(blockEntities.get(modId), bus);
    }

    DeferredRegister<Item> item(String modId) {
        return items.computeIfAbsent(modId,
                id -> attachIfNeeded(id, DeferredRegister.create(ForgeRegistries.ITEMS, id)));
    }

    DeferredRegister<Block> block(String modId) {
        return blocks.computeIfAbsent(modId,
                id -> attachIfNeeded(id, DeferredRegister.create(ForgeRegistries.BLOCKS, id)));
    }

    DeferredRegister<CreativeModeTab> tab(String modId) {
        return tabs.computeIfAbsent(modId,
                id -> attachIfNeeded(id, DeferredRegister.create(CREATIVE_MODE_TAB_REGISTRY, id)));
    }

    DeferredRegister<EntityType<?>> entity(String modId) {
        return entities.computeIfAbsent(modId,
                id -> attachIfNeeded(id, DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, id)));
    }

    DeferredRegister<ParticleType<?>> particle(String modId) {
        return particles.computeIfAbsent(modId,
                id -> attachIfNeeded(id, DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, id)));
    }

    DeferredRegister<MobEffect> mobEffect(String modId) {
        return mobEffects.computeIfAbsent(modId,
                id -> attachIfNeeded(id, DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, id)));
    }

    DeferredRegister<Potion> potion(String modId) {
        return potions.computeIfAbsent(modId,
                id -> attachIfNeeded(id, DeferredRegister.create(ForgeRegistries.POTIONS, id)));
    }

    DeferredRegister<BlockEntityType<?>> blockEntity(String modId) {
        return blockEntities.computeIfAbsent(modId, id -> attachIfNeeded(id,
                DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, id)));
    }

    private <T> DeferredRegister<T> attachIfNeeded(String modId, DeferredRegister<T> register) {
        IEventBus bus = attachedBuses.get(modId);
        if (bus == null) bus = attachedBuses.get("mlua");
        if (bus != null) register.register(bus);
        return register;
    }

    private static void registerIfPresent(DeferredRegister<?> register, IEventBus bus) {
        if (register != null) register.register(bus);
    }

    private static net.minecraft.resources.ResourceKey<net.minecraft.core.Registry<CreativeModeTab>>
    resolveCreativeModeTabRegistryKey() {
        try {
            for (Field field : Registries.class.getFields()) {
                if (Modifier.isStatic(field.getModifiers())
                        && net.minecraft.resources.ResourceKey.class.isAssignableFrom(field.getType())) {
                    Object value = field.get(null);
                    if (value instanceof net.minecraft.resources.ResourceKey<?> key
                            && String.valueOf(key).contains("creative_mode_tab")) {
                        return (net.minecraft.resources.ResourceKey<net.minecraft.core.Registry<CreativeModeTab>>) key;
                    }
                }
            }
        } catch (Exception ignored) { }

        try {
            var id = MLuaResourceLocations.build("minecraft", "creative_mode_tab");
            try {
                Method create = net.minecraft.resources.ResourceKey.class
                        .getMethod("createRegistryKey", net.minecraft.resources.ResourceLocation.class);
                Object created = create.invoke(null, id);
                if (created instanceof net.minecraft.resources.ResourceKey<?> key) {
                    return (net.minecraft.resources.ResourceKey<net.minecraft.core.Registry<CreativeModeTab>>) key;
                }
            } catch (NoSuchMethodException ignored) { }

            for (Method method : net.minecraft.resources.ResourceKey.class.getMethods()) {
                if (Modifier.isStatic(method.getModifiers()) && method.getParameterCount() == 1
                        && method.getParameterTypes()[0] == net.minecraft.resources.ResourceLocation.class
                        && net.minecraft.resources.ResourceKey.class.isAssignableFrom(method.getReturnType())) {
                    Object created = method.invoke(null, id);
                    if (created instanceof net.minecraft.resources.ResourceKey<?> key) {
                        return (net.minecraft.resources.ResourceKey<net.minecraft.core.Registry<CreativeModeTab>>) key;
                    }
                }
            }
        } catch (Exception ignored) { }

        throw new IllegalStateException("[MLua] Failed to resolve creative mode tab registry key.");
    }
}
