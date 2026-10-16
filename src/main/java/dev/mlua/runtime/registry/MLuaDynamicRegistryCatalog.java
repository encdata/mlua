package dev.mlua.runtime.registry;

import dev.mlua.api.registry.MLuaRegistry;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

/**
 * Runtime-owned state for addon-defined registry categories and their entries.
 * Lua-facing registration and Minecraft adapters stay in MLuaRegistryService;
 * this catalog owns snapshots, duplicate reservations, and lifecycle cleanup.
 */
public final class MLuaDynamicRegistryCatalog {
    private final MLuaRegistry<Category> categories = new MLuaRegistry<>();
    private final Map<String, MLuaRegistry<DefinitionHandle>> definitions = new HashMap<>();
    private final Map<String, Map<String, String>> pendingDefinitions = new HashMap<>();

    public synchronized boolean register(Category category) {
        String key = key(category.name());
        if (!this.categories.register(category.owner(), key, category)) return false;
        this.definitions.put(key, new MLuaRegistry<>());
        this.pendingDefinitions.put(key, new HashMap<>());
        return true;
    }

    public synchronized List<Category> categories() {
        return List.copyOf(this.categories.snapshot().values());
    }

    public synchronized Category category(String name) {
        return this.categories.get(key(name));
    }

    public synchronized boolean unregister(String name, Category expected) {
        String key = key(name);
        if (!this.categories.remove(expected.owner(), key, expected)) return false;
        this.definitions.remove(key);
        this.pendingDefinitions.remove(key);
        return true;
    }

    public synchronized boolean beginDefinition(String category, String id, String owner) {
        String key = key(category);
        MLuaRegistry<DefinitionHandle> values = this.definitions.get(key);
        Map<String, String> pending = this.pendingDefinitions.get(key);
        if (values == null || pending == null || values.contains(id) || pending.containsKey(id)) return false;
        pending.put(id, owner);
        return true;
    }

    public synchronized void cancelPendingDefinition(String category, String id) {
        Map<String, String> pending = this.pendingDefinitions.get(key(category));
        if (pending != null) pending.remove(id);
    }

    public synchronized DefinitionHandle registerDefinition(String category, String id,
                                                            String owner, LuaTable definition) {
        String key = key(category);
        MLuaRegistry<DefinitionHandle> values = this.definitions.get(key);
        Map<String, String> pending = this.pendingDefinitions.get(key);
        if (values == null || pending == null || !owner.equals(pending.get(id)) || values.contains(id)) return null;
        DefinitionHandle stored = new DefinitionHandle(owner, copyTable(definition));
        return values.register(owner, id, stored) ? stored : null;
    }

    public synchronized void unregisterDefinition(String category, String id, DefinitionHandle expected) {
        MLuaRegistry<DefinitionHandle> values = this.definitions.get(key(category));
        if (values != null && expected != null) values.remove(expected.owner(), id, expected);
    }

    public synchronized void unregisterOwner(String ownerModId) {
        this.categories.removeOwner(ownerModId);
        List<String> removedCategories = this.definitions.keySet().stream()
            .filter(key -> this.categories.get(key) == null).toList();
        removedCategories.forEach(key -> {
            this.definitions.remove(key);
            this.pendingDefinitions.remove(key);
        });
        this.definitions.values().forEach(values -> values.removeOwner(ownerModId));
        this.pendingDefinitions.values().forEach(pending -> pending.entrySet()
            .removeIf(entry -> ownerModId.equals(entry.getValue())));
    }

    public synchronized LuaTable definition(String category, String id) {
        MLuaRegistry<DefinitionHandle> values = this.definitions.get(key(category));
        DefinitionHandle definition = values == null ? null : values.get(id);
        return definition == null ? null : copyTable(definition.value());
    }

    public synchronized boolean containsDefinition(String category, String id) {
        MLuaRegistry<DefinitionHandle> values = this.definitions.get(key(category));
        return values != null && values.contains(id);
    }

    public synchronized List<String> ids(String category, String namespace) {
        MLuaRegistry<DefinitionHandle> values = this.definitions.get(key(category));
        if (values == null) return List.of();
        return values.snapshot().keySet().stream().filter(id -> namespace == null || id.startsWith(namespace + ":"))
            .sorted().toList();
    }

    public synchronized LuaTable definitions(String category, String namespace) {
        LuaTable result = new LuaTable();
        MLuaRegistry<DefinitionHandle> values = this.definitions.get(key(category));
        if (values != null) values.snapshot().entrySet().stream()
            .filter(entry -> namespace == null || entry.getKey().startsWith(namespace + ":"))
            .forEach(entry -> result.set(entry.getKey(), copyTable(entry.getValue().value())));
        return result;
    }

    public static LuaTable copyTable(LuaTable source) {
        return copyTable(source, new java.util.IdentityHashMap<>());
    }

    private static LuaTable copyTable(LuaTable source, Map<LuaTable, LuaTable> copies) {
        LuaTable existing = copies.get(source);
        if (existing != null) return existing;
        LuaTable copy = new LuaTable();
        copies.put(source, copy);
        LuaValue key = LuaValue.NIL;
        while (true) {
            Varargs entry = source.next(key);
            key = entry.arg1();
            if (key.isnil()) return copy;
            LuaValue value = entry.arg(2);
            LuaValue copiedKey = key.istable() ? copyTable(key.checktable(), copies) : key;
            LuaValue copiedValue = value.istable() ? copyTable(value.checktable(), copies) : value;
            copy.rawset(copiedKey, copiedValue);
        }
    }

    private static String key(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    public record Category(String name, String description, String domain, String owner, LuaValue register) { }

    public static final class DefinitionHandle {
        private final String owner;
        private final LuaTable value;

        private DefinitionHandle(String owner, LuaTable value) {
            this.owner = owner;
            this.value = value;
        }

        private String owner() { return this.owner; }
        private LuaTable value() { return this.value; }
    }
}
