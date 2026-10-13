-- Register a short addon guide or add steps to Getting Started.
Docs:Register({
    id = "my_addon:custom-guide",
    section = "Guides",
    title = "Custom addon guide",
    description = "Explain what the guide helps the reader build.",
    order = 100,
    steps = {
        {
            title = "Register your content",
            description = "Put the registration call in a registry script.",
            example = 'local Registry = Minecraft:GetService("Registry")\nRegistry.Items:Register("sample", { maxStackSize = 16 })'
        }
    },
    examples = {
        {
            title = "Use the registered item",
            description = "Reference the item from a server script.",
            code = 'local Registry = Minecraft:GetService("Registry")\nRegistry.Items:Register("sample", { maxStackSize = 16 })'
        }
    }
})

-- Remove one of this addon's pages during a later registry pass if it is no longer needed.
-- The short id is expanded to this addon's namespace, just like Docs:Register.
-- Docs:Unregister("custom-guide")

Docs:Register({
    id = "setup",
    section = "Getting Started",
    title = "Set up My Addon",
    description = "Add a short setup step to the shared starter guide.",
    order = 20,
    steps = {
        {
            title = "Register the addon item",
            description = "Add this call to your registry script.",
            example = 'local Registry = Minecraft:GetService("Registry")\nRegistry.Items:Register("sample", { maxStackSize = 16 })'
        }
    }
})
