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
            .description("Displays native Minecraft client toasts with vanilla multiline wrapping.")
            .reflective(MLuaToastService.class)
            .build());
    }

    @Override
    public void contributeDocs(MLuaDocBuilder docs) {
        docs.register(MLuaDocBuilder.Registration.service("Toast", "MLuaToastService", "Displays native Minecraft client toasts with vanilla multiline wrapping.", "services/toast.html"));
        docs.register(MLuaDocBuilder.Registration.signature("Toast", "Toast:Show(title: string|Component, message: string|Component)", "void"));
        docs.register(MLuaDocBuilder.Registration.guideStep("Toast", "Show a native client toast",
            "Toast:Show adds a vanilla system notification to the local client's toast manager. Minecraft wraps long messages and sizes the toast to fit them. Pass strings for plain text or a Java Component when you need to preserve formatted Minecraft text.",
            "local Toast = Minecraft:GetService(\"Toast\")\nToast:Show(\"Signal found\", \"The radio picked up a nearby transmission.\")\n\nlocal Java = Minecraft:GetService(\"Java\")\nlocal Component = Java:LoadClass(\"net.minecraft.network.chat.Component\")\nToast:Show(\n    Component.literal(\"Radio\"),\n    Component.literal(\"Signal acquired\")\n)"));
    }
}
