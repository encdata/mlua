package dev.mlua.modules.client;

import dev.mlua.common.enums.Side;
import dev.mlua.loaderapi.LoaderApi;
import dev.mlua.module.MLuaDocBuilder;
import dev.mlua.module.MLuaModule;
import dev.mlua.modules.client.Service.MLuaClientService;
import java.util.List;

@dev.mlua.module.MLuaModuleSide(Side.CLIENT)
public final class ClientModule implements MLuaModule {
    @Override public String id() { return "client"; }
    @Override public Side side() { return Side.CLIENT; }

    @Override
    public List<ServiceEntry> services() {
        if (!LoaderApi.platform().isClient()) return List.of();
        return List.of(ServiceEntry.builder("Client", MLuaClientService::new)
            .reflective(MLuaClientService.class).build());
    }

    @Override
    public void contributeDocs(MLuaDocBuilder docs) {
        docs.register(MLuaDocBuilder.Registration.service("Client", "MLuaClientService",
            "Read-only access to local Minecraft client and window state.", "services/client.html"));
        docs.register(MLuaDocBuilder.Registration.signature("Client", "Client:GetFPS()", "number"));
        docs.register(MLuaDocBuilder.Registration.signature("Client", "Client:IsPaused()", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("Client", "Client:IsWindowFocused()", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("Client", "Client:GetVersion()", "string"));
        docs.register(MLuaDocBuilder.Registration.signature("Client", "Client:GetScreenName()", "string|nil"));
        docs.register(MLuaDocBuilder.Registration.signature("Client", "Client:IsModLoaded(id: string)", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("Client", "Client:GetWindowSize()", "Vector2"));
        docs.register(MLuaDocBuilder.Registration.signature("Client", "Client:GetScaledWindowSize()", "Vector2"));
        docs.register(MLuaDocBuilder.Registration.signature("Client", "Client:GetGuiScale()", "number"));
        docs.register(MLuaDocBuilder.Registration.signature("Client", "Client:GetMousePosition()", "Vector2"));
        docs.register(MLuaDocBuilder.Registration.guideStep("Client", "Read local client state",
            "Client is available to client scripts and returns local display state. WindowSize uses physical pixels; ScaledWindowSize and MousePosition use GUI-scaled pixels.",
            "local Client = Minecraft:GetService(\"Client\")\nprint(Client:GetVersion(), Client:GetFPS(), Client:IsPaused())\nprint(Client:GetWindowSize(), Client:GetScaledWindowSize(), Client:GetGuiScale())\nprint(Client:GetMousePosition(), Client:IsWindowFocused())\n\nif Client:IsModLoaded(\"some_mod\") then\n    print(\"Optional integration available\")\nend"));
    }
}
