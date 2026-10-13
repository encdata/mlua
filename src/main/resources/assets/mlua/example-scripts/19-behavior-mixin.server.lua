-- Use the class and behavior mixin registered by 19-behavior-mixin.registry.lua.
local EncounterState = Minecraft:GetClass("ExampleEncounterState")
local encounter = EncounterState.new({ Health = 80 })

print("Health:", encounter:ApplyDamage(15))
print("Is an Instance:", encounter:IsA("Instance"))
print("Is the mixin a class:", encounter:IsA("ExampleDamageBehavior")) -- false
