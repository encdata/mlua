package dev.mlua.modules.registry.service;

import dev.mlua.minecraft.MLuaResourceLocations;
import dev.mlua.runtime.registry.MLuaDynamicRegistryCatalog;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;

/** Owns registry alias routing and the live endpoints contributed by addon categories. */
final class MLuaRegistryEndpointResolver {
    private final MLuaRegistryService service;
    private final RegistryEndpointCatalog catalog = new RegistryEndpointCatalog();
    private final Set<String> dynamicEndpoints = ConcurrentHashMap.newKeySet();

    MLuaRegistryEndpointResolver(MLuaRegistryService service) {
        this.service = service;
    }

    RegistryEndpoint resolve(String category, String purpose, LuaTable registry) {
        String key = category.trim().toLowerCase(java.util.Locale.ROOT);
        MLuaDynamicRegistryCatalog.Category dynamic = this.service.dynamicRegistry.category(key);
        RegistryEndpoint endpoint;
        if (dynamic != null) {
            endpoint = new RegistryEndpoint(dynamic.name(), "Register", dynamic.domain(), dynamic.name(), null, 2, List.of());
            if (!endpoint.equals(this.catalog.get(key))) {
                this.catalog.remove(key);
                registry.set(dynamic.name(), org.luaj.vm2.LuaValue.NIL);
                this.service.installDynamicCategory(registry, dynamic);
            }
            endpoint = this.catalog.get(key);
        } else {
            endpoint = this.catalog.builtIn(key);
            RegistryEndpoint active = this.catalog.get(key);
            if (endpoint == null) {
                RegistryEndpoint stale = this.catalog.remove(key);
                if (stale != null) registry.set(stale.table(), org.luaj.vm2.LuaValue.NIL);
                this.dynamicEndpoints.remove(key);
            } else if (active != endpoint) {
                this.catalog.putIfAbsent(key, endpoint);
            }
        }
        if (endpoint == null) throw new LuaError("[MLua] Unknown or unsupported Registry " + purpose + " '" + category + "'");
        return endpoint;
    }

    void refresh(LuaTable registry) {
        for (String key : List.copyOf(this.dynamicEndpoints)) {
            if (this.service.dynamicRegistry.category(key) != null) continue;
            RegistryEndpoint stale = this.catalog.remove(key);
            this.dynamicEndpoints.remove(key);
            if (stale != null) registry.set(stale.table(), org.luaj.vm2.LuaValue.NIL);
        }
    }

    boolean contains(String alias) {
        return this.catalog.contains(alias);
    }

    RegistryEndpoint registerDynamic(String alias, RegistryEndpoint endpoint) {
        RegistryEndpoint existing = this.catalog.putIfAbsent(alias, endpoint);
        if (existing == null) this.dynamicEndpoints.add(RegistryEndpointCatalog.normalize(alias));
        return existing;
    }

    boolean removeDynamic(String alias, RegistryEndpoint expected) {
        this.dynamicEndpoints.remove(RegistryEndpointCatalog.normalize(alias));
        return this.catalog.remove(alias, expected);
    }

    List<RegistryEndpoint> uniqueEndpoints() {
        return this.catalog.uniqueEndpoints();
    }

    List<String> categoryNames() {
        return this.catalog.categoryNames();
    }

    static String categoryName(String value) {
        String candidate = value == null ? "" : value.trim();
        if (candidate.length() > MLuaDynamicRegistryCatalog.MAX_CATEGORY_NAME_LENGTH) {
            throw new LuaError("[MLua] Registry category names cannot exceed "
                + MLuaDynamicRegistryCatalog.MAX_CATEGORY_NAME_LENGTH + " characters");
        }
        ResourceLocation id = candidate.contains(":") ? MLuaResourceLocations.tryParseOrNull(candidate) : null;
        if (id == null || !id.toString().equals(candidate)) {
            throw new LuaError("[MLua] Registry category must be a valid lowercase namespaced identifier, such as 'addon:weapons'");
        }
        return id.toString();
    }

    static String contentTypeDomain(String owner, String category) {
        String encodedCategory = HexFormat.of().formatHex(category.getBytes(StandardCharsets.UTF_8));
        return "addon." + owner + ".category." + encodedCategory;
    }

}
