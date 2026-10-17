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
        MLuaRunService.setRenderDeltaTime(runtimeSession, deltaTime);
    }

    @Override
    public List<ServiceEntry> services() {
        return List.of(ServiceEntry.builder("RunService", MLuaRunService::new)
            .reflective(MLuaRunService.class)
            .build());
    }
}
