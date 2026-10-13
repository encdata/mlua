package dev.mlua.api;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/** Runtime-readable API compatibility and capability contract. */
@MLuaApiStatus(since = "1.0.0")
public final class MLuaApiPolicy {
    public static final String VERSION = "1.0.0";
    private static final Set<String> CAPABILITIES = Set.of(
        "api.stable_v1", "class.registry", "class.registry_lifecycle", "class.instance_identity",
        "class.protected_instance_metatables",
        "class.multiple_inheritance",
        "class.lookup", "class.discovery", "class.behavior_mixins",
        "service.registry", "service.described_functions", "service.typed_reflective_arguments",
        "service.reflective_varargs", "service.reflective_collections", "service.optional_values",
        "service.reflective_exclusions", "service.reflective_arity_validation",
        "service.side_specific_registration", "service.custom_value_conversion", "service.live_proxy_identity",
        "entity.generic", "entity.service", "registry.entities",
        "entity.components", "entity.scripted", "itemstack.service", "inventory.service",
        "render.world_space", "render.primitives", "render.attachments", "render.follow_entity",
        "java.interop", "sound.handles", "sound.follow_entity", "sound.follow_offset",
        "particles.typed", "particles.shapes", "model.extended_parts",
        "events.player_respawn", "events.entity_spawn", "events.world_lifecycle",
        "events.block_interaction", "events.cancellation", "events.item_drop", "events.item_pickup",
        "events.item_craft", "events.containers", "events.projectiles", "events.explosions",
        "events.advancements", "wrappers.level", "wrappers.dimension"
    );
    private static final Set<String> FABRIC_UNAVAILABLE = Set.of(
        "events.entity_spawn", "events.item_pickup", "events.item_craft",
        "events.containers", "events.projectiles", "events.explosions", "events.advancements"
    );
    private static final Set<String> NEOFORGE_UNAVAILABLE = Set.of(
        "events.item_pickup", "events.item_craft", "events.advancements"
    );

    private MLuaApiPolicy() {
    }

    public static boolean supports(String capability) {
        return capability != null && CAPABILITIES.contains(capability);
    }

    public static boolean supports(String capability, String loaderName) {
        if (!supports(capability) || loaderName == null) return false;
        return switch (loaderName.toLowerCase(Locale.ROOT)) {
            case "fabric" -> !FABRIC_UNAVAILABLE.contains(capability);
            case "neoforge" -> !NEOFORGE_UNAVAILABLE.contains(capability);
            case "forge" -> true;
            default -> false;
        };
    }

    public static Set<String> capabilities() {
        return new LinkedHashSet<>(CAPABILITIES);
    }

}
