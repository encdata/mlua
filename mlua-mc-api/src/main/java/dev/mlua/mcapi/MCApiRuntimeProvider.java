package dev.mlua.mcapi;

import dev.mlua.mcapi.server.MCMinecraftServer;
import dev.mlua.mcapi.world.MCLevel;

/**
 * Loader/version-owned boundary for operations that need Minecraft classes.
 * The shared runtime only depends on this contract; each target may install
 * a more direct implementation when its mappings and loader APIs are known.
 */
public interface MCApiRuntimeProvider {
    MCMinecraftServer getServer();

    MCLevel getCurrentLevel();

    Object lookupPotion(String name);

    Object lookupRegistry(String registry, String id);

    String registryKey(String registry, Object value);

    MCMinecraftServer wrapServer(Object server);

    MCLevel wrapLevel(Object level);

    Object wrapPotion(Object potion);

    /**
     * Target-selected binding for a Minecraft API that changed between game lines.
     * The implementation is supplied from mlua-versions/common/<target>.
     */
    default Object copyBlockProperties(Object block) {
        try {
            Class<?> binding = Class.forName("dev.mlua.mc.impl.block.MCBlockPropertiesBinding");
            return binding.getMethod("copy", Object.class).invoke(null, block);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("This Minecraft target has no block-properties binding.", exception);
        }
    }

    default Object createResourceLocation(String namespace, String path) {
        try {
            Class<?> binding = Class.forName("dev.mlua.mc.impl.core.MCResourceLocationBinding");
            return binding.getMethod("create", String.class, String.class).invoke(null, namespace, path);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("This Minecraft target has no resource-location binding.", exception);
        }
    }

    default Object createCreativeTabBuilder() {
        try {
            Class<?> binding = Class.forName("dev.mlua.mc.impl.item.MCCreativeTabBinding");
            return binding.getMethod("create").invoke(null);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("This Minecraft target has no creative-tab binding.", exception);
        }
    }
}
