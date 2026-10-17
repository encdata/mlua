package dev.mlua.runtime.registry;

import dev.mlua.api.registry.MLuaRegistry;
import dev.mlua.core.MLuaContext;
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
    private static final int MAX_TABLE_DEPTH = 64;
    public static final int MAX_CATEGORIES = 256;
    public static final int MAX_DEFINITIONS_PER_CATEGORY = 16_384;
    public static final int MAX_TOTAL_DEFINITIONS = 65_536;
    public static final int MAX_PENDING_PER_CATEGORY = 128;
    public static final int MAX_TABLE_ENTRIES_PER_DEFINITION = 16_384;
    private final MLuaRegistry<Category> categories = new MLuaRegistry<>();
    private final Map<String, CategoryState> categoryStates = new HashMap<>();
    private int definitionCount;
    private int pendingDefinitionCount;

    public synchronized boolean register(Category category) {
        Objects.requireNonNull(category, "category");
        String key = key(category.name());
        if (this.categories.size() >= MAX_CATEGORIES && this.categories.get(key) == null) {
            throw new org.luaj.vm2.LuaError("[MLua] Dynamic registry category limit reached (" + MAX_CATEGORIES + ")");
        }
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
        CategoryState removed = this.categoryStates.remove(key);
        if (removed != null) {
            this.definitionCount -= removed.definitions.size();
            this.pendingDefinitionCount -= removed.pending.size();
        }
        return true;
    }

    /** Reserves an id until the callback either stores a definition or fails. */
    public synchronized DefinitionReservation beginDefinition(String category, String id, String owner) {
        String key = key(category);
        requireText(id, "definition id");
        requireText(owner, "definition owner");
        CategoryState state = this.categoryStates.get(key);
        if (state == null || state.definitions.contains(id) || state.pending.containsKey(id)) return null;
        if (state.definitions.size() + state.pending.size() >= MAX_DEFINITIONS_PER_CATEGORY) {
            throw new org.luaj.vm2.LuaError("[MLua] Definition limit reached for registry category '"
                + category + "' (" + MAX_DEFINITIONS_PER_CATEGORY + ")");
        }
        if (this.definitionCount + this.pendingDefinitionCount >= MAX_TOTAL_DEFINITIONS) {
            throw new org.luaj.vm2.LuaError("[MLua] Dynamic registry definition limit reached ("
                + MAX_TOTAL_DEFINITIONS + ")");
        }
        if (state.pending.size() >= MAX_PENDING_PER_CATEGORY) {
            throw new org.luaj.vm2.LuaError("[MLua] Pending registration limit reached for registry category '"
                + category + "' (" + MAX_PENDING_PER_CATEGORY + ")");
        }
        DefinitionReservation reservation = new DefinitionReservation(key, id, owner);
        state.pending.put(id, reservation);
        this.pendingDefinitionCount++;
        return reservation;
    }

    /** Clears only the exact reservation created by beginDefinition. */
    public synchronized void cancelPendingDefinition(DefinitionReservation reservation) {
        if (reservation == null) return;
        CategoryState state = this.categoryStates.get(reservation.category());
        if (state != null && state.pending.remove(reservation.id(), reservation)) this.pendingDefinitionCount--;
    }

    public synchronized DefinitionHandle registerDefinition(DefinitionReservation reservation, LuaTable definition) {
        if (reservation == null) return null;
        CategoryState state = this.categoryStates.get(reservation.category());
        if (state == null || state.pending.get(reservation.id()) != reservation
            || state.definitions.contains(reservation.id())) return null;
        DefinitionHandle stored = new DefinitionHandle(reservation.owner(), copyTable(definition));
        if (!state.definitions.register(reservation.owner(), reservation.id(), stored)) return null;
        this.definitionCount++;
        return stored;
    }

    public synchronized void unregisterDefinition(String category, String id, DefinitionHandle expected) {
        CategoryState state = this.categoryStates.get(key(category));
        if (state != null && expected != null && state.definitions.remove(expected.owner(), id, expected)) {
            this.definitionCount--;
        }
    }

    public synchronized void unregisterOwner(String ownerModId) {
        this.categories.removeOwner(ownerModId);
        List<String> removedCategories = this.categoryStates.keySet().stream()
            .filter(key -> this.categories.get(key) == null).toList();
        removedCategories.forEach(key -> {
            CategoryState removed = this.categoryStates.remove(key);
            if (removed != null) {
                this.definitionCount -= removed.definitions.size();
                this.pendingDefinitionCount -= removed.pending.size();
            }
        });
        this.categoryStates.values().forEach(state -> {
            this.definitionCount -= state.definitions.removeOwner(ownerModId);
            int removedPending = (int) state.pending.values().stream()
                .filter(reservation -> ownerModId.equals(reservation.owner())).count();
            state.pending.entrySet().removeIf(entry -> ownerModId.equals(entry.getValue().owner()));
            this.pendingDefinitionCount -= removedPending;
        });
    }

    public synchronized LuaTable definition(String category, String id) {
        CategoryState state = this.categoryStates.get(key(category));
        DefinitionHandle definition = state == null ? null : state.definitions.get(id);
        return definition == null ? null : copyTable(definition.value());
    }

    public synchronized String definitionOwner(String category, String id) {
        CategoryState state = this.categoryStates.get(key(category));
        DefinitionHandle definition = state == null ? null : state.definitions.get(id);
        return definition == null ? null : definition.owner();
    }

    public synchronized String categoryDescription(String category) {
        Category value = this.categories.get(key(category));
        return value == null ? null : value.description();
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

    public static LuaTable copyTable(LuaTable source) {
        return copyTable(source, new java.util.IdentityHashMap<>(), 0,
            new CopyBudget(MAX_TABLE_ENTRIES_PER_DEFINITION));
    }

    private static LuaTable copyTable(LuaTable source, Map<LuaTable, LuaTable> copies, int depth, CopyBudget budget) {
        if (depth > MAX_TABLE_DEPTH) {
            throw new org.luaj.vm2.LuaError("[MLua] Registry definitions cannot be nested more than "
                + MAX_TABLE_DEPTH + " tables deep");
        }
        LuaTable existing = copies.get(source);
        if (existing != null) return existing;
        LuaTable copy = new LuaTable();
        copies.put(source, copy);
        LuaValue key = LuaValue.NIL;
        while (true) {
            Varargs entry = source.next(key);
            key = entry.arg1();
            if (key.isnil()) return copy;
            if (--budget.remaining < 0) {
                throw new org.luaj.vm2.LuaError("[MLua] Registry definition exceeds the table entry limit of "
                    + MAX_TABLE_ENTRIES_PER_DEFINITION);
            }
            LuaValue value = entry.arg(2);
            LuaValue copiedKey = key.istable() ? copyTable(key.checktable(), copies, depth + 1, budget) : key;
            LuaValue copiedValue = value.istable() ? copyTable(value.checktable(), copies, depth + 1, budget) : value;
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

    private static final class CopyBudget {
        private int remaining;

        private CopyBudget(int remaining) {
            this.remaining = remaining;
        }
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

    public record Category(String name, String description, String domain, String owner,
                           LuaValue register, MLuaContext context) {
        public Category {
            name = requireText(name, "category name");
            description = requireText(description, "category description");
            domain = requireText(domain, "category content type domain");
            owner = requireText(owner, "category owner");
            Objects.requireNonNull(register, "category register callback");
            if (!register.isfunction()) throw new IllegalArgumentException("category register callback must be a function");
            Objects.requireNonNull(context, "category register context");
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
