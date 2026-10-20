-- Add a reusable mixin to a Lua class without changing its IsA ancestry.
Minecraft:RegisterClass(
    "ExampleEncounterState",
    "Instance",
    "Tracks one scripted encounter and its health."
)

Minecraft:RegisterClassFunction("ExampleEncounterState", "Init", function(self, fields)
    self.Health = fields.Health or 100
end, "Initializes encounter state from constructor fields.")

Minecraft:RegisterBehaviorMixin(
    "ExampleDamageBehavior",
    "Adds reusable damage handling to compatible live instances.",
    {
        ApplyDamage = {
            implementation = function(self, amount)
                amount = math.max(0, tonumber(amount) or 0)
                self.Health = math.max(0, self.Health - amount)
                return self.Health
            end,
            description = "Reduces this instance's health and returns the remaining amount."
        }
    }
)

Minecraft:ApplyBehaviorMixin("ExampleEncounterState", "ExampleDamageBehavior")
