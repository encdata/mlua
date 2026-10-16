package dev.mlua.modules.registry.Service;

import dev.mlua.loaderapi.RegistryKind;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/** Loader-neutral aliases and routing metadata for built-in registry APIs. */
final class RegistryEndpointCatalog {
    private final Map<String, RegistryEndpoint> builtIns;
    private final Map<String, RegistryEndpoint> endpoints;

    RegistryEndpointCatalog() {
        this.builtIns = new HashMap<>();
        registerBuiltIns(this.builtIns);
        this.endpoints = new HashMap<>(this.builtIns);
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
        return this.endpoints.values().stream().distinct().toList();
    }

    List<String> categoryNames() {
        return new ArrayList<>(new TreeSet<>(this.endpoints.values().stream().map(RegistryEndpoint::table).toList()));
    }

    private static String normalize(String alias) {
        return alias.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static void registerBuiltIns(Map<String, RegistryEndpoint> endpoints) {
        add(endpoints, "Items", "Register", "item", "Items", RegistryKind.ITEM, 2,
            List.of("item", "items"), "Register:3");
        add(endpoints, "Blocks", "Register", "block", "Blocks", RegistryKind.BLOCK, 2,
            List.of("block", "blocks"), "Register:3");
        add(endpoints, "Blocks", "RegisterItem", "item", "Items", null, 2,
            List.of("block_item", "blockitem", "blockitems"));
        add(endpoints, "Entities", "Register", "entity", "Entities", RegistryKind.ENTITY, 2,
            List.of("entity", "entities", "entity_type", "entity_types"), "Register:3");
        add(endpoints, "Effects", "Register", "effect", "Effects", RegistryKind.MOB_EFFECT, 2,
            List.of("effect", "effects", "mob_effect", "mob_effects", "mobeffect"), "Register:3");
        add(endpoints, "Potions", "Register", "potion", "Potions", RegistryKind.POTION, 2,
            List.of("potion", "potions"), "Register:3");
        add(endpoints, "Particles", "Register", "particle", "Particles", RegistryKind.PARTICLE, 2,
            List.of("particle", "particles"), "Register:3");
        add(endpoints, "Containers", "Register", "container", "Containers", null, 2,
            List.of("container", "containers"), "Register:3");
        add(endpoints, "Projectiles", "Register", "projectile", "Projectiles", null, 2,
            List.of("projectile", "projectiles"), "Register:3");
        add(endpoints, "DataPack", "Register", "datapack", "DataPack", null, 3,
            List.of("data", "datapack", "data_pack"), "Register:4");
        add(endpoints, "Assets", "register", "assets", "Assets", null, 2,
            List.of("asset", "assets"));
        add(endpoints, "WorldGeneration", "addFeature", "worldgen", "WorldGeneration", null, 2,
            List.of("worldgen", "worldgeneration", "world_generation", "biome_feature"),
            "addFeature:3", "register:4", "AddFeature:3", "Register:4");
        add(endpoints, "Tabs", "Register", "tab", "Tabs", RegistryKind.CREATIVE_TAB, 2,
            List.of("tab", "tabs", "creative_tab", "creative_tabs"), "Register:3");
        add(endpoints, "Recipes", "Register", "recipe", "Recipes", null, 2,
            List.of("recipe", "recipes"), "Register:3", "RegisterShaped:3", "RegisterShapeless:3",
            "RegisterSmelting:3", "RegisterStonecutting:3");
        add(endpoints, "Loot", "Register", "loot", "Loot", null, 2,
            List.of("loot"), "Register:3", "RegisterEntity:3", "RegisterSimpleBlock:3",
            "RegisterSelfBlock:3", "RegisterSilkTouchBlock:3", "RegisterOreBlock:3");
        add(endpoints, "Tags", "Register", "tag", "Tags", null, 3,
            List.of("tag", "tags"), "Register:4", "RegisterItem:3", "RegisterBlock:3", "RegisterEntity:3");
        add(endpoints, "Config", "RegisterCommon", "config", "Config", null, 2,
            List.of("config"), "Register:4", "RegisterCommon:3", "RegisterClient:3", "RegisterServer:3");
        add(endpoints, "DamageSource", "Register", "damage_source", "DamageSource", null, 2,
            List.of("damage_source", "damagesource"), "Register:3");
    }

    private static void add(Map<String, RegistryEndpoint> endpoints, String table, String registerMethod,
                            String typeDomain, String typeTable, RegistryKind registryKind,
                            int genericArgumentCount, List<String> aliases, String... typedDefinitions) {
        RegistryEndpoint endpoint = new RegistryEndpoint(table, registerMethod, typeDomain, typeTable,
            registryKind, genericArgumentCount, List.of(typedDefinitions));
        for (String alias : aliases) {
            RegistryEndpoint previous = endpoints.putIfAbsent(normalize(alias), endpoint);
            if (previous != null) throw new ExceptionInInitializerError("Duplicate Registry category alias: " + alias);
        }
    }
}
