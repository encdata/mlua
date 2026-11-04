-- Player, Inventory, Container, and Slot are live instances.
local Players = Minecraft:GetService("Players")

Players.PlayerAdded:Connect(function(player)
    print("Player joined in:", player.World:GetDimension())
    local inventory = player.Inventory
    print(player:GetName(), "has", inventory.Size, "inventory slots")

    local firstSlot = inventory.Slots[1]
    if firstSlot.Empty then
        print("The first slot is empty")
    else
        print("First slot item:", firstSlot.Item:GetDisplayName())
    end

    -- OpenContainer follows the player's current menu.
    local enderChest = player.EnderChest
    print("Ender chest slots:", enderChest.Size)
    local openMenu = player.OpenContainer
    if openMenu then
        print("Open menu slots:", openMenu.Size)
    end
end)
