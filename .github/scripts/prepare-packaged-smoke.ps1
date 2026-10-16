$jar = Get-ChildItem client-smoke/mod -Filter '*.jar' | Select-Object -First 1
if ($null -eq $jar) {
  throw "Build artifact is missing for $env:MLUA_LOADER / $env:MLUA_TARGET; refusing to skip packaged boot checks."
}
$template = "client-smoke/$env:MLUA_LOADER"
New-Item -ItemType Directory -Force "$template/run/mods" | Out-Null
New-Item -ItemType Directory -Force "$template/run/mlua/scripts" | Out-Null
New-Item -ItemType Directory -Force "$template/compile-libs" | Out-Null
New-Item -ItemType Directory -Force "$template/gradle/wrapper" | Out-Null
Copy-Item 'gradlew.bat' "$template/gradlew.bat"
Copy-Item 'gradle/wrapper/gradle-wrapper.jar' "$template/gradle/wrapper/gradle-wrapper.jar"
@(
  'distributionBase=GRADLE_USER_HOME'
  'distributionPath=wrapper/dists'
  "distributionUrl=https\://services.gradle.org/distributions/gradle-$env:MLUA_GRADLE_VERSION-bin.zip"
  'networkTimeout=10000'
  'zipStoreBase=GRADLE_USER_HOME'
  'zipStorePath=wrapper/dists'
) | Set-Content "$template/gradle/wrapper/gradle-wrapper.properties"
if ($env:MLUA_LOADER -eq 'neoforge' -and $env:MLUA_TARGET -eq '1.21.1') {
  New-Item -ItemType Directory -Force "$template/run/config" | Out-Null
  'earlyWindowControl = false' | Set-Content "$template/run/config/fml.toml"
}
Copy-Item $jar.FullName "$template/run/mods/mlua.jar"
Copy-Item $jar.FullName "$template/compile-libs/mlua.jar"
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [System.IO.Compression.ZipFile]::OpenRead($jar.FullName)
try {
  $runtimeEntry = $archive.GetEntry('embedded/mlua/mlua-runtime.jar')
  if ($null -ne $runtimeEntry) {
    [System.IO.Compression.ZipFileExtensions]::ExtractToFile(
      $runtimeEntry, "$template/compile-libs/mlua-runtime.jar", $true)
  }
} finally {
  $archive.Dispose()
}
@'
local Mixin = Minecraft:GetService("Mixin")
Mixin:Target("dev.mlua.ci.SmokeTarget"):Inject("probe", {
    descriptor = "()V",
    at = "HEAD",
    callback = function()
        print("MLUA_SCRIPT_MIXIN_HOOK_OK")
    end
})
'@ | Set-Content "$template/run/mlua/scripts/ci_smoke.mixin.lua"
@'
local Registry = Minecraft:GetService("Registry")

local earlyEntity = Registry.Entities:Properties()
assert(type(earlyEntity.MovementSpeed) == "function",
    "EntityProperties did not expose MovementSpeed before generic registry queries")
assert(earlyEntity:MovementSpeed(0.25) == earlyEntity,
    "EntityProperties:MovementSpeed did not return the same chainable instance")

local item = Item.Properties()
    :StacksTo(1)
    :Rarity("rare")
    :OnUse(function(context)
        return true
    end)
assert(item:IsA("ItemProperties") and item:IsA("RegistryProperties"),
    "Item.Properties did not create an inherited registry property class")
assert(item:Build().maxStackSize == 1, "ItemProperties:Build lost StacksTo")
assert(not pcall(function() Item.Properties():OnUse("not a function") end),
    "ItemProperties accepted a non-function callback")
assert(not pcall(function() Registry.Items:Register("ci_wrong_property_class", Registry.Blocks:Properties()) end),
    "Registry.Items:Register accepted BlockProperties")
Registry.Items:Register("ci_property_item", item)
local genericItemHandle = Registry:GetEntry("items", "ci_property_item")
assert(genericItemHandle:IsA("RegistryEntry") and genericItemHandle.Kind == "item"
    and genericItemHandle.Id == "mlua:ci_property_item",
    "root Registry:GetEntry did not return a typed item handle")
local genericItems = Registry:GetEntries("item", "mlua")
local foundGenericItem = false
for _, entry in ipairs(genericItems) do
    if entry.Id == genericItemHandle.Id then foundGenericItem = entry:Equals(genericItemHandle) end
end
assert(foundGenericItem, "root Registry:GetEntries did not include the registered item handle")
Registry.Containers:Register("ci_small_container", { Rows = 2, Title = "CI Container" })
local containerHandle = Registry.Containers:GetEntry("ci_small_container")
assert(containerHandle:IsA("ContainerType") and containerHandle.SlotCount == 18,
    "Registry.Containers:GetEntry did not return a typed container handle")
assert(Registry:GetEntry("containers", "ci_small_container").Id == containerHandle.Id,
    "root Registry:GetEntry did not route to the container category")
local foundContainer = false
for _, entry in ipairs(Registry.Containers:GetEntries("mlua")) do
    if entry.Id == containerHandle.Id then foundContainer = true end
end
assert(foundContainer, "Registry.Containers:GetEntries did not include the registered container")
Registry.DataPack:Register("mlua_test/records", "entry", { Value = 7, nested = { enabled = true } })
local dataEntry = Registry.DataPack:Get("mlua_test/records", "entry")
assert(dataEntry ~= nil and dataEntry:IsA("GeneratedDataResource")
    and dataEntry.Type == "mlua_test/records" and dataEntry.Id == "mlua:entry",
    "Registry.DataPack:Get did not return a typed generated resource")
assert(dataEntry:GetType() == "GeneratedDataResource"
    and dataEntry:GetResourceType() == "mlua_test/records",
    "GeneratedDataResource did not preserve Instance:GetType alongside its Minecraft resource type")
assert(dataEntry.Definition.Value == 7,
    "GeneratedDataResource did not convert JSON numbers to Lua numbers")
assert(Registry.DataPack:Get("mlua_test/records", "missing") == nil
    and Registry:GetEntry("datapack", "mlua_test/records", "missing") == nil,
    "missing generated data lookups did not return Lua nil")
assert(Registry.DataPack:GetEntry("mlua_test/records", "entry"):Equals(dataEntry),
    "Registry.DataPack:GetEntry did not alias Get")
local dataCopy = dataEntry:GetDefinition()
dataCopy.nested.enabled = false
assert(dataEntry:GetDefinition().nested.enabled == true,
    "GeneratedDataResource exposed mutable registration state")
assert(Registry.DataPack:Exists("mlua_test/records", "entry"),
    "Registry.DataPack:Exists missed a registered data resource")
local allDataEntries = Registry.DataPack:GetAll("mlua_test/records", "mlua")
assert(allDataEntries["mlua:entry"]:Equals(dataEntry),
    "Registry.DataPack:GetAll did not return a map of typed handles")
local rootDataEntry = Registry:GetEntry("datapack", "mlua_test/records", "entry")
assert(rootDataEntry:IsA("GeneratedDataResource") and rootDataEntry:Equals(dataEntry),
    "root Registry:GetEntry did not route generated data resources")
assert(Registry:GetAll("datapack", "mlua_test/records", "mlua")["mlua:entry"]:Equals(dataEntry),
    "root Registry:GetAll did not return generated data handles")
Registry.WorldGeneration:Register("worldgen/mlua_test", "entry", { Value = 9 })
local worldgenEntry = Registry.WorldGeneration:Get("worldgen/mlua_test", "entry")
assert(worldgenEntry:IsA("GeneratedDataResource") and worldgenEntry.Type == "worldgen/mlua_test",
    "WorldGeneration:Get did not return a typed data resource")
assert(Registry.WorldGeneration:Exists("worldgen/mlua_test", "entry"),
    "WorldGeneration:Exists missed a registered resource")
assert(Registry:Get("worldgen", "worldgen/mlua_test", "entry"):Equals(worldgenEntry)
    and Registry:GetEntry("worldgen", "worldgen/mlua_test", "entry"):Equals(worldgenEntry),
    "root Registry lookup aliases did not route world-generation resource arguments")
assert(Registry:GetIds("worldgen", "worldgen/mlua_test", "mlua")[1] == "mlua:entry"
    and Registry:Exists("worldgen", "worldgen/mlua_test", "entry"),
    "root Registry list and exists methods did not route world-generation resource arguments")
assert(Registry:GetAll("worldgen", "worldgen/mlua_test", "mlua")["mlua:entry"]:Equals(worldgenEntry)
    and Registry:GetEntries("worldgen", "worldgen/mlua_test", "mlua")[1]:Equals(worldgenEntry),
    "root Registry collection methods did not return world-generation resource handles")
local dataIds = Registry.DataPack:GetIds("mlua_test/records", "mlua")
assert(#dataIds == 1 and dataIds[1] == "mlua:entry",
    "Registry.DataPack:GetIds did not filter the resource type and namespace")
local dataEntries = Registry.DataPack:GetEntries("mlua_test/records", "mlua")
assert(#dataEntries == 1 and dataEntries[1]:Equals(dataEntry),
    "Registry.DataPack:GetEntries did not return typed handles")
local rootDataEntries = Registry:GetEntries("datapack", "mlua_test/records", "mlua")
assert(#rootDataEntries == 1 and rootDataEntries[1]:Equals(dataEntry),
    "root Registry:GetEntries did not return generated data handles")
Registry.Items:RegisterType("ci_property_item_kind", {
    Base = "item",
    Description = "CI item defaults built from ItemProperties.",
    Defaults = Item.Properties():StacksTo(4):Rarity("uncommon"),
})
local inherited = Registry.Items:ResolveType({ kind = "ci_property_item_kind" })
assert(inherited.maxStackSize == 4, "content type did not copy ItemProperties defaults")
Registry.Items:Register("ci_property_typed_item", { kind = "ci_property_item_kind" })

local block = Block.Properties()
    :Strength(2, 4)
    :RequiresCorrectToolForDrops(true)
    :OnRightClick(function(context)
        return true
    end)
assert(block:IsA("BlockProperties") and block:IsA("RegistryProperties"),
    "Block.Properties did not create an inherited registry property class")
assert(block:Build().strength == 2, "BlockProperties:Build lost Strength")
Registry.Blocks:Register("ci_property_block", block)
Registry.Blocks:RegisterType("ci_property_block_kind", {
    Base = "default",
    Description = "CI block defaults built from BlockProperties.",
    Defaults = Block.Properties():Strength(1, 2),
})
local inheritedBlock = Registry.Blocks:ResolveType({ type = "ci_property_block_kind" })
assert(inheritedBlock.strength == 1, "content type did not copy BlockProperties defaults")
Registry.Blocks:Register("ci_property_typed_block", { type = "ci_property_block_kind" })

local entity = Registry.Entities:Properties()
assert(type(entity.MaxHealth) == "function" and type(entity.MovementSpeed) == "function"
    and type(entity.AttackDamage) == "function",
    "EntityProperties did not expose its named attribute builder methods")
entity:Category("monster")
    :Size(0.7, 1.9)
    :TrackingRange(10)
    :AIInterval(4)
    :MaxHealth(24)
    :MovementSpeed(0.25)
    :OnTick(function(self)
    end)
assert(entity:IsA("EntityProperties") and entity:IsA("RegistryProperties"),
    "Registry.Entities:Properties did not create an inherited registry property class")
assert(entity:Build().attributes.maxHealth == 24, "EntityProperties:Build lost MaxHealth")
assert(entity:Build().aiInterval == 4, "EntityProperties:Build lost AIInterval")
Registry.Entities:Register("ci_property_entity", entity)
Registry.Entities:RegisterType("ci_property_entity_kind", {
    Base = "entity",
    Description = "CI entity defaults built from EntityProperties.",
    Defaults = Registry.Entities:Properties():Size(0.6, 1.8):MaxHealth(18),
})
local inheritedEntity = Registry.Entities:ResolveType({ RegistryType = "ci_property_entity_kind" })
assert(inheritedEntity.width == 0.6 and inheritedEntity.attributes.maxHealth == 18,
    "content type did not copy EntityProperties defaults")
Registry.Entities:Register("ci_property_typed_entity", { RegistryType = "ci_property_entity_kind" })
Registry:RegisterCategory("mlua:ci_records", "CI dynamic registry definitions.", function(id, definition)
    assert(id == "mlua:record", "dynamic registry callback received the wrong id")
    assert(definition.Value == 7, "dynamic registry callback received the wrong definition")
end)
local dynamic = Registry:GetCategory("mlua:ci_records")
dynamic:Register("record", { Value = 7 })
local handle = dynamic:GetEntry("record")
assert(handle ~= nil and handle:IsA("RegistryDefinition"), "dynamic GetEntry did not return a RegistryDefinition")
assert(dynamic:Get("record"):Equals(handle), "dynamic Get did not return the typed RegistryDefinition handle")
assert(dynamic:Get("missing") == nil
    and Registry:Get("mlua:ci_records", "missing") == nil,
    "dynamic Get returned a handle for a missing definition")
assert(handle.Id == "mlua:record" and handle.Category == "mlua:ci_records", "dynamic entry metadata is incorrect")
assert(handle.Owner == "mlua" and handle.IsRegistered, "dynamic entry owner or registration status is incorrect")
local rootHandle = Registry:GetEntry("mlua:ci_records", "record")
assert(rootHandle ~= nil and rootHandle:IsA("RegistryDefinition") and rootHandle:Equals(handle),
    "root Registry:GetEntry did not route to the addon category entry")
assert(Registry:Get("mlua:ci_records", "record"):Equals(handle),
    "root Registry:Get did not return the typed RegistryDefinition handle")
assert(Registry:GetEntry("mlua:ci_records", "missing") == nil,
    "root Registry:GetEntry returned a handle for a missing addon definition")
local rootEntries = Registry:GetEntries("mlua:ci_records", "mlua")
assert(#rootEntries == 1 and rootEntries[1]:Equals(handle),
    "root Registry:GetEntries did not return sorted typed addon entry handles")
local snapshot = handle:GetDefinition()
snapshot.Value = 99
assert(handle:GetDefinition().Value == 7, "RegistryDefinition exposed mutable catalog state")
local entries = dynamic:GetEntries("mlua")
assert(#entries == 1 and entries[1]:Equals(handle), "dynamic GetEntries did not return the matching live handle")
assert(dynamic:GetAll("mlua")["mlua:record"]:Equals(handle)
    and Registry:GetAll("mlua:ci_records", "mlua")["mlua:record"]:Equals(handle),
    "dynamic and root GetAll did not return keyed RegistryDefinition handles")
print("MLUA_CI_REGISTRY_PROPERTIES_OK")
print("MLUA_CI_DYNAMIC_REGISTRY_ENTRY_OK")
'@ | Set-Content "$template/run/mlua/scripts/ci_registry_properties.registry.lua"
@'
local stacks = Minecraft:GetService("ItemStackService")
local first = stacks:Empty()
local second = stacks:Empty()
local key = "mlua_ci_empty_stack_probe"
first:SetNbt(key, "changed")
local actual = first:GetNbt(key)
print("MLUA_EMPTY_STACK_NBT_VALUE=" .. tostring(actual))
assert(actual == "changed", "empty stack did not retain its own NBT; got " .. tostring(actual))
assert(second:GetNbt("mlua_ci_empty_stack_probe") == nil, "empty stack NBT leaked into another empty stack")
local copied = stacks:Clone(first)
assert(copied:GetNbt(key) == "changed", "empty stack clone lost its NBT")
copied:SetNbt(key, "copy")
assert(first:GetNbt(key) == "changed", "empty stack clone shares its NBT with the source")
print("MLUA_EMPTY_STACK_ISOLATION_OK")
'@ | Set-Content "$template/run/mlua/scripts/ci_empty_stack.server.lua"
@'
local service = Minecraft:GetService("CiSmokeService")
assert(service.__type == "CiSmokeService", "Java registered service class was not attached")
assert(service:GetSide() == "SERVER", "Java service returned the wrong side")
local quests = Minecraft:GetService("Quest")
assert(type(quests.QuestStarted.Connect) == "function", "QuestStarted signal was not exposed")
assert(type(quests.QuestCompleted.Connect) == "function", "QuestCompleted signal was not exposed")
assert(type(quests.ObjectiveUpdated.Connect) == "function", "ObjectiveUpdated signal was not exposed")
local inventories = Minecraft:GetService("InventoryService")
local validUuidError, message = pcall(function()
    inventories:GetPlayerInventory("x" .. string.rep("0", 35))
end)
assert(not validUuidError and tostring(message):find("valid UUID string", 1, true),
    "InventoryService did not validate UUID player lookups consistently")
print("MLUA_CI_SERVER_SERVICE_SCRIPT_OK")
'@ | Set-Content "$template/run/mlua/scripts/ci_java_service.server.lua"
@'
local service = Minecraft:GetService("CiSmokeService")
assert(service.__type == "CiSmokeService", "Java registered service class was not attached")
assert(service:GetSide() == "CLIENT", "Java service returned the wrong side")
assert(type(Animation.new) == "function", "Animation.new constructor was not exposed")
local definition = Animation.new({
    { Time = 0, Data = { head = CFrame.identity } },
    { Time = 0.5, Data = { head = CFrame.Angles(0, math.rad(20), 0) } },
})
assert(definition.Length == 0.5, "Animation length was not derived from its keyframes")
assert(definition:GetKeyframeCount() == 2, "Animation keyframe count is wrong")
assert(definition:HasBone("head"), "Animation omitted its head bone")
assert(not definition:HasBone("tail"), "Animation reported a bone not present in its frames")
assert(definition:GetBoneNames()[1] == "head", "Animation bone names are not exposed")
assert(definition:Sample(0.25).head ~= nil, "Animation sampling did not return its interpolated bone pose")
local animationService = Minecraft:GetService("Animation")
animationService:RegisterAnimation("ci_identity", definition)
assert(animationService:GetAnimation("ci_identity") == animationService:GetAnimation("ci_identity"),
  "Animation wrappers were not stable for the same registered asset")
assert(animationService:HasAnimation("ci_identity"), "Animation service did not report its registered id")
assert(animationService:GetRegisteredAnimations().ci_identity ~= nil,
  "Animation service omitted its registered asset from the snapshot")
assert(animationService:GetAnimationIds()[1] == "ci_identity",
  "Animation service did not return registered ids in a predictable array")
assert(animationService:RemoveAnimation("ci_identity"), "Animation service failed to remove a registered id")
assert(not animationService:HasAnimation("ci_identity"), "Animation service retained a removed id")
animationService:RegisterAnimation("ci_identity", definition)
assert(animationService:ClearAnimations() == 1, "Animation service did not report the number of cleared assets")
animationService:ClearPoses()
local badAnimationIdOk = pcall(function() animationService:RegisterAnimation("bad id", definition) end)
assert(not badAnimationIdOk, "Animation service accepted an id containing whitespace")
assert(type(Animation.Animator.new) == "function", "Animator class was not registered")
assert(type(Animation.Animator.LoadAnimation) == "function", "Animator methods were not registered")
assert(type(Animation.Animator.GetAnimationTracks) == "function", "Animator track query was not registered")
assert(type(Animation.Animator.GetTracksByPriority) == "function", "Animator priority query was not registered")
assert(type(Animation.Animator.StopAll) == "function", "Animator stop control was not registered")
assert(type(Animation.AnimationTrack.AddMarker) == "function", "AnimationTrack marker API was not registered")
assert(type(Animation.AnimationTrack.GetAnimator) == "function", "AnimationTrack owner API was not registered")
assert(type(Animation.AnimationTrack.IsAlive) == "function", "AnimationTrack liveness query was not registered")
assert(type(Animation.AnimationTrack.IsFading) == "function", "AnimationTrack fade-state query was not registered")
assert(type(Animation.AnimationTrack.GetTargetWeight) == "function", "AnimationTrack target-weight query was not registered")
local input = Minecraft:GetService("InputService")
local keybinds = input:RegisterKeybinds({
    mlua_ci_keybind_z = {},
    mlua_ci_keybind_a = { Label = "MLua CI keybind" },
})
assert(#keybinds == 2 and keybinds[1].Name == "mlua_ci_keybind_a"
    and keybinds[2].Name == "mlua_ci_keybind_z",
    "InputService:RegisterKeybinds did not return stable, sorted keybind instances")
local partialKeybindsRegistered = pcall(function()
    input:RegisterKeybinds({ mlua_ci_atomic_a = {}, mlua_ci_atomic_z = false })
end)
assert(not partialKeybindsRegistered and input:GetKeybind("mlua_ci_atomic_a") == nil,
    "InputService:RegisterKeybinds partially registered a malformed batch")
local camera = Minecraft:GetService("Camera")
assert(not pcall(function() camera:SetFOV(math.huge) end), "Camera:SetFOV accepted a non-finite value")
local cameraMode = camera:GetMode()
assert(not pcall(function() camera:SetMode("invalid_camera_mode") end)
    and camera:GetMode() == cameraMode,
    "Camera:SetMode accepted an invalid mode or changed state after rejection")
local attributes = Minecraft:GetService("Attribute")
assert(type(attributes.GetAttributeInstance) == "function", "Attribute service did not expose AttributeInstance")
assert(type(attributes.ResetBaseValue) == "function", "Attribute reset method was not registered")
assert(type(attributes.HasModifier) == "function", "Attribute modifier query was not registered")
local components = Minecraft:GetService("Components")
assert(type(components.Find) == "function", "Components:Find was not registered")
assert(type(components.Has) == "function", "Components:Has was not registered")
assert(type(components.GetAll) == "function", "Components:GetAll was not registered")
local config = Minecraft:GetService("Config")
assert(type(config.SetMany) == "function", "Config:SetMany was not registered")
local playerService = Minecraft:GetService("Players")
local invalidPlayerUuid = pcall(function() playerService:GetPlayerByUUID("not-a-uuid") end)
assert(not invalidPlayerUuid, "Players:GetPlayerByUUID accepted malformed UUID input")
local entityService = Minecraft:GetService("EntityService")
local invalidEntityUuid = pcall(function() entityService:FindByUUID("not-a-uuid") end)
assert(not invalidEntityUuid, "EntityService:FindByUUID accepted malformed UUID input")
local soundService = Minecraft:GetService("SoundService")
local categories = soundService:GetCategories()
assert(#categories > 0, "SoundService did not expose Minecraft sound categories")
for index, category in ipairs(categories) do
    assert(category == category:lower(), "SoundService categories must use lowercase names")
    if index > 1 then assert(categories[index - 1] <= category, "SoundService categories must be sorted") end
end
local invalidSoundCategory = pcall(function()
    soundService:Play("minecraft:ambient.cave", { Category = "not_a_category" })
end)
assert(not invalidSoundCategory, "SoundService silently accepted an invalid category")
local render = Minecraft:GetService("RenderService")
local renderText = render:CreateText("MLua render type check", { Visible = false })
assert(renderText:IsA("RenderObject") and renderText:GetType() == "RenderObject",
    "RenderObject:GetType did not return its MLua runtime class")
local renderMethodOk, renderType = pcall(function() return renderText:GetRenderType() end)
print("MLUA_CI_RENDER_METHOD_RESULT=" .. tostring(renderMethodOk) .. ":" .. tostring(renderType))
assert(renderMethodOk and renderType == "text", "RenderObject:GetRenderType failed or returned the wrong primitive")
local propertyOk, renderTypeProperty = pcall(function() return renderText.Type end)
print("MLUA_CI_RENDER_PROPERTY_RESULT=" .. tostring(propertyOk) .. ":" .. tostring(renderTypeProperty))
assert(propertyOk and renderTypeProperty == "text",
    "RenderObject render primitive was not exposed consistently through GetRenderType and Type")
local renderTextSnapshot
for _, object in ipairs(render:GetAll()) do
    if object.Id == renderText.Id then renderTextSnapshot = object end
end
assert(renderTextSnapshot ~= nil and renderText:Equals(renderTextSnapshot)
    and type(renderText:GetHashCode()) == "number"
    and renderText:ToString():find("RenderObject", 1, true),
    "RenderObject identity methods did not produce stable, typed handles")
renderText:Destroy()
assert(renderText.IsDestroyed, "RenderObject handle did not remain readable after Destroy")
print("MLUA_CI_ANIMATION_API_OK")
print("MLUA_CI_SERVICE_PARITY_OK")
print("MLUA_CI_CLIENT_SERVICE_SCRIPT_OK")
'@ | Set-Content "$template/run/mlua/scripts/ci_java_service.client.lua"
Set-Content "$template/run/eula.txt" 'eula=true'
Set-Content "$template/run/server.properties" @('online-mode=false', 'server-port=25565', 'enable-query=false', 'enable-rcon=false')
