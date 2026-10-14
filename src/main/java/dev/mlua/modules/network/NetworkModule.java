package dev.mlua.modules.network;

import dev.mlua.modules.network.Service.MLuaNetworkService;
import dev.mlua.core.classes.MLuaClassMethods;
import dev.mlua.module.MLuaDocBuilder;
import dev.mlua.module.MLuaModule;
import dev.mlua.module.MLuaRegistration;
import java.util.List;

public class NetworkModule implements MLuaModule {
    @Override
    public String id() { return "network"; }

    @Override
    public void registerClasses(MLuaRegistration registration) {
        registration.registerClass("RemoteEvent", registration.requireInstanceBaseClassName(),
            "A named client-server event channel that sends serialized Lua values.");
        String[][] methods = {
            {"GetName", "Returns this remote event's registered name."},
            {"GetOnServerEvent", "Returns the signal fired on the server when a client sends data."},
            {"GetOnClientEvent", "Returns the signal fired on the client when the server sends data."},
            {"FireServer", "Sends serialized values from the client to the server."},
            {"FireServerOptional", "Sends serialized values from the client; returns false instead of raising when unavailable."},
            {"FireClient", "Sends serialized values from the server to one player."},
            {"FireClientOptional", "Sends serialized values to one player; returns false instead of raising when unavailable."},
            {"FireAllClients", "Sends serialized values from the server to all connected clients."},
            {"FireAllClientsOptional", "Sends serialized values to all clients; returns false instead of raising when unavailable."}
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
        ServiceEntry.Builder network = ServiceEntry.builder("Network", MLuaNetworkService::new)
            .description("Provides client-server remote events, HTTP requests, and JSON conversion.");
        network.function("GetRemote", "Gets or creates a named remote event.")
            .function("IsServerAvailable", "Returns whether a server is available to this script.")
            .function("IsClientAvailable", "Returns whether a client is available to this script.")
            .function("HttpGet", "Performs an HTTP GET request and returns its result.")
            .function("HttpPost", "Performs an HTTP POST request and returns its result.")
            .function("HttpGetAsync", "Performs an HTTP GET request and invokes a callback with the result.")
            .function("HttpPostAsync", "Performs an HTTP POST request and invokes a callback with the result.")
            .function("JSONEncode", "Encodes a Lua value as JSON.")
            .function("JSONDecode", "Decodes a JSON string into Lua values.");
        return List.of(network.build());
    }

    @Override
    public void contributeDocs(MLuaDocBuilder docs) {
        docs.clazz("RemoteEvent", "MLuaRemoteEvent", "A named client-server event channel that sends serialized Lua values.", "classes/remote-event.html", "Instance");
        docs.property("RemoteEvent", "RemoteEvent.Name", "string", "This remote event's registered name.", "shared:Shared");
        docs.property("RemoteEvent", "RemoteEvent.OnServerEvent", "Signal", "Fires on the server when a client sends data.", "server:Server");
        docs.property("RemoteEvent", "RemoteEvent.OnClientEvent", "Signal", "Fires on the client when the server sends data.", "client:Client");
        docs.signature("Network", "Network:GetRemote(name: string)", "RemoteEvent");
        docs.signature("Network", "Network:HttpGet(url: string)", null);
        docs.signature("Network", "Network:HttpPost(url: string, body: string)", null);
        docs.signature("Network", "Network:HttpGetAsync(url: string, callback: function)", null);
        docs.signature("Network", "Network:HttpPostAsync(url: string, body: string, callback: function)", null);
        docs.signature("RemoteEvent", "RemoteEvent:FireClient(player: Player, ...: any)", null);
        docs.signature("RemoteEvent", "RemoteEvent:FireClientOptional(player: Player, ...: any)", null);
        docs.signature("RemoteEvent", "RemoteEvent:FireAllClients(...: any)", null);
        docs.signature("RemoteEvent", "RemoteEvent:FireAllClientsOptional(...: any)", null);
        docs.signature("RemoteEvent", "RemoteEvent:FireServer(...: any)", null);
        docs.signature("RemoteEvent", "RemoteEvent:FireServerOptional(...: any)", null);

        docs.contribution(new MLuaDocBuilder.Contribution(
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
                    "FireServer and OnClientEvent are client-side. Use FireClient or FireAllClients from server scripts. Supported payload values are nil, booleans, numbers, strings, arrays, and string-keyed tables; raw Java objects are not serialized.",
                    "-- client_network.client.lua\nlocal Network = Minecraft:GetService(\"Network\")\nlocal Remote = Network:GetRemote(\"Example\")\n\nRemote.OnClientEvent:Connect(function(message)\n    print(\"Server says:\", message)\nend)\n\nRemote:FireServer(\"hello from client\")")),
            List.of()));
    }
}
