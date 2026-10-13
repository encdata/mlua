package dev.mlua.runtime.registry;

/**
 * Compatibility name for the public owner-aware registry API.
 * New addon integrations should depend on {@code dev.mlua.api.registry.MLuaRegistry}.
 */
@Deprecated(forRemoval = false)
public final class MLuaRegistry<T> extends dev.mlua.api.registry.MLuaRegistry<T> {
}
