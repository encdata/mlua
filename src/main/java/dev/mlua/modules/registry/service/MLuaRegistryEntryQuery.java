package dev.mlua.modules.registry.service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;

import dev.mlua.loaderapi.RegistryKind;
import dev.mlua.runtime.registry.MLuaLoaderRegistry;

/** Resolves stable, sorted identifier lists from Minecraft and MLua's owned entries. */
final class MLuaRegistryEntryQuery {
    private static final Map<RegistryKind, Set<String>> NATIVE_IDS = new ConcurrentHashMap<>();

    private MLuaRegistryEntryQuery() { }

    static List<String> registeredIdList(RegistryKind kind, String namespace) {
        Set<String> ids = new java.util.TreeSet<>(NATIVE_IDS.computeIfAbsent(kind,
            key -> Collections.unmodifiableSet(nativeRegistryIds(key))));
        ids.addAll(MLuaLoaderRegistry.getRegisteredIds(kind));
        return ids.stream()
            .filter(value -> namespace == null || value.startsWith(namespace + ":"))
            .toList();
    }

    static List<String> addonRegisteredIds(RegistryKind kind, String namespace) {
        return MLuaLoaderRegistry.getRegisteredIds(kind).stream().sorted()
            .filter(value -> namespace == null || value.startsWith(namespace + ":"))
            .toList();
    }

    private static Set<String> nativeRegistryIds(RegistryKind kind) {
        return switch (kind) {
            case ITEM -> stringIds(BuiltInRegistries.ITEM.keySet());
            case BLOCK_ITEM -> blockItemIds();
            case BLOCK -> stringIds(BuiltInRegistries.BLOCK.keySet());
            case ENTITY -> stringIds(BuiltInRegistries.ENTITY_TYPE.keySet());
            case PARTICLE -> stringIds(BuiltInRegistries.PARTICLE_TYPE.keySet());
            case POTION -> stringIds(BuiltInRegistries.POTION.keySet());
            case MOB_EFFECT -> stringIds(BuiltInRegistries.MOB_EFFECT.keySet());
            case CREATIVE_TAB -> stringIds(BuiltInRegistries.CREATIVE_MODE_TAB.keySet());
        };
    }

    private static Set<String> blockItemIds() {
        Set<String> ids = new java.util.TreeSet<>();
        for (Object value : BuiltInRegistries.ITEM.entrySet()) {
            if (!(value instanceof Map.Entry<?, ?> entry)) continue;
            Object item = entry.getValue();
            if (item instanceof Holder<?> holder) item = holder.value();
            if (item instanceof BlockItem) ids.add(String.valueOf(entry.getKey()));
        }
        return ids;
    }

    private static Set<String> stringIds(Iterable<?> identifiers) {
        Set<String> ids = new java.util.TreeSet<>();
        for (Object identifier : identifiers) {
            if (identifier != null) ids.add(identifier.toString());
        }
        return ids;
    }
}
