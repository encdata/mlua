-- Register a scripted entity definition.
local Registry = Minecraft:GetService("Registry")

Registry.Entities:Register("example_entity", {
    width = 0.8,
    height = 1.8,
    health = 20,
    onTick = function(entity)
        -- Add custom AI or state updates here.
    end
})
