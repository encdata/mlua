package dev.mlua.runtime.registry;

import dev.mlua.api.registry.MLuaRegistry;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
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
    private final Map<String, CategoryState> categoryStates = new HashMap<>();

    public synchronized boolean register(Category category) {
        Objects.requireNonNull(category, "category");
        String key = key(category.name());
        if (!this.categories.register(category.owner(), key, category)) return false;
        this.categoryStates.put(key, new CategoryState());
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
        this.categoryStates.remove(key);
        return true;
    }

    /** Reserves an id until the callback either stores a definition or fails. */
    public synchronized DefinitionReservation beginDefinition(String category, String id, String owner) {
        String key = key(category);
        requireText(id, "definition id");
        requireText(owner, "definition owner");
        CategoryState state = this.categoryStates.get(key);
        if (state == null || state.definitions.contains(id) || state.pending.containsKey(id)) return null;
        DefinitionReservation reservation = new DefinitionReservation(key, id, owner);
        state.pending.put(id, reservation);
        return reservation;
    }

    /** Clears only the exact reservation created by beginDefinition. */
    public synchronized void cancelPendingDefinition(DefinitionReservation reservation) {
        if (reservation == null) return;
        CategoryState state = this.categoryStates.get(reservation.category());
        if (state != null) state.pending.remove(reservation.id(), reservation);
    }

    public synchronized DefinitionHandle registerDefinition(DefinitionReservation reservation, LuaTable definition) {
        if (reservation == null) return null;
        CategoryState state = this.categoryStates.get(reservation.category());
        if (state == null || state.pending.get(reservation.id()) != reservation
            || state.definitions.contains(reservation.id())) return null;
        DefinitionHandle stored = new DefinitionHandle(reservation.owner(), copyTable(definition));
        return state.definitions.register(reservation.owner(), reservation.id(), stored) ? stored : null;
    }

    public synchronized void unregisterDefinition(String category, String id, DefinitionHandle expected) {
        CategoryState state = this.categoryStates.get(key(category));
        if (state != null && expected != null) state.definitions.remove(expected.owner(), id, expected);
    }

    public synchronized void unregisterOwner(String ownerModId) {
        this.categories.removeOwner(ownerModId);
        List<String> removedCategories = this.categoryStates.keySet().stream()
            .filter(key -> this.categories.get(key) == null).toList();
        removedCategories.forEach(this.categoryStates::remove);
        this.categoryStates.values().forEach(state -> {
            state.definitions.removeOwner(ownerModId);
            state.pending.entrySet().removeIf(entry -> ownerModId.equals(entry.getValue().owner()));
        });
    }

    public synchronized LuaTable definition(String category, String id) {
        CategoryState state = this.categoryStates.get(key(category));
        DefinitionHandle definition = state == null ? null : state.definitions.get(id);
        return definition == null ? null : copyTable(definition.value());
    }

    public synchronized boolean containsDefinition(String category, String id) {
        CategoryState state = this.categoryStates.get(key(category));
        return state != null && state.definitions.contains(id);
    }

    public synchronized List<String> ids(String category, String namespace) {
        CategoryState state = this.categoryStates.get(key(category));
        if (state == null) return List.of();
        return state.definitions.snapshot().keySet().stream().filter(id -> namespace == null || id.startsWith(namespace + ":"))
            .sorted().toList();
    }

    public synchronized LuaTable definitions(String category, String namespace) {
        LuaTable result = new LuaTable();
        CategoryState state = this.categoryStates.get(key(category));
        if (state != null) state.definitions.snapshot().entrySet().stream()
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
        return requireText(value, "category name").toLowerCase(Locale.ROOT);
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must be non-empty");
        return value.trim();
    }

    private static final class CategoryState {
        private final MLuaRegistry<DefinitionHandle> definitions = new MLuaRegistry<>();
        private final Map<String, DefinitionReservation> pending = new HashMap<>();
    }

    /** Opaque ticket; only the catalog that created it can complete or cancel it. */
    public static final class DefinitionReservation {
        private final String category;
        private final String id;
        private final String owner;

        private DefinitionReservation(String category, String id, String owner) {
            this.category = category;
            this.id = id;
            this.owner = owner;
        }

        private String category() { return this.category; }
        private String id() { return this.id; }
        private String owner() { return this.owner; }
    }

    public record Category(String name, String description, String domain, String owner, LuaValue register) {
        public Category {
            name = requireText(name, "category name");
            description = requireText(description, "category description");
            domain = requireText(domain, "category content type domain");
            owner = requireText(owner, "category owner");
            Objects.requireNonNull(register, "category register callback");
            if (!register.isfunction()) throw new IllegalArgumentException("category register callback must be a function");
            int namespaceEnd = name.indexOf(':');
            if (namespaceEnd < 1 || !owner.equals(name.substring(0, namespaceEnd))) {
                throw new IllegalArgumentException("category must be namespaced to its owner");
            }
        }
    }

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
