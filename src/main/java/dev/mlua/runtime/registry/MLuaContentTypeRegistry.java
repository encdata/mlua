package dev.mlua.runtime.registry;

import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.WeakHashMap;
import dev.mlua.module.MLuaRegistration;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/** Runtime definitions for reusable Lua-authored content types. */
public final class MLuaContentTypeRegistry {
    private static final Map<MLuaRegistration, Map<String, dev.mlua.api.registry.MLuaRegistry<Definition>>> TYPES = new WeakHashMap<>();

    private MLuaContentTypeRegistry() { }

    public static void register(MLuaRegistration scope, String domain, String owner, String name, String base,
                                String description, LuaTable defaults) {
        java.util.Objects.requireNonNull(scope, "scope");
        domain = normalizeDomain(domain);
        String key = qualify(owner, name);
        String localName = key.substring(key.indexOf(':') + 1);
        boolean reserved = "item".equals(domain) && MLuaItemFactory.registeredTypes().containsKey(localName)
            || "block".equals(domain) && MLuaBlockFactory.registeredTypes().containsKey(localName);
        if (reserved) throw new LuaError("[MLua] Content type name '" + localName + "' is already provided by MLua or a Java addon");
        if (base == null || base.isBlank()) throw new LuaError("[MLua] Content type Base must be a built-in, Java, or registered Lua type");
        if (description == null || description.isBlank()) throw new LuaError("[MLua] Content type Description must be non-empty");
        // Freeze the caller's table at registration time; later addon mutations must not
        // silently change the behavior inherited by already-registered content types.
        LuaTable defaultProperties = defaults == null ? new LuaTable() : copy(defaults);
        String requestedBase = base.trim();
        String parent = normalizeReference(owner, requestedBase);
        if (find(scope, domain, owner, parent) == null) {
            if (requestedBase.contains(":")) {
                throw new LuaError("[MLua] Content type base '" + requestedBase
                    + "' is not registered for domain '" + domain + "'");
            }
            validateBase(domain, requestedBase);
            parent = requestedBase;
        }
        Definition definition = new Definition(key, parent, description.trim(), defaultProperties);
        dev.mlua.api.registry.MLuaRegistry<Definition> domainTypes = domainRegistry(scope, domain, true);
        if (!domainTypes.register(owner, key, definition)) {
            throw new LuaError("[MLua] Content type '" + key + "' is already registered");
        }
    }

    public static LuaTable resolve(MLuaRegistration scope, String domain, String owner, LuaTable properties) {
        java.util.Objects.requireNonNull(scope, "scope");
        domain = normalizeDomain(domain);
        if (properties == null) return new LuaTable();
        String field = selectionField(domain);
        LuaValue selected = properties.get(field);
        if (selected.isnil() && !"item".equals(domain) && !"block".equals(domain)) selected = properties.get("registryType");
        if (selected.isnil() && !"item".equals(domain) && !"block".equals(domain)) selected = properties.get("RegistryType");
        if (selected.isnil()) return copy(properties);
        if (!selected.isstring() || selected.tojstring().isBlank()) {
            throw new LuaError("[MLua] Content type selector '" + field + "' must be a non-empty string");
        }
        Definition definition = find(scope, domain, owner, selected.tojstring());
        if (definition == null) {
            if (!"item".equals(domain) && !"block".equals(domain)) {
                throw new LuaError("[MLua] Content type '" + selected.tojstring()
                    + "' is not registered for domain '" + domain + "'");
            }
            return copy(properties);
        }
        LuaTable result = new LuaTable();
        String base = apply(scope, domain, owner, definition, result, new HashSet<>());
        merge(result, properties);
        if ("item".equals(domain) || "block".equals(domain)) result.set(field, LuaValue.valueOf(base));
        else {
            result.set("registryType", LuaValue.NIL);
            result.set("RegistryType", LuaValue.NIL);
        }
        return result;
    }

    public static Map<String, String> types(MLuaRegistration scope, String domain, String owner) {
        java.util.Objects.requireNonNull(scope, "scope");
        domain = normalizeDomain(domain);
        if (owner == null || owner.isBlank()) throw new LuaError("[MLua] Content type owner must be non-empty");
        dev.mlua.api.registry.MLuaRegistry<Definition> domainTypes = domainRegistry(scope, domain, false);
        if (domainTypes == null) return Map.of();
        Map<String, String> result = new TreeMap<>();
        String prefix = owner + ":";
        domainTypes.snapshot().forEach((key, definition) -> {
            result.put(key.startsWith(prefix) ? key.substring(prefix.length()) : key, definition.description());
        });
        return Collections.unmodifiableMap(result);
    }

    public static boolean unregister(MLuaRegistration scope, String domain, String owner, String name) {
        java.util.Objects.requireNonNull(scope, "scope");
        domain = normalizeDomain(domain);
        dev.mlua.api.registry.MLuaRegistry<Definition> domainTypes = domainRegistry(scope, domain, false);
        return domainTypes != null && domainTypes.remove(owner, qualify(owner, name));
    }

    public static void clearOwner(MLuaRegistration scope, String owner) {
        java.util.Objects.requireNonNull(scope, "scope");
        if (owner == null || owner.isBlank()) return;
        synchronized (TYPES) {
            Map<String, dev.mlua.api.registry.MLuaRegistry<Definition>> scopedTypes = TYPES.get(scope);
            if (scopedTypes == null) return;
            scopedTypes.values().forEach(domainTypes -> domainTypes.removeOwner(owner));
            scopedTypes.entrySet().removeIf(entry -> entry.getValue().size() == 0);
            if (scopedTypes.isEmpty()) TYPES.remove(scope);
        }
    }

    private static String apply(MLuaRegistration scope, String domain, String owner, Definition definition,
                                LuaTable result, Set<String> visited) {
        if (!visited.add(definition.key())) throw new LuaError("[MLua] Content type inheritance cycle at '" + definition.key() + "'");
        Definition parent = find(scope, domain, owner, definition.base());
        String rootBase;
        if (parent != null) {
            rootBase = apply(scope, domain, owner, parent, result, visited);
        } else {
            if (definition.base().contains(":")) {
                throw new LuaError("[MLua] Content type '" + definition.key() + "' extends unavailable type '"
                    + definition.base() + "'. Its owning addon may have been unloaded.");
            }
            rootBase = definition.base();
        }
        merge(result, definition.defaults());
        visited.remove(definition.key());
        return rootBase;
    }

    private static Definition find(MLuaRegistration scope, String domain, String owner, String name) {
        dev.mlua.api.registry.MLuaRegistry<Definition> domainTypes = domainRegistry(scope, domain, false);
        if (domainTypes == null) return null;
        return domainTypes.get(normalizeReference(owner, name));
    }

    private static dev.mlua.api.registry.MLuaRegistry<Definition> domainRegistry(MLuaRegistration scope, String domain, boolean create) {
        synchronized (TYPES) {
            Map<String, dev.mlua.api.registry.MLuaRegistry<Definition>> scopedTypes = TYPES.get(scope);
            if (scopedTypes == null) {
                if (!create) return null;
                scopedTypes = new java.util.HashMap<>();
                TYPES.put(scope, scopedTypes);
            }
            dev.mlua.api.registry.MLuaRegistry<Definition> registry = scopedTypes.get(domain);
            if (registry == null && create) {
                registry = new dev.mlua.api.registry.MLuaRegistry<>();
                scopedTypes.put(domain, registry);
            }
            return registry;
        }
    }

    private static String normalizeDomain(String domain) {
        if (domain == null || domain.isBlank()) throw new LuaError("[MLua] Content type domain must be non-empty");
        String normalized = domain.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z][a-z0-9_.-]*")) {
            throw new LuaError("[MLua] Invalid content type domain '" + domain + "'");
        }
        return normalized;
    }

    private static String selectionField(String domain) {
        return "item".equals(domain) ? "kind" : "block".equals(domain) ? "type" : "RegistryType";
    }

    private static void validateBase(String domain, String base) {
        try {
            if (base.contains(":")) throw new IllegalArgumentException("type '" + base + "' is not registered");
            if ("item".equals(domain)) new MLuaItemFactory().validateTypeDefinition(Map.of("kind", base));
            else if ("block".equals(domain)) MLuaBlockFactory.validateTypeDefinition(Map.of("type", base));
            else if (!"default".equals(base) && !domain.equals(base)) {
                throw new IllegalArgumentException("expected 'default' or '" + domain + "'");
            }
        } catch (IllegalArgumentException error) {
            throw new LuaError("[MLua] Content type Base '" + base + "' is not available: " + error.getMessage());
        }
    }

    private static String normalizeReference(String owner, String name) {
        if (owner == null || owner.isBlank()) throw new LuaError("[MLua] Content type owner must be non-empty");
        if (name == null || name.isBlank()) throw new LuaError("[MLua] Content type reference must be non-empty");
        String normalized = name.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
            if (!normalized.matches("[a-z][a-z0-9_./-]*")) throw new LuaError("[MLua] Invalid content type reference '" + name + "'");
            normalized = owner + ":" + normalized;
        }
        return normalized;
    }

    private static String qualify(String owner, String name) {
        if (owner == null || owner.isBlank()) throw new LuaError("[MLua] Content type owner must be non-empty");
        if (name == null || name.isBlank()) throw new LuaError("[MLua] Content type name must be non-empty");
        String normalized = name.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
            if (!normalized.matches("[a-z][a-z0-9_./-]*")) throw new LuaError("[MLua] Invalid content type name '" + name + "'");
            normalized = owner + ":" + normalized;
        }
        if (!normalized.substring(0, normalized.indexOf(':')).equals(owner)) {
            throw new LuaError("[MLua] An addon may only register content types in its own namespace");
        }
        return normalized;
    }

    private static LuaTable copy(LuaTable source) {
        LuaTable copy = new LuaTable();
        merge(copy, source);
        return copy;
    }

    private static void merge(LuaTable target, LuaTable source) {
        for (LuaValue key : source.keys()) {
            LuaValue value = source.get(key);
            LuaValue old = target.get(key);
            if (value.istable() && old.istable()) {
                if (isArray(value.checktable()) || isArray(old.checktable())) {
                    target.set(key, copy(value.checktable()));
                    continue;
                }
                LuaTable merged = copy(old.checktable());
                merge(merged, value.checktable());
                target.set(key, merged);
            } else if (value.istable()) {
                target.set(key, copy(value.checktable()));
            } else {
                target.set(key, value);
            }
        }
    }

    private static boolean isArray(LuaTable table) {
        if (table.length() == 0) return false;
        for (LuaValue key : table.keys()) if (!key.isint() || key.checkint() < 1) return false;
        return true;
    }

    private record Definition(String key, String base, String description, LuaTable defaults) { }
}
