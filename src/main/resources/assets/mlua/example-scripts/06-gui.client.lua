-- A small client-side GUI starter.
local Gui = Minecraft:GetService("Gui")
local screen = Gui:Create("ScreenGui")
local label = screen:Create("TextLabel")

label.Text = "Hello from MLua"
label.Parent = screen
screen.Parent = Minecraft.PlayerGui
