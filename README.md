<p align="center"><img src="https://media.discordapp.net/attachments/1539432926662492222/1567579423815897220/Icon.png?ex=6aecb19a&is=6aeb601a&hm=4ca6b1148db73c7d1a2f80237fa1d703b5b195e6711e87c3b1892852c5b6e8e6&=&format=webp&quality=lossless&width=904&height=904" alt="Logo" width="200"></p>
<h1 align="center">MLua<br>
	<a href=""><img src="https://img.shields.io/discord/1565448662975381534?color=5865F2&label=Discord&style=flat" alt="Discord"></a>
	<!-- <a href=""><img src="https://img.shields.io/curseforge/dt/328085?logo=curseforge&label=&suffix=%20&style=flat&color=242629&labelColor=F16436&logoColor=1C1C1C" alt="CurseForge"></a> -->
    <a href=""><img src="https://img.shields.io/modrinth/dt/mlua?logo=modrinth&label=&suffix=%20&style=flat&color=242629&labelColor=5CA424&logoColor=1C1C1C" alt="Modrinth"></a>
    <br><br>
</h1>

MLua lets you write Minecraft mod scripts in Lua. Scripts can interact with the game through APIs for players, worlds, events, commands, UI, and registered content.

## API reference

MLua would generate its documentation in the Minecraft client. Open run/mlua/docs/index.html after launching the game. It shows you the APIs available in that build.

## Example
```lua
local Players = Minecraft:GetService("Players")
local World = Minecraft:GetService("World")

Players.PlayerAdded:Connect(function(player)
    player:SendMessage("Hello from MLua!")
    player:SetGameMode(GameMode.Creative)
end)

World:SetWeather(Weather.Clear)
```

## Using MLua from another mod

Include MLua with your mod and declare it as a dependency.
