-- A server-side block that disappears two seconds after an entity steps on it.
-- Copy this file to mlua/scripts before using it.
local Registry = Minecraft:GetService("Registry")

Registry.Blocks:Register("crumbling_stone", {
    copy = "minecraft:stone",
    copyModel = true,
    strength = 1.5,
    onStepOn = function(context)
        context.Block:ScheduleTick(40)
    end,
    onTick = function(context)
        context.Block:Set("minecraft:air")
    end,
})
