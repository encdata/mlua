package dev.mlua.modules.run;

import dev.mlua.modules.run.Service.MLuaRunService;
import dev.mlua.module.MLuaModule;
import java.util.List;

public class RunModule implements MLuaModule {
    @Override
    public String id() { return "run"; }

    @Override
    public void onRuntimeTick(dev.mlua.module.MLuaRegistration runtime, Object runtimeSession, dev.mlua.common.enums.Side side, double deltaTime) {
        if (side == dev.mlua.common.enums.Side.SERVER || side == dev.mlua.common.enums.Side.BOTH) {
            MLuaRunService.setDeltaTime(runtimeSession, side, deltaTime);
        }
    }

    @Override
    public void onRenderFrame(dev.mlua.module.MLuaRegistration runtime, Object runtimeSession, double deltaTime) {
        MLuaRunService.setDeltaTime(runtimeSession, dev.mlua.common.enums.Side.CLIENT, deltaTime);
    }

    @Override
    public List<ServiceEntry> services() {
        ServiceEntry.Builder run = ServiceEntry.builder("RunService", MLuaRunService::new)
            .description("Provides runtime frame signals and environment information.");
        run.function("GetDeltaTime", "Returns the latest simulation frame delta time for this runtime side.")
            .function("GetRenderDeltaTime", "Returns the latest render frame delta time for this runtime side.")
            .function("IsServer", "Returns whether this script is running on the server.")
            .function("IsClient", "Returns whether this script is running on a client.")
            .function("GetSide", "Returns the runtime side for this script.")
            .function("IsForge", "Returns whether MLua is running on Forge.")
            .function("IsFabric", "Returns whether MLua is running on Fabric.")
            .function("IsNeoForge", "Returns whether MLua is running on NeoForge.")
            .function("GetVersion", "Returns the Minecraft version string.")
            .function("GetMluaVersion", "Returns the MLua mod version string.")
            .function("GetLoader", "Returns the active mod loader name.")
            .function("IsDevelopmentMode", "Returns whether the runtime is in development mode.");
        return List.of(run.build());
    }
}
