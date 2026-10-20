-- Server side of a small networking example.
local Network = Minecraft:GetService("Network")

Network:RegisterChannel("example_channel")
Network:OnServer("example_channel", function(player, payload)
    print("Received from " .. player.Name .. ": " .. tostring(payload))
end)
