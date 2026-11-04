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
    private final MLuaRuntimeSessionServices sessionServices = new MLuaRuntimeSessionServices();
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
        return this.sessionServices.materialize(side, name, classTable, factory);
    }

    public synchronized <T> T sharedServiceState(Side side, String serviceName, String key,
                                                 Class<T> stateType, Supplier<? extends T> factory) {
        return this.sessionServices.sharedState(side, serviceName, key, stateType, factory);
    }

    public synchronized <T> T existingSharedServiceState(Side side, String serviceName, String key,
                                                          Class<T> stateType) {
        return this.sessionServices.existingState(side, serviceName, key, stateType);
    }

    public synchronized boolean removeSharedServiceState(Side side, String serviceName, String key) {
        return this.sessionServices.removeState(side, serviceName, key);
    }

    public synchronized void forgetLuaServicesForClass(LuaTable classTable) {
        this.sessionServices.forgetServicesForClass(classTable);
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
        this.sessionServices.clear(side);
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
}
