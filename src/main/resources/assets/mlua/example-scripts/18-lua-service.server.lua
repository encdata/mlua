-- Use the Lua-backed service registered by 18-lua-service.registry.lua.
local Horror = Minecraft:GetService("ExampleHorror")

local fear = Horror:AddFear(15, "lights_out")
print("Encounter fear:", fear)

local state = Horror:GetState()
print("Last event:", state.LastEvent)
