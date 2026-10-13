-- A minimal server-side script. Join a world to see the welcome message.
local Players = Minecraft:GetService("Players")

Players.PlayerAdded:Connect(function(player)
    player:SendMessage("Welcome! This message came from MLua.")
end)
