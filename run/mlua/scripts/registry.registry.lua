local Registry = Minecraft:GetService("Registry")

Registry.Items:Register("ruby", {
    maxStackSize = 64
})

Registry.Items:Register("wand", {
    maxStackSize = 1
})

Registry.Blocks:Register("ruby_block", {
    copy = "minecraft:iron_block",
    strength = 5.0
})

Registry.Blocks:Register("magic_block", {
    copy = "minecraft:stone",
    strength = 3.0
})

Registry.Blocks:RegisterItem("ruby_block", {
    description = "A shiny block of ruby!"
})

Registry.Blocks:RegisterItem("magic_block", {
    description = "An odd magical block."
})
