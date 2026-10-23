$jar = Get-ChildItem client-smoke/mod -Filter '*.jar' | Select-Object -First 1
if ($null -eq $jar) {
  throw "Build artifact is missing for $env:MLUA_LOADER / $env:MLUA_TARGET; refusing to skip packaged boot checks."
}
$template = "client-smoke/$env:MLUA_LOADER"
New-Item -ItemType Directory -Force "$template/run/mods" | Out-Null
New-Item -ItemType Directory -Force "$template/run/mlua/scripts" | Out-Null
New-Item -ItemType Directory -Force "$template/run/mlua/scripts/models" | Out-Null
New-Item -ItemType Directory -Force "$template/player-test-scripts" | Out-Null
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
local registry = Minecraft:GetService("Registry")
local expectedRegistry = registry
registry:RegisterCategory("mlua:ci_records", "CI dynamic registry definitions.", function(id, definition)
    assert(Minecraft:GetService("Registry") == expectedRegistry,
        "dynamic registry callback did not restore its registering script context")
    assert(id == "mlua:record", "dynamic registry callback received the wrong id")
    assert(definition.Value == 7, "dynamic registry callback received the wrong definition")
end)
'@ | Set-Content "$template/run/mlua/scripts/ci_dynamic_category.registry.lua"
$registryPropertiesScript = @'
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
Registry.Items:Register("ci_runtime_property_item", {
    rarity = "epic",
    fireResistant = true,
    craftRemainder = "minecraft:bucket",
    food = { nutrition = 4, saturation = 0.5, alwaysEdible = true, fast = true },
})
Registry.Items:Register("ci_tiered_sword", { kind = "sword", tier = "iron" })
Registry.Items:Register("ci_registered_bow", { kind = "bow" })
Registry.Items:Register("ci_scripted_bow", {
    kind = "bow",
    onUse = function(context)
        print("MLUA_CI_SCRIPTED_BOW_USE_OK")
        return false
    end,
})
Registry.Items:Register("ci_copied_food_item", { copy = "minecraft:golden_apple" })
local ciContainer = Registry.Containers:Register("ci_small_container", {
    Rows = 2,
    Title = "CI Container",
    AllowedItems = { "minecraft:stick" },
    Slots = {
        { AllowedItems = { "minecraft:paper" } },
        { AllowedItems = {} },
    },
})
assert(ciContainer.SlotCount == 18 and ciContainer.Title == "CI Container",
    "registered ContainerType did not preserve its dimensions and title")
assert(ciContainer:IsA("ContainerType") and ciContainer.Rows == 2,
    "Registry.Containers:Register did not return a live two-row ContainerType instance")
assert(ciContainer:AllowsItem("minecraft:stick") and not ciContainer:AllowsItem("minecraft:dirt"),
    "ContainerType did not apply its container-wide item rule")
assert(ciContainer:AllowsItemInSlot(1, "minecraft:paper")
    and not ciContainer:AllowsItemInSlot(1, "minecraft:stick"),
    "ContainerType did not apply a per-slot item override")
assert(ciContainer:AllowsItemInSlot(2, "minecraft:dirt")
    and ciContainer:AllowsItemInSlot(3, "minecraft:stick"),
    "ContainerType did not distinguish an empty allow-all override from inherited rules")
local ciContainerBlock = Registry.Blocks:Register("ci_container_block", {
    strength = 2,
    container = ciContainer,
    onRightClick = function(context) return false end,
    blockEntity = {
        onLoad = function(blockEntity, context)
            assert(blockEntity:HasContainer(),
                "BlockEntity:HasContainer failed while Minecraft was attaching the entity")
            local liveContainer = blockEntity.Container
            assert(liveContainer ~= nil,
                "BlockEntity.Container was nil while onLoad had a live container")
            local liveContainerSize = liveContainer:GetSize()
            print("MLUA_CI_BLOCK_ENTITY_CONTAINER_SIZE:" .. tostring(liveContainerSize))
            assert(liveContainerSize == 18,
                "BlockEntity.Container had " .. tostring(liveContainerSize) .. " slots during onLoad")
            assert(blockEntity.Nbt ~= nil,
                "BlockEntity.Nbt could not read its live instance during onLoad")
            print("MLUA_CI_PLAYER_BLOCK_ENTITY_ON_LOAD_INSTANCE_OK")
        end,
        onContainerChanged = function(blockEntity, context) end,
    },
})
assert(ciContainerBlock:IsA("RegistryEntry") and ciContainerBlock.Id == "mlua:ci_container_block",
    "Registry.Blocks:Register did not create a typed scripted container block entry")
print("MLUA_CI_SCRIPTED_CONTAINER_REGISTRATION_OK")
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
Registry.WorldGeneration:RegisterOreVein("ci_ore_vein", {
    targets = {
        {
            target = { predicate_type = "minecraft:tag_match", tag = "minecraft:stone_ore_replaceables" },
            state = { Name = "minecraft:iron_ore" },
        },
    },
    biomes = { "#minecraft:is_overworld" },
    step = "underground_ores",
    size = 1,
    count = 1,
    minY = 0,
    maxY = 4,
})
assert(Registry.WorldGeneration:Get("worldgen/configured_feature", "ci_ore_vein").Definition.type == "minecraft:ore",
    "RegisterOreVein did not write a configured ore feature")
assert(Registry.WorldGeneration:Get("worldgen/placed_feature", "ci_ore_vein_placed").Definition.feature == "mlua:ci_ore_vein",
    "RegisterOreVein did not write a placed feature")
print("MLUA_CI_ORE_VEIN_REGISTRATION_OK")
local dataIds = Registry.DataPack:GetIds("mlua_test/records", "mlua")
assert(#dataIds == 1 and dataIds[1] == "mlua:entry",
    "Registry.DataPack:GetIds did not filter the resource type and namespace")
local dataEntries = Registry.DataPack:GetEntries("mlua_test/records", "mlua")
assert(#dataEntries == 1 and dataEntries[1]:Equals(dataEntry),
    "Registry.DataPack:GetEntries did not return typed handles")
local rootDataEntries = Registry:GetEntries("datapack", "mlua_test/records", "mlua")
assert(#rootDataEntries == 1 and rootDataEntries[1]:Equals(dataEntry),
    "root Registry:GetEntries did not return generated data handles")
Registry.Recipes:RegisterShapeless("ci_handle_recipe", {
    ingredients = { "minecraft:dirt" },
    result = "minecraft:stone",
})
Registry.Recipes:RegisterCooking("ci_cooking_default", "smelting", {
    ingredient = "minecraft:dirt",
    result = "minecraft:stone",
    cookingTime = 1,
})
local defaultCookingResult = Registry.Recipes:Get("ci_cooking_default").Definition.result
if "__MLUA_TARGET__" == "1.20.1" then
    assert(defaultCookingResult == "minecraft:stone",
        "Minecraft 1.20.1 cooking recipe did not keep its string result format")
    local accepted, errorMessage = pcall(function()
        Registry.Recipes:RegisterCooking("ci_cooking_multi_rejected", "smelting", {
            ingredient = "minecraft:dirt",
            result = "minecraft:stone",
            count = 3,
            cookingTime = 1,
        })
    end)
    assert(not accepted and tostring(errorMessage):find("only support a result count of 1", 1, true),
        "Minecraft 1.20.1 did not clearly reject unsupported cooking result counts")
else
    assert(defaultCookingResult.id == "minecraft:stone" and defaultCookingResult.count == 1,
        "modern cooking recipe did not emit the default item-stack result")
    Registry.Recipes:RegisterCooking("ci_cooking_multi", "smelting", {
        ingredient = "minecraft:dirt",
        result = "minecraft:stone",
        count = 3,
        cookingTime = 1,
    })
    local multipleCookingResult = Registry.Recipes:Get("ci_cooking_multi").Definition.result
    assert(multipleCookingResult.id == "minecraft:stone" and multipleCookingResult.count == 3,
        "modern cooking recipe did not preserve its requested result count")
end
print("MLUA_CI_COOKING_RESULT_COUNT_OK")
Registry.Loot:Register("ci_handle_loot", { type = "minecraft:block", pools = {} })
local recipeHandle = Registry.Recipes:Get("ci_handle_recipe")
local lootHandle = Registry.Loot:GetEntry("ci_handle_loot")
assert(recipeHandle ~= nil and recipeHandle:IsA("GeneratedAssetResource")
    and recipeHandle.Kind == "recipe" and recipeHandle.Id == "mlua:ci_handle_recipe",
    "Registry.Recipes:Get did not return a typed generated recipe handle")
assert(lootHandle ~= nil and lootHandle:IsA("GeneratedAssetResource")
    and lootHandle.Kind == "loot_table" and lootHandle.Id == "mlua:ci_handle_loot",
    "Registry.Loot:GetEntry did not return a typed generated loot handle")
assert(recipeHandle.Definition.result ~= nil and lootHandle.Definition.pools ~= nil,
    "generated asset handles did not expose their JSON definitions")
local recipeCopy = recipeHandle:GetDefinition()
recipeCopy.changed = true
assert(recipeHandle:GetDefinition().changed == nil,
    "GeneratedAssetResource exposed mutable registration state")
assert(Registry.Recipes:Exists("ci_handle_recipe")
    and Registry:Exists("recipes", "ci_handle_recipe")
    and Registry.Recipes:Get("missing_ci_recipe") == nil
    and Registry:Get("recipes", "ci_handle_recipe"):Equals(recipeHandle)
    and Registry:GetEntry("recipes", "ci_handle_recipe"):Equals(recipeHandle),
    "root recipe lookup aliases did not return the typed generated asset handle")
assert(Registry:Get("loot", "ci_handle_loot"):Equals(lootHandle)
    and Registry:GetEntry("loot", "ci_handle_loot"):Equals(lootHandle)
    and Registry.Loot:Exists("ci_handle_loot")
    and Registry:Exists("loot", "ci_handle_loot"),
    "root loot lookup aliases did not return the typed generated asset handle")
local foundRecipeHandle = false
for _, entry in ipairs(Registry.Recipes:GetEntries("mlua")) do
    if entry:Equals(recipeHandle) then foundRecipeHandle = true end
end
local foundRootRecipeId = false
for _, id in ipairs(Registry:GetIds("recipes", "mlua")) do
    if id == recipeHandle.Id then foundRootRecipeId = true end
end
local foundRootRecipeEntry = false
for _, entry in ipairs(Registry:GetEntries("recipes", "mlua")) do
    if entry:Equals(recipeHandle) then foundRootRecipeEntry = true end
end
assert(foundRecipeHandle
    and foundRootRecipeId
    and foundRootRecipeEntry
    and Registry.Recipes:GetAll("mlua")[recipeHandle.Id]:Equals(recipeHandle)
    and Registry:GetAll("recipes", "mlua")[recipeHandle.Id]:Equals(recipeHandle),
    "recipe entry listing did not return handles keyed by the registered recipe id")
assert(Registry.Loot:GetAll("mlua")[lootHandle.Id]:Equals(lootHandle)
    and Registry:GetEntries("loot", "mlua")[1] ~= nil,
    "loot entry listing did not return generated asset handles")
print("MLUA_CI_GENERATED_ASSET_HANDLES_OK")
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
local ciEntityTickReported = false
local ciScriptedEntity = Registry.Entities:Properties()
    :Category("creature")
    :Size(0.6, 1.8)
    :AIInterval(1)
    :MaxHealth(10)
    :MovementSpeed(0.2)
    :OnSpawn(function(entity)
        entity:SetData({ smokeSpawned = true })
        print("MLUA_CI_SCRIPTED_ENTITY_SPAWN_OK")
    end)
    :OnTick(function(entity)
        if not ciEntityTickReported then
            assert(entity:GetData().smokeSpawned,
                "scripted entity did not retain its spawn callback data")
            ciEntityTickReported = true
            print("MLUA_CI_SCRIPTED_ENTITY_TICK_OK")
        end
    end)
    :AI({
        initial = "Waiting",
        states = {
            Waiting = {
                update = function(entity, state)
                    if state.TicksInState >= 1 then return "Active" end
                end,
            },
            Active = {
                onEnter = function(entity, state)
                    assert(state.State == "Active",
                        "scripted AI transition provided the wrong state context")
                    print("MLUA_CI_SCRIPTED_ENTITY_AI_TRANSITION_OK")
                end,
            },
        },
    })
Registry.Entities:Register("ci_scripted_behavior", ciScriptedEntity)
local dynamic = Registry:GetCategory("mlua:ci_records")
dynamic:Register("record", { Value = 7 })
assert(Minecraft:GetService("Registry") == Registry,
    "dynamic registry callback did not restore the calling script context")
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
'@
$registryPropertiesScript = $registryPropertiesScript.Replace("__MLUA_TARGET__", $env:MLUA_TARGET)
$registryPropertiesScript | Set-Content "$template/run/mlua/scripts/ci_registry_properties.registry.lua"
@'
{
  "meta": { "format_version": "5.0", "model_format": "free" },
  "resolution": { "width": 16, "height": 16 },
  "elements": [
    { "uuid": "ci-cube", "from": [-1, 0, -1], "to": [1, 2, 1], "faces": {} }
  ],
  "outliner": [
    {
      "uuid": "ci-root", "name": "root", "origin": [0, 0, 0],
      "children": [
        {
          "uuid": "ci-arm", "name": "arm", "origin": [0, 1, 0],
          "children": ["ci-cube"]
        }
      ]
    }
  ],
  "animations": [
    {
      "name": "idle", "loop": "loop", "length": 1,
      "animators": {
        "ci-arm": {
          "rotation": [
            { "time": 0, "data_points": [{ "x": 0, "y": 0, "z": 0 }] },
            { "time": 1, "data_points": [{ "x": 0, "y": 20, "z": 0 }] }
          ]
        }
      }
    }
  ]
}
'@ | Set-Content "$template/run/mlua/scripts/models/ci_model.bbmodel"
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
local propertyStack = Item.Find("mlua:ci_runtime_property_item")
assert(propertyStack ~= nil, "registry smoke did not produce the item-properties probe")
assert(service:VerifyItemProperties(propertyStack.__item_stack, "EPIC", true, 4, 0.5, true, true,
    "minecraft:bucket") == "verified",
    "custom registry item did not preserve rarity, fire resistance, food, and crafting remainder properties")
print("MLUA_CI_ITEM_PROPERTIES_APPLIED_OK")
local swordStack = Item.Find("mlua:ci_tiered_sword")
assert(swordStack ~= nil and swordStack.__item_stack ~= nil,
    "registered tiered sword did not produce a native item stack")
assert(service:VerifyTieredTool(swordStack.__item_stack) == "verified",
    "registered sword did not use native Minecraft tiered tool behavior")
print("MLUA_CI_TIERED_TOOL_CREATED_OK")
local bowStack = Item.Find("mlua:ci_registered_bow")
assert(bowStack ~= nil and bowStack.__item_stack ~= nil
    and service:VerifyNativeBow(bowStack.__item_stack, 384) == "verified",
    "registered bow did not retain native draw behavior and the standard durability")
print("MLUA_CI_NATIVE_BOW_CREATED_OK")
local scriptedBow = Item.Find("mlua:ci_scripted_bow")
assert(scriptedBow ~= nil and scriptedBow.__item_stack ~= nil
    and service:VerifyNativeBow(scriptedBow.__item_stack, 384) == "verified",
    "scripted bow did not preserve native bow behavior and default durability")
print("MLUA_CI_SCRIPTED_BOW_CREATED_OK")
local copiedFood = Item.Find("mlua:ci_copied_food_item")
assert(copiedFood ~= nil and copiedFood.__item_stack ~= nil,
    "item copy definition did not create an item from a vanilla item's properties")
print("MLUA_CI_ITEM_COPY_CREATED_OK")
local events = Minecraft:GetService("Events")
local unavailableEvents = {}
for _, name in ipairs(events:GetUnavailableSignals()) do
    unavailableEvents[name] = true
end
assert(not unavailableEvents.ItemPickup and events.ItemPickup ~= nil
    and type(events.ItemPickup.Connect) == "function",
    "Events.ItemPickup must be available on every loader")
local pickupConnection = events.ItemPickup:Connect(function() end)
assert(type(pickupConnection.Disconnect) == "function",
    "Events.ItemPickup did not return a disconnectable connection")
pickupConnection:Disconnect()
print("MLUA_CI_ITEM_PICKUP_EVENT_OK")
local scoreboard = Minecraft:GetService("Scoreboard")
local objective = scoreboard:CreateObjective("mlua_ci", "dummy", "MLua CI")
assert(objective:IsA("ScoreboardObjective") and objective.Name == "mlua_ci"
    and objective.DisplayName == "MLua CI",
    "Scoreboard objective did not return a typed Objective instance")
assert(not pcall(function() objective.Name = "mutated" end) and objective.Name == "mlua_ci",
    "Scoreboard objective snapshot allowed a read-only property to be changed")
scoreboard:SetScore("mlua_ci_bot", "mlua_ci", 7)
local foundScore
for _, score in ipairs(scoreboard:GetScores("mlua_ci")) do
    if score.PlayerName == "mlua_ci_bot" then foundScore = score end
end
assert(foundScore and foundScore:IsA("ScoreboardScore") and foundScore.Score == 7
    and foundScore.ObjectiveName == "mlua_ci",
    "Scoreboard score did not return a typed score instance")
local team = scoreboard:CreateTeam("mlua_ci", "MLua CI team")
assert(team:IsA("ScoreboardTeam") and team.Name == "mlua_ci",
    "Scoreboard team did not return a typed Team instance")
scoreboard:AddPlayerToTeam("mlua_ci_bot", "mlua_ci")
local refreshedTeam = scoreboard:GetTeam("mlua_ci")
assert(refreshedTeam:IsA("ScoreboardTeam") and refreshedTeam.Members[1] == "mlua_ci_bot",
    "Scoreboard team lookup did not return its typed member snapshot")
scoreboard:RemoveTeam("mlua_ci")
scoreboard:RemoveObjective("mlua_ci")
assert(scoreboard:GetTeam("mlua_ci") == nil and scoreboard:GetObjective("mlua_ci") == nil,
    "Scoreboard cleanup did not complete")
print("MLUA_CI_SCOREBOARD_CLASSES_OK")
local quests = Minecraft:GetService("Quest")
assert(type(quests.QuestStarted.Connect) == "function", "QuestStarted signal was not exposed")
assert(type(quests.QuestCompleted.Connect) == "function", "QuestCompleted signal was not exposed")
assert(type(quests.ObjectiveUpdated.Connect) == "function", "ObjectiveUpdated signal was not exposed")
local inventories = Minecraft:GetService("InventoryService")
for _, methodName in ipairs({ "GetOpenContainer", "GetOpenContainerSlot", "GetOpenContainerSlots" }) do
    assert(type(inventories[methodName]) == "function",
        "InventoryService did not register " .. methodName)
end
local validUuidError, message = pcall(function()
    inventories:GetPlayerInventory("x" .. string.rep("0", 35))
end)
assert(not validUuidError and tostring(message):find("valid UUID string", 1, true),
    "InventoryService did not validate UUID player lookups consistently")
print("MLUA_CI_SERVER_SERVICE_SCRIPT_OK")
'@ | Set-Content "$template/run/mlua/scripts/ci_java_service.server.lua"
@'
local Players = Minecraft:GetService("Players")
local Inventory = Minecraft:GetService("InventoryService")
local Chat = Minecraft:GetService("Chat")
local Events = Minecraft:GetService("Events")
local Java = Minecraft:GetService("Java")
local ciTestSheepUuid
local ciTestPlayer
Events.ItemPickup:Connect(function(event)
    if event.Item:GetItemId() ~= "minecraft:diamond" then return end
    assert(event.Item:GetCount() == 3,
        "ItemPickup did not report the exact stack count transferred to the player")
    assert(event.Player:IsA("Player"), "ItemPickup did not provide its Player instance")
    event.Player:SendMessage("MLUA_CI_PLAYER_PICKUP_RECEIVED")
    print("MLUA_CI_PLAYER_PICKUP_COUNT_OK")
end)
Events.EntityHurt:Connect(function(event)
    if ciTestSheepUuid == nil or event.Entity:GetUUID() ~= ciTestSheepUuid then return end
    -- The spawned sheep can take fall damage before the client submits its attack.
    -- Only treat player-sourced damage as the attack under test; the runner still
    -- requires the confirmation marker, so a missing player attack remains a failure.
    if event.Player == nil then
        if event.Attacker ~= nil and event.Attacker:IsA("Player") then
            error("EntityHurt omitted Player for attacker " .. event.Attacker:GetUUID())
        end
        return
    end
    assert(ciTestPlayer ~= nil and event.Player:GetUUID() == ciTestPlayer:GetUUID(),
        "the server attributed the sheep hit to " .. event.Player:GetUUID()
            .. " instead of " .. (ciTestPlayer and ciTestPlayer:GetUUID() or "the test player"))
    assert(event.Amount > 0, "client attack did not produce positive server-side damage")
    print("MLUA_CI_PLAYER_ATTACK_CONFIRMED")
    ciTestPlayer:SendMessage("MLUA_CI_PLAYER_ATTACK_CONFIRMED")
end)
Events.ContainerOpen:Connect(function(event)
    if ciTestPlayer == nil or event.Player:GetUUID() ~= ciTestPlayer:GetUUID() then return end
    assert(event.Container:GetSize() > 46,
        "opening the scripted block did not expose its chest slots and player inventory")
    print("MLUA_CI_PLAYER_CONTAINER_OPENED")
end)
Chat.OnMessage:Connect(function(event)
    if event.Message == "MLUA_CI_PLAYER_CHAT_SENT" then
        print("MLUA_CI_PLAYER_CHAT_SENT")
        local world = Minecraft:GetService("World"):Get(event.Player:GetWorld():GetDimension())
        assert(world ~= nil, "World:Get could not resolve the chat sender's dimension")
        assert(world:DropItem(event.Player:GetPosition(), "minecraft:diamond", 3) ~= nil,
            "server could not drop the item for the player-pickup simulation")
        print("MLUA_CI_PLAYER_PICKUP_DROPPED")
    end
end)
Players.PlayerAdded:Connect(function(player)
    ciTestPlayer = player
    assert(player:IsA("Player"), "Players.PlayerAdded did not provide a Player instance")
    local openContainer = Inventory:GetOpenContainer(player)
    assert(openContainer and openContainer:IsA("Container") and openContainer.Size > 0,
        "InventoryService did not return the player's live open container")
    local openSlot = Inventory:GetOpenContainerSlot(player, 1)
    local openSlots = Inventory:GetOpenContainerSlots(player)
    assert(openSlot and openSlot:IsA("Slot") and openSlots[1] == openSlot
        and openContainer:GetSlot(1) == openSlot,
        "InventoryService did not preserve live open-container slot identity")
    print("MLUA_CI_PLAYER_OPEN_CONTAINER_API_OK")
    assert(Players:GetPlayerByName(player.Name) ~= nil,
        "Players:GetPlayerByName could not resolve the newly joined player")
    print("MLUA_CI_PLAYER_SERVER_JOINED:" .. player.Name)
    assert(Inventory:AddItem(player, Item.Find("minecraft:stick", 1)),
        "server could not equip the simulated player with the render-test item")
    player:SetSelectedSlot(1)
    player:SendMessage("MLUA_CI_PLAYER_SERVER_GREETING")
    local WorldService = Minecraft:GetService("World")
    local dimension = player:GetWorld():GetDimension()
    local world = WorldService:Get(dimension)
    local dotWorld = WorldService.Get(dimension)
    assert(world ~= nil and dotWorld ~= nil
            and world:GetDimension() == dotWorld:GetDimension()
            and WorldService.GetSide() == WorldService:GetSide(),
        "World service colon and dot calls returned inconsistent values")
    local daylightRule = world:GetGameRuleEntry("doDaylightCycle")
    assert(daylightRule:IsA("GameRuleEntry") and daylightRule.Name == "doDaylightCycle"
        and daylightRule.ValueType == "boolean" and daylightRule.Value == world:GetGameRule("doDaylightCycle"),
        "World:GetGameRuleEntry did not return a live typed gamerule instance")
    local originalDaylightCycle = daylightRule.Value
    local listedRules = world:ListGameRules()
    assert(listedRules.doDaylightCycle == originalDaylightCycle,
        "World:ListGameRules did not expose the live gamerule value")
    daylightRule.Value = not originalDaylightCycle
    assert(world:GetGameRule("doDaylightCycle") == (not originalDaylightCycle),
        "GameRuleEntry.Value did not update the world's live gamerule")
    world:SetGameRule("doDaylightCycle", originalDaylightCycle)
    assert(daylightRule.Value == originalDaylightCycle,
        "World:SetGameRule did not restore the typed gamerule entry")
    print("MLUA_CI_TYPED_GAMERULE_OK")
    assert(player:GiveEffect("minecraft:speed", 1200, 0),
        "server could not apply a potion effect for the typed effect-instance check")
    local activeSpeed
    for _, effect in ipairs(player:GetEffects()) do
        if effect.Id == "minecraft:speed" then activeSpeed = effect end
    end
    assert(activeSpeed and activeSpeed:IsA("ActivePotionEffect")
        and activeSpeed.Duration > 0 and activeSpeed.Amplifier == 0,
        "Player:GetEffects did not return a typed active potion effect")
    assert(not pcall(function() activeSpeed.Amplifier = 99 end)
        and activeSpeed.Amplifier == 0,
        "ActivePotionEffect snapshot allowed a read-only property to be changed")
    print("MLUA_CI_PLAYER_WORLD_DOT_CALL_OK")
    assert(world ~= nil, "World:Get could not resolve the joining player's dimension")
    local containerX = math.floor(player:GetPositionX()) + 2
    local containerY = math.floor(player:GetPositionY())
    local containerZ = math.floor(player:GetPositionZ())
    local containerPosition = Vector3.new(containerX, containerY, containerZ)
    print("MLUA_CI_CONTAINER_SET_BLOCK_BEGIN")
    assert(world:SetBlock(containerPosition, "mlua:ci_container_block"),
        "server could not place the registered scripted container block")
    print("MLUA_CI_CONTAINER_SET_BLOCK_OK")
    player:SendMessage("MLUA_CI_CONTAINER_TARGET:" .. containerX .. "," .. containerY .. "," .. containerZ)
    print("MLUA_CI_CONTAINER_GET_BLOCK_ENTITY_BEGIN")
    local blockEntity = world:GetBlockEntity(containerPosition)
    print("MLUA_CI_CONTAINER_GET_BLOCK_ENTITY_OK")
    assert(blockEntity ~= nil and blockEntity:HasContainer(),
        "placed scripted block did not create its registered container block entity")
    local container = blockEntity:GetContainer()
    local containerSize = container and container:GetSize() or nil
    print("MLUA_CI_BLOCK_ENTITY_CONTAINER_SIZE_AFTER_LOAD:" .. tostring(containerSize))
    assert(containerSize == 18,
        "scripted block entity container had " .. tostring(containerSize) .. " slots after onLoad")
    container:SetSlot(1, Item.Find("minecraft:paper", 1))
    assert(container:GetSlot(1):GetItemId() == "minecraft:paper",
        "scripted container did not persist its allowed slot item")
    local rejectedItem = pcall(function()
        container:SetSlot(1, Item.Find("minecraft:stick", 1))
    end)
    assert(not rejectedItem and container:GetSlot(1):GetItemId() == "minecraft:paper",
        "scripted container accepted an item forbidden by its per-slot rule")
    container:SetSlot(2, Item.Find("minecraft:dirt", 1))
    assert(container:GetSlot(2):GetItemId() == "minecraft:dirt",
        "scripted container did not honor an empty allow-all slot override")
    print("MLUA_CI_CONTAINER_DATA_OK")
    print("MLUA_CI_PLAYER_CONTAINER_DATA_OK")
    local scriptedEntity = world:SpawnEntity("mlua:ci_scripted_behavior", Vector3.new(
        player:GetPositionX() + 5,
        player:GetPositionY(),
        player:GetPositionZ() + 3))
    assert(scriptedEntity ~= nil and scriptedEntity:IsA("ScriptedEntity"),
        "World:SpawnEntity did not return the registered scripted entity class")
    print("MLUA_CI_SCRIPTED_ENTITY_SPAWN_REQUEST_OK")
    local vanillaSheepEntry = RegistryEntry.new("entity", "minecraft:sheep")
    local vanillaSheepRegistered = vanillaSheepEntry:Exists()
    print("MLUA_CI_VANILLA_SHEEP_REGISTRY_EXISTS=" .. tostring(vanillaSheepRegistered))
    assert(vanillaSheepRegistered, "Minecraft's entity registry did not expose minecraft:sheep")
    local vanillaSheepType = Java:TypeOf(vanillaSheepEntry:GetValue())
    assert(type(vanillaSheepType) == "string" and vanillaSheepType:match("EntityType$"),
        "RegistryEntry:GetValue returned an unexpected entity type: " .. tostring(vanillaSheepType))
    local sheep = world:SpawnEntity("minecraft:sheep", Vector3.new(
        player:GetPositionX() + 1,
        player:GetPositionY(),
        player:GetPositionZ() + 0.5))
    assert(sheep ~= nil and sheep:IsA("LivingEntity"),
        "World:SpawnEntity did not return a live LivingEntity for the mob render simulation")
    ciTestSheepUuid = sheep:GetUUID()
    player:SendMessage("MLUA_CI_PLAYER_TEST_SHEEP:" .. ciTestSheepUuid)
    print("MLUA_CI_PLAYER_MOB_SPAWN_OK")
end)
print("MLUA_CI_PLAYER_SERVER_LISTENER_READY")
'@ | Set-Content "$template/player-test-scripts/ci_player.server.lua"
@'
local service = Minecraft:GetService("CiSmokeService")
assert(service.__type == "CiSmokeService", "Java registered service class was not attached")
assert(service:GetSide() == "CLIENT", "Java service returned the wrong side")
local toast = Minecraft:GetService("Toast")
assert(toast.__side == "Client", "Toast service was not registered as client-only")
toast:Show("MLua CI", "Native toast service smoke test")
print("MLUA_CI_TOAST_API_OK")
local taskLimits = Minecraft:GetService("RunService"):GetTaskLimits()
assert(type(taskLimits.Pending) == "number" and type(taskLimits.PendingOwned) == "number"
    and taskLimits.PendingOwned <= taskLimits.Pending
    and taskLimits.Maximum > 0 and taskLimits.MaximumPerOwner > 0,
    "RunService:GetTaskLimits returned invalid scheduler counts or limits")
print("MLUA_CI_TASK_LIMITS_OK")
task.defer(function()
    task.defer(function()
        print("MLUA_CI_NESTED_TASK_SCHEDULE_OK")
    end)
end)
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
assert(animationService:GetRegisteredAnimations()["mlua:ci_identity"] ~= nil,
  "Animation service omitted its registered asset from the snapshot")
assert(animationService:GetAnimationIds()[1] == "mlua:ci_identity",
  "Animation service did not return registered ids in a predictable array")
assert(animationService:GetAnimationCount() == 1,
    "Animation service owner count did not match its registered ids")
local replacementDefinition = Animation.new({
    { Time = 0, Data = { head = CFrame.identity } },
    { Time = 0.25, Data = { head = CFrame.Angles(0, math.rad(10), 0) } },
})
animationService:RegisterAnimation("ci_identity", replacementDefinition)
assert(animationService:GetAnimationCount() == 1
    and animationService:GetAnimation("ci_identity").Length == 0.25,
    "Replacing an owned animation duplicated its id or retained the previous asset")
assert(animationService:RemoveAnimation("ci_identity"), "Animation service failed to remove a registered id")
assert(not animationService:HasAnimation("ci_identity") and animationService:GetAnimationCount() == 0,
    "Animation service retained a removed id or stale owner count")
animationService:RegisterAnimation("ci_identity", definition)
assert(animationService:ClearAnimations() == 1 and animationService:GetAnimationCount() == 0,
    "Animation service did not clear assets and their owner index")
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
task.defer(function()
    local input = Minecraft:GetService("InputService")
    local keybinds = input:RegisterKeybinds({
        mlua_ci_keybind_z = {},
        mlua_ci_keybind_a = { Label = "MLua CI keybind" },
    })
    assert(#keybinds == 2 and keybinds[1].Name == "mlua_ci_keybind_a"
        and keybinds[2].Name == "mlua_ci_keybind_z",
        "InputService:RegisterKeybinds did not return stable, sorted keybind instances")
    input:SetKeyStates({ mlua_ci_keybind_a = true, mlua_ci_keybind_z = true })
    assert(input:IsKeyDown("mlua_ci_keybind_a") and input:IsKeyDown("mlua_ci_keybind_z"),
        "InputService:SetKeyStates did not apply the complete press batch")
    local unknownMappingBatch = pcall(function()
        input:SetKeyStates({ mlua_ci_keybind_a = false, mlua_ci_missing = true })
    end)
    assert(not unknownMappingBatch and input:IsKeyDown("mlua_ci_keybind_a")
        and input:IsKeyDown("mlua_ci_keybind_z"),
        "InputService:SetKeyStates changed states before validating every mapping")
    local invalidStateBatch = pcall(function()
        input:SetKeyStates({ mlua_ci_keybind_a = false, mlua_ci_keybind_z = "pressed" })
    end)
    assert(not invalidStateBatch and input:IsKeyDown("mlua_ci_keybind_a"),
        "InputService:SetKeyStates changed states before validating every value")
    input:SetKeyStates({ mlua_ci_keybind_a = false, mlua_ci_keybind_z = false })
    assert(not input:IsKeyDown("mlua_ci_keybind_a") and not input:IsKeyDown("mlua_ci_keybind_z"),
        "InputService:SetKeyStates did not release the calling addon's simulated presses")
    local partialKeybindsRegistered = pcall(function()
        input:RegisterKeybinds({ mlua_ci_atomic_a = {}, mlua_ci_atomic_z = false })
    end)
    assert(not partialKeybindsRegistered and input:GetKeybind("mlua_ci_atomic_a") == nil,
        "InputService:RegisterKeybinds partially registered a malformed batch")
    print("MLUA_CI_INPUT_KEYBINDS_OK")
end)
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
local activeSounds = soundService:GetAll()
assert(type(activeSounds) == "table" and soundService:GetCount() == #activeSounds,
    "SoundService:GetAll and GetCount disagreed about the active owned handles")
for _, soundHandle in ipairs(activeSounds) do
    assert(soundHandle:IsA("SoundHandle") and soundHandle:GetId() > 0,
        "SoundService:GetAll returned an invalid SoundHandle instance")
end
print("MLUA_CI_SOUND_HANDLE_QUERY_OK")
local particles = Minecraft:GetService("ParticleService")
local emitter = particles:CreateEmitter("minecraft:smoke", Vector3.new(0, 64, 0), 0)
assert(emitter:IsA("ParticleEmitter") and emitter.SpreadVector.X == emitter.Spread,
    "ParticleEmitter class or spread-vector readback was inconsistent")
emitter.SpreadVector = Vector3.new(0.25, 0.5, 0.75)
local spread = emitter:GetSpreadVector()
assert(math.abs(spread.X - 0.25) < 0.000001
    and math.abs(spread.Y - 0.5) < 0.000001
    and math.abs(spread.Z - 0.75) < 0.000001,
    "ParticleEmitter spread vector did not preserve per-axis values")
local ownedEmitters = particles:GetAll()
assert(particles:GetCount() == #ownedEmitters,
    "ParticleService:GetAll and GetCount disagreed about owned emitters")
local foundEmitter
for _, ownedEmitter in ipairs(ownedEmitters) do
    if ownedEmitter.Id == emitter.Id then foundEmitter = ownedEmitter end
end
assert(foundEmitter and foundEmitter:IsA("ParticleEmitter"),
    "ParticleService:GetAll did not include the live emitter handle")
assert(foundEmitter:Equals(emitter) and foundEmitter:GetHashCode() == emitter:GetHashCode()
    and foundEmitter:ToString():find("ParticleEmitter", 1, true),
    "ParticleEmitter handles from GetAll did not preserve class identity")
emitter:Destroy()
assert(emitter.IsDestroyed and particles:GetCount() == 0,
    "ParticleEmitter destruction did not update its live state or service query")
print("MLUA_CI_PARTICLE_EMITTER_API_OK")
local render = Minecraft:GetService("RenderService")
local customModel = render:LoadBBModel("models/ci_model.bbmodel", { Visible = false })
assert(customModel:IsA("RenderObject"), "RenderService:LoadBBModel did not return a RenderObject")
local modelTransform = CFrame.new(0.25, 0.5, -0.75) * CFrame.Angles(0, math.pi / 2, 0)
customModel.ModelTransform = modelTransform
local returnedModelTransform = customModel:GetModelTransform()
assert(math.abs(returnedModelTransform.X - 0.25) < 0.000001
    and math.abs(returnedModelTransform.Y - 0.5) < 0.000001
    and math.abs(returnedModelTransform.Z + 0.75) < 0.000001
    and math.abs(returnedModelTransform.LookVector.X - modelTransform.LookVector.X) < 0.000001
    and math.abs(returnedModelTransform.LookVector.Z - modelTransform.LookVector.Z) < 0.000001,
    "RenderObject ModelTransform did not preserve its CFrame translation and rotation")
print("MLUA_CI_MODEL_TRANSFORM_OK")
local customParts = customModel:GetModelParts()
assert(#customParts == 2, "loaded Blockbench model did not expose both hierarchical parts")
local rootPart = customModel:GetModelPartById("ci-root")
local armPart = customModel:GetModelPart("arm")
assert(rootPart:IsValid(), "RenderPart:IsValid returned false for a live root bone")
assert(rootPart.Valid, "root RenderPart handle is not valid")
assert(armPart.Valid, "arm RenderPart handle is not valid")
assert(rootPart.Name == "root", "root RenderPart handle returned the wrong name")
assert(armPart.Name == "arm", "arm RenderPart handle returned the wrong name")
assert(armPart.Parent:Equals(rootPart) and rootPart:GetChildren()[1]:Equals(armPart),
    "Blockbench RenderPart handles did not preserve parent/child hierarchy")
customModel:PlayAnimation("idle")
assert(customModel:GetAnimationName() == "idle" and customModel:IsAnimationPlaying(),
    "Blockbench RenderObject did not start its registered animation")
customModel:SetAnimationSpeed(0.5)
assert(customModel:GetAnimationSpeed() == 0.5,
    "Blockbench RenderObject did not retain its configured animation speed")
customModel:PauseAnimation()
assert(customModel:IsAnimationPaused(), "Blockbench RenderObject did not pause its animation")
customModel:ResumeAnimation()
assert(customModel:IsAnimationPlaying(), "Blockbench RenderObject did not resume its animation")
customModel:StopAnimation()
customModel:Destroy()
assert(not rootPart.Valid and not armPart.Valid,
    "destroying a Blockbench RenderObject left its RenderPart handles valid")
print("MLUA_CI_BBMODEL_HIERARCHY_OK")
local renderText = render:CreateText("MLua render type check", { Visible = false })
assert(not pcall(function() renderText.ModelTransform = CFrame.new() end),
    "RenderObject ModelTransform was accepted on a text primitive")
assert(renderText:IsA("RenderObject") and renderText:GetType() == "RenderObject",
    "RenderObject:GetType did not return its MLua runtime class")
local renderMethodOk, renderType = pcall(function() return renderText:GetRenderType() end)
print("MLUA_CI_RENDER_METHOD_RESULT=" .. tostring(renderMethodOk) .. ":" .. tostring(renderType))
assert(renderMethodOk and renderType == "text", "RenderObject:GetRenderType failed or returned the wrong primitive")
local propertyOk, renderTypeProperty = pcall(function() return renderText.Type end)
print("MLUA_CI_RENDER_PROPERTY_RESULT=" .. tostring(propertyOk) .. ":" .. tostring(renderTypeProperty))
assert(propertyOk and renderTypeProperty == "text",
    "RenderObject render primitive was not exposed consistently through GetRenderType and Type")
print("MLUA_CI_RENDER_GETALL_BEGIN")
local renderTextSnapshot
local renderObjects = render:GetAll()
print("MLUA_CI_RENDER_GETALL_RETURNED=" .. tostring(#renderObjects))
for _, object in ipairs(renderObjects) do
    print("MLUA_CI_RENDER_GETALL_HANDLE=" .. tostring(object.Id))
    if object.Id == renderText.Id then renderTextSnapshot = object end
end
print("MLUA_CI_RENDER_EQUALS_BEGIN")
assert(renderTextSnapshot ~= nil and renderText:Equals(renderTextSnapshot),
    "RenderObject handles did not compare equal to their service snapshot")
print("MLUA_CI_RENDER_HASH_BEGIN")
assert(type(renderText:GetHashCode()) == "number", "RenderObject:GetHashCode did not return a number")
print("MLUA_CI_RENDER_TOSTRING_BEGIN")
local renderTextDescription = renderText:ToString()
print("MLUA_CI_RENDER_TOSTRING_RETURNED=" .. tostring(renderTextDescription))
print("MLUA_CI_RENDER_TOSTRING_ASSERT_BEGIN")
assert(renderTextDescription:find("RenderObject", 1, true), "RenderObject:ToString omitted the runtime class")
print("MLUA_CI_RENDER_TOSTRING_ASSERT_OK")
print("MLUA_CI_RENDER_DESTROY_BEGIN")
renderText:Destroy()
print("MLUA_CI_RENDER_DESTROY_RETURNED")
print("MLUA_CI_RENDER_DESTROYED_PROPERTY_BEGIN")
assert(renderText.IsDestroyed, "RenderObject handle did not remain readable after Destroy")
print("MLUA_CI_RENDER_DESTROYED_PROPERTY_OK")
local gui = Minecraft:GetService("Gui")
local screenTicks = 0
local editBoxChanged = false
local buttonActivated = false
local tickScreen
task.delay(100, function()
tickScreen = gui:OpenNativeScreen("MLua CI screen lifecycle", function(screen)
    print("MLUA_CI_NATIVE_SCREEN_INIT_CALLBACK_OK")
    screen:AddButton("CI button", UDim2.fromOffset(20, 20), UDim2.fromOffset(100, 20), function()
        buttonActivated = true
        print("MLUA_CI_NATIVE_SCREEN_BUTTON_CALLBACK_OK")
    end)
    local field = screen:AddEditBox(UDim2.fromOffset(20, 48), UDim2.fromOffset(140, 20), "before", function(value)
        if value == "after" then
            editBoxChanged = true
            print("MLUA_CI_NATIVE_SCREEN_EDITBOX_CHANGE_OK")
        end
    end)
    screen:SetOnTick(function()
        if screenTicks == 0 then print("MLUA_CI_NATIVE_SCREEN_TICK_CALLBACK_ENTERED") end
        screenTicks = screenTicks + 1
        if screenTicks == 1 then
            assert(tickScreen.IsOpen, "NativeScreen did not report open state during its client tick")
            local controls = tickScreen:GetControls()
            assert(#controls == 2, "NativeScreen:GetControls did not return the native button and edit box")
            local button = controls[1]
            local editBox = controls[2]
            assert(button:IsWidgetClass("Button") and button.Text == "CI button",
                "NativeControl did not expose the native button's type and text")
            local onPressParameters
            for _, signature in ipairs(button:GetMethods()) do
                if signature:find(".onPress(", 1, true) then
                    onPressParameters = signature:match("onPress%((.*)%)")
                    break
                end
            end
            assert(onPressParameters ~= nil,
                "Minecraft's native button did not expose its activation method")
            if onPressParameters == "" then
                button:Invoke("onPress")
            else
                -- Newer Minecraft versions pass InputWithModifiers to the button;
                -- Button.onPress ignores it and dispatches the same registered action.
                button:Invoke("onPress", nil)
            end
            assert(buttonActivated,
                "Minecraft button activation did not deliver the registered Lua callback")
            editBox.Value = "after"
            assert(editBox.Value == "after" and editBoxChanged,
                "NativeControl did not update the native edit box or dispatch its value callback")
            print("MLUA_CI_NATIVE_SCREEN_CONTROLS_OK")
            print("MLUA_CI_NATIVE_SCREEN_TICK_OK")
            tickScreen:Close()
            assert(#tickScreen:GetControls() == 0 and tickScreen:GetControl(1) == nil,
                "Closed NativeScreen exposed stale NativeControl handles")
            print("MLUA_CI_CLOSED_SCREEN_CONTROLS_OK")
            local smoke = Minecraft:GetService("CiSmokeService")
            assert(smoke:MarkNativeScreenLifecycleComplete() == "complete",
                "Java smoke service did not acknowledge native screen lifecycle completion")
        end
    end)
    print("MLUA_CI_NATIVE_SCREEN_TICK_CALLBACK_REGISTERED")
end)
assert(type(tickScreen.IsPending) == "boolean",
    "NativeScreen.IsPending did not return a boolean state")
print("MLUA_CI_NATIVE_SCREEN_STATE_OK")
end)
print("MLUA_CI_ANIMATION_API_OK")
local serviceContracts = {
    { "World", { "Get", "GetAll", "GetSide" } },
    { "Players", { "GetPlayerByName", "GetPlayerByUUID" } },
    { "RunService", { "GetDeltaTime", "GetTaskLimits" } },
    { "Animation", { "RegisterAnimation", "GetAnimation", "GetAnimationIds", "GetAnimationCount" } },
    { "Camera", { "GetMode", "SetMode", "SetFOV" } },
    { "InputService", { "GetKeybind", "RegisterKeybinds", "SetKeyStates" } },
    { "Attribute", { "GetAttributeInstance", "ResetBaseValue", "HasModifier" } },
    { "Components", { "Find", "Has", "GetAll" } },
    { "Config", { "SetMany" } },
    { "SoundService", { "GetCategories", "Play", "GetAll", "GetCount" } },
    { "ParticleService", { "CreateEmitter", "SpawnBurst", "GetAll", "GetCount" } },
    { "RenderService", { "CreateText", "GetAll", "LoadBBModel" } },
    { "Gui", { "OpenNativeScreen" } },
    { "Toast", { "Show" } },
    { "Events", { "GetSignalLimits", "GetSignalNames", "GetUnavailableSignals" } },
    { "PotionEffects", { "Get", "GetAll", "Exists" } },
    { "InventoryService", { "GetOpenContainer", "GetOpenContainerSlot", "GetOpenContainerSlots" } },
}
for _, contract in ipairs(serviceContracts) do
    local serviceName = contract[1]
    local service = Minecraft:GetService(serviceName)
    assert(service ~= nil, "Missing service on this supported target: " .. serviceName)
    for _, methodName in ipairs(contract[2]) do
        assert(type(service[methodName]) == "function",
            "Missing " .. serviceName .. ":" .. methodName .. " on this supported target")
    end
end
local runService = Minecraft:GetService("RunService")
local colonLimits = runService:GetTaskLimits()
local dotLimits = runService.GetTaskLimits()
assert(type(colonLimits) == "table" and type(dotLimits) == "table"
    and colonLimits.Maximum == dotLimits.Maximum
    and colonLimits.MaximumPerOwner == dotLimits.MaximumPerOwner
    and colonLimits.MaxInstructionsPerResume == dotLimits.MaxInstructionsPerResume,
    "RunService.GetTaskLimits and RunService:GetTaskLimits returned different shapes or values")
local signalLimits = Minecraft:GetService("Events"):GetSignalLimits()
assert(signalLimits:IsA("SignalLimits") and signalLimits.MaxListenersPerSignal > 0,
    "Events:GetSignalLimits did not return a typed SignalLimits instance")
local potionEffects = Minecraft:GetService("PotionEffects")
local speedEffect = potionEffects:Get("minecraft:speed")
assert(speedEffect:IsA("PotionEffectType") and speedEffect.Id == "minecraft:speed"
    and speedEffect.IsInstant == false,
    "PotionEffects:Get did not return typed potion-effect metadata")
assert(not pcall(function() speedEffect.Id = "mlua:mutated" end)
    and speedEffect.Id == "minecraft:speed",
    "PotionEffectType snapshot allowed a read-only property to be changed")
assert(potionEffects:Exists("speed"),
    "PotionEffects:Exists did not apply the default Minecraft namespace")
assert(not potionEffects:Exists("not a real effect"),
    "PotionEffects:Exists returned true for an invalid effect identifier")
local unknownEffectOk, unknownEffectError = pcall(function()
    potionEffects:Get("mlua:not_a_real_effect")
end)
assert(not unknownEffectOk and tostring(unknownEffectError):find("Unknown potion effect", 1, true),
    "PotionEffects:Get did not provide a useful unknown-identifier error")
local registeredEffects = potionEffects:GetAll()
assert(#registeredEffects > 0 and registeredEffects[1]:IsA("PotionEffectType"),
    "PotionEffects:GetAll did not return typed potion-effect instances")
for index = 2, #registeredEffects do
    assert(registeredEffects[index - 1].Id <= registeredEffects[index].Id,
        "PotionEffects:GetAll did not return effects in identifier order")
end
print("MLUA_CI_SERVICE_PARITY_OK")
print("MLUA_CI_CLIENT_SERVICE_SCRIPT_OK")
'@ | Set-Content "$template/run/mlua/scripts/ci_java_service.client.lua"
@'
local Players = Minecraft:GetService("Players")
local RunService = Minecraft:GetService("RunService")
local Gui = Minecraft:GetService("Gui")
local Camera = Minecraft:GetService("Camera")
local Render = Minecraft:GetService("RenderService")
local Input = Minecraft:GetService("InputService")
local Models = Minecraft:GetService("ModelService")
local Java = Minecraft:GetService("Java")
local headAnimation = Animation.new({
    { Time = 0, Data = { head = CFrame.new(0.08, 0, 0) } },
})
local playerSeen = false
local heartbeatTicks = 0
local localPlayerNilReported = false
local chatSent = false
local firstPersonRendered = false
local thirdPersonRequested = false
local thirdPersonRendered = false
local playerModelRendered = false
local playerAdapterRegistered = false
local playerTrack
local playerAdapterApplied = false
local playerAnimationPoseReady = false
local mobModelRendered = false
local mobAdapterRegistered = false
local mobTrack
local mobAdapterApplied = false
local mobAnimationPoseReady = false
local worldGeometryContextPassed = false
local renderDeltaTimeChecked = false
local inputPressState = 0
local inputMovementTicks = 0
local inputStartX = 0
local inputStartZ = 0
local playerMovementPassed = false
local mousePressReleasePassed = false
local playerPickupReceived = false
local containerTarget
local containerUseState = 0
local containerUseAttempts = 0
local containerUseWaitTicks = 0
local clientContainerOpened = false
local testSheepUuid
local attackSubmitted = false
local nearbyQueryDiagnosticLogged = false
local nearbyQueryResultLogged = false
local attackConfirmed = false
local connection
local chatLineHookObserved = false
local worldTransformMesh = Render:CreateMesh({
    Vertices = {
        { Position = Vector3.new(0, 0, 0) },
        { Position = Vector3.new(0.25, 0, 0) },
        { Position = Vector3.new(0, 0.25, 0) },
    },
    Indices = { 0, 1, 2 },
}, {
    Target = "Manual",
    ModelTransform = CFrame.new(0.1, 0.05, 0) * CFrame.Angles(0, math.pi / 4, 0),
})
local worldTransformMeshDrawn = false
Render:RegisterWorldGeometryPass("mlua:ci-world-geometry", function(context)
    assert(context.Target == "WorldGeometry", "world geometry callback received the wrong render target")
    assert(context.Stage == "CUSTOM_GEOMETRY", "world geometry context omitted its canonical stage")
    assert(type(context.DeltaTime) == "number" and context.DeltaTime == context.DeltaTime
        and context.DeltaTime >= 0 and context.DeltaTime <= 1,
        "RenderContext.DeltaTime must be a finite, bounded number of seconds")
    if context.HasDeltaTime and not renderDeltaTimeChecked then
        renderDeltaTimeChecked = true
        print("MLUA_CI_PLAYER_RENDER_DELTA_TIME_OK")
    end
    if context.HasDrawObjectSource and not worldTransformMeshDrawn then
        assert(context:DrawObject(worldTransformMesh, CFrame.new(0, 1, 0)),
            "RenderContext:DrawObject did not submit a ModelTransform Mesh")
        worldTransformMeshDrawn = true
        print("MLUA_CI_PLAYER_MODEL_TRANSFORM_DRAW_OK")
    end
    if not worldGeometryContextPassed then
        worldGeometryContextPassed = true
        print("MLUA_CI_PLAYER_WORLD_GEOMETRY_CONTEXT_OK")
    end
end)
Render:RegisterFirstPersonItemRenderPass("minecraft:stick", "mlua:ci-player-first-person", function(player, context)
    assert(context.Target == "FirstPersonItem" and context.ItemStack ~= nil,
        "first-person item callback did not receive its live render context")
    if not firstPersonRendered then
        firstPersonRendered = true
        print("MLUA_CI_PLAYER_FIRST_PERSON_ITEM_RENDERED")
    end
end)
Render:RegisterThirdPersonItemRenderPass("minecraft:stick", "mlua:ci-player-third-person", function(entity, context)
    assert(context.Target == "ThirdPersonItem" and context.ItemStack ~= nil
        and context.Hand ~= nil and context.Model == nil,
        "third-person held-item callback did not receive its item render context")
    if not thirdPersonRendered then
        thirdPersonRendered = true
        print("MLUA_CI_PLAYER_THIRD_PERSON_ITEM_RENDERED")
    end
end)
Render:RegisterEntityRenderPass("minecraft:player", "mlua:ci-player-model", function(entity, context)
    assert(context.Target == "EntityModel" and context.Model ~= nil,
        "player model callback did not receive its live model render context")
    if not playerModelRendered then
        playerModelRendered = true
        print("MLUA_CI_PLAYER_MODEL_RENDERED")
    end
    if not playerAdapterRegistered then
        local modelClass = Java:TypeOf(context.Model)
        assert(type(modelClass) == "string" and #modelClass > 0,
            "Java:TypeOf could not identify the active player model class")
        Models:RegisterAdapter(modelClass, { head = "head" })
        playerTrack = entity:GetAnimator():LoadAnimation(headAnimation)
        playerTrack:Play()
        playerAdapterRegistered = true
    end
    local applied = Models:GetAppliedAdapter(entity)
    if applied and applied.Mappings.head == "head" then
        for _, bone in ipairs(applied.ResolvedBones) do
            if bone == "head" and not playerAdapterApplied then
                playerAdapterApplied = true
                print("MLUA_CI_PLAYER_MODEL_ADAPTER_APPLIED")
            end
        end
    end
    if playerTrack and playerTrack:GetBonePose("head") ~= nil and not playerAnimationPoseReady then
        playerAnimationPoseReady = true
        print("MLUA_CI_PLAYER_ANIMATION_POSE_READY")
    end
end)
Render:RegisterEntityRenderPass("minecraft:sheep", "mlua:ci-sheep-model", function(entity, context)
    assert(context.Target == "EntityModel" and context.Model ~= nil,
        "non-player mob callback did not receive its live model render context")
    if not mobModelRendered then
        mobModelRendered = true
        print("MLUA_CI_PLAYER_MOB_MODEL_RENDERED")
    end
    if not mobAdapterRegistered then
        local modelClass = Java:TypeOf(context.Model)
        assert(type(modelClass) == "string" and #modelClass > 0,
            "Java:TypeOf could not identify the active sheep model class")
        local staleResolver = Models:RegisterAdapterResolver(modelClass, function(model)
            return { head = "head" }
        end)
        local resolver = Models:RegisterAdapterResolver(modelClass, function(model)
            return { head = "head" }
        end)
        assert(not staleResolver.Connected and resolver.Connected,
            "replacing a model resolver did not update its Connection state")
        staleResolver:Disconnect()
        assert(resolver.Connected,
            "disconnecting a stale model resolver removed its replacement")
        print("MLUA_CI_PLAYER_MODEL_ADAPTER_CONNECTION_OK")
        mobTrack = entity:GetAnimator():LoadAnimation(headAnimation)
        mobTrack:Play()
        mobAdapterRegistered = true
    end
    local applied = Models:GetAppliedAdapter(entity)
    if applied and applied.Mappings.head == "head" then
        for _, bone in ipairs(applied.ResolvedBones) do
            if bone == "head" and not mobAdapterApplied then
                mobAdapterApplied = true
                print("MLUA_CI_PLAYER_MOB_ADAPTER_APPLIED")
            end
        end
    end
    if mobTrack and mobTrack:GetBonePose("head") ~= nil and not mobAnimationPoseReady then
        mobAnimationPoseReady = true
        print("MLUA_CI_PLAYER_MOB_ANIMATION_POSE_READY")
    end
end)
Gui.ChatLineAdding:Connect(function(line)
    if not chatLineHookObserved then
        chatLineHookObserved = true
        print("MLUA_CI_PLAYER_CHAT_LINE_HOOK_FIRED")
    end
    if line.OriginalText:find("MLUA_CI_PLAYER_SERVER_GREETING", 1, true) then
        print("MLUA_CI_PLAYER_GREETING_RECEIVED")
    end
    if line.OriginalText:find("MLUA_CI_PLAYER_PICKUP_RECEIVED", 1, true) then
        playerPickupReceived = true
        print("MLUA_CI_PLAYER_PICKUP_RECEIVED")
    end
    local containerX, containerY, containerZ = line.OriginalText:match(
        "MLUA_CI_CONTAINER_TARGET:(%-?%d+),(%-?%d+),(%-?%d+)")
    if containerX then
        containerTarget = Vector3.new(tonumber(containerX), tonumber(containerY), tonumber(containerZ))
    end
    local sheepUuid = line.OriginalText:match("MLUA_CI_PLAYER_TEST_SHEEP:([%x%-]+)")
    if sheepUuid then
        testSheepUuid = sheepUuid
        print("MLUA_CI_PLAYER_SHEEP_UUID_RECEIVED")
    end
    if line.OriginalText:find("MLUA_CI_PLAYER_ATTACK_CONFIRMED", 1, true) then
        attackConfirmed = true
        print("MLUA_CI_PLAYER_ATTACK_CONFIRMED")
    end
end)
connection = RunService.Heartbeat:Connect(function()
    heartbeatTicks = heartbeatTicks + 1
    if heartbeatTicks == 1 then print("MLUA_CI_PLAYER_HEARTBEAT_FIRED") end
    local player = Players:GetLocalPlayer()
    if player == nil then
        if heartbeatTicks >= 20 and not localPlayerNilReported then
            localPlayerNilReported = true
            print("MLUA_CI_PLAYER_HEARTBEAT_LOCAL_PLAYER_NIL")
        end
        return
    end
    if not playerSeen then
        playerSeen = true
        print("MLUA_CI_PLAYER_CLIENT_JOINED:" .. player.Name)
        assert(player.IsLocalPlayer, "connected player was not the local player")
        assert(Players:GetPlayerByName(player.Name) ~= nil,
            "client Players:GetPlayerByName could not resolve the local player")
        player:SetSelectedSlot(1)
        assert(player:GetSelectedSlot() == 1,
            "client could not select the server-equipped render-test item")
        Camera:SetViewMode("first_person")
    end
    -- Keep the native container interaction independent from render adapters and
    -- the later keyboard/mouse assertions. Those are separate client capabilities;
    -- a slow render pass must not prevent us from exercising block interaction.
    if containerTarget and not clientContainerOpened then
        if containerUseState == 0 and containerUseAttempts < 3 then
            local dx = containerTarget.X + 0.5 - player:GetPositionX()
            local dy = containerTarget.Y + 0.5 - (player:GetPositionY() + 1.62)
            local dz = containerTarget.Z + 0.5 - player:GetPositionZ()
            local yaw = math.deg(math.atan2(dz, dx)) - 90
            local pitch = -math.deg(math.atan2(dy, math.sqrt(dx * dx + dz * dz)))
            player:SetRotation(pitch, yaw)
            Input:SetMouseButtonDown(1, true)
            containerUseAttempts = containerUseAttempts + 1
            containerUseWaitTicks = 0
            containerUseState = 1
        elseif containerUseState == 1 then
            Input:SetMouseButtonDown(1, false)
            assert(not Input:IsKeyDown("key.use"),
                "InputService did not release the simulated right mouse button")
            containerUseState = 2
        elseif containerUseState == 2 then
            if player:GetOpenContainerSize() > 46 then
                clientContainerOpened = true
                print("MLUA_CI_PLAYER_CLIENT_CONTAINER_OPENED")
            else
                containerUseWaitTicks = containerUseWaitTicks + 1
                if containerUseWaitTicks >= 20 then containerUseState = 0 end
            end
        end
    end
    if testSheepUuid and not attackSubmitted then
        if not nearbyQueryDiagnosticLogged then
            nearbyQueryDiagnosticLogged = true
            print("MLUA_CI_PLAYER_NEARBY_QUERY_BEGIN")
        end
        local nearbyEntityCount = 0
        for _, entity in ipairs(player:GetNearbyEntities(8)) do
            nearbyEntityCount = nearbyEntityCount + 1
            if entity.UUID == testSheepUuid then
                print("MLUA_CI_PLAYER_ATTACK_CALL_BEGIN")
                assert(entity:Attack(), "Entity:Attack did not submit the local-player attack")
                attackSubmitted = true
                print("MLUA_CI_PLAYER_ATTACK_SUBMITTED")
                break
            end
        end
        if not nearbyQueryResultLogged then
            nearbyQueryResultLogged = true
            print("MLUA_CI_PLAYER_NEARBY_QUERY_DONE:" .. nearbyEntityCount)
        end
    end
    if playerPickupReceived and inputPressState == 0 then
        print("MLUA_CI_PLAYER_INPUT_PRESS_BEGIN")
        inputStartX = player:GetPositionX()
        inputStartZ = player:GetPositionZ()
        Input:SetKeyDown("key.forward", true)
        Input:SetKeyDown("key.jump", true)
        assert(Input:IsKeyDown("key.jump"),
            "InputService did not report the simulated vanilla jump key as pressed")
        inputPressState = 1
    elseif inputPressState == 1 then
        assert(Input:IsKeyDown("key.jump"),
            "InputService did not keep the simulated jump key pressed through a client tick")
        Input:SetKeyDown("key.jump", false)
        assert(not Input:IsKeyDown("key.jump"),
            "InputService did not release this addon's simulated jump key")
        inputPressState = 2
        print("MLUA_CI_PLAYER_INPUT_PRESS_RELEASE_OK")
    elseif inputPressState == 2 then
        assert(Input:IsKeyDown("key.forward"),
            "InputService did not keep the simulated forward key pressed")
        inputMovementTicks = inputMovementTicks + 1
        if inputMovementTicks >= 10 then
            Input:SetKeyDown("key.forward", false)
            assert(not Input:IsKeyDown("key.forward"),
                "InputService did not release this addon's simulated forward key")
            local dx = player:GetPositionX() - inputStartX
            local dz = player:GetPositionZ() - inputStartZ
            assert(dx * dx + dz * dz > 0.0025,
                "holding the simulated forward key did not move the local player")
            playerMovementPassed = true
            inputPressState = 3
            print("MLUA_CI_PLAYER_MOVEMENT_OK")
        end
    elseif inputPressState == 3 then
        Input:SetMouseButtonDown(2, true)
        assert(Input:IsKeyDown("key.pickItem"),
            "InputService did not report the simulated middle mouse button as pressed")
        inputPressState = 4
    elseif inputPressState == 4 then
        assert(Input:IsKeyDown("key.pickItem"),
            "InputService did not keep the simulated middle mouse button pressed through a client tick")
        Input:SetMouseButtonDown(2, false)
        assert(not Input:IsKeyDown("key.pickItem"),
            "InputService did not release the simulated middle mouse button")
        Input:ClickMouseButton(2)
        mousePressReleasePassed = true
        inputPressState = 5
        print("MLUA_CI_PLAYER_MOUSE_PRESS_RELEASE_OK")
    end
    if not chatSent then
        assert(player:SendChatMessage("MLUA_CI_PLAYER_CHAT_SENT") == 1,
            "client could not submit chat through the active server connection")
        chatSent = true
        print("MLUA_CI_PLAYER_CHAT_SUBMITTED")
    end
    if firstPersonRendered and not thirdPersonRequested then
        Camera:SetViewMode("third_person")
        thirdPersonRequested = true
    end
end)
'@ | Set-Content "$template/player-test-scripts/ci_player.client.lua"
Set-Content "$template/run/eula.txt" 'eula=true'
Set-Content "$template/run/server.properties" @('online-mode=false', 'enforce-secure-profile=false', 'server-port=25565', 'enable-query=false', 'enable-rcon=false')
