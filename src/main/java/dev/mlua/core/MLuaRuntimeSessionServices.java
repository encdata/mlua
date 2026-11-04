package dev.mlua.core;

import dev.mlua.common.enums.Side;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import org.luaj.vm2.LuaTable;

final class MLuaRuntimeSessionServices {
    private final Map<Side, Map<String, LuaServiceInstance>> luaServices = new EnumMap<>(Side.class);
    private final Map<Side, Map<ServiceStateKey, Object>> javaServiceStates = new EnumMap<>(Side.class);

    LuaTable materialize(Side side, String name, LuaTable classTable, Supplier<LuaTable> factory) {
        requireConcreteSide(side, "Lua service instances");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("A non-empty service name is required for Lua service instances");
        }
        java.util.Objects.requireNonNull(classTable, "classTable");
        java.util.Objects.requireNonNull(factory, "factory");
        Map<String, LuaServiceInstance> services = this.luaServices.computeIfAbsent(
            side, ignored -> new LinkedHashMap<>());
        LuaServiceInstance existing = services.get(name);
        if (existing != null && existing.classTable() == classTable) return existing.instance();
        LuaTable created = factory.get();
        if (created == null) throw new IllegalStateException("Lua service factory returned null: " + name);
        services.put(name, new LuaServiceInstance(classTable, created));
        return created;
    }

    <T> T sharedState(Side side, String serviceName, String key, Class<T> stateType,
                      Supplier<? extends T> factory) {
        requireConcreteSide(side, "service state");
        requireStateKey(serviceName, key);
        java.util.Objects.requireNonNull(stateType, "stateType");
        java.util.Objects.requireNonNull(factory, "factory");
        ServiceStateKey stateKey = new ServiceStateKey(serviceName, key);
        Map<ServiceStateKey, Object> states = this.javaServiceStates.computeIfAbsent(
            side, ignored -> new LinkedHashMap<>());
        Object existing = states.get(stateKey);
        if (existing != null) {
            if (!stateType.isInstance(existing)) {
                throw stateTypeMismatch(serviceName, key, existing);
            }
            return stateType.cast(existing);
        }
        T created = java.util.Objects.requireNonNull(factory.get(), "service state factory returned null");
        states.put(stateKey, created);
        return created;
    }

    <T> T existingState(Side side, String serviceName, String key, Class<T> stateType) {
        requireConcreteSide(side, "service state");
        Map<ServiceStateKey, Object> states = this.javaServiceStates.get(side);
        Object existing = states == null ? null : states.get(new ServiceStateKey(serviceName, key));
        if (existing == null) return null;
        if (!stateType.isInstance(existing)) throw stateTypeMismatch(serviceName, key, existing);
        return stateType.cast(existing);
    }

    boolean removeState(Side side, String serviceName, String key) {
        if (side == null || serviceName == null || serviceName.isBlank() || key == null || key.isBlank()) {
            throw new IllegalArgumentException("Service state removal requires a side, service, and state key");
        }
        ServiceStateKey stateKey = new ServiceStateKey(serviceName, key);
        if (side == Side.BOTH) {
            boolean removed = false;
            for (Map<ServiceStateKey, Object> states : this.javaServiceStates.values()) {
                removed |= states.remove(stateKey) != null;
            }
            return removed;
        }
        Map<ServiceStateKey, Object> states = this.javaServiceStates.get(side);
        return states != null && states.remove(stateKey) != null;
    }

    void forgetServicesForClass(LuaTable classTable) {
        if (classTable == null) return;
        this.luaServices.values().forEach(services ->
            services.values().removeIf(service -> service.classTable() == classTable));
    }

    void clear(Side side) {
        if (side == Side.BOTH) {
            this.luaServices.clear();
            this.javaServiceStates.clear();
        } else {
            this.luaServices.remove(side);
            this.javaServiceStates.remove(side);
        }
    }

    private static void requireConcreteSide(Side side, String stateDescription) {
        if (side == null || side == Side.BOTH) {
            throw new IllegalArgumentException("A concrete side is required for " + stateDescription);
        }
    }

    private static void requireStateKey(String serviceName, String key) {
        if (serviceName == null || serviceName.isBlank() || key == null || key.isBlank()) {
            throw new IllegalArgumentException("Service state requires non-empty service and state keys");
        }
    }

    private static IllegalStateException stateTypeMismatch(String serviceName, String key, Object existing) {
        return new IllegalStateException("Java service '" + serviceName + "' state '" + key
            + "' was already created as " + existing.getClass().getName());
    }

    private record ServiceStateKey(String serviceName, String key) { }
    private record LuaServiceInstance(LuaTable classTable, LuaTable instance) { }
}
