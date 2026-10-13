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
        ServiceEntry.Builder modal = ServiceEntry.builder("Modal", MLuaModalService::new)
            .description("Shows client-side modal dialogs and prompts.");
        modal.function("SetModal", "Sets the active modal state for the calling script.")
            .function("IsModal", "Returns whether a modal is currently active.")
            .function("ShowMessageBox", "Displays a message dialog to the local player.")
            .function("ShowPrompt", "Displays a text prompt and returns the submitted value.")
            .function("ShowConfirmDialog", "Displays a confirmation dialog and returns the choice.");
        return List.of(modal.build());
    }
}
