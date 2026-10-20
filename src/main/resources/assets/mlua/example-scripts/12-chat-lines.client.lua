-- Rewrite visible chat text before Minecraft stores the message.
local Gui = Minecraft:GetService("Gui")

Gui.ChatLineAdding:Connect(function(line)
    if line.Text:find("[SERVER]", 1, true) then
        line.Text = "<Server> " .. line.Text
    end
end)
