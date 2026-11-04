package dev.mlua.modules.events.service;

import dev.mlua.core.signal.MLuaSignal;
import dev.mlua.common.enums.Side;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Process-wide registry and lifecycle owner for built-in and addon event signals. */
final class MLuaEventSignalRegistry {
    static final int MAX_CUSTOM_SIGNALS = 4_096;
    static final int MAX_CUSTOM_SIGNALS_PER_ADDON = 256;
    private static final Set<String> RESERVED_NAMES = Set.of("PlayerAdded", "PlayerRemoving", "GetSignalNames",
        "GetUnavailableSignals", "RegisterSignal", "UnregisterSignal", "ClassName", "GetClassName",
        "GetType", "IsA", "ToString");
    private static final Map<String, Entry> SIGNALS = new LinkedHashMap<>();
    private static final Set<String> BUILT_IN_NAMES = new LinkedHashSet<>();
    private static final Map<MLuaSignal, String> NAMES_BY_SIGNAL = new IdentityHashMap<>();
    private static final Map<String, Integer> SIGNAL_COUNTS_BY_OWNER = new LinkedHashMap<>();
    private static volatile Map<String, Entry> publishedSnapshot = Map.of();

    private MLuaEventSignalRegistry() { }

    static synchronized void registerBuiltIn(String name, MLuaSignal signal, String description) {
        validate(name, signal, description, null);
        if (SIGNALS.containsKey(name)) {
            throw new IllegalStateException("Built-in event signal is already registered: " + name);
        }
        SIGNALS.put(name, new Entry(signal, description, null));
        BUILT_IN_NAMES.add(name);
        NAMES_BY_SIGNAL.put(signal, name);
        publishSnapshot();
    }

    static synchronized void register(String name, MLuaSignal signal, String description, String ownerModId) {
        validate(name, signal, description, ownerModId);
        ensureCapacity(name, ownerModId);
        SIGNALS.put(name, new Entry(signal, description, ownerModId));
        NAMES_BY_SIGNAL.put(signal, name);
        if (ownerModId != null) SIGNAL_COUNTS_BY_OWNER.merge(ownerModId, 1, Integer::sum);
        publishSnapshot();
    }

    private static void ensureCapacity(String name, String ownerModId) {
        if (SIGNALS.containsKey(name)) throw new IllegalStateException("Event signal is already registered: " + name);
        if (SIGNALS.size() - BUILT_IN_NAMES.size() >= MAX_CUSTOM_SIGNALS) {
            throw new IllegalStateException("MLua custom event signal limit reached (" + MAX_CUSTOM_SIGNALS + ")");
        }
        if (ownerModId != null && SIGNAL_COUNTS_BY_OWNER.getOrDefault(ownerModId, 0) >= MAX_CUSTOM_SIGNALS_PER_ADDON) {
            throw new IllegalStateException("Addon '" + ownerModId + "' already has the maximum of "
                + MAX_CUSTOM_SIGNALS_PER_ADDON + " custom event signals");
        }
    }

    private static void validate(String name, MLuaSignal signal, String description, String ownerModId) {
        if (name == null) throw new IllegalArgumentException("Event signal name must be a Lua identifier or namespaced key");
        int separator = name.indexOf(':');
        boolean simple = separator < 0 && name.matches("[A-Za-z_][A-Za-z0-9_]*") && !name.startsWith("__");
        boolean qualified = separator > 0 && separator == name.lastIndexOf(':')
            && name.substring(0, separator).matches("[a-z0-9_.-]+")
            && name.substring(separator + 1).matches("[A-Za-z_][A-Za-z0-9_]*")
            && !name.substring(separator + 1).startsWith("__");
        if (!simple && !qualified) {
            throw new IllegalArgumentException("Event signal name must be a Lua identifier or a namespaced Lua identifier");
        }
        if (ownerModId != null && qualified && !ownerModId.equals(name.substring(0, separator))) {
            throw new IllegalArgumentException("Addon event signal namespace must match its owner: " + ownerModId);
        }
        if (simple && RESERVED_NAMES.contains(name)) throw new IllegalArgumentException("Event signal name is reserved: " + name);
        if (signal == null) throw new NullPointerException("signal");
        if (description == null || description.isBlank()) throw new IllegalArgumentException("Event signal description must be non-empty");
        if (NAMES_BY_SIGNAL.containsKey(signal)) {
            throw new IllegalStateException("MLua signal instance is already registered under another event name");
        }
    }

    static synchronized boolean unregister(String name, MLuaSignal expected) {
        if (name == null || expected == null || BUILT_IN_NAMES.contains(name)) return false;
        Entry entry = SIGNALS.get(name);
        if (entry == null || entry.signal() != expected) return false;
        SIGNALS.remove(name);
        NAMES_BY_SIGNAL.remove(expected);
        decrementOwnerCount(entry.ownerModId());
        publishSnapshot();
        entry.signal().destroyPermanently();
        return true;
    }

    private static void publishSnapshot() {
        publishedSnapshot = Collections.unmodifiableMap(new LinkedHashMap<>(SIGNALS));
    }

    private static void decrementOwnerCount(String ownerModId) {
        if (ownerModId == null) return;
        int current = SIGNAL_COUNTS_BY_OWNER.getOrDefault(ownerModId, 0);
        if (current <= 1) SIGNAL_COUNTS_BY_OWNER.remove(ownerModId);
        else SIGNAL_COUNTS_BY_OWNER.put(ownerModId, current - 1);
    }

    static synchronized boolean unregisterOwned(String name, String ownerModId, MLuaSignal expected) {
        Entry entry = SIGNALS.get(name);
        if (entry == null || entry.signal() != expected || entry.ownerModId() == null
            || !entry.ownerModId().equals(ownerModId)) return false;
        return unregister(name, expected);
    }

    static synchronized boolean owns(String name, String ownerModId) {
        Entry entry = SIGNALS.get(name);
        return entry != null && entry.ownerModId() != null && entry.ownerModId().equals(ownerModId);
    }

    static synchronized Set<String> names() {
        return Collections.unmodifiableSet(new TreeSet<>(SIGNALS.keySet()));
    }

    static Map<String, Entry> snapshot() {
        return publishedSnapshot;
    }

    static synchronized int countOwner(String ownerModId) {
        return ownerModId == null ? 0 : SIGNAL_COUNTS_BY_OWNER.getOrDefault(ownerModId, 0);
    }

    static synchronized Counts counts(String ownerModId) {
        int addonCount = countOwner(ownerModId);
        return new Counts(SIGNALS.size() - BUILT_IN_NAMES.size(), addonCount);
    }

    static void destroyAll() { snapshot().values().forEach(entry -> entry.signal().Destroy()); }
    static void destroySide(Side side) { snapshot().values().forEach(entry -> entry.signal().destroySide(side)); }
    static void destroyOwned(String ownerModId) { snapshot().values().forEach(entry -> entry.signal().destroyOwned(ownerModId)); }

    record Entry(MLuaSignal signal, String description, String ownerModId) { }
    record Counts(int customSignals, int addonSignals) { }
}
