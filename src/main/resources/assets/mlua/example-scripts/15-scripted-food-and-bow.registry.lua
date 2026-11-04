-- Register custom food and a native animated bow. Copy this file to mlua/scripts before using it.
local Registry = Minecraft:GetService("Registry")

Registry.Items:Register("haunted_ration", {
    food = {
        nutrition = 6,
        saturation = 0.8,
        alwaysEdible = true,
    },
    onConsume = function(context)
        if context.Player then
            context.Player:SendMessage("The ration tastes strangely warm.")
        end
    end,
})

Registry.Items:Register("ember_bow", {
    kind = "bow",
    copy = "minecraft:bow",
    copyModel = true,
    durability = 384,
    maxStackSize = 1,
    description = "A registered bow with Minecraft's native draw, pull, and release behavior.",
})
