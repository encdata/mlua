-- Register a simple custom item. Copy this file to mlua/scripts before using it.
local Registry = Minecraft:GetService("Registry")

Registry.Items:Register("example_item", {
    maxStackSize = 16,
    creativeTab = "misc"
})
