package dev.mlua.modules.toast;

import dev.mlua.common.enums.Side;
import dev.mlua.module.MLuaDocBuilder;
import dev.mlua.module.MLuaModule;
import dev.mlua.modules.toast.Service.MLuaToastService;
import java.util.List;

@dev.mlua.module.MLuaModuleSide(Side.CLIENT)
public final class ToastModule implements MLuaModule {
    @Override
    public String id() {
        return "toast";
    }

    @Override
    public Side side() {
        return Side.CLIENT;
    }

    @Override
    public List<ServiceEntry> services() {
        return List.of(ServiceEntry.builder("Toast", MLuaToastService::new)
            .description("Displays native Minecraft client toasts.")
            .reflective(MLuaToastService.class)
            .build());
    }

    @Override
    public void contributeDocs(MLuaDocBuilder docs) {
        docs.service("Toast", "MLuaToastService", "Displays native Minecraft client toasts.", "services/toast.html");
        docs.signature("Toast", "Toast:Show(title: string, message: string)", "void");
        docs.guideStep("Toast", "Show a native client toast",
            "Toast:Show adds a vanilla system notification to the local client's toast manager.",
            "local Toast = Minecraft:GetService(\"Toast\")\nToast:Show(\"Signal found\", \"The radio picked up a nearby transmission.\")");
    }
}
