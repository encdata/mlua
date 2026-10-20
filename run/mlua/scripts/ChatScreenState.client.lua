local Java = Minecraft:GetService("Java")
local Events = Minecraft:GetService("Events")
local Players = Minecraft:GetService("Players")

local MinecraftClass = Java:LoadClass("net.minecraft.client.Minecraft")
local ChatScreenClass = Java:LoadClass("net.minecraft.client.gui.screens.ChatScreen")

local wasChatOpen = nil

local function isChatScreenOpen()
    local mc = MinecraftClass:getInstance()
    if not mc then
        return false
    end

    local screen = mc.screen
    if not screen then
        return false
    end

    local screenClass = screen:getClass()
    return screenClass:getName() == ChatScreenClass:getName()
end

local function notifyState(isOpen)
    local player = Players:GetLocalPlayer() or Players.LocalPlayer
    if not player then
        return
    end

    if isOpen then
        player:SendMessage("[MLua] Chat screen opened")
    else
        player:SendMessage("[MLua] Chat screen closed")
    end
end

Events.ClientTick:Connect(function()
    local isOpen = isChatScreenOpen()

    if wasChatOpen == nil then
        wasChatOpen = isOpen
        return
    end

    if isOpen ~= wasChatOpen then
        wasChatOpen = isOpen
        notifyState(isOpen)
    end
end)
