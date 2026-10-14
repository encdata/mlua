package dev.mlua.modules.modal;

import dev.mlua.modules.modal.Service.MLuaModalService;
import dev.mlua.common.enums.Side;
import dev.mlua.module.MLuaModule;
import java.util.List;

@dev.mlua.module.MLuaModuleSide(Side.CLIENT)
public class ModalModule implements MLuaModule {
    @Override
    public void clearRuntimeState(dev.mlua.module.MLuaRegistration runtime, Object runtimeSession, Side side) {
        MLuaModalService.clearForSession((dev.mlua.core.MLuaRuntimeSession) runtimeSession, side);
    }

    @Override
    public String id() { return "modal"; }

    @Override
    public Side side() { return Side.CLIENT; }

    @Override
    public List<ServiceEntry> services() {
        return List.of(ServiceEntry.builder("Modal", MLuaModalService::new)
            .reflective(MLuaModalService.class)
            .build());
    }
}
