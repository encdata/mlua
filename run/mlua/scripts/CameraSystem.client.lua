local Camera = Minecraft:GetService("Camera")
local RunService = Minecraft:GetService("RunService")
local Players = Minecraft:GetService("Players")
local InputService = Minecraft:GetService("InputService")

InputService:RegisterKeybind("zoom", {
    label = "Zoom",
    defaultKey = "Z"
})

RunService.RenderStepped:Connect(function(_deltaTime)
    local player = Players:GetLocalPlayer() or Players.LocalPlayer
    if not player then
        return
    end

    if InputService:IsKeyDown("zoom") then
        Camera:SetFOVMultiplier(0.5)
    else
        Camera:SetFOVMultiplier(1.0)
    end
end)
