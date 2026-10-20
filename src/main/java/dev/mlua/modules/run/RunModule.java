package dev.mlua.modules.run;

import dev.mlua.modules.run.service.MLuaRunService;
import dev.mlua.module.MLuaModule;
import dev.mlua.module.MLuaDocBuilder;
import java.util.List;

public class RunModule implements MLuaModule {
    @Override
    public String id() { return "run"; }

    @Override
    public void onRuntimeTick(dev.mlua.module.MLuaRegistration runtime, Object runtimeSession, dev.mlua.common.enums.Side side, double deltaTime) {
        MLuaRunService.setDeltaTime(runtimeSession, side, deltaTime);
    }

    @Override
    public void clearRuntimeState(dev.mlua.module.MLuaRegistration runtime, Object runtimeSession,
                                  dev.mlua.common.enums.Side side) {
        if (side == dev.mlua.common.enums.Side.CLIENT || side == dev.mlua.common.enums.Side.BOTH) {
            MLuaRunService.resetRenderDeltaTime(runtimeSession);
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

    @Override
    public void contributeDocs(MLuaDocBuilder docs) {
        docs.register(MLuaDocBuilder.Registration.event("RunService", "RunService.Heartbeat",
            "deltaTime: number", "Fires after each simulation tick on the active script side. Client scripts receive the client tick; server scripts receive the server tick.",
            "client:Client or server:Server"));
        docs.register(MLuaDocBuilder.Registration.event("RunService", "RunService.Stepped",
            "deltaTime: number", "Alias for the active side's post-tick Heartbeat signal. It does not run before physics.",
            "client:Client or server:Server"));
        docs.register(MLuaDocBuilder.Registration.event("RunService", "RunService.RenderStepped",
            "deltaTime: number", "Fires on the client at the start of each render frame, before Minecraft draws the frame.",
            "client:Client"));
        docs.register(MLuaDocBuilder.Registration.event("RunService", "RunService.PreRender",
            "deltaTime: number", "Fires immediately before RenderStepped at the start of a client render frame.",
            "client:Client"));
        docs.register(MLuaDocBuilder.Registration.event("RunService", "RunService.PostRender",
            "deltaTime: number", "Fires after Minecraft finishes drawing the client frame.",
            "client:Client"));
    }
}
