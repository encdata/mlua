-- Decorate player chat lines before Minecraft stores them.
-- Prefix and Suffix preserve the original message component and its formatting.
local Gui = Minecraft:GetService("Gui")

Gui.ChatLineAdding:Connect(function(line)
    if line.Text:match("^<[^>]+>") then
        line.Prefix = "✦ "
    end
end)
