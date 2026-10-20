package dev.mlua.runtime.registry;

import dev.mlua.api.registry.MLuaRegistry;
import dev.mlua.core.MLuaContext;
import dev.mlua.minecraft.MLuaResourceLocations;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.LuaError;

/**
 * Runtime-owned state for addon-defined registry categories and their entries.
 * Lua-facing registration and Minecraft adapters stay in MLuaRegistryService;
 * this catalog owns snapshots, duplicate reservations, and lifecycle cleanup.
 */
public final class MLuaDynamicRegistryCatalog {
    public static final int MAX_TABLE_DEPTH = 64;
    public static final int MAX_CATEGORIES = 256;
    public static final int MAX_CATEGORIES_PER_OWNER = 64;
    public static final int MAX_CATEGORY_NAME_LENGTH = 256;
    public static final int MAX_CATEGORY_DESCRIPTION_LENGTH = 2_048;
    public static final int MAX_CATEGORY_DOMAIN_LENGTH = 1_024;
    public static final int MAX_CATEGORY_OWNER_LENGTH = 128;
    public static final int MAX_DEFINITION_ID_LENGTH = 256;
    public static final int MAX_DEFINITIONS_PER_CATEGORY = 16_384;
    public static final int MAX_DEFINITIONS_PER_OWNER = 16_384;
    public static final int MAX_TOTAL_DEFINITIONS = 65_536;
    public static final int MAX_PENDING_PER_CATEGORY = 128;
    public static final int MAX_TABLE_ENTRIES_PER_DEFINITION = 16_384;
    public static final int MAX_DEFINITION_TABLE_ENTRIES_PER_OWNER = 262_144;
    public static final int MAX_TOTAL_DEFINITION_TABLE_ENTRIES = 1_048_576;
    private final MLuaRegistry<Category> categories = new MLuaRegistry<>();
    private final Map<String, CategoryState> categoryStates = new HashMap<>();
    private final Map<String, Integer> definitionsByOwner = new HashMap<>();
    private final Map<String, Integer> definitionTableEntriesByOwner = new HashMap<>();
    private final Map<String, Integer> pendingTableEntriesByOwner = new HashMap<>();
    private final Map<String, Integer> pendingByOwner = new HashMap<>();
    private int definitionCount;
    private int definitionTableEntryCount;
    private int pendingDefinitionCount;
    private int pendingDefinitionTableEntryCount;

    public synchronized boolean register(Category category) {
        Objects.requireNonNull(category, "category");
        String key = key(category.name());
        if (this.categories.size() >= MAX_CATEGORIES && this.categories.get(key) == null) {
            throw new LuaError("[MLua] Dynamic registry category limit reached (" + MAX_CATEGORIES + ")");
        }
        if (this.categories.get(key) == null && this.categories.snapshotEntries().values().stream()
            .filter(entry -> category.owner().equals(entry.owner())).count() >= MAX_CATEGORIES_PER_OWNER) {
            throw new LuaError("[MLua] Addon '" + category.owner()
                + "' reached the dynamic registry category limit (" + MAX_CATEGORIES_PER_OWNER + ")");
        }
        if (!this.categories.register(category.owner(), key, category)) return false;
        this.categoryStates.put(key, new CategoryState());
        return true;
    }

    public synchronized List<Category> categories() {
        return List.copyOf(this.categories.snapshot().values());
    }

    /** Returns a consistent view of dynamic registry usage for diagnostics. */
    public synchronized Usage usage(String owner) {
        int ownedCategories = 0;
        for (MLuaRegistry.OwnedValue<Category> entry : this.categories.snapshotEntries().values()) {
            if (owner != null && owner.equals(entry.owner())) ownedCategories++;
        }
        int ownedDefinitions = owner == null ? 0 : this.definitionsByOwner.getOrDefault(owner, 0);
        int ownedDefinitionTableEntries = owner == null ? 0
            : this.definitionTableEntriesByOwner.getOrDefault(owner, 0);
        int ownedPending = owner == null ? 0 : this.pendingByOwner.getOrDefault(owner, 0);
        return new Usage(this.categories.size(), ownedCategories, this.definitionCount,
            ownedDefinitions, this.pendingDefinitionCount, ownedPending,
            this.definitionTableEntryCount, ownedDefinitionTableEntries);
    }

    public synchronized Category category(String name) {
        return this.categories.get(key(name));
    }

    public boolean unregister(String name, Category expected) {
        if (expected == null) return false;
        String key = key(name);
        Category current;
        synchronized (this) {
            current = this.categories.get(key);
            if (current != expected) return false;
        }
        synchronized (current.context().executionLock()) {
            synchronized (this) {
                if (this.categories.get(key) != expected
                    || !this.categories.remove(expected.owner(), key, expected)) return false;
                CategoryState removed = this.categoryStates.remove(key);
                if (removed != null) this.removeCategoryState(removed);
                return true;
            }
        }
    }

    /** Reserves an id until the callback either stores a definition or fails. */
    public synchronized DefinitionReservation beginDefinition(String category, String id, String owner) {
        return beginDefinition(category, id, owner, (TableSnapshot) null);
    }

    /**
     * Validates and snapshots a definition before its category callback runs, then reserves
     * both the id and its share of the bounded catalog storage.
     */
    public DefinitionReservation beginDefinition(String category, String id, String owner, LuaTable definition) {
        TableSnapshot snapshot = definition == null ? null : copyTableSnapshot(definition);
        return beginDefinition(category, id, owner, snapshot);
    }

    private synchronized DefinitionReservation beginDefinition(String category, String id, String owner,
                                                                TableSnapshot snapshot) {
        String key = key(category);
        id = requireBoundedText(id, "definition id", MAX_DEFINITION_ID_LENGTH);
        owner = requireBoundedText(owner, "definition owner", MAX_CATEGORY_OWNER_LENGTH);
        requireOwnedDefinitionId(id, owner);
        CategoryState state = this.categoryStates.get(key);
        if (state == null || state.definitions.contains(id) || state.pending.containsKey(id)) return null;
        if (state.definitions.size() + state.pending.size() >= MAX_DEFINITIONS_PER_CATEGORY) {
            throw new LuaError("[MLua] Definition limit reached for registry category '"
                + category + "' (" + MAX_DEFINITIONS_PER_CATEGORY + ")");
        }
        if (this.definitionCount + this.pendingDefinitionCount >= MAX_TOTAL_DEFINITIONS) {
            throw new LuaError("[MLua] Dynamic registry definition limit reached ("
                + MAX_TOTAL_DEFINITIONS + ")");
        }
        if (this.definitionsByOwner.getOrDefault(owner, 0) + this.pendingByOwner.getOrDefault(owner, 0)
            >= MAX_DEFINITIONS_PER_OWNER) {
            throw new LuaError("[MLua] Addon '" + owner + "' reached the dynamic registry definition limit ("
                + MAX_DEFINITIONS_PER_OWNER + ")");
        }
        if (state.pending.size() >= MAX_PENDING_PER_CATEGORY) {
            throw new LuaError("[MLua] Pending registration limit reached for registry category '"
                + category + "' (" + MAX_PENDING_PER_CATEGORY + ")");
        }
        int tableEntries = snapshot == null ? 0 : snapshot.entryCount();
        if (this.definitionTableEntryCount + this.pendingDefinitionTableEntryCount + tableEntries
                > MAX_TOTAL_DEFINITION_TABLE_ENTRIES) {
            throw new LuaError("[MLua] Dynamic registry table-entry limit reached ("
                + MAX_TOTAL_DEFINITION_TABLE_ENTRIES + ")");
        }
        if (this.definitionTableEntriesByOwner.getOrDefault(owner, 0)
                + this.pendingTableEntriesByOwner.getOrDefault(owner, 0) + tableEntries
                > MAX_DEFINITION_TABLE_ENTRIES_PER_OWNER) {
            throw new LuaError("[MLua] Addon '" + owner
                + "' reached the dynamic registry table-entry limit ("
                + MAX_DEFINITION_TABLE_ENTRIES_PER_OWNER + ")");
        }
        DefinitionReservation reservation = new DefinitionReservation(key, id, owner, snapshot);
        state.pending.put(id, reservation);
        this.pendingDefinitionCount++;
        this.pendingByOwner.merge(owner, 1, Integer::sum);
        this.pendingDefinitionTableEntryCount += tableEntries;
        if (tableEntries > 0) this.pendingTableEntriesByOwner.merge(owner, tableEntries, Integer::sum);
        return reservation;
    }

    /** Clears only the exact reservation created by beginDefinition. */
    public synchronized void cancelPendingDefinition(DefinitionReservation reservation) {
        if (reservation == null) return;
        CategoryState state = this.categoryStates.get(reservation.category());
        if (state != null && state.pending.remove(reservation.id(), reservation)) {
            this.pendingDefinitionCount--;
            decrement(this.pendingByOwner, reservation.owner());
            this.removePendingTableEntries(reservation.owner(), reservation.tableEntryCount());
        }
    }

    /** Returns a detached callback argument from the reservation's prevalidated snapshot. */
    public synchronized LuaTable callbackDefinition(DefinitionReservation reservation) {
        if (reservation == null || reservation.snapshot() == null) return null;
        CategoryState state = this.categoryStates.get(reservation.category());
        if (state == null || state.pending.get(reservation.id()) != reservation) return null;
        return copyTable(reservation.snapshot().value());
    }

    /** Commits the exact immutable snapshot whose capacity was reserved before the callback. */
    public synchronized DefinitionHandle registerDefinition(DefinitionReservation reservation) {
        if (reservation == null || reservation.snapshot() == null) return null;
        CategoryState state = this.categoryStates.get(reservation.category());
        if (state == null || state.pending.get(reservation.id()) != reservation
            || state.definitions.contains(reservation.id())) return null;
        DefinitionHandle stored = new DefinitionHandle(reservation.category(), state.generation,
            reservation.id(), reservation.owner(), reservation.snapshot().value(), reservation.tableEntryCount());
        if (!state.definitions.register(reservation.owner(), reservation.id(), stored)) return null;
        state.pending.remove(reservation.id(), reservation);
        this.pendingDefinitionCount--;
        decrement(this.pendingByOwner, reservation.owner());
        this.removePendingTableEntries(reservation.owner(), reservation.tableEntryCount());
        this.definitionCount++;
        this.definitionsByOwner.merge(reservation.owner(), 1, Integer::sum);
        this.definitionTableEntryCount += reservation.tableEntryCount();
        if (reservation.tableEntryCount() > 0) {
            this.definitionTableEntriesByOwner.merge(reservation.owner(), reservation.tableEntryCount(), Integer::sum);
        }
        return stored;
    }

    /** @deprecated Reserve with a definition before executing the category callback. */
    @Deprecated(forRemoval = false)
    public synchronized DefinitionHandle registerDefinition(DefinitionReservation reservation, LuaTable definition) {
        if (reservation == null) return null;
        Objects.requireNonNull(definition, "definition");
        CategoryState state = this.categoryStates.get(reservation.category());
        if (state == null || state.pending.get(reservation.id()) != reservation
            || state.definitions.contains(reservation.id())) return null;
        TableSnapshot snapshot = copyTableSnapshot(definition);
        if (this.definitionTableEntryCount + this.pendingDefinitionTableEntryCount + snapshot.entryCount()
                > MAX_TOTAL_DEFINITION_TABLE_ENTRIES) {
            throw new LuaError("[MLua] Dynamic registry table-entry limit reached ("
                + MAX_TOTAL_DEFINITION_TABLE_ENTRIES + ")");
        }
        if (this.definitionTableEntriesByOwner.getOrDefault(reservation.owner(), 0)
                + this.pendingTableEntriesByOwner.getOrDefault(reservation.owner(), 0) + snapshot.entryCount()
                > MAX_DEFINITION_TABLE_ENTRIES_PER_OWNER) {
            throw new LuaError("[MLua] Addon '" + reservation.owner()
                + "' reached the dynamic registry table-entry limit ("
                + MAX_DEFINITION_TABLE_ENTRIES_PER_OWNER + ")");
        }
        DefinitionHandle stored = new DefinitionHandle(reservation.category(), state.generation,
            reservation.id(), reservation.owner(), snapshot.value(), snapshot.entryCount());
        if (!state.definitions.register(reservation.owner(), reservation.id(), stored)) return null;
        state.pending.remove(reservation.id(), reservation);
        this.pendingDefinitionCount--;
        decrement(this.pendingByOwner, reservation.owner());
        this.removePendingTableEntries(reservation.owner(), reservation.tableEntryCount());
        this.definitionCount++;
        this.definitionsByOwner.merge(reservation.owner(), 1, Integer::sum);
        this.definitionTableEntryCount += snapshot.entryCount();
        if (snapshot.entryCount() > 0) {
            this.definitionTableEntriesByOwner.merge(reservation.owner(), snapshot.entryCount(), Integer::sum);
        }
        return stored;
    }

    public void unregisterDefinition(String category, String id, DefinitionHandle expected) {
        if (expected == null) return;
        String key = key(category);
        while (true) {
            Category current;
            synchronized (this) {
                if (this.categoryStates.get(key) == null) return;
                current = this.categories.get(key);
            }
            if (current == null) {
                synchronized (this) {
                    if (this.categories.get(key) != null) continue;
                    this.removeDefinition(key, id, expected);
                    return;
                }
            }
            synchronized (current.context().executionLock()) {
                synchronized (this) {
                    if (this.categoryStates.get(key) == null) return;
                    if (this.categories.get(key) != current) continue;
                    this.removeDefinition(key, id, expected);
                    return;
                }
            }
        }
    }

    public void unregisterOwner(String ownerModId) {
        if (ownerModId == null || ownerModId.isBlank()) return;
        List<String> categories;
        synchronized (this) {
            categories = this.categoryStates.keySet().stream().sorted().toList();
        }
        for (String category : categories) {
            this.unregisterOwner(category, ownerModId);
        }
    }

    /** Removes one addon's category or definitions from a category after its callback context is quiescent. */
    public void unregisterOwner(String categoryName, String ownerModId) {
        if (categoryName == null || categoryName.isBlank() || ownerModId == null || ownerModId.isBlank()) return;
        String key = key(categoryName);
        while (true) {
            Category current;
            synchronized (this) {
                if (this.categoryStates.get(key) == null) return;
                current = this.categories.get(key);
            }
            if (current == null) {
                synchronized (this) {
                    if (this.categories.get(key) != null) continue;
                    this.unregisterOwnerLocked(key, ownerModId);
                    return;
                }
            }
            synchronized (current.context().executionLock()) {
                synchronized (this) {
                    if (this.categoryStates.get(key) == null) return;
                    if (this.categories.get(key) != current) continue;
                    this.unregisterOwnerLocked(key, ownerModId);
                    return;
                }
            }
        }
    }

    private void unregisterOwnerLocked(String key, String ownerModId) {
        CategoryState state = this.categoryStates.get(key);
        if (state == null) return;
        Category category = this.categories.get(key);
        if (category != null && ownerModId.equals(category.owner())) {
            if (this.categories.remove(ownerModId, key, category)) {
                this.categoryStates.remove(key);
                this.removeCategoryState(state);
            }
            return;
        }
        for (MLuaRegistry.OwnedValue<DefinitionHandle> entry : state.definitions.snapshotEntries().values()) {
            if (ownerModId.equals(entry.owner())) {
                this.removeDefinitionTableEntries(ownerModId, entry.value().tableEntryCount());
            }
        }
        int removedDefinitions = state.definitions.removeOwner(ownerModId);
        this.definitionCount -= removedDefinitions;
        for (int index = 0; index < removedDefinitions; index++) decrement(this.definitionsByOwner, ownerModId);
        int removedPending = (int) state.pending.values().stream()
            .filter(reservation -> ownerModId.equals(reservation.owner())).count();
        state.pending.values().stream().filter(reservation -> ownerModId.equals(reservation.owner()))
            .forEach(reservation -> this.removePendingTableEntries(ownerModId, reservation.tableEntryCount()));
        state.pending.entrySet().removeIf(entry -> ownerModId.equals(entry.getValue().owner()));
        this.pendingDefinitionCount -= removedPending;
        for (int index = 0; index < removedPending; index++) decrement(this.pendingByOwner, ownerModId);
    }

    private void removeDefinition(String key, String id, DefinitionHandle expected) {
        CategoryState state = this.categoryStates.get(key);
        if (state != null && state.definitions.remove(expected.owner(), id, expected)) {
            this.definitionCount--;
            decrement(this.definitionsByOwner, expected.owner());
            this.removeDefinitionTableEntries(expected.owner(), expected.tableEntryCount());
        }
    }

    private void removeCategoryState(CategoryState state) {
        this.definitionCount -= state.definitions.size();
        this.pendingDefinitionCount -= state.pending.size();
        state.definitions.snapshotEntries().values().forEach(entry -> {
            decrement(this.definitionsByOwner, entry.owner());
            this.removeDefinitionTableEntries(entry.owner(), entry.value().tableEntryCount());
        });
        state.pending.values().forEach(reservation -> {
            decrement(this.pendingByOwner, reservation.owner());
            this.removePendingTableEntries(reservation.owner(), reservation.tableEntryCount());
        });
    }

    private void removeDefinitionTableEntries(String owner, int count) {
        if (count <= 0) return;
        this.definitionTableEntryCount -= count;
        int remaining = this.definitionTableEntriesByOwner.getOrDefault(owner, 0) - count;
        if (remaining <= 0) this.definitionTableEntriesByOwner.remove(owner);
        else this.definitionTableEntriesByOwner.put(owner, remaining);
    }

    private void removePendingTableEntries(String owner, int count) {
        if (count <= 0) return;
        this.pendingDefinitionTableEntryCount -= count;
        int remaining = this.pendingTableEntriesByOwner.getOrDefault(owner, 0) - count;
        if (remaining <= 0) this.pendingTableEntriesByOwner.remove(owner);
        else this.pendingTableEntriesByOwner.put(owner, remaining);
    }

    private static void decrement(Map<String, Integer> counts, String owner) {
        int remaining = counts.getOrDefault(owner, 0) - 1;
        if (remaining <= 0) counts.remove(owner);
        else counts.put(owner, remaining);
    }

    public synchronized LuaTable definition(String category, String id) {
        CategoryState state = this.categoryStates.get(key(category));
        DefinitionHandle definition = state == null ? null : state.definitions.get(id);
        return definition == null ? null : copyTable(definition.value());
    }

    /** Captures the current definition generation for a stable Lua handle. */
    public synchronized DefinitionReference definitionReference(String category, String id) {
        CategoryState state = this.categoryStates.get(key(category));
        DefinitionHandle definition = state == null ? null : state.definitions.get(id);
        return definition == null ? null : new DefinitionReference(definition);
    }

    /** Returns a detached copy only while this exact definition generation remains registered. */
    public synchronized LuaTable definition(DefinitionReference expected) {
        if (!this.isCurrent(expected)) return null;
        CategoryState state = this.categoryStates.get(key(expected.category()));
        DefinitionHandle definition = state == null ? null : state.definitions.get(expected.id());
        return definition == null ? null : copyTable(definition.value());
    }

    /** Checks whether this exact definition generation remains registered. */
    public synchronized boolean containsDefinition(DefinitionReference expected) {
        return this.isCurrent(expected);
    }

    /** Returns category metadata only while the definition's original category remains registered. */
    public synchronized String categoryDescription(DefinitionReference expected) {
        if (!this.isCurrentCategory(expected)) return null;
        Category category = this.categories.get(key(expected.category()));
        return category == null ? null : category.description();
    }

    private boolean isCurrent(DefinitionReference expected) {
        if (!this.isCurrentCategory(expected)) return false;
        CategoryState state = this.categoryStates.get(key(expected.category()));
        DefinitionHandle current = state == null ? null : state.definitions.get(expected.id());
        return current != null && current.generation() == expected.definitionGeneration();
    }

    private boolean isCurrentCategory(DefinitionReference expected) {
        if (expected == null) return false;
        CategoryState state = this.categoryStates.get(key(expected.category()));
        return state != null && state.generation == expected.categoryGeneration();
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
        return copyTableSnapshot(source).value();
    }

    private static TableSnapshot copyTableSnapshot(LuaTable source) {
        CopyBudget budget = new CopyBudget(MAX_TABLE_ENTRIES_PER_DEFINITION);
        LuaTable copy = copyTable(source, new java.util.IdentityHashMap<>(),
            new java.util.IdentityHashMap<>(), 0, budget);
        return new TableSnapshot(copy, MAX_TABLE_ENTRIES_PER_DEFINITION - budget.remaining);
    }

    private static LuaTable copyTable(LuaTable source, Map<LuaTable, LuaTable> copies,
                                      java.util.IdentityHashMap<LuaTable, Boolean> active,
                                      int depth, CopyBudget budget) {
        if (depth >= MAX_TABLE_DEPTH) {
            throw new LuaError("[MLua] Registry definitions cannot be nested more than "
                + MAX_TABLE_DEPTH + " tables deep");
        }
        if (active.containsKey(source)) {
            throw new LuaError("[MLua] Registry definitions cannot contain cyclic tables");
        }
        LuaTable existing = copies.get(source);
        if (existing != null) return existing;
        LuaTable copy = new LuaTable();
        copies.put(source, copy);
        active.put(source, Boolean.TRUE);
        try {
            LuaValue key = LuaValue.NIL;
            while (true) {
                Varargs entry = source.next(key);
                key = entry.arg1();
                if (key.isnil()) return copy;
                if (--budget.remaining < 0) {
                    throw new LuaError("[MLua] Registry definition exceeds the table entry limit of "
                        + MAX_TABLE_ENTRIES_PER_DEFINITION);
                }
                LuaValue value = entry.arg(2);
                LuaValue copiedKey = key.istable()
                    ? copyTable(key.checktable(), copies, active, depth + 1, budget) : key;
                LuaValue copiedValue = value.istable()
                    ? copyTable(value.checktable(), copies, active, depth + 1, budget) : value;
                copy.rawset(copiedKey, copiedValue);
            }
        } finally {
            active.remove(source);
        }
    }

    private static String key(String value) {
        return requireText(value, "category name").toLowerCase(Locale.ROOT);
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must be non-empty");
        return value.trim();
    }

    private static String requireBoundedText(String value, String name, int maximumLength) {
        String normalized = requireText(value, name);
        if (normalized.length() > maximumLength) {
            throw new IllegalArgumentException(name + " cannot exceed " + maximumLength + " characters");
        }
        return normalized;
    }

    private static void requireOwnedDefinitionId(String id, String owner) {
        ResourceLocation location = MLuaResourceLocations.tryParseOrNull(id);
        if (location == null || !location.toString().equals(id)) {
            throw new LuaError("[MLua] Dynamic registry definition id must be a canonical namespaced resource location: " + id);
        }
        if (!owner.equals(MLuaResourceLocations.namespace(location))) {
            throw new LuaError("[MLua] Dynamic registry definition id '" + id
                + "' must use its registering addon's namespace '" + owner + "'");
        }
    }

    private static final class CategoryState {
        private final Object generation = new Object();
        private final MLuaRegistry<DefinitionHandle> definitions = new MLuaRegistry<>();
        private final Map<String, DefinitionReservation> pending = new HashMap<>();
    }

    /** Opaque identity captured by a Lua-facing live handle without retaining the definition payload. */
    public static final class DefinitionReference {
        private final String category;
        private final Object categoryGeneration;
        private final String id;
        private final String owner;
        private final Object definitionGeneration;

        private DefinitionReference(DefinitionHandle definition) {
            this.category = definition.category();
            this.categoryGeneration = definition.categoryGeneration();
            this.id = definition.id();
            this.owner = definition.owner();
            this.definitionGeneration = definition.generation();
        }

        public String category() { return this.category; }
        private Object categoryGeneration() { return this.categoryGeneration; }
        public String id() { return this.id; }
        public String owner() { return this.owner; }
        private Object definitionGeneration() { return this.definitionGeneration; }
    }

    private static final class CopyBudget {
        private int remaining;

        private CopyBudget(int remaining) {
            this.remaining = remaining;
        }
    }

    private record TableSnapshot(LuaTable value, int entryCount) { }

    /** Opaque ticket; only the catalog that created it can complete or cancel it. */
    public static final class DefinitionReservation {
        private final String category;
        private final String id;
        private final String owner;
        private final TableSnapshot snapshot;

        private DefinitionReservation(String category, String id, String owner, TableSnapshot snapshot) {
            this.category = category;
            this.id = id;
            this.owner = owner;
            this.snapshot = snapshot;
        }

        private String category() { return this.category; }
        private String id() { return this.id; }
        private String owner() { return this.owner; }
        private TableSnapshot snapshot() { return this.snapshot; }
        private int tableEntryCount() { return this.snapshot == null ? 0 : this.snapshot.entryCount(); }
    }

    public record Category(String name, String description, String domain, String owner,
                           LuaValue register, MLuaContext context) {
        public Category {
            name = requireBoundedText(name, "category name", MAX_CATEGORY_NAME_LENGTH);
            description = requireBoundedText(description, "category description", MAX_CATEGORY_DESCRIPTION_LENGTH);
            domain = requireBoundedText(domain, "category content type domain", MAX_CATEGORY_DOMAIN_LENGTH);
            owner = requireBoundedText(owner, "category owner", MAX_CATEGORY_OWNER_LENGTH);
            Objects.requireNonNull(register, "category register callback");
            if (!register.isfunction()) throw new IllegalArgumentException("category register callback must be a function");
            Objects.requireNonNull(context, "category register context");
            if (!owner.equals(context.ownerModId())) {
                throw new IllegalArgumentException("category callback context must belong to its category owner");
            }
            int namespaceEnd = name.indexOf(':');
            if (namespaceEnd < 1 || !owner.equals(name.substring(0, namespaceEnd))
                || !name.equals(name.toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("category must be namespaced to its owner");
            }
        }
    }

    public record Usage(int categories, int categoriesForOwner, int definitions, int definitionsForOwner,
                        int pendingDefinitions, int pendingDefinitionsForOwner,
                        int definitionTableEntries, int definitionTableEntriesForOwner) { }

    public static final class DefinitionHandle {
        private final String category;
        private final Object categoryGeneration;
        private final Object generation = new Object();
        private final String id;
        private final String owner;
        private final LuaTable value;
        private final int tableEntryCount;

        private DefinitionHandle(String category, Object categoryGeneration, String id, String owner,
                                 LuaTable value, int tableEntryCount) {
            this.category = category;
            this.categoryGeneration = categoryGeneration;
            this.id = id;
            this.owner = owner;
            this.value = value;
            this.tableEntryCount = tableEntryCount;
        }

        public String category() { return this.category; }
        private Object categoryGeneration() { return this.categoryGeneration; }
        private Object generation() { return this.generation; }
        public String id() { return this.id; }
        public String owner() { return this.owner; }
        private LuaTable value() { return this.value; }
        private int tableEntryCount() { return this.tableEntryCount; }
    }
}
