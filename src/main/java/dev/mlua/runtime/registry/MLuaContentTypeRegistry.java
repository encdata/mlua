package dev.mlua.runtime.registry;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.WeakHashMap;
import dev.mlua.api.MLuaLuaCoercions;
import dev.mlua.module.MLuaRegistration;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

/** Runtime definitions for reusable Lua-authored content types. */
public final class MLuaContentTypeRegistry {
    public static final int MAX_TABLE_DEPTH = MLuaLuaCoercions.MAX_CONVERSION_DEPTH;
    public static final int MAX_TYPES_PER_DOMAIN = 4_096;
    public static final int MAX_TYPES_PER_SCOPE = 16_384;
    public static final int MAX_DOMAIN_LENGTH = 1_024;
    public static final int MAX_QUALIFIED_NAME_LENGTH = 256;
    public static final int MAX_BASE_REFERENCE_LENGTH = 256;
    public static final int MAX_DESCRIPTION_LENGTH = 2_048;
    public static final int MAX_OWNER_LENGTH = 128;
    public static final int MAX_DEFAULT_ENTRIES_PER_TYPE = 16_384;
    public static final int MAX_DEFAULT_ENTRIES_PER_OWNER = 262_144;
    public static final int MAX_TOTAL_DEFAULT_ENTRIES = 1_048_576;
    private static final Map<MLuaRegistration, ScopeTypes> TYPES = new WeakHashMap<>();

    private MLuaContentTypeRegistry() { }

    public static void register(MLuaRegistration scope, String domain, String owner, String name, String base,
                                String description, LuaTable defaults) {
        java.util.Objects.requireNonNull(scope, "scope");
        domain = normalizeDomain(domain);
        String key = qualify(owner, name);
        String localName = key.substring(key.indexOf(':') + 1);
        boolean reserved = "item".equals(domain) && isReservedBuilderName(MLuaItemFactory.registeredTypes(), key, localName)
            || "block".equals(domain) && isReservedBuilderName(MLuaBlockFactory.registeredTypes(), key, localName);
        if (reserved) throw new LuaError("[MLua] Content type name '" + localName + "' is already provided by MLua or a Java addon");
        if (base == null || base.isBlank()) throw new LuaError("[MLua] Content type Base must be a built-in, Java, or registered Lua type");
        if (description == null || description.isBlank()) throw new LuaError("[MLua] Content type Description must be non-empty");
        if (base.trim().length() > MAX_BASE_REFERENCE_LENGTH) {
            throw new LuaError("[MLua] Content type Base cannot exceed " + MAX_BASE_REFERENCE_LENGTH + " characters");
        }
        if (description.trim().length() > MAX_DESCRIPTION_LENGTH) {
            throw new LuaError("[MLua] Content type Description cannot exceed " + MAX_DESCRIPTION_LENGTH + " characters");
        }
        // Freeze the caller's table at registration time; later addon mutations must not
        // silently change the behavior inherited by already-registered content types.
        if (defaults != null) {
            validateAcyclic(defaults, "Content type Defaults");
            validateEntryLimit(defaults, "Content type Defaults");
        }
        LuaTable defaultProperties = defaults == null ? new LuaTable() : copy(defaults);
        String requestedBase = base.trim();
        String parent = normalizeReference(owner, requestedBase);
        synchronized (TYPES) {
            ScopeTypes scopeTypes = scopeTypes(scope, true);
            dev.mlua.api.registry.MLuaRegistry<Definition> domainTypes = scopeTypes.domain(domain, false);
            if (domainTypes != null && domainTypes.contains(key)) {
                throw new LuaError("[MLua] Content type '" + key + "' is already registered");
            }
            if (domainTypes != null && domainTypes.size() >= MAX_TYPES_PER_DOMAIN) {
                throw new LuaError("[MLua] Content type limit reached for domain '" + domain + "' ("
                    + MAX_TYPES_PER_DOMAIN + ")");
            }
            if (scopeTypes.count >= MAX_TYPES_PER_SCOPE) {
                throw new LuaError("[MLua] Content type limit reached for this runtime (" + MAX_TYPES_PER_SCOPE + ")");
            }
            Definition parentDefinition = domainTypes == null ? null : domainTypes.get(parent);
            String resolvedBase;
            LuaTable inheritedDefaults;
            if (parentDefinition != null) {
                resolvedBase = parentDefinition.base();
                inheritedDefaults = copy(parentDefinition.defaults());
                merge(inheritedDefaults, defaultProperties);
                validateEntryLimit(inheritedDefaults, "Inherited content type Defaults");
            } else {
                validateBase(domain, requestedBase);
                resolvedBase = requestedBase;
                inheritedDefaults = defaultProperties;
            }
            int defaultEntries = countEntries(inheritedDefaults, 0);
            if (scopeTypes.defaultEntries + defaultEntries > MAX_TOTAL_DEFAULT_ENTRIES) {
                throw new LuaError("[MLua] Content type default data limit reached for this runtime ("
                    + MAX_TOTAL_DEFAULT_ENTRIES + " table entries)");
            }
            if (scopeTypes.defaultEntriesByOwner.getOrDefault(owner, 0) + defaultEntries > MAX_DEFAULT_ENTRIES_PER_OWNER) {
                throw new LuaError("[MLua] Content type default data limit reached for addon '" + owner + "' ("
                    + MAX_DEFAULT_ENTRIES_PER_OWNER + " table entries)");
            }
            Definition definition = new Definition(key, resolvedBase, description.trim(), inheritedDefaults, defaultEntries);
            if (domainTypes == null) domainTypes = scopeTypes.domain(domain, true);
            if (!domainTypes.register(owner, key, definition)) {
                throw new LuaError("[MLua] Content type '" + key + "' is already registered");
            }
            scopeTypes.count++;
            scopeTypes.typesByOwner.merge(owner, 1, Integer::sum);
            scopeTypes.defaultEntries += defaultEntries;
            scopeTypes.defaultEntriesByOwner.merge(owner, defaultEntries, Integer::sum);
        }
    }

    public static LuaTable resolve(MLuaRegistration scope, String domain, String owner, LuaTable properties) {
        java.util.Objects.requireNonNull(scope, "scope");
        domain = normalizeDomain(domain);
        if (properties == null) return new LuaTable();
        validateAcyclic(properties, "Content definition");
        validateEntryLimit(properties, "Content definition");
        String field = selectionField(domain);
        LuaValue selected = properties.get(field);
        if (selected.isnil() && !"item".equals(domain) && !"block".equals(domain)) selected = properties.get("registryType");
        if (selected.isnil() && !"item".equals(domain) && !"block".equals(domain)) selected = properties.get("RegistryType");
        if (selected.isnil()) return copy(properties);
        if (selected.type() != LuaValue.TSTRING || selected.tojstring().isBlank()) {
            throw new LuaError("[MLua] Content type selector '" + field + "' must be a non-empty string");
        }
        Definition definition = find(scope, domain, owner, selected.tojstring());
        if (definition == null) {
            if ("item".equals(domain) || "block".equals(domain)) {
                try {
                    if ("item".equals(domain)) {
                        new MLuaItemFactory().validateTypeDefinition(Map.of("kind", selected.tojstring()));
                    } else {
                        MLuaBlockFactory.validateTypeDefinition(Map.of("type", selected.tojstring()));
                    }
                } catch (IllegalArgumentException failure) {
                    throw new LuaError("[MLua] Content type '" + selected.tojstring()
                        + "' is not registered for domain '" + domain
                        + "' and is not a built-in or Java-registered type: " + failure.getMessage());
                }
                return copy(properties);
            }
            throw new LuaError("[MLua] Content type '" + selected.tojstring()
                + "' is not registered for domain '" + domain + "'");
        }
        LuaTable result = new LuaTable();
        String base = apply(definition, result);
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
            if (key.startsWith(prefix)) {
                result.put(key.substring(prefix.length()), definition.description());
            }
        });
        return Collections.unmodifiableMap(result);
    }

    public static Limits limits(MLuaRegistration scope, String domain) {
        java.util.Objects.requireNonNull(scope, "scope");
        domain = normalizeDomain(domain);
        synchronized (TYPES) {
            ScopeTypes scopeTypes = TYPES.get(scope);
            dev.mlua.api.registry.MLuaRegistry<Definition> domainTypes =
                scopeTypes == null ? null : scopeTypes.domain(domain, false);
            return new Limits(domainTypes == null ? 0 : domainTypes.size(),
                scopeTypes == null ? 0 : scopeTypes.count);
        }
    }

    public static Usage usage(MLuaRegistration scope, String owner) {
        java.util.Objects.requireNonNull(scope, "scope");
        synchronized (TYPES) {
            ScopeTypes scopeTypes = TYPES.get(scope);
            if (scopeTypes == null) return new Usage(0, 0, 0, 0);
            int owned = owner == null || owner.isBlank() ? 0 : scopeTypes.typesByOwner.getOrDefault(owner, 0);
            int ownedDefaultEntries = owner == null || owner.isBlank()
                ? 0 : scopeTypes.defaultEntriesByOwner.getOrDefault(owner, 0);
            return new Usage(scopeTypes.count, owned, scopeTypes.defaultEntries, ownedDefaultEntries);
        }
    }

    public static boolean unregister(MLuaRegistration scope, String domain, String owner, String name) {
        java.util.Objects.requireNonNull(scope, "scope");
        domain = normalizeDomain(domain);
        synchronized (TYPES) {
            ScopeTypes scopeTypes = scopeTypes(scope, false);
            if (scopeTypes == null) return false;
            dev.mlua.api.registry.MLuaRegistry<Definition> domainTypes = scopeTypes.domain(domain, false);
            if (domainTypes == null) return false;
            String key = qualify(owner, name);
            Definition definition = domainTypes.get(key);
            if (definition == null || !domainTypes.remove(owner, key)) return false;
            scopeTypes.count--;
            scopeTypes.removeType(owner);
            scopeTypes.removeDefaultEntries(owner, definition.defaultEntries());
            if (domainTypes.size() == 0) scopeTypes.domains.remove(domain);
            if (scopeTypes.domains.isEmpty()) TYPES.remove(scope);
            return true;
        }
    }

    public static void clearOwner(MLuaRegistration scope, String owner) {
        java.util.Objects.requireNonNull(scope, "scope");
        if (owner == null || owner.isBlank()) return;
        synchronized (TYPES) {
            ScopeTypes scopedTypes = TYPES.get(scope);
            if (scopedTypes == null) return;
            int removedTypes = 0;
            for (dev.mlua.api.registry.MLuaRegistry<Definition> domainTypes : scopedTypes.domains.values()) {
                for (dev.mlua.api.registry.MLuaRegistry.OwnedValue<Definition> entry : domainTypes.snapshotEntries().values()) {
                    if (owner.equals(entry.owner())) scopedTypes.removeDefaultEntries(owner, entry.value().defaultEntries());
                }
                removedTypes += domainTypes.removeOwner(owner);
            }
            scopedTypes.count -= removedTypes;
            if (removedTypes > 0) scopedTypes.typesByOwner.remove(owner);
            scopedTypes.domains.entrySet().removeIf(entry -> entry.getValue().size() == 0);
            if (scopedTypes.domains.isEmpty()) TYPES.remove(scope);
        }
    }

    private static String apply(Definition definition, LuaTable result) {
        merge(result, definition.defaults());
        return definition.base();
    }

    private static boolean isReservedBuilderName(Map<String, String> builders, String qualifiedName, String localName) {
        // Keep legacy unqualified Java builder names reserved: otherwise a Lua content type
        // with the same local selector would shadow it during resolution. Namespaced Java
        // builders only reserve their exact ID, so addons can independently use the same path.
        return builders.containsKey(localName) || builders.containsKey(qualifiedName);
    }

    private static Definition find(MLuaRegistration scope, String domain, String owner, String name) {
        dev.mlua.api.registry.MLuaRegistry<Definition> domainTypes = domainRegistry(scope, domain, false);
        if (domainTypes == null) return null;
        return domainTypes.get(normalizeReference(owner, name));
    }

    private static dev.mlua.api.registry.MLuaRegistry<Definition> domainRegistry(MLuaRegistration scope, String domain, boolean create) {
        synchronized (TYPES) {
            ScopeTypes scopedTypes = scopeTypes(scope, create);
            return scopedTypes == null ? null : scopedTypes.domain(domain, create);
        }
    }

    private static ScopeTypes scopeTypes(MLuaRegistration scope, boolean create) {
        ScopeTypes scopedTypes = TYPES.get(scope);
        if (scopedTypes == null && create) {
            scopedTypes = new ScopeTypes();
            TYPES.put(scope, scopedTypes);
        }
        return scopedTypes;
    }

    private static void validateEntryLimit(LuaTable table, String label) {
        int count = countEntries(table, 0);
        if (count > MAX_DEFAULT_ENTRIES_PER_TYPE) {
            throw new LuaError("[MLua] " + label + " exceeds the table entry limit of "
                + MAX_DEFAULT_ENTRIES_PER_TYPE);
        }
    }

    private static int countEntries(LuaTable table, int count) {
        LuaValue key = LuaValue.NIL;
        while (true) {
            Varargs entry = table.next(key);
            key = entry.arg1();
            if (key.isnil()) return count;
            if (++count > MAX_DEFAULT_ENTRIES_PER_TYPE) return count;
            if (key.istable()) count = countEntries(key.checktable(), count);
            LuaValue value = entry.arg(2);
            if (value.istable()) count = countEntries(value.checktable(), count);
            if (count > MAX_DEFAULT_ENTRIES_PER_TYPE) return count;
        }
    }

    private static final class ScopeTypes {
        private final Map<String, dev.mlua.api.registry.MLuaRegistry<Definition>> domains = new java.util.HashMap<>();
        private final Map<String, Integer> typesByOwner = new java.util.HashMap<>();
        private final Map<String, Integer> defaultEntriesByOwner = new java.util.HashMap<>();
        private int count;
        private int defaultEntries;

        private void removeType(String owner) {
            int remaining = typesByOwner.getOrDefault(owner, 0) - 1;
            if (remaining <= 0) typesByOwner.remove(owner);
            else typesByOwner.put(owner, remaining);
        }

        private void removeDefaultEntries(String owner, int entries) {
            defaultEntries -= entries;
            int remaining = defaultEntriesByOwner.getOrDefault(owner, 0) - entries;
            if (remaining <= 0) defaultEntriesByOwner.remove(owner);
            else defaultEntriesByOwner.put(owner, remaining);
        }

        private dev.mlua.api.registry.MLuaRegistry<Definition> domain(String name, boolean create) {
            dev.mlua.api.registry.MLuaRegistry<Definition> registry = this.domains.get(name);
            if (registry == null && create) {
                registry = new dev.mlua.api.registry.MLuaRegistry<>();
                this.domains.put(name, registry);
            }
            return registry;
        }
    }

    private static String normalizeDomain(String domain) {
        if (domain == null || domain.isBlank()) throw new LuaError("[MLua] Content type domain must be non-empty");
        String normalized = domain.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() > MAX_DOMAIN_LENGTH) {
            throw new LuaError("[MLua] Content type domain cannot exceed " + MAX_DOMAIN_LENGTH + " characters");
        }
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
        if (owner.length() > MAX_OWNER_LENGTH) throw new LuaError("[MLua] Content type owner is too long");
        String normalized = name.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
            if (!normalized.matches("[a-z][a-z0-9_./-]*")) throw new LuaError("[MLua] Invalid content type reference '" + name + "'");
            normalized = owner + ":" + normalized;
        }
        if (normalized.length() > MAX_QUALIFIED_NAME_LENGTH) {
            throw new LuaError("[MLua] Content type reference cannot exceed " + MAX_QUALIFIED_NAME_LENGTH + " characters");
        }
        return normalized;
    }

    private static String qualify(String owner, String name) {
        if (owner == null || owner.isBlank()) throw new LuaError("[MLua] Content type owner must be non-empty");
        if (name == null || name.isBlank()) throw new LuaError("[MLua] Content type name must be non-empty");
        if (owner.length() > MAX_OWNER_LENGTH) throw new LuaError("[MLua] Content type owner is too long");
        String normalized = name.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
            if (!normalized.matches("[a-z][a-z0-9_./-]*")) throw new LuaError("[MLua] Invalid content type name '" + name + "'");
            normalized = owner + ":" + normalized;
        }
        if (normalized.length() > MAX_QUALIFIED_NAME_LENGTH) {
            throw new LuaError("[MLua] Content type name cannot exceed " + MAX_QUALIFIED_NAME_LENGTH + " characters");
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

    private static void validateAcyclic(LuaTable table, String label) {
        validateAcyclic(table, label, new java.util.IdentityHashMap<>(), new java.util.IdentityHashMap<>(), 0);
    }

    private static void validateAcyclic(LuaTable table, String label,
                                        java.util.IdentityHashMap<LuaTable, Boolean> active,
                                        java.util.IdentityHashMap<LuaTable, Boolean> visited, int depth) {
        if (depth >= MAX_TABLE_DEPTH) {
            throw new LuaError("[MLua] " + label + " cannot be nested more than " + MAX_TABLE_DEPTH + " tables deep");
        }
        if (active.containsKey(table)) {
            throw new LuaError("[MLua] " + label + " cannot contain cyclic tables");
        }
        if (visited.containsKey(table)) return;
        active.put(table, Boolean.TRUE);
        LuaValue key = LuaValue.NIL;
        while (true) {
            Varargs entry = table.next(key);
            key = entry.arg1();
            if (key.isnil()) break;
            LuaValue value = entry.arg(2);
            if (key.istable()) validateAcyclic(key.checktable(), label, active, visited, depth + 1);
            if (value.istable()) validateAcyclic(value.checktable(), label, active, visited, depth + 1);
        }
        active.remove(table);
        visited.put(table, Boolean.TRUE);
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

    private record Definition(String key, String base, String description, LuaTable defaults, int defaultEntries) { }

    public record Limits(int activeInDomain, int activeTotal) { }
    public record Usage(int activeTotal, int activeForOwner, int defaultTableEntries, int defaultTableEntriesForOwner) { }
}
