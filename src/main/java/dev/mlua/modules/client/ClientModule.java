package dev.mlua.modules.client;

import dev.mlua.common.enums.Side;
import dev.mlua.loaderapi.LoaderApi;
import dev.mlua.module.MLuaDocBuilder;
import dev.mlua.module.MLuaModule;
import dev.mlua.modules.client.service.MLuaClientService;
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
        docs.register(MLuaDocBuilder.Registration.signature("Client", "Client:IsInGame()", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("Client", "Client:IsSingleplayer()", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("Client", "Client:GetServerAddress()", "string|nil"));
        docs.register(MLuaDocBuilder.Registration.signature("Client", "Client:IsWindowFocused()", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("Client", "Client:GetVersion()", "string"));
        docs.register(MLuaDocBuilder.Registration.signature("Client", "Client:GetScreenName()", "string|nil"));
        docs.register(MLuaDocBuilder.Registration.signature("Client", "Client:IsModLoaded(id: string)", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("Client", "Client:GetWindowSize()", "Vector2"));
        docs.register(MLuaDocBuilder.Registration.signature("Client", "Client:GetScaledWindowSize()", "Vector2"));
        docs.register(MLuaDocBuilder.Registration.signature("Client", "Client:GetGuiScale()", "number"));
        docs.register(MLuaDocBuilder.Registration.signature("Client", "Client:GetMousePosition()", "Vector2"));
        docs.register(MLuaDocBuilder.Registration.method("Client", "Client:GetMouseDelta()", "Returns raw mouse movement in physical pixels since this service instance was last queried. The first query and queries after the window loses focus or the cursor changes grab state return a zero vector. Call it from the client thread; this is a polling delta, not a render-frame event.", null, "Vector2", "client:Client", null));
        docs.register(MLuaDocBuilder.Registration.guideStep("Client", "Read local client state",
            "Client is available to client scripts and returns local display state. WindowSize uses physical pixels; ScaledWindowSize and MousePosition use GUI-scaled pixels. MouseDelta is raw physical-pixel movement since the previous query on this service instance.",
            "local Client = Minecraft:GetService(\"Client\")\nprint(Client:GetVersion(), Client:GetFPS(), Client:IsPaused())\nprint(Client:IsInGame(), Client:IsSingleplayer(), Client:GetServerAddress())\nprint(Client:GetWindowSize(), Client:GetScaledWindowSize(), Client:GetGuiScale())\nprint(Client:GetMousePosition(), Client:IsWindowFocused())\n-- In a client tick or frame callback, this returns movement since its previous call.\nlocal mouseDelta = Client:GetMouseDelta()\nprint(mouseDelta.X, mouseDelta.Y)\n\nif Client:IsModLoaded(\"some_mod\") then\n    print(\"Optional integration available\")\nend"));
    }
}
