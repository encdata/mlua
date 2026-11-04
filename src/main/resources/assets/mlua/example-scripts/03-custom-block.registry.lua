-- Register a simple custom block.
local Registry = Minecraft:GetService("Registry")

Registry.Blocks:Register("example_block", {
    strength = 2.0,
    requiresCorrectTool = false
})
