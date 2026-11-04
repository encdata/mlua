-- This file runs during startup, alongside the addon's other .registry.lua scripts.
-- Docs:Register publishes a guide in the in-game documentation.

local Registry = Minecraft:GetService("Registry")

Registry.Items:Register("relic", {
    maxStackSize = 1,
    onUse = function(context)
        if not context.Player then return false end
        context.Player:SendMessage("The relic feels cold.")
        return false -- let Minecraft continue its normal item-use behavior
    end
})

local guideId = Docs:Register({
    id = "relic-guide", -- expands to this addon's namespace
    section = "Horror addon",
    title = "Add a usable relic",
    description = "Register an item during startup, then test its server-side use callback in game.",
    order = 20,
    steps = {
        {
            title = "Register the item",
            description = "Keep native content registration in a .registry.lua file. The item id becomes <addon id>:relic.",
            example = [[
local Registry = Minecraft:GetService("Registry")

Registry.Items:Register("relic", {
    maxStackSize = 1,
    onUse = function(context)
        if not context.Player then return false end
        context.Player:SendMessage("The relic feels cold.")
        return false
    end
})
]]
        },
        {
            title = "Give yourself the item",
            description = "Restart Minecraft after changing a registry script, then replace my_addon with the addon's actual id.",
            example = "/give @s my_addon:relic"
        }
    },
    examples = {
        {
            title = "Keep the callback server-side",
            description = "Use the player supplied in the item context. Return true when the callback handled the interaction; return false to let Minecraft continue normal item-use behavior.",
            code = [[
onUse = function(context)
    local player = context.Player
    if not player then return false end

    player:SendMessage("Something stirs nearby.")
    return false
end
]]
        }
    },
    templates = {
        {
            title = "Relic registration",
            description = "A minimal, ready-to-edit registry script.",
            filename = "relic.registry.lua",
            code = [[
local Registry = Minecraft:GetService("Registry")

Registry.Items:Register("relic", {
    maxStackSize = 1
})
]]
        }
    }
})

-- Docs:Register returns the owner-qualified id. A later registry pass can remove it:
-- Docs:Unregister(guideId)

-- Add one step to the shared Getting Started guide when this addon is installed.
Docs:Register({
    id = "relic-setup",
    section = "Getting Started",
    title = "Try the Relic addon",
    description = "Register and test the relic item.",
    order = 20,
    steps = {
        {
            title = "Start a world and test the relic",
            description = "Use the give command, then right-click with the relic to run its registered callback.",
            example = "/give @s my_addon:relic"
        }
    }
})
