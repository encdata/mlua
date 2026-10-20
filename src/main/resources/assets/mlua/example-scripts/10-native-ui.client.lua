-- Move or replace native widgets as screens initialize.
local Gui = Minecraft:GetService("Gui")
Gui.ScreenOpened:Connect(function(screenName, controls)
    if not screenName:match("OptionsScreen$") then return end

    for _, control in ipairs(controls) do
        if control.Text == "Controls..." then
            -- NativeControl properties update the Minecraft widget itself.
            control.Position = UDim2.fromOffset(16, 24)

            local before = UIObject.TextLabel.new()
            before.Text = "!"
            before.Size = UDim2.fromOffset(20, 20)
            control.Before = before -- placed to the left of the native control

            local after = UIObject.TextButton.new()
            after.Text = "Addon"
            after.Size = UDim2.fromOffset(64, 20)
            after.Activated:Connect(function()
                print("Addon action next to the native control")
            end)
            control.After = after -- placed to the right; it remains a normal GuiObject
            break
        end
    end
end)
