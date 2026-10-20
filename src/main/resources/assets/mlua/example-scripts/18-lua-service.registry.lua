-- Register a Lua-backed service class and described methods during startup.
Minecraft:RegisterServiceClass(
    "ExampleHorror",
    "ExampleHorrorService",
    "Shared state and behavior for a scripted horror encounter."
)

Minecraft:RegisterServiceClassFunction("ExampleHorror", "Init", function(self, fields)
    self.Fear = 0
    self.LastEvent = "none"
end, "Initializes the shared horror service instance.")

Minecraft:RegisterServiceClassFunction("ExampleHorror", "AddFear", function(self, amount, reason)
    amount = math.max(0, tonumber(amount) or 0)
    self.Fear = math.min(100, self.Fear + amount)
    self.LastEvent = reason or "unknown"
    return self.Fear
end, "Adds fear to the shared encounter state and returns the new level.")

Minecraft:RegisterServiceClassFunction("ExampleHorror", "GetState", function(self)
    return {
        Fear = self.Fear,
        LastEvent = self.LastEvent
    }
end, "Returns a snapshot of the current encounter state.")
