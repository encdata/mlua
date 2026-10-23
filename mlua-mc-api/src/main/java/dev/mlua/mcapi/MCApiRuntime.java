package dev.mlua.mcapi;

import dev.mlua.mcapi.server.MCMinecraftServer;
import dev.mlua.mcapi.world.MCLevel;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class MCApiRuntime {
    private static volatile MCApiRuntimeProvider provider = new ReflectiveProvider();
    private static final ClassValue<java.util.Optional<Method>> HOLDER_VALUE_ACCESSORS = new ClassValue<>() {
        @Override
        protected java.util.Optional<Method> computeValue(Class<?> type) {
            try {
                return java.util.Optional.of(type.getMethod("value"));
            } catch (NoSuchMethodException ignored) {
                return java.util.Optional.empty();
            }
        }
    };

    private MCApiRuntime() {
    }

    public static void installProvider(MCApiRuntimeProvider runtimeProvider) {
        if (runtimeProvider == null) {
            throw new IllegalArgumentException("runtimeProvider");
        }
        provider = runtimeProvider;
    }

    /** Returns the compatibility implementation used when a target has no adapter. */
    public static MCApiRuntimeProvider reflectiveProvider() {
        return new ReflectiveProvider();
    }

    public static Object invokeStaticNoArgs(String className, String methodName) {
        try {
            Class<?> type = Class.forName(className);
            Method method = type.getMethod(methodName);
            return method.invoke(null);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to invoke " + className + "#" + methodName, exception);
        }
    }

    public static Object invokeStaticSingleArg(String className, String methodName, Object arg) {
        try {
            Class<?> type = Class.forName(className);
            for (Method method : type.getMethods()) {
                if (method.getName().equals(methodName) && method.getParameterCount() == 1) {
                    return method.invoke(null, arg);
                }
            }
            throw new NoSuchMethodException(methodName);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to invoke " + className + "#" + methodName, exception);
        }
    }

    public static MCMinecraftServer getServer() {
        return provider.getServer();
    }

    public static MCLevel getCurrentLevel() {
        return provider.getCurrentLevel();
    }

    public static Object lookupPotion(String name) {
        return provider.lookupPotion(name);
    }

    public static MCMinecraftServer wrapServer(Object server) {
        return provider.wrapServer(server);
    }

    public static MCLevel wrapLevel(Object level) {
        return provider.wrapLevel(level);
    }

    public static Object wrapPotion(Object potion) {
        return provider.wrapPotion(potion);
    }

    public static Object lookupRegistry(String registry, String id) {
        return provider.lookupRegistry(registry, id);
    }

    /** Converts version-specific registry results such as Optional and Holder into their value. */
    public static Object normalizeRegistryValue(Object value) {
        if (value instanceof java.util.Optional<?> optional) {
            if (optional.isEmpty()) return null;
            value = optional.get();
        }
        if (value != null) {
            try {
                Method holderValue = HOLDER_VALUE_ACCESSORS.get(value.getClass()).orElse(null);
                return holderValue == null ? value : holderValue.invoke(value);
            } catch (ReflectiveOperationException ignored) {
                // Ordinary registry values do not expose Holder.value().
            }
        }
        return value;
    }

    public static String registryKey(String registry, Object value) {
        return provider.registryKey(registry, value);
    }

    public static Object copyBlockProperties(Object block) {
        return provider.copyBlockProperties(block);
    }

    public static Object createResourceLocation(String namespace, String path) {
        return provider.createResourceLocation(namespace, path);
    }

    public static Object createCreativeTabBuilder() {
        return provider.createCreativeTabBuilder();
    }

    private static MCMinecraftServer reflectiveGetServer() {
        Object server = tryGetForgeServer();
        if (server == null) {
            server = tryGetClientIntegratedServer();
        }
        return wrapServer(server);
    }

    private static MCLevel reflectiveGetCurrentLevel() {
        Object server = tryGetForgeServer();
        if (server != null) {
            Object level = invokeNoArg(server, "overworld");
            if (level == null) {
                level = firstIterable(invokeNoArg(server, "getAllLevels"));
            }
            MCLevel wrapped = wrapLevel(level);
            if (wrapped != null) {
                return wrapped;
            }
        }

        Object minecraft = tryInvokeStaticNoArgs("net.minecraft.client.Minecraft", "getInstance");
        if (minecraft != null) {
            Object level = readField(minecraft, "level");
            MCLevel wrapped = wrapLevel(level);
            if (wrapped != null) {
                return wrapped;
            }
        }

        throw new IllegalStateException("No active Minecraft level is available.");
    }

    private static Object reflectiveLookupPotion(String name) {
        String resolved = name.contains(":") ? name : "minecraft:" + name;
        Object resourceLocation = construct("net.minecraft.resources.ResourceLocation",
            new Class<?>[]{String.class}, resolved);

        Object potion = tryLookupRegistryValue("net.minecraftforge.registries.ForgeRegistries", "POTIONS", resourceLocation);
        if (potion == null) {
            potion = tryLookupRegistryValue("net.minecraft.core.registries.BuiltInRegistries", "POTION", resourceLocation);
        }
        if (potion == null) {
            throw new IllegalArgumentException("Unknown potion: " + resolved);
        }
        return potion;
    }

    public static Object construct(String className, Class<?>[] parameterTypes, Object... args) {
        try {
            Class<?> type = Class.forName(className);
            Constructor<?> ctor = type.getConstructor(parameterTypes);
            return ctor.newInstance(args);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to construct " + className, exception);
        }
    }

    public static Object tryInvokeStaticNoArgs(String className, String methodName) {
        try {
            return invokeStaticNoArgs(className, methodName);
        } catch (IllegalStateException ignored) {
            return null;
        }
    }

    private static MCMinecraftServer reflectiveWrapServer(Object server) {
        if (server == null) {
            return null;
        }

        try {
            Class<?> impl = Class.forName("dev.mlua.mc.impl.server.MCMinecraftServerImpl");
            Method fromReal = impl.getMethod("fromReal", Class.forName("net.minecraft.server.MinecraftServer"));
            return (MCMinecraftServer) fromReal.invoke(null, server);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to wrap MinecraftServer.", exception);
        }
    }

    private static MCLevel reflectiveWrapLevel(Object level) {
        if (level == null) {
            return null;
        }

        try {
            Class<?> serverLevelClass = Class.forName("net.minecraft.server.level.ServerLevel");
            if (serverLevelClass.isInstance(level)) {
                return (MCLevel) construct(
                    "dev.mlua.mc.impl.world.MCServerLevelImpl",
                    new Class<?>[]{serverLevelClass},
                    level
                );
            }

            return (MCLevel) construct(
                "dev.mlua.mc.impl.world.MCLevelImpl",
                new Class<?>[]{Class.forName("net.minecraft.world.level.Level")},
                level
            );
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to wrap level.", exception);
        }
    }

    private static Object reflectiveWrapPotion(Object potion) {
        if (potion == null) {
            return null;
        }

        try {
            Class<?> potionClass = Class.forName("net.minecraft.world.item.alchemy.Potion");
            return construct(
                "dev.mlua.mc.impl.item.alchemy.MCPotionImpl",
                new Class<?>[]{potionClass},
                potion
            );
        } catch (ReflectiveOperationException | IllegalStateException exception) {
            throw new IllegalStateException("Failed to wrap potion.", exception);
        }
    }

    private static Object tryGetForgeServer() {
        return tryInvokeStaticNoArgs("net.minecraftforge.server.ServerLifecycleHooks", "getCurrentServer");
    }

    private static Object tryGetClientIntegratedServer() {
        Object minecraft = tryInvokeStaticNoArgs("net.minecraft.client.Minecraft", "getInstance");
        return minecraft == null ? null : invokeNoArg(minecraft, "getSingleplayerServer");
    }

    private static Object tryLookupRegistryValue(String ownerClassName, String fieldName, Object resourceLocation) {
        try {
            Class<?> owner = Class.forName(ownerClassName);
            Field field = owner.getField(fieldName);
            Object registry = field.get(null);
            return registry == null ? null : invokeSingleArg(registry, "getValue", resourceLocation);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static Object readField(Object target, String fieldName) {
        try {
            Field field = target.getClass().getField(fieldName);
            return field.get(target);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to read field " + fieldName + " from " + target.getClass().getName(), exception);
        }
    }

    private static Object invokeNoArg(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to invoke " + methodName + " on " + target.getClass().getName(), exception);
        }
    }

    private static Object invokeSingleArg(Object target, String methodName, Object arg) {
        try {
            for (Method method : target.getClass().getMethods()) {
                if (method.getName().equals(methodName) && method.getParameterCount() == 1) {
                    return method.invoke(target, arg);
                }
            }
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to invoke " + methodName + " on " + target.getClass().getName(), exception);
        }
        return null;
    }

    private static Object firstIterable(Object value) {
        if (value instanceof Iterable<?> iterable) {
            for (Object element : iterable) {
                return element;
            }
        }
        return null;
    }

    private static final class ReflectiveProvider implements MCApiRuntimeProvider {
        @Override
        public MCMinecraftServer getServer() {
            return reflectiveGetServer();
        }

        @Override
        public MCLevel getCurrentLevel() {
            return reflectiveGetCurrentLevel();
        }

        @Override
        public Object lookupPotion(String name) {
            return reflectiveLookupPotion(name);
        }

        @Override
        public Object lookupRegistry(String registry, String id) {
            Object location = construct("net.minecraft.resources.ResourceLocation", new Class<?>[]{String.class}, id);
            Object value = tryLookupRegistryValue("net.minecraft.core.registries.BuiltInRegistries", registry.toUpperCase(), location);
            return normalizeRegistryValue(value);
        }

        @Override
        public String registryKey(String registry, Object value) {
            Object location = tryLookupRegistryKey("net.minecraft.core.registries.BuiltInRegistries", registry.toUpperCase(), value);
            return location == null ? null : location.toString();
        }

        @Override
        public MCMinecraftServer wrapServer(Object server) {
            return reflectiveWrapServer(server);
        }

        @Override
        public MCLevel wrapLevel(Object level) {
            return reflectiveWrapLevel(level);
        }

        @Override
        public Object wrapPotion(Object potion) {
            return reflectiveWrapPotion(potion);
        }
    }

    private static Object tryLookupRegistryKey(String ownerClassName, String fieldName, Object value) {
        try {
            Class<?> owner = Class.forName(ownerClassName);
            Field field = owner.getField(fieldName);
            Object registry = field.get(null);
            return registry == null ? null : invokeSingleArg(registry, "getKey", value);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

}
