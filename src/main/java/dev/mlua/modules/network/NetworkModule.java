package dev.mlua.modules.network;

import dev.mlua.modules.network.service.MLuaNetworkService;
import dev.mlua.core.classes.MLuaClassMethods;
import dev.mlua.module.MLuaDocBuilder;
import dev.mlua.module.MLuaModule;
import dev.mlua.module.MLuaRegistration;
import java.util.List;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaValue;

public class NetworkModule implements MLuaModule {
    @Override
    public String id() { return "network"; }

    @Override
    public void contributeGlobals(Globals globals) {
        globals.set("JSONNull", LuaValue.userdataOf(MLuaJsonNull.INSTANCE));
    }

    @Override
    public void registerClasses(MLuaRegistration registration) {
        registration.registerClass("RemoteEvent", registration.requireInstanceBaseClassName(),
            "A named client-server event channel that sends serialized Lua values.");
        String[][] methods = {
            {"GetName", "Returns this remote event's registered name."},
            {"GetOnServerEvent", "Returns the signal fired on the server when a client sends data."},
            {"GetOnClientEvent", "Returns the signal fired on the client when the server sends data."},
            {"FireServer", "Sends up to " + dev.mlua.minecraft.network.MLuaNetworkSerializer.MAX_ARGUMENTS + " serialized values from the client to the server. Payloads are limited to " + dev.mlua.minecraft.network.MLuaNetworkSerializer.MAX_TOTAL_VALUES + " values, " + dev.mlua.minecraft.network.MLuaNetworkSerializer.MAX_CONTAINER_ENTRIES + " entries per table, and " + dev.mlua.minecraft.network.MLuaNetworkSerializer.MAX_DEPTH + " nested table levels."},
            {"FireServerOptional", "Sends bounded serialized values from the client; returns false instead of raising when the server is unavailable."},
            {"FireClient", "Sends bounded serialized values from the server to one player."},
            {"FireClientOptional", "Sends bounded serialized values to one player; returns false instead of raising when unavailable."},
            {"FireAllClients", "Sends bounded serialized values from the server to all connected clients."},
            {"FireAllClientsOptional", "Sends bounded serialized values to all clients; returns false instead of raising when unavailable."}
        };
        for (String[] method : methods) {
            MLuaClassMethods.registerInstanceMethod(registration, "RemoteEvent", method[0], method[1]);
        }
        MLuaClassMethods.registerInstanceProperty(registration, "RemoteEvent", "Name", "GetName", null,
            "string", "shared:Shared", "This remote event's registered name.");
        MLuaClassMethods.registerInstanceProperty(registration, "RemoteEvent", "OnServerEvent", "GetOnServerEvent", null,
            "Signal", "server:Server", "Fires on the server when a client sends data.");
        MLuaClassMethods.registerInstanceProperty(registration, "RemoteEvent", "OnClientEvent", "GetOnClientEvent", null,
            "Signal", "client:Client", "Fires on the client when the server sends data.");
    }

    @Override
    public List<ServiceEntry> services() {
        return List.of(ServiceEntry.builder("Network", MLuaNetworkService::new)
            .reflective(MLuaNetworkService.class)
            .build());
    }

    @Override
    public void contributeDocs(MLuaDocBuilder docs) {
        docs.register(MLuaDocBuilder.Registration.dataType("JSONNull",
            "A singleton global value representing JSON null. JSONDecode returns it for null values, and JSONEncode writes it as null without removing an array slot or object field. Empty Lua tables encode as JSON arrays; use string-keyed tables for JSON objects.",
            "types/json-null.html"));
        docs.register(MLuaDocBuilder.Registration.clazz("RemoteEvent", "MLuaRemoteEvent", "A named client-server event channel that sends serialized Lua values.", "classes/remote-event.html", "Instance"));
        docs.register(MLuaDocBuilder.Registration.property("RemoteEvent", "RemoteEvent.Name", "string", "This remote event's registered name.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("RemoteEvent", "RemoteEvent.OnServerEvent", "Signal", "Fires on the server when a client sends data.", "server:Server"));
        docs.register(MLuaDocBuilder.Registration.property("RemoteEvent", "RemoteEvent.OnClientEvent", "Signal", "Fires on the client when the server sends data.", "client:Client"));
        docs.register(MLuaDocBuilder.Registration.signature("Network", "Network:GetRemote(name: string)", "RemoteEvent"));
        docs.register(MLuaDocBuilder.Registration.signature("Network", "Network:HttpGet(url: string)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Network", "Network:HttpPost(url: string, body: string)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Network", "Network:HttpGetAsync(url: string, callback: function)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Network", "Network:HttpPostAsync(url: string, body: string, callback: function)", null));
        docs.register(MLuaDocBuilder.Registration.signature("RemoteEvent", "RemoteEvent:FireClient(player: Player, ...: any)", null));
        docs.register(MLuaDocBuilder.Registration.signature("RemoteEvent", "RemoteEvent:FireClientOptional(player: Player, ...: any)", null));
        docs.register(MLuaDocBuilder.Registration.signature("RemoteEvent", "RemoteEvent:FireAllClients(...: any)", null));
        docs.register(MLuaDocBuilder.Registration.signature("RemoteEvent", "RemoteEvent:FireAllClientsOptional(...: any)", null));
        docs.register(MLuaDocBuilder.Registration.signature("RemoteEvent", "RemoteEvent:FireServer(...: any)", null));
        docs.register(MLuaDocBuilder.Registration.signature("RemoteEvent", "RemoteEvent:FireServerOptional(...: any)", null));

        docs.register(MLuaDocBuilder.Registration.contribution(new MLuaDocBuilder.Contribution(
            "mlua:core-network-guide", "Network", "Send data between sides",
            "Use named remote events for server-authoritative client-server communication.",
            "mlua", 0,
            List.of(
                new MLuaDocBuilder.GuideStep("Network", "Get the same remote on both sides",
                    "Remote names are scoped to the requesting addon. Calling GetRemote with the same name from client and server scripts selects the same remote channel.",
                    "local Network = Minecraft:GetService(\"Network\")\nlocal Remote = Network:GetRemote(\"Example\")"),
                new MLuaDocBuilder.GuideStep("Network", "Receive and reply on the server",
                    "OnServerEvent runs on the server and receives the sending Player followed by the serialized payload. Server code can reply to that player with FireClient.",
                    "-- server_network.server.lua\nlocal Network = Minecraft:GetService(\"Network\")\nlocal Remote = Network:GetRemote(\"Example\")\n\nRemote.OnServerEvent:Connect(function(player, message)\n    print(player.Name, \"sent\", message)\n    Remote:FireClient(player, \"thanks!\")\nend)"),
                new MLuaDocBuilder.GuideStep("Network", "Send from a client and receive the reply",
                    "FireServer and OnClientEvent are client-side. Use FireClient or FireAllClients from server scripts. Supported payload values are nil, booleans, finite numbers, strings, Vector3, CFrame, arrays, and string-keyed tables; raw Java objects are not serialized. Each message is limited to " + dev.mlua.minecraft.network.MLuaNetworkSerializer.MAX_ARGUMENTS + " arguments, " + dev.mlua.minecraft.network.MLuaNetworkSerializer.MAX_TOTAL_VALUES + " values across nested tables, " + dev.mlua.minecraft.network.MLuaNetworkSerializer.MAX_CONTAINER_ENTRIES + " entries in one table, " + dev.mlua.minecraft.network.MLuaNetworkSerializer.MAX_DEPTH + " nested table levels, and " + dev.mlua.minecraft.network.MLuaNetworkSerializer.MAX_STRING + " characters per string.",
                    "-- client_network.client.lua\nlocal Network = Minecraft:GetService(\"Network\")\nlocal Remote = Network:GetRemote(\"Example\")\n\nRemote.OnClientEvent:Connect(function(message)\n    print(\"Server says:\", message)\nend)\n\nRemote:FireServer(\"hello from client\")")),
            List.of())));
    }
}
