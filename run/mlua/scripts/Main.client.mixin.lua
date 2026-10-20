local mixin = Minecraft:GetService("Mixin")

--for i, v in pairs(mixin) do print(i, v) end
mixin:Overwrite("net.minecraft.BlockUtil","randomSkewChance",{
    callback = function(self) 
            end
})