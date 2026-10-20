package dev.mlua.modules.events.service;

import dev.mlua.core.signal.MLuaSignal;
import dev.mlua.common.enums.Side;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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

    private MLuaEventSignalRegistry() { }

    static synchronized void registerBuiltIn(String name, MLuaSignal signal, String description) {
        SIGNALS.put(name, new Entry(signal, description, null));
        BUILT_IN_NAMES.add(name);
    }

    static synchronized void register(String name, MLuaSignal signal, String description, String ownerModId) {
        validate(name, signal, description, ownerModId);
        ensureCapacity(name, ownerModId);
        SIGNALS.put(name, new Entry(signal, description, ownerModId));
    }

    private static void ensureCapacity(String name, String ownerModId) {
        if (SIGNALS.containsKey(name)) throw new IllegalStateException("Event signal is already registered: " + name);
        if (SIGNALS.size() - BUILT_IN_NAMES.size() >= MAX_CUSTOM_SIGNALS) {
            throw new IllegalStateException("MLua custom event signal limit reached (" + MAX_CUSTOM_SIGNALS + ")");
        }
        if (ownerModId != null && countOwner(ownerModId) >= MAX_CUSTOM_SIGNALS_PER_ADDON) {
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
        if (SIGNALS.values().stream().anyMatch(entry -> entry.signal() == signal)) {
            throw new IllegalStateException("MLua signal instance is already registered under another event name");
        }
    }

    static synchronized boolean unregister(String name, MLuaSignal expected) {
        if (name == null || expected == null || BUILT_IN_NAMES.contains(name)) return false;
        Entry entry = SIGNALS.get(name);
        if (entry == null || entry.signal() != expected) return false;
        SIGNALS.remove(name);
        entry.signal().destroyPermanently();
        return true;
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

    static synchronized Map<String, Entry> snapshot() {
        return new LinkedHashMap<>(SIGNALS);
    }

    static synchronized int countOwner(String ownerModId) {
        if (ownerModId == null) return 0;
        int count = 0;
        for (Entry entry : SIGNALS.values()) {
            if (ownerModId.equals(entry.ownerModId())) count++;
        }
        return count;
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
