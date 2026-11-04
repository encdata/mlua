<p align="center"><img src="./src/main/resources/icon.png" alt="Logo" width="200"></p>
<h1 align="center">MLua  <br>
	<a href="https://r.createmod.net/d"><img src="https://img.shields.io/discord/1565448662975381534?color=5865F2&label=Discord&style=flat" alt="Discord"></a>
	<!-- <a href="https://r.createmod.net/cf"><img src="https://img.shields.io/curseforge/dt/328085?logo=curseforge&label=&suffix=%20&style=flat&color=242629&labelColor=F16436&logoColor=1C1C1C" alt="CurseForge"></a> -->
    <a href="https://r.createmod.net/mr"><img src="https://img.shields.io/modrinth/dt/mlua?logo=modrinth&label=&suffix=%20&style=flat&color=242629&labelColor=5CA424&logoColor=1C1C1C" alt="Modrinth"></a>
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