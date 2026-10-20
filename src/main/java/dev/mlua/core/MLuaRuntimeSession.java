package dev.mlua.core;

import dev.mlua.core.require.MLuaModuleCache;
import dev.mlua.core.scheduler.MLuaTaskScheduler;
import dev.mlua.core.scheduler.MLuaTaskSchedulerView;
import dev.mlua.core.signal.MLuaSignal;
import dev.mlua.common.enums.Side;
import dev.mlua.module.MLuaRegistration;
import net.minecraft.server.MinecraftServer;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Supplier;
import org.luaj.vm2.LuaTable;

public final class MLuaRuntimeSession {
    private static final Map<MinecraftServer, WeakReference<MLuaRuntimeSession>> SERVER_SESSIONS =
            Collections.synchronizedMap(new WeakHashMap<>());
    private final MLuaModuleCache serverModules = new MLuaModuleCache();
    private final MLuaModuleCache clientModules = new MLuaModuleCache();
    private final MLuaTaskScheduler serverScheduler = new MLuaTaskScheduler();
    private final MLuaTaskScheduler clientScheduler = new MLuaTaskScheduler();
    private final Map<Side, Map<String, LuaServiceInstance>> luaServices = new EnumMap<>(Side.class);
    private final Map<Side, Map<ServiceStateKey, Object>> javaServiceStates = new EnumMap<>(Side.class);
    private volatile MinecraftServer server;
    private volatile MLuaRegistration registration;

    public MLuaRegistration registration() { return registration; }
    void attachRegistration(MLuaRegistration registration) { this.registration = registration; }

    public void setFailureReporter(java.util.function.BiConsumer<String, Throwable> reporter) {
        this.serverScheduler.setFailureReporter(reporter);
        this.clientScheduler.setFailureReporter(reporter);
    }

    public boolean ownsScheduler(MLuaTaskSchedulerView scheduler) {
        return scheduler == this.serverScheduler || scheduler == this.clientScheduler;
    }

    public MLuaModuleCache serverModules() { return serverModules; }
    public MLuaModuleCache clientModules() { return clientModules; }
    public MLuaTaskScheduler serverScheduler() { return serverScheduler; }
    public MLuaTaskScheduler clientScheduler() { return clientScheduler; }
    public MinecraftServer server() { return server; }

    public synchronized LuaTable materializeLuaService(Side side, String name, LuaTable classTable,
                                                        Supplier<LuaTable> factory) {
        if (side == null || side == Side.BOTH) {
            throw new IllegalArgumentException("A concrete side is required for Lua service instances");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("A non-empty service name is required for Lua service instances");
        }
        java.util.Objects.requireNonNull(classTable, "classTable");
        java.util.Objects.requireNonNull(factory, "factory");
        Map<String, LuaServiceInstance> sideServices = this.luaServices.computeIfAbsent(
                side, ignored -> new LinkedHashMap<>());
        LuaServiceInstance existing = sideServices.get(name);
        if (existing != null && existing.classTable() == classTable) return existing.instance();
        LuaTable created = factory.get();
        if (created == null) throw new IllegalStateException("Lua service factory returned null: " + name);
        sideServices.put(name, new LuaServiceInstance(classTable, created));
        return created;
    }

    public synchronized <T> T sharedServiceState(Side side, String serviceName, String key,
                                                 Class<T> stateType, Supplier<? extends T> factory) {
        if (side == null || side == Side.BOTH) throw new IllegalArgumentException("A concrete side is required for service state");
        if (serviceName == null || serviceName.isBlank() || key == null || key.isBlank()) {
            throw new IllegalArgumentException("Service state requires non-empty service and state keys");
        }
        java.util.Objects.requireNonNull(stateType, "stateType");
        java.util.Objects.requireNonNull(factory, "factory");
        ServiceStateKey stateKey = new ServiceStateKey(serviceName, key);
        Map<ServiceStateKey, Object> states = this.javaServiceStates.computeIfAbsent(side, ignored -> new LinkedHashMap<>());
        Object existing = states.get(stateKey);
        if (existing != null) {
            if (!stateType.isInstance(existing)) {
                throw new IllegalStateException("Java service '" + serviceName + "' state '" + key
                    + "' was already created as " + existing.getClass().getName());
            }
            return stateType.cast(existing);
        }
        T created = java.util.Objects.requireNonNull(factory.get(), "service state factory returned null");
        states.put(stateKey, created);
        return created;
    }

    public synchronized <T> T existingSharedServiceState(Side side, String serviceName, String key,
                                                          Class<T> stateType) {
        if (side == null || side == Side.BOTH) throw new IllegalArgumentException("A concrete side is required for service state");
        ServiceStateKey stateKey = new ServiceStateKey(serviceName, key);
        Map<ServiceStateKey, Object> states = this.javaServiceStates.get(side);
        Object existing = states == null ? null : states.get(stateKey);
        if (existing == null) return null;
        if (!stateType.isInstance(existing)) {
            throw new IllegalStateException("Java service '" + serviceName + "' state '" + key
                + "' was already created as " + existing.getClass().getName());
        }
        return stateType.cast(existing);
    }

    public synchronized boolean removeSharedServiceState(Side side, String serviceName, String key) {
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

    private record ServiceStateKey(String serviceName, String key) { }

    public synchronized void forgetLuaServicesForClass(LuaTable classTable) {
        if (classTable == null) return;
        this.luaServices.values().forEach(sideServices ->
                sideServices.values().removeIf(service -> service.classTable() == classTable));
    }
    public static MLuaRuntimeSession forServer(MinecraftServer server) {
        if (server == null) return null;
        synchronized (SERVER_SESSIONS) {
            WeakReference<MLuaRuntimeSession> reference = SERVER_SESSIONS.get(server);
            return reference == null ? null : reference.get();
        }
    }

    public void attachServer(MinecraftServer server) {
        synchronized (SERVER_SESSIONS) {
            MinecraftServer previousServer = this.server;
            if (previousServer != null && previousServer != server) removeMappingIfOwned(previousServer, this);
            this.server = server;
            if (server != null) SERVER_SESSIONS.put(server, new WeakReference<>(this));
        }
    }

    public void detachServer(MinecraftServer server) {
        synchronized (SERVER_SESSIONS) {
            if (this.server == server) {
                removeMappingIfOwned(server, this);
                this.server = null;
            }
        }
    }

    private static void removeMappingIfOwned(MinecraftServer server, MLuaRuntimeSession owner) {
        WeakReference<MLuaRuntimeSession> reference = SERVER_SESSIONS.get(server);
        if (reference != null && reference.get() == owner) SERVER_SESSIONS.remove(server);
    }

    public void clearScriptState() {
        this.clearScriptState(Side.SERVER);
        this.clearScriptState(Side.CLIENT);
    }

    public void clearScriptState(Side side) {
        this.clearServiceInstances(side);
        if (side == Side.SERVER || side == Side.BOTH) {
            this.serverScheduler.Destroy();
            this.serverModules.Destroy();
        }
        if (side == Side.CLIENT || side == Side.BOTH) {
            this.clientScheduler.Destroy();
            this.clientModules.Destroy();
        }
        MLuaSignal.destroyAllOnSession(this, side);
    }

    private synchronized void clearServiceInstances(Side side) {
        if (side == Side.BOTH) {
            this.luaServices.clear();
            this.javaServiceStates.clear();
        } else {
            this.luaServices.remove(side);
            this.javaServiceStates.remove(side);
        }
    }

    public void clearModuleCache(Side side) {
        if (side == Side.SERVER || side == Side.BOTH) this.serverModules.Destroy();
        if (side == Side.CLIENT || side == Side.BOTH) this.clientModules.Destroy();
    }

    public int clearOwnedTasks(Side side, String ownerModId) {
        if (side == Side.BOTH) {
            return this.serverScheduler.removeOwned(ownerModId) + this.clientScheduler.removeOwned(ownerModId);
        }
        return (side == Side.SERVER ? this.serverScheduler : this.clientScheduler).removeOwned(side, ownerModId);
    }

    private record LuaServiceInstance(LuaTable classTable, LuaTable instance) { }
}
