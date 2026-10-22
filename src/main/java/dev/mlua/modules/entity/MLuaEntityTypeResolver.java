package dev.mlua.modules.entity;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Function;

import dev.mlua.loaderapi.RegistryHandle;
import dev.mlua.loaderapi.RegistryKind;
import dev.mlua.minecraft.MLuaResourceLocations;
import dev.mlua.runtime.registry.MLuaLoaderRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;

/** Resolves entity identifiers through the active world, vanilla, and loader-owned registries. */
final class MLuaEntityTypeResolver {
    // Entity-type registries are frozen before runtime lookups; weak keys avoid retaining reloadable registries.
    private static final Map<Registry<EntityType<?>>, Map<String, EntityType<?>>> FALLBACK_INDEXES =
        new WeakHashMap<>();

    private MLuaEntityTypeResolver() { }

    static EntityType<?> resolve(ServerLevel level, String owner, String requestedId) {
        if (requestedId == null || requestedId.isBlank()) return null;
        String id = requestedId.contains(":") ? requestedId
            : (owner == null || owner.isBlank() ? "minecraft" : owner) + ":" + requestedId;
        ResourceLocation location = MLuaResourceLocations.tryParseOrNull(id);
        if (location == null) return null;
        if (level != null) {
            //--version 26.1
            EntityType<?> worldType = level.registryAccess()
                .lookupOrThrow(Registries.ENTITY_TYPE).getOptional(location).orElse(null);
            //--endversion
            //--version 1.20.1,1.21.1
            Registry<EntityType<?>> entityTypes = level.registryAccess().registryOrThrow(Registries.ENTITY_TYPE);
            EntityType<?> worldType = findType(entityTypes, location);
            //--endversion
            if (worldType != null) return worldType;
        }
        EntityType<?> builtIn = findType(BuiltInRegistries.ENTITY_TYPE, location);
        if (builtIn != null) return builtIn;
        RegistryHandle<?> handle = MLuaLoaderRegistry.get(RegistryKind.ENTITY,
            MLuaResourceLocations.namespace(location), MLuaResourceLocations.path(location));
        Object registered = handle == null ? null : handle.get();
        return registered instanceof EntityType<?> entityType ? entityType : null;
    }

    /** A few loader registry views expose values through iteration before their key index is ready. */
    private static EntityType<?> findType(Registry<EntityType<?>> registry, ResourceLocation location) {
        EntityType<?> direct = registry.getOptional(location).orElse(null);
        if (direct != null) return direct;
        // Some loader registry wrappers expose an empty Optional from getOptional even when
        // their ordinary Registry#get path can resolve the key. Keep both lookup paths before
        // paying for the iteration fallback; this is also how the other runtime registry APIs
        // resolve vanilla entries on those loaders.
        direct = registry.get(location);
        if (direct != null) return direct;
        return fallbackIndex(registry).get(location.toString());
    }

    private static Map<String, EntityType<?>> fallbackIndex(Registry<EntityType<?>> registry) {
        synchronized (FALLBACK_INDEXES) {
            Map<String, EntityType<?>> cached = FALLBACK_INDEXES.get(registry);
            if (cached != null) return cached;
        }
        Map<String, EntityType<?>> immutable = indexByKey(registry,
            entityType -> {
                ResourceLocation key = registry.getKey(entityType);
                return key == null ? null : key.toString();
            });
        synchronized (FALLBACK_INDEXES) {
            return FALLBACK_INDEXES.computeIfAbsent(registry, ignored -> immutable);
        }
    }

    static <T, K> Map<K, T> indexByKey(Iterable<T> values, Function<T, K> keyOf) {
        Map<K, T> index = new HashMap<>();
        for (T candidate : values) {
            K key = keyOf.apply(candidate);
            if (key != null) index.putIfAbsent(key, candidate);
        }
        return Map.copyOf(index);
    }
}
