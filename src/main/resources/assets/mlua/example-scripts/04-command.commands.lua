-- Register a command and send feedback to the executing player.
local Commands = Minecraft:GetService("Commands")

Commands:Register("hello", {
    description = "A command added by MLua",
    run = function(context, arguments)
        context:Reply("Hello from an MLua command!")
    end
})
