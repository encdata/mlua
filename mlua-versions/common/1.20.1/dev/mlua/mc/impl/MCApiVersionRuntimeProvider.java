package dev.mlua.mc.impl;

import dev.mlua.mcapi.MCApiRuntimeProvider;
import dev.mlua.mcapi.server.MCMinecraftServer;
import dev.mlua.mcapi.world.MCLevel;
import dev.mlua.mc.impl.item.alchemy.MCPotionImpl;
import dev.mlua.mc.impl.server.MCMinecraftServerImpl;
import dev.mlua.mc.impl.world.MCLevelImpl;
import dev.mlua.mc.impl.world.MCServerLevelImpl;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.level.Level;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Target-owned runtime adapter. Loader/version projects may replace this
 * class in their source chain when direct mappings are available.
 */
public final class MCApiVersionRuntimeProvider implements MCApiRuntimeProvider {
    @Override
    public MCMinecraftServer getServer() {
        return wrapServer(findServer());
    }

    @Override
    public MCLevel getCurrentLevel() {
        Object server = findServer();
        if (server != null) {
            Object level = invoke(server, "overworld");
            if (level == null) {
                level = first(invoke(server, "getAllLevels"));
            }
            MCLevel wrapped = wrapLevel(level);
            if (wrapped != null) {
                return wrapped;
            }
        }

        MCLevel wrapped = wrapLevel(Minecraft.getInstance().level);
        if (wrapped != null) {
            return wrapped;
        }
        throw new IllegalStateException("No active Minecraft level is available.");
    }

    @Override
    public Object lookupPotion(String name) {
        String resolved = name.contains(":") ? name : "minecraft:" + name;
        ResourceLocation id = ResourceLocation.tryParse(resolved);
        Object registryValue = id == null ? null : BuiltInRegistries.POTION.get(id);
        Potion potion = null;
        if (registryValue instanceof Potion directPotion) {
            potion = directPotion;
        } else if (registryValue instanceof java.util.Optional<?> optional && optional.isPresent()
            && optional.get() instanceof net.minecraft.core.Holder<?> holder
            && holder.value() instanceof Potion heldPotion) {
            potion = heldPotion;
        }
        if (potion == null) {
            throw new IllegalArgumentException("Unknown potion: " + resolved);
        }
        return potion;
    }

    @Override
    public Object lookupRegistry(String registry, String id) {
        Object value = registry(registry).get(ResourceLocation.tryParse(id));
        if (value instanceof java.util.Optional<?> optional) {
            if (optional.isEmpty()) return null;
            value = optional.get();
        }
        return value instanceof net.minecraft.core.Holder<?> holder ? holder.value() : value;
    }

    @Override
    public String registryKey(String registry, Object value) {
        if (value == null) {
            return null;
        }
        net.minecraft.core.Registry<?> target = registry(registry);
        for (java.lang.reflect.Method method : target.getClass().getMethods()) {
            if (!method.getName().equals("getKey") || method.getParameterCount() != 1
                || !method.getParameterTypes()[0].isInstance(value)) {
                continue;
            }
            try {
                Object key = method.invoke(target, value);
                return key == null ? null : key.toString();
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Failed to read registry key for " + registry, exception);
            }
        }
        throw new IllegalStateException("No compatible getKey method for " + registry + " value "
            + value.getClass().getName());
    }

    @Override
    public MCMinecraftServer wrapServer(Object server) {
        return server instanceof net.minecraft.server.MinecraftServer minecraftServer
            ? MCMinecraftServerImpl.fromReal(minecraftServer) : null;
    }

    @Override
    public MCLevel wrapLevel(Object level) {
        if (level instanceof ServerLevel serverLevel) {
            return new MCServerLevelImpl(serverLevel);
        }
        return level instanceof Level rawLevel ? new MCLevelImpl(rawLevel) : null;
    }

    @Override
    public Object wrapPotion(Object potion) {
        return potion instanceof Potion rawPotion ? new MCPotionImpl(rawPotion) : null;
    }

    private static Object findServer() {
        for (String hook : new String[]{
            "net.minecraftforge.server.ServerLifecycleHooks",
            "net.neoforged.neoforge.server.ServerLifecycleHooks"
        }) {
            Object server = invokeStatic(hook, "getCurrentServer");
            if (server != null) {
                return server;
            }
        }
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft == null ? null : minecraft.getSingleplayerServer();
    }

    private static net.minecraft.core.Registry<?> registry(String name) {
        return switch (name.toLowerCase(java.util.Locale.ROOT)) {
            case "item", "items" -> BuiltInRegistries.ITEM;
            case "block", "blocks" -> BuiltInRegistries.BLOCK;
            case "entity_type", "entity", "entities" -> BuiltInRegistries.ENTITY_TYPE;
            case "potion", "potions" -> BuiltInRegistries.POTION;
            default -> throw new IllegalArgumentException("Unknown registry: " + name);
        };
    }

    private static Object invokeStatic(String className, String methodName) {
        try {
            Class<?> type = Class.forName(className);
            return type.getMethod(methodName).invoke(null);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static Object invoke(Object target, String methodName) {
        try {
            return target.getClass().getMethod(methodName).invoke(target);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to invoke " + methodName, exception);
        }
    }

    private static Object first(Object value) {
        if (value instanceof Iterable<?> iterable) {
            for (Object element : iterable) {
                return element;
            }
        }
        return null;
    }
}
