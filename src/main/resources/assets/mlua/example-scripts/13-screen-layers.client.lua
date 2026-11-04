-- Place addon UI behind or in front of any matching Minecraft screen.
local Gui = Minecraft:GetService("Gui")

local backplate = Gui:CreateScreen("OptionsBackplate", {
    TargetScreen = "Options",
    RenderLayer = "Before",
})

local left = UIObject.Frame.new()
left.Position = UDim2.fromOffset(8, 36)
left.Size = UDim2.fromOffset(120, 48)
left.BackgroundColor3 = 0xFF202A38
left.Parent = backplate

local overlay = Gui:CreateScreen("OptionsAddon", {
    TargetScreen = "Options",
    RenderLayer = "After",
})

local right = UIObject.TextButton.new()
right.Position = UDim2.new(1, -128, 0, 36)
right.Size = UDim2.fromOffset(120, 24)
right.Text = "Addon action"
right.Parent = overlay
