package dev.mlua.core;

import dev.mlua.common.enums.RegistrationPhase;
import dev.mlua.common.enums.ScriptType;
import dev.mlua.common.enums.Side;
import dev.mlua.core.engine.LuaJEngineBackend;
import dev.mlua.core.engine.MLuaEngine;
import org.luaj.vm2.LuaTable;
import dev.mlua.core.require.MLuaModuleCache;
import dev.mlua.core.scheduler.MLuaTaskScheduler;
import dev.mlua.core.script.MLuaScript;
import dev.mlua.core.service.MLuaServiceRegistry;
import dev.mlua.loaderapi.LoaderApi;
import dev.mlua.module.MLuaRegistration;
import java.util.List;
import net.minecraft.server.MinecraftServer;

/** Owns the Lua backend and builds the context used to execute each script. */
final class MLuaRuntimeScriptExecutor {
    private final MLuaRuntime runtime;
    private volatile MLuaEngine engine;
    private long scriptGeneration = Long.MIN_VALUE;

    MLuaRuntimeScriptExecutor(MLuaRuntime runtime) {
        this.runtime = runtime;
    }

    synchronized void replaceScripts(List<MLuaScript> scripts, long generation) {
        if (this.engine != null && this.scriptGeneration == generation) return;
        if (this.engine == null) this.engine = new LuaJEngineBackend(scripts);
        else this.engine.replaceScripts(scripts);
        this.scriptGeneration = generation;
    }

    boolean initialized() { return this.engine != null; }

    void resetSide(Side side) {
        if (this.engine != null) this.engine.resetSide(side);
    }

    void execute(MLuaScript script, Side side, RegistrationPhase phase, MinecraftServer server) {
        MLuaContext context = this.contextFor(script, side, phase, server, this.runtime);
        MLuaLogger.LOGGER.info("Loading script {}", script.displayName());
        if (phase == RegistrationPhase.RUNTIME
                && (script.type() == ScriptType.SERVER || script.type() == ScriptType.CLIENT)) {
            this.engine.executeResumable(script, context);
        } else {
            this.engine.execute(script, context);
        }
    }

    boolean executeEntrypoint(MLuaScript script, Side side, RegistrationPhase phase,
                              MinecraftServer server) {
        String entrypoint = entrypointName(side, phase);
        return entrypoint != null && this.executeEntrypoint(script, side, phase, server,
            entrypoint, null, this.runtime);
    }

    boolean executeEntrypoint(MLuaScript script, Side side, RegistrationPhase phase,
                              MinecraftServer server, String entrypoint, LuaTable event,
                              MLuaRegistration registration) {
        MLuaContext context = this.contextFor(script, side, phase, server, registration);
        if (!this.engine.executeEntrypoint(script, context, entrypoint, event)) return false;
        MLuaLogger.LOGGER.info("Loading script {}#{}", script.displayName(), entrypoint);
        return true;
    }

    void executeRegistryScript(MLuaScript script, boolean entrypoint,
                              MLuaRegistration registration) {
        MLuaContext context = this.contextFor(script, Side.SERVER, RegistrationPhase.REGISTRY,
            null, registration);
        if (entrypoint) this.engine.executeEntrypoint(script, context, "run_registry");
        else this.engine.execute(script, context);
    }

    private MLuaContext contextFor(MLuaScript script, Side side, RegistrationPhase phase,
                                   MinecraftServer server, MLuaRegistration registration) {
        MLuaRuntimeSession session = this.runtime.session();
        boolean serverSide = side == Side.SERVER;
        MLuaServiceRegistry services = this.runtime.services();
        MLuaTaskScheduler scheduler = serverSide ? session.serverScheduler() : session.clientScheduler();
        MLuaModuleCache moduleCache = serverSide ? session.serverModules() : session.clientModules();
        MLuaContext context = new MLuaContext(script, side, phase, services, scheduler, moduleCache, server,
            LoaderApi.platform().mluaDataDir(), this.runtime::reportRuntimeFailure, session, registration);
        context.bindExecutionLock(this.engine);
        return context;
    }

    private static String entrypointName(Side side, RegistrationPhase phase) {
        if (phase == RegistrationPhase.MIXIN) return "run_mixin";
        if (phase == RegistrationPhase.REGISTRY) return "run_registry";
        if (phase == RegistrationPhase.COMMANDS) return "run_commands";
        if (side == Side.CLIENT) return "run_client";
        return side == Side.SERVER ? "run_server" : null;
    }
}
