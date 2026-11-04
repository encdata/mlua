package dev.mlua.modules.modal;

import dev.mlua.modules.modal.service.MLuaModalService;
import dev.mlua.common.enums.Side;
import dev.mlua.module.MLuaModule;
import java.util.List;

@dev.mlua.module.MLuaModuleSide(Side.CLIENT)
public class ModalModule implements MLuaModule {
    @Override
    public void clearRuntimeState(dev.mlua.module.MLuaRegistration runtime, Object runtimeSession, Side side) {
        if (runtimeSession instanceof dev.mlua.core.MLuaRuntimeSession session) {
            MLuaModalService.clearForSession(session, side);
        }
    }

    @Override
    public void clearOwnedState(dev.mlua.module.MLuaRegistration runtime, Object runtimeSession, Side side, String ownerModId) {
        if (runtimeSession instanceof dev.mlua.core.MLuaRuntimeSession session) {
            MLuaModalService.clearOwnedForSession(session, side, ownerModId);
        }
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
