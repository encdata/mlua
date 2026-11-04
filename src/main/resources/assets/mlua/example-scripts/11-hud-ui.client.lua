-- Add a Lua-owned element to the in-game HUD layer.
local Gui = Minecraft:GetService("Gui")
local hud = Gui:CreateScreen("AddonHud", {
    TargetScreen = "HUD",
    DisplayOrder = 20,
})

local label = UIObject.TextLabel.new()
label.Text = "Addon HUD"
label.TextColor3 = 0xFFFFFFFF
label.Size = {
    X = { Scale = 0, Offset = 120 },
    Y = { Scale = 0, Offset = 18 },
}
label.Position = {
    X = { Scale = 0, Offset = 8 },
    Y = { Scale = 0, Offset = 8 },
}
label.Parent = hud
