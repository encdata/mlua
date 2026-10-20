-- Safe Java interop: whitelist the exact class and call a harmless static method.
local Java = Minecraft:GetService("Java")
local Math = Java:Import("java.lang.Math")

print("Absolute value: " .. tostring(Math:abs(-42)))
