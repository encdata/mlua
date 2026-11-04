-- Client side of the networking example.
local Network = Minecraft:GetService("Network")

Network:OnClient("example_channel", function(payload)
    print("Server payload: " .. tostring(payload))
end)
