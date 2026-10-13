package dev.mlua.modules.network;

import dev.mlua.modules.network.Service.MLuaNetworkService;
import dev.mlua.module.MLuaDocBuilder;
import dev.mlua.module.MLuaModule;
import java.util.List;

public class NetworkModule implements MLuaModule {
    @Override
    public String id() { return "network"; }

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
        docs.signature("Network", "Network:GetRemote(name: string)", null);
        docs.signature("Network", "Network:HttpGet(url: string)", null);
        docs.signature("Network", "Network:HttpPost(url: string, body: string)", null);
        docs.signature("Network", "Network:HttpGetAsync(url: string, callback: function)", null);
        docs.signature("Network", "Network:HttpPostAsync(url: string, body: string, callback: function)", null);
        docs.signature("Network", "Network:FireClient(player: Player, ...: any)", null);
        docs.signature("Network", "Network:FireAllClients(...: any)", null);
        docs.signature("Network", "Network:FireServer(...: any)", null);

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
