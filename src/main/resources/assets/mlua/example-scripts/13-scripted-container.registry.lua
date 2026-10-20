-- A scripted block can open a persistent server-backed vanilla container.
local Registry = Minecraft:GetService("Registry")

Registry.Containers:Register("supply_crate", {
    Rows = 3,
    Title = "Supply Crate",
})

Registry.Blocks:Register("supply_crate", {
    strength = 2.5,
    Container = "supply_crate",
    onRightClick = function(context)
        local container = context.Block:GetContainer()
        if container and container:IsEmpty() then
            container:SetSlot(1, { Id = "minecraft:torch", Count = 1 })
        end
    end,
})
