package dev.mlua.modules.registry.service;

import dev.mlua.loaderapi.RegistryKind;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/** Loader-neutral aliases and routing metadata for built-in registry APIs. */
final class RegistryEndpointCatalog {
    private final Map<String, RegistryEndpoint> builtIns;
    private final Map<String, RegistryEndpoint> endpoints;

    RegistryEndpointCatalog() {
        this.builtIns = new LinkedHashMap<>();
        registerBuiltIns(this.builtIns);
        this.endpoints = new LinkedHashMap<>(this.builtIns);
    }

    RegistryEndpoint get(String alias) {
        return this.endpoints.get(normalize(alias));
    }

    RegistryEndpoint builtIn(String alias) {
        return this.builtIns.get(normalize(alias));
    }

    boolean contains(String alias) {
        return this.endpoints.containsKey(normalize(alias));
    }

    RegistryEndpoint putIfAbsent(String alias, RegistryEndpoint endpoint) {
        return this.endpoints.putIfAbsent(normalize(alias), endpoint);
    }

    boolean remove(String alias, RegistryEndpoint endpoint) {
        return this.endpoints.remove(normalize(alias), endpoint);
    }

    RegistryEndpoint remove(String alias) {
        return this.endpoints.remove(normalize(alias));
    }

    List<RegistryEndpoint> uniqueEndpoints() {
        return this.endpoints.values().stream().distinct()
            .sorted(java.util.Comparator.comparing(RegistryEndpoint::table)
                .thenComparing(RegistryEndpoint::registerMethod)
                .thenComparing(RegistryEndpoint::typeDomain))
            .toList();
    }

    List<String> categoryNames() {
        return new ArrayList<>(new TreeSet<>(this.endpoints.values().stream().map(RegistryEndpoint::table).toList()));
    }

    static String normalize(String alias) {
        return alias.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static void registerBuiltIns(Map<String, RegistryEndpoint> endpoints) {
        add(endpoints, "Items", "Register", "item", "Items", RegistryKind.ITEM, 2,
            List.of("item", "items"), definition("Register", 3));
        add(endpoints, "Blocks", "Register", "block", "Blocks", RegistryKind.BLOCK, 2,
            List.of("block", "blocks"), definition("Register", 3));
        add(endpoints, "Blocks", "RegisterItem", "item", "Items", RegistryKind.BLOCK_ITEM, 2,
            List.of("block_item", "blockitem", "blockitems"));
        add(endpoints, "Entities", "Register", "entity", "Entities", RegistryKind.ENTITY, 2,
            List.of("entity", "entities", "entity_type", "entity_types"), definition("Register", 3));
        add(endpoints, "Effects", "Register", "effect", "Effects", RegistryKind.MOB_EFFECT, 2,
            List.of("effect", "effects", "mob_effect", "mob_effects", "mobeffect"), definition("Register", 3));
        add(endpoints, "Potions", "Register", "potion", "Potions", RegistryKind.POTION, 2,
            List.of("potion", "potions"), definition("Register", 3));
        add(endpoints, "Particles", "Register", "particle", "Particles", RegistryKind.PARTICLE, 2,
            List.of("particle", "particles"), definition("Register", 3));
        add(endpoints, "Containers", "Register", "container", "Containers", null, 2,
            List.of("container", "containers"), definition("Register", 3));
        add(endpoints, "Projectiles", "Register", "projectile", "Projectiles", null, 2,
            List.of("projectile", "projectiles"), definition("Register", 3));
        add(endpoints, "DataPack", "Register", "datapack", "DataPack", null, 3,
            List.of("data", "datapack", "data_pack"), definition("Register", 4));
        add(endpoints, "Assets", "register", "assets", "Assets", null, 2,
            List.of("asset", "assets"));
        add(endpoints, "WorldGeneration", "addFeature", "worldgen", "WorldGeneration", null, 2,
            List.of("worldgen", "worldgeneration", "world_generation", "biome_feature"),
            definition("addFeature", 3), definition("register", 4), definition("AddFeature", 3), definition("Register", 4));
        add(endpoints, "Tabs", "Register", "tab", "Tabs", RegistryKind.CREATIVE_TAB, 2,
            List.of("tab", "tabs", "creative_tab", "creative_tabs"), definition("Register", 3));
        add(endpoints, "Recipes", "Register", "recipe", "Recipes", null, 2,
            List.of("recipe", "recipes"), definition("Register", 3), definition("RegisterShaped", 3), definition("RegisterShapeless", 3),
            definition("RegisterSmelting", 3), definition("RegisterCooking", 4), definition("RegisterStonecutting", 3),
            definition("RegisterSmithingTransform", 3), definition("RegisterSmithingTrim", 3));
        add(endpoints, "Loot", "Register", "loot", "Loot", null, 2,
            List.of("loot"), definition("Register", 3), definition("RegisterEntity", 3), definition("RegisterSimpleBlock", 3),
            definition("RegisterSelfBlock", 3), definition("RegisterSilkTouchBlock", 3), definition("RegisterOreBlock", 3));
        add(endpoints, "Tags", "Register", "tag", "Tags", null, 3,
            List.of("tag", "tags"), definition("Register", 4), definition("RegisterItem", 3), definition("RegisterBlock", 3), definition("RegisterEntity", 3));
        add(endpoints, "Config", "RegisterCommon", "config", "Config", null, 2,
            List.of("config"), definition("Register", 4), definition("RegisterCommon", 3), definition("RegisterClient", 3), definition("RegisterServer", 3));
        add(endpoints, "DamageSource", "Register", "damage_source", "DamageSource", null, 2,
            List.of("damage_source", "damagesource"), definition("Register", 3));
    }

    private static RegistryEndpoint.TypedDefinition definition(String method, int argumentIndex) {
        return new RegistryEndpoint.TypedDefinition(method, argumentIndex);
    }

    private static void add(Map<String, RegistryEndpoint> endpoints, String table, String registerMethod,
                            String typeDomain, String typeTable, RegistryKind registryKind,
                            int genericArgumentCount, List<String> aliases, RegistryEndpoint.TypedDefinition... typedDefinitions) {
        RegistryEndpoint endpoint = new RegistryEndpoint(table, registerMethod, typeDomain, typeTable,
            registryKind, genericArgumentCount, List.of(typedDefinitions));
        for (String alias : aliases) {
            RegistryEndpoint previous = endpoints.putIfAbsent(normalize(alias), endpoint);
            if (previous != null) throw new ExceptionInInitializerError("Duplicate Registry category alias: " + alias);
        }
    }
}
