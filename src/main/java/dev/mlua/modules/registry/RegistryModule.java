package dev.mlua.modules.registry;

import dev.mlua.core.MLuaContextView;
import dev.mlua.core.classes.MLuaClassMethods;
import dev.mlua.minecraft.binding.MLuaRegistryProperties;
import dev.mlua.module.MLuaRegistration;
import dev.mlua.minecraft.binding.MLuaItemWrapper;
import dev.mlua.minecraft.binding.MLuaBlockWrapper;
import dev.mlua.minecraft.binding.MLuaRegistryCatalogs;
import dev.mlua.minecraft.binding.MLuaRegistryEntry;
import dev.mlua.minecraft.binding.MLuaContainerType;
import dev.mlua.minecraft.binding.MLuaTextureBindings;
import dev.mlua.modules.registry.Service.MLuaRegistryService;
import dev.mlua.runtime.registry.MLuaContentTypeRegistry;
import dev.mlua.minecraft.scripted.MLuaBehaviorRegistry;
import dev.mlua.module.MLuaDocBuilder;
import dev.mlua.module.MLuaModule;
import java.util.List;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaTable;

public class RegistryModule implements MLuaModule {
    @Override
    public void clearOwnedState(MLuaRegistration runtime, Object runtimeSession,
                                dev.mlua.common.enums.Side side, String ownerModId) {
        MLuaRegistryService.clearOwnedCategories(runtimeSession, side, ownerModId);
        MLuaContentTypeRegistry.clearOwner(runtime, ownerModId);
        MLuaBehaviorRegistry.clearOwner(ownerModId);
        dev.mlua.runtime.registry.MLuaLoaderRegistry.clearGeneratedDataResources(ownerModId);
        dev.mlua.runtime.registry.MLuaLoaderRegistry.clearGeneratedAssetResources(ownerModId);
    }

    @Override
    public String id() { return "registry"; }

    @Override
    public int priority() { return -50; }

    @Override
    public List<String> dependencies() { return List.of("core-classes", "players"); }

    @Override
    public List<ServiceEntry> services() {
        ServiceEntry.Builder registry = ServiceEntry.builder("Registry", MLuaRegistryService::new)
            .description("Registers and queries addon content, generated data pack resources, Minecraft registries, and reusable inherited content types.");
        MLuaRegistryService.serviceMethodDescriptions().forEach(registry::function);
        return List.of(registry.build());
    }

    @Override
    public void registerClasses(MLuaRegistration registration) {
        MLuaRegistryProperties.registerClasses(registration);
        registration.registerClass("Nbt", registration.requireInstanceBaseClassName(), "A live view of a Minecraft NBT compound that reads and writes its backing data.");
        MLuaClassMethods.registerInstanceMethod(registration, "Nbt", "Get", "Reads a value at the given NBT key.");
        MLuaClassMethods.registerInstanceMethod(registration, "Nbt", "Set", "Writes a value at the given NBT key.");
        MLuaClassMethods.registerInstanceMethod(registration, "Nbt", "Remove", "Removes the value at the given NBT key.");
        registration.registerClass("RegistryCategory", registration.requireInstanceBaseClassName(),
            "A live category API table exposed by the Registry service.");
        registration.registerClassFunction("RegistryCategory", "GetName", new org.luaj.vm2.lib.OneArgFunction() {
            @Override public org.luaj.vm2.LuaValue call(org.luaj.vm2.LuaValue self) {
                return self.checktable().get("__registry_category_name");
            }
        }, "Returns this category's canonical Registry table name.");
        registration.registerClassFunction("RegistryCategory", "GetDescription", new org.luaj.vm2.lib.OneArgFunction() {
            @Override public org.luaj.vm2.LuaValue call(org.luaj.vm2.LuaValue self) {
                return self.checktable().get("__mlua_doc_description");
            }
        }, "Returns the category's registered description.");
        registration.registerClassFunction("RegistryCategory", "GetMethods", new org.luaj.vm2.lib.OneArgFunction() {
            @Override public org.luaj.vm2.LuaValue call(org.luaj.vm2.LuaValue self) {
                org.luaj.vm2.LuaValue metadata = self.checktable().rawget("__mlua_doc_methods");
                java.util.List<String> names = new java.util.ArrayList<>();
                if (metadata.istable()) {
                    for (org.luaj.vm2.LuaValue key : metadata.checktable().keys()) {
                        if (key.isstring()) names.add(key.tojstring());
                    }
                }
                java.util.Collections.sort(names);
                org.luaj.vm2.LuaTable result = new org.luaj.vm2.LuaTable();
                for (int index = 0; index < names.size(); index++) {
                    result.set(index + 1, org.luaj.vm2.LuaValue.valueOf(names.get(index)));
                }
                return result;
            }
        }, "Lists the category-specific methods registered on this API table.");
        registration.registerClassFunction("RegistryCategory", "GetMethodDescription", new org.luaj.vm2.lib.TwoArgFunction() {
            @Override public org.luaj.vm2.LuaValue call(org.luaj.vm2.LuaValue self, org.luaj.vm2.LuaValue name) {
                org.luaj.vm2.LuaValue metadata = self.checktable().rawget("__mlua_doc_methods");
                return metadata.istable() ? metadata.checktable().get(name) : org.luaj.vm2.LuaValue.NIL;
            }
        }, "Returns the registered description for one category method, or nil when that method is unavailable.");
        registration.registerClassProperty("RegistryCategory", "Name", new org.luaj.vm2.lib.OneArgFunction() {
            @Override public org.luaj.vm2.LuaValue call(org.luaj.vm2.LuaValue self) {
                return self.checktable().get("__registry_category_name");
            }
        }, org.luaj.vm2.LuaValue.NIL, "string", "shared:Shared", "This category's canonical Registry table name.");
        registration.registerClassProperty("RegistryCategory", "Description", new org.luaj.vm2.lib.OneArgFunction() {
            @Override public org.luaj.vm2.LuaValue call(org.luaj.vm2.LuaValue self) {
                return self.checktable().get("__mlua_doc_description");
            }
        }, org.luaj.vm2.LuaValue.NIL, "string", "shared:Shared", "The category's registered description.");
        registration.registerClass("ScriptedEntity", "Mob",
            "A registered Lua-controlled mob with navigation, sensing, combat, and persistent AI data.");
        String[][] aiMethods = {
            {"MoveTo", "On the server, finds a path to a Vector3 or CFrame position using a finite positive speed."},
            {"StopMoving", "On the server, stops the current navigation path and reports whether a path was active."},
            {"LookAt", "Turns the mob toward a native Vector3 or CFrame position."},
            {"AttackTarget", "On the server, sets or clears this mob's target; a target must be in the same dimension."},
            {"GetTarget", "Returns the mob's current live target, or nil."},
            {"CanSee", "Returns whether this mob has line of sight to a live entity in the same dimension."},
            {"GetBehaviorKey", "Returns the registered content key that defines this mob's behavior."},
            {"GetAIState", "Returns the mob's active scripted AI state, or nil."},
            {"GetAIStateTicks", "Returns the number of ticks spent in the active AI state."},
            {"SetAIState", "Changes the active scripted AI state, runs its exit and enter callbacks, and resumes AI disabled after an error."},
            {"FindNearestPlayer", "Finds the nearest living player within a finite range capped at 128 blocks, optionally requiring line of sight."},
            {"FindNearestEntity", "Finds the nearest living entity within a finite range capped at 128 blocks, optionally filtering by entity type."},
            {"FindNearestBlock", "Finds a matching block within a whole-number radius clamped to 0 through 16 blocks."},
            {"OpenDoor", "Opens or toggles a door at the given position."},
            {"CloseDoor", "Closes a door at the given position."},
            {"IsDoorOpen", "Returns whether the door at the given position is open."},
            {"SetDoorBreakProgress", "Shows door breaking progress from 0 through 9, or clears it with -1."},
            {"BreakDoor", "Breaks a wooden door at the given position."},
            {"SetCanOpenDoors", "Enables or disables opening doors while navigating."},
            {"SetCanPassDoors", "Enables or disables pathfinding through doors."},
            {"GetData", "Returns persistent Lua data saved with this mob."},
            {"SetData", "Replaces persistent Lua data after conversion; cyclic tables, nesting deeper than 64 levels, non-finite numbers, and mixed-type arrays are rejected."},
            {"RandomStroll", "Chooses a nearby safe position and walks there."},
            {"Panic", "Moves away from the last attacker or chooses a random escape direction."},
            {"DistanceTo", "Returns the distance to another entity instance."},
            {"MeleeAttack", "Attacks a living target in reach, with an optional whole-number cooldown in ticks clamped to 1 through 200."}
        };
        for (String[] method : aiMethods) {
            MLuaClassMethods.registerInstanceMethod(registration, "ScriptedEntity", method[0], method[1]);
        }
        MLuaClassMethods.registerInstanceProperty(registration, "ScriptedEntity", "BehaviorKey",
            "GetBehaviorKey", null, "string", "shared:Shared",
            "The registered content key that selects this scripted mob's behavior definition.");
        MLuaBlockWrapper.classTable(registration);
        MLuaRegistryEntry.registerClass(registration);
        MLuaContainerType.registerClass(registration);
    }

    @Override
    public void contributeGlobals(Globals globals, MLuaContextView context) {
        var registration = context.registration();
        globals.set("Block", registration.classTable("Block"));
        LuaTable itemGlobal = MLuaItemWrapper.itemGlobal(registration);
        globals.set("Item", itemGlobal);
        LuaTable items = MLuaRegistryCatalogs.items();
        globals.set("Items", items);
        globals.set("Blocks", MLuaRegistryCatalogs.blocks());
        globals.set("Texture", MLuaTextureBindings.textureGlobal());
        globals.set("ScriptedEntity", registration.classTable("ScriptedEntity"));
        globals.set("RegistryEntry", registration.classTable("RegistryEntry"));
        globals.set("ContainerType", registration.classTable("ContainerType"));
        globals.set("RegistryCategory", registration.classTable("RegistryCategory"));
        MLuaItemWrapper.attachCatalogs(itemGlobal, items);
    }

    @Override
    public void contributeDocs(MLuaDocBuilder docs) {
        docs.register(MLuaDocBuilder.Registration.clazz("RegistryCategory", "MLuaRegistryService",
            "A live category API table exposed by the Registry service.", "classes/registry-category.html", "Instance"));
        docs.register(MLuaDocBuilder.Registration.property("RegistryCategory", "RegistryCategory.Name", "string",
            "This category's canonical Registry table name.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("RegistryCategory", "RegistryCategory.Description", "string",
            "The category's registered description.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.method("RegistryCategory", "RegistryCategory:GetName()",
            "Returns this category's canonical Registry table name.", null, "string", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("RegistryCategory", "RegistryCategory:GetDescription()",
            "Returns the category's registered description.", null, "string", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("RegistryCategory", "RegistryCategory:GetMethods()",
            "Lists the category-specific methods registered on this API table.", null, "string[]", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("RegistryCategory", "RegistryCategory:GetMethodDescription(name: string)",
            "Returns the registered description for one category method, or nil when that method is unavailable.",
            "Method name", "string|nil", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.signature("Registry.DataPack", "Registry.DataPack:Register(resourceType: string, id: string, definition: table)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Registry.Assets", "Registry.Assets:Register(path: string, definition: table)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Registry.Assets", "Registry.Assets:register(path: string, definition: table)", "Alias of Register."));
        docs.register(MLuaDocBuilder.Registration.signature("Registry.WorldGeneration", "Registry.WorldGeneration:Register(resourceType: string, id: string, definition: table)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Registry.WorldGeneration", "Registry.WorldGeneration:AddFeature(name: string, definition: WorldGenerationFeatureDefinition)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Registry.WorldGeneration", "Registry.WorldGeneration:register(resourceType: string, id: string, definition: table)", "Alias of Register."));
        docs.register(MLuaDocBuilder.Registration.signature("Registry.WorldGeneration", "Registry.WorldGeneration:RegisterBiome(id: string, definition: table)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Registry.WorldGeneration", "Registry.WorldGeneration:RegisterDimensionType(id: string, definition: table)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Registry.WorldGeneration", "Registry.WorldGeneration:RegisterDimension(id: string, definition: table)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Registry.WorldGeneration", "Registry.WorldGeneration:RegisterNoiseSettings(id: string, definition: table)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Registry.WorldGeneration", "Registry.WorldGeneration:RegisterDensityFunction(id: string, definition: table)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Registry.WorldGeneration", "Registry.WorldGeneration:RegisterConfiguredFeature(id: string, definition: table)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Registry.WorldGeneration", "Registry.WorldGeneration:RegisterPlacedFeature(id: string, definition: table)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Registry.WorldGeneration", "Registry.WorldGeneration:RegisterConfiguredCarver(id: string, definition: table)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Registry.WorldGeneration", "Registry.WorldGeneration:RegisterStructure(id: string, definition: table)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Registry.WorldGeneration", "Registry.WorldGeneration:RegisterStructureSet(id: string, definition: table)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Registry.WorldGeneration", "Registry.WorldGeneration:RegisterTemplatePool(id: string, definition: table)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Registry.WorldGeneration", "Registry.WorldGeneration:addFeature(name: string, definition: WorldGenerationFeatureDefinition)", "Alias of AddFeature."));

        contributeDefinitionSchemas(docs);
        contributeSignatures(docs);
        docs.register(MLuaDocBuilder.Registration.signature("Block", "Block:GetInventory()", "Container"));
        docs.register(MLuaDocBuilder.Registration.signature("Block", "Block:GetClassName()", "string"));
        docs.register(MLuaDocBuilder.Registration.signature("BlockEntity", "BlockEntity:GetLevel()", "Level"));
        docs.register(MLuaDocBuilder.Registration.signature("BlockEntity", "BlockEntity:GetDimension()", "string"));
        docs.register(MLuaDocBuilder.Registration.signature("BlockEntity", "BlockEntity:GetIsRemoved()", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("Block", "Block:GetStateProperties()", "BlockStateProperties"));
        docs.register(MLuaDocBuilder.Registration.signature("Block", "Block:GetState()", "BlockState"));
        docs.register(MLuaDocBuilder.Registration.signature("Block", "Block:SetStateProperties(properties: BlockStateProperties)", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("Block", "Block:GetFluid()", "string?"));
        docs.register(MLuaDocBuilder.Registration.signature("Block", "Block:GetFluidState()", "FluidState"));
        docs.register(MLuaDocBuilder.Registration.signature("Block", "Block:GetLightLevel()", "number"));
        docs.register(MLuaDocBuilder.Registration.signature("Block", "Block:GetSkyLight()", "number"));
        docs.register(MLuaDocBuilder.Registration.signature("Block", "Block:GetBlockLight()", "number"));
        docs.register(MLuaDocBuilder.Registration.signature("Block", "Block:GetCanSeeSky()", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("Block", "Block:GetRedstonePower()", "number"));
        docs.register(MLuaDocBuilder.Registration.signature("Block", "Block:GetIsPowered()", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("Block", "Block:GetDestroySpeed()", "number"));
        docs.register(MLuaDocBuilder.Registration.signature("Block", "Block:GetCanSurvive()", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("Block", "Block:GetCanBeReplaced()", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("Block", "Block:GetHasBlockEntity()", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("Block", "Block:GetIsRandomlyTicking()", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("Block", "Block:Set(blockId: string, properties: BlockStateProperties?)", "boolean"));
        docs.register(MLuaDocBuilder.Registration.service("Registry", "MLuaRegistryService",
                "Registers addon content, generated data pack resources, Minecraft registry entries, and reusable inherited content types.",
                "services/registry.html"));
        docs.register(MLuaDocBuilder.Registration.clazz("RegistryProperties", "Registry", "Shared chainable settings for registered Minecraft content.", "classes/registry-properties.html", "Instance"));
        docs.register(MLuaDocBuilder.Registration.clazz("ItemProperties", "Registry", "Item settings matching Minecraft Item.Properties, plus MLua's scripted item callbacks.", "classes/item-properties.html", "RegistryProperties"));
        docs.register(MLuaDocBuilder.Registration.clazz("BlockProperties", "Registry", "Block settings matching Minecraft BlockBehaviour.Properties, plus MLua's scripted block callbacks.", "classes/block-properties.html", "RegistryProperties"));
        docs.register(MLuaDocBuilder.Registration.clazz("EntityProperties", "Registry", "Entity dimensions, attributes, tracking settings, and scripted behavior callbacks.", "classes/entity-properties.html", "RegistryProperties"));
        docs.register(MLuaDocBuilder.Registration.guideStep("Registry.Containers", "Register a reusable container type",
                "Register the layout once and keep its ContainerType instance. Its properties expose the namespaced id, row and slot counts, title, and allowed item ids. Reference the type from a scripted block with its id; an empty or omitted allowedItems list accepts every item. Lua-style lower-camel fields are canonical; Rows, Title, and AllowedItems are accepted aliases.",
                "local Registry = Minecraft:GetService(\"Registry\")\n\nlocal crate = Registry.Containers:Register(\"supply_crate\", {\n    rows = 3,\n    title = \"Supply Crate\",\n    allowedItems = { \"minecraft:torch\", \"minecraft:lantern\" },\n})\n\nprint(crate.Id, crate.SlotCount, crate.AllowedItems[1])\nprint(crate:AllowsItem(\"minecraft:torch\"))\nlocal registered = Registry.Containers:Get(crate.Id)"));
        docs.register(MLuaDocBuilder.Registration.guideStep("Registry.Containers", "Attach the registered type to a block",
                "The block definition stores the container type id. On interaction, MLua opens the vanilla container screen; the type sets its row count, title, and item rule. Add an onRightClick callback only when you need custom server logic before it opens.",
                "Registry.Blocks:Register(\"supply_crate_block\", {\n    strength = 2,\n    container = crate.Id,\n})"));
        docs.register(MLuaDocBuilder.Registration.guideStep("Registry.Blocks", "Register a block and its item",
                "Register the block first, then register its item form. Each call returns a RegistryEntry key you can keep for later lookup or to check registration state. The block definition accepts Minecraft block properties and scripted callbacks; the item definition accepts stack, durability, food, rarity, and item callbacks.",
                "local Registry = Minecraft:GetService(\"Registry\")\n\nlocal block = Registry.Blocks:Register(\"signal_block\", {\n    strength = 3,\n    resistance = 6,\n    sound = \"stone\",\n    mapColor = \"stone\",\n    requiresCorrectTool = true,\n    onRightClick = function(context)\n        context.Player:SendMessage(\"The block answers with a quiet hum.\")\n        return true\n    end,\n})\n\nlocal item = Registry.Blocks:RegisterItem(\"signal_block\", {\n    maxStackSize = 16,\n    rarity = \"uncommon\",\n    description = \"A block that listens for a signal.\",\n})\n\nprint(block.Id, item.Id)\nprint(table.concat(Registry.Blocks:GetSounds(), \", \"))\nprint(table.concat(Registry.Items:GetRarities(), \", \"))"));
        docs.register(MLuaDocBuilder.Registration.guideStep("Registry.Items", "Use Minecraft-style property chains",
                "ItemProperties and BlockProperties are real Lua classes with inherited common settings. Their chainable methods map to native Minecraft properties, and the same instances can be passed directly to Register. Build returns a plain table when you need to add or inspect fields yourself.",
                "local Registry = Minecraft:GetService(\"Registry\")\n\nlocal sword = Registry.Items:Properties()\n    :Kind(\"sword\")\n    :Tier(\"iron\")\n    :Durability(420)\n    :AttackDamage(7)\n    :AttackSpeed(1.4)\n    :Rarity(\"uncommon\")\n    :OnUse(function(context)\n        context.Player:SendMessage(\"The blade answers.\")\n    end)\n\nRegistry.Items:Register(\"signal_sword\", sword)\n\nlocal block = Registry.Blocks:Properties()\n    :Strength(3, 6)\n    :Sound(\"stone\")\n    :RequiresCorrectToolForDrops(true)\n    :OnRightClick(function(context)\n        context.Player:SendMessage(\"The block answers.\")\n        return true\n    end)\n\nRegistry.Blocks:Register(\"signal_block\", block)"));
        docs.register(MLuaDocBuilder.Registration.guideStep("Registry", "Use the shared registration entry point",
                "Registry:Register routes definitions through the typed registries for items, blocks, entities, effects, potions, particles, containers, projectiles, world generation, tabs, recipes, loot, tags, common config, and damage sources. Use block_item after registering its block; register a tag as Registry:Register(\"tag\", kind, name, definition). RegisterType defines reusable addon-scoped defaults for each category. Items select them with kind, blocks with type, and other categories with RegistryType. Use Registry.ContentTypes for reusable defaults in your own data domains. GetRegistered returns IDs visible for a Minecraft registry kind.",
                "local Registry = Minecraft:GetService(\"Registry\")\n\nRegistry:Register(\"item\", \"flare_gun\", {\n    kind = \"firearm\",\n    maxStackSize = 1,\n    onUse = function(context)\n        -- Use the live player and projectile services here.\n    end,\n})\n\nfor _, id in ipairs(Registry:GetRegistered(\"items\")) do\n    print(id)\nend"));
        docs.register(MLuaDocBuilder.Registration.guideStep("Registry", "Add an addon-owned registry category",
                "RegisterCategory adds a category under your addon's namespace (for example, 'horror:weapons'). Its handler receives each namespaced id and resolved definition during the registry phase. GetCategory resolves a name from GetCategories to the live category API, including addon categories. RegisterType and ResolveType use the same inherited defaults as built-in categories. Get and GetAll return copies of registered definitions; GetIds returns sorted keys. These read methods accept an optional namespace filter.",
                "local Registry = Minecraft:GetService(\"Registry\")\n\nRegistry:RegisterCategory(\"horror:weapons\", \"Weapon tuning data owned by this addon.\", function(id, definition)\n    if definition.Damage <= 0 then\n        error(\"Weapon damage must be positive\")\n    end\nend)\n\nRegistry:RegisterType(\"horror:weapons\", \"sidearm\", {\n    Base = \"default\",\n    Defaults = { Damage = 8, MagazineSize = 6 },\n})\n\nRegistry:Register(\"horror:weapons\", \"revolver\", {\n    RegistryType = \"sidearm\",\n    Damage = 12,\n})\n\nlocal revolver = Registry:Get(\"horror:weapons\", \"horror:revolver\")\nlocal weapons = Registry:GetCategory(\"horror:weapons\")\nfor _, id in ipairs(weapons:GetIds(\"horror\")) do\n    print(id)\nend\nfor id, weapon in pairs(Registry:GetAll(\"horror:weapons\")) do\n    print(id, weapon.Damage)\nend"));
        docs.register(MLuaDocBuilder.Registration.guideStep("Registry.ContentTypes", "Define types for addon data",
                "ContentTypes lets an addon reuse inherited defaults and descriptions outside Minecraft's built-in categories. Give each domain a stable name, register its types during the registry phase, then resolve definitions with RegistryType. A definition without a selector passes through unchanged; a named type that is missing from that domain raises an error.",
                "local Registry = Minecraft:GetService(\"Registry\")\n\nRegistry.ContentTypes:Register(\"horror.weapon\", \"sidearm\", {\n    Base = \"default\",\n    Description = \"Shared defaults for compact horror weapons.\",\n    Defaults = { Damage = 6, Magazine = { Size = 8, ReloadTicks = 30 } },\n})\n\nRegistry.ContentTypes:Register(\"horror.weapon\", \"revolver\", {\n    Base = \"sidearm\",\n    Description = \"A slow, powerful revolver.\",\n    Defaults = { Damage = 12, Magazine = { Size = 6 } },\n})\n\nlocal weapon = Registry.ContentTypes:Resolve(\"horror.weapon\", {\n    RegistryType = \"revolver\",\n    Damage = 14,\n})\nprint(weapon.Damage, weapon.Magazine.Size, weapon.Magazine.ReloadTicks)"));
        docs.register(MLuaDocBuilder.Registration.guideStep("Registry", "Attach Lua classes to items and blocks",
                "Register a class under Item or Block, then set className in the content definition. Callback contexts receive live instances of that class, including inherited methods and applied mixins.",
                "Minecraft:RegisterClass(\"HuntingBow\", \"Item\", \"A reusable hunting bow.\")\nMinecraft:RegisterClassFunction(\"HuntingBow\", \"IsReady\", function(self)\n    return self:GetDamage() < self:GetMaxDamage()\nend, \"Checks whether the bow can still be used.\")\n\nMinecraft:RegisterClass(\"HauntedSoil\", \"Block\", \"Soil that reacts to nearby players.\")\nMinecraft:RegisterClassFunction(\"HauntedSoil\", \"Collapse\", function(self)\n    self:Set(\"minecraft:air\")\nend, \"Removes this soil block.\")\n\nRegistry.Items:Register(\"hunting_bow\", { className = \"HuntingBow\" })\nRegistry.Blocks:Register(\"haunted_soil\", { className = \"HauntedSoil\" })"));
        docs.register(MLuaDocBuilder.Registration.guideStep("Registry.DataPack", "Register data-driven world generation",
                "WorldGeneration writes vanilla dimension, dimension_type, and worldgen resources into the addon's namespace. Use the named registration methods for biomes, dimensions, terrain settings, carvers, features, and structures. Use Register for less common resource paths. Definitions use Minecraft's version-specific data format and can inherit reusable defaults through RegistryType.",
                "local Registry = Minecraft:GetService(\"Registry\")\n\nRegistry.DataPack:RegisterType(\"tree_feature\", {\n    Base = \"default\",\n    Description = \"Shared placement settings for this addon's trees.\",\n    Defaults = { type = \"minecraft:tree\" },\n})\n\nRegistry.DataPack:Register(\"worldgen/configured_feature\", \"dead_oak\", {\n    RegistryType = \"tree_feature\",\n    config = {\n        dirt_provider = { type = \"minecraft:simple_state_provider\", state = { Name = \"minecraft:dirt\" } },\n        trunk_provider = { type = \"minecraft:simple_state_provider\", state = { Name = \"minecraft:oak_log\" } },\n        foliage_provider = { type = \"minecraft:simple_state_provider\", state = { Name = \"minecraft:oak_leaves\", Properties = { distance = \"7\", persistent = \"false\" } } },\n        trunk_placer = { type = \"minecraft:straight_trunk_placer\", base_height = 4, height_rand_a = 2, height_rand_b = 0 },\n        foliage_placer = { type = \"minecraft:blob_foliage_placer\", radius = 2, offset = 0, height = 3 },\n        minimum_size = { type = \"minecraft:two_layers_feature_size\", limit = 1, lower_size = 0, upper_size = 1 },\n        decorators = {},\n    },\n})\n\nRegistry.DataPack:Register(\"worldgen/placed_feature\", \"dead_oak\", {\n    feature = \"myaddon:dead_oak\",\n    placement = {\n        { type = \"minecraft:count\", count = 1 },\n        { type = \"minecraft:in_square\" },\n        { type = \"minecraft:heightmap\", heightmap = \"WORLD_SURFACE_WG\" },\n        { type = \"minecraft:biome\" },\n    },\n})"));
        docs.register(MLuaDocBuilder.Registration.guideStep("Registry.WorldGeneration", "Place an ore, tree, or plant in selected biomes",
                "Use a biome id or tag selector with the same API on Fabric, Forge, and NeoForge. The placed feature must already be registered as a data pack resource. Set step to a string or an Enum.GenerationStep.Decoration value; both forms are validated against Minecraft's decoration stages.",
                "Registry.WorldGeneration:AddFeature(\"dead_oak_forest\", {\n    feature = \"myaddon:dead_oak\",\n    biomes = { \"#minecraft:is_forest\" },\n    step = Enum.GenerationStep.Decoration.VegetalDecoration,\n})"));
        docs.register(MLuaDocBuilder.Registration.guideStep("Registry.WorldGeneration", "Define a biome, dimension, or terrain resource",
                "register writes a canonical Minecraft data resource to the generated server pack. resourceType is an open path: use dimension, dimension_type, or any worldgen/* registry path such as worldgen/biome, worldgen/noise_settings, and worldgen/density_function. IDs are owned by the addon namespace. These definitions are version-specific Minecraft JSON and are loaded when the generated data pack is enabled.",
                "local WorldGeneration = Minecraft:GetService(\"Registry\").WorldGeneration\n\nWorldGeneration:Register(\"worldgen/biome\", \"ashen_marsh\", {\n    has_precipitation = true,\n    temperature = 0.7,\n    downfall = 0.9,\n    effects = {\n        fog_color = 12638463,\n        water_color = 4159204,\n        water_fog_color = 329011,\n        sky_color = 7907327,\n    },\n    spawners = {},\n    spawn_costs = {},\n    carvers = {},\n    features = {},\n})\n\nWorldGeneration:Register(\"dimension_type\", \"ashen_depths\", {\n    ultrawarm = false,\n    natural = false,\n    coordinate_scale = 1.0,\n    has_skylight = false,\n    has_ceiling = true,\n    ambient_light = 0.1,\n    logical_height = 256,\n    infiniburn = \"minecraft:infiniburn_overworld\",\n    effects = \"minecraft:the_nether\",\n    min_y = -64,\n    height = 384,\n    monster_spawn_block_light_limit = 0,\n    monster_spawn_light_level = { type = \"minecraft:uniform\", value = { min_inclusive = 0, max_inclusive = 7 } },\n})\n\nWorldGeneration:Register(\"dimension\", \"ashen_depths\", {\n    type = \"myaddon:ashen_depths\",\n    generator = {\n        type = \"minecraft:noise\",\n        settings = \"myaddon:ashen_depths\",\n        biome_source = {\n            type = \"minecraft:fixed\",\n            biome = \"myaddon:ashen_marsh\",\n        },\n    },\n})\n\nWorldGeneration:Register(\"worldgen/noise_settings\", \"ashen_depths\", {\n    sea_level = 32,\n    disable_mob_generation = false,\n    aquifers_enabled = true,\n    ore_veins_enabled = false,\n    legacy_random_source = false,\n    default_block = { Name = \"minecraft:deepslate\" },\n    default_fluid = { Name = \"minecraft:lava\" },\n    noise = { min_y = -64, height = 384, size_horizontal = 1, size_vertical = 2 },\n    noise_router = {\n        barrier = \"minecraft:overworld/barrier\",\n        fluid_level_floodedness = \"minecraft:overworld/fluid_level_floodedness\",\n        fluid_level_spread = \"minecraft:overworld/fluid_level_spread\",\n        lava = \"minecraft:overworld/lava\",\n        temperature = \"minecraft:overworld/temperature\",\n        vegetation = \"minecraft:overworld/vegetation\",\n        continents = \"minecraft:overworld/continents\",\n        erosion = \"minecraft:overworld/erosion\",\n        depth = \"minecraft:overworld/depth\",\n        ridges = \"minecraft:overworld/ridges\",\n        initial_density_without_jaggedness = \"minecraft:overworld/initial_density_without_jaggedness\",\n        final_density = \"minecraft:overworld/final_density\",\n        vein_toggle = \"minecraft:overworld/vein_toggle\",\n        vein_ridged = \"minecraft:overworld/vein_ridged\",\n        vein_gap = \"minecraft:overworld/vein_gap\",\n    },\n    spawn_target = {},\n    surface_rule = { type = \"minecraft:block\", result_state = { Name = \"minecraft:deepslate\" } },\n})"));
        docs.register(MLuaDocBuilder.Registration.guideStep("Registry.WorldGeneration", "Use named terrain resource methods",
                "Named methods fix the Minecraft data path and still use the same inherited worldgen defaults as Register. For anything outside these common resource kinds, keep using Register with the resource path.",
                "local WorldGeneration = Minecraft:GetService(\"Registry\").WorldGeneration\n\nWorldGeneration:RegisterBiome(\"ashen_marsh\", biomeDefinition)\nWorldGeneration:RegisterDimensionType(\"ashen_depths\", dimensionTypeDefinition)\nWorldGeneration:RegisterDimension(\"ashen_depths\", dimensionDefinition)\nWorldGeneration:RegisterNoiseSettings(\"ashen_depths\", noiseSettings)\nWorldGeneration:RegisterDensityFunction(\"ashen_depths/caves\", densityFunction)\nWorldGeneration:RegisterStructure(\"buried_station\", structureDefinition)\nWorldGeneration:RegisterStructureSet(\"buried_station\", structurePlacement)"));
        docs.register(MLuaDocBuilder.Registration.guideStep("Registry.Recipes", "Register a vanilla recipe type directly",
                "Registry.Recipes:Register accepts a Lua table or a JSON string. Use it for vanilla recipe types that do not have a dedicated Recipe builder; keep the fields aligned with the Minecraft version you target.",
                "local Recipes = Minecraft:GetService(\"Registry\").Recipes\n\nRecipes:Register(\"armor_trim\", {\n    type = \"minecraft:smithing_trim\",\n    template = { item = \"minecraft:coast_armor_trim_smithing_template\" },\n    base = { tag = \"minecraft:trimmable_armor\" },\n    addition = { tag = \"minecraft:trim_materials\" },\n})"));
        docs.register(MLuaDocBuilder.Registration.guideStep("Registry", "Work with registry entries generically",
                "Built-in category tables and the root Registry lookup share the same addon-local ID resolver. Get returns one live RegistryEntry; GetAll returns an ID-keyed map and GetIds returns a sorted array. GetRegistered and GetEntries remain available when you want explicit names. RegistryEntry.new takes a full namespaced identifier and returns the same class.",
                "local Registry = Minecraft:GetService(\"Registry\")\nlocal bow = Registry.Items:Get(\"bow\")\nlocal rootBow = Registry:Get(\"items\", \"bow\")\nlocal stone = Registry.Blocks:Get(\"minecraft:stone\")\nlocal fullyQualified = RegistryEntry.new(\"items\", bow.Id)\n\nprint(Registry.Items:GetId(\"bow\"))\nprint(bow.Kind, bow.Id, Registry:Exists(\"items\", \"bow\"))\nprint(rootBow:Equals(fullyQualified))\nif stone.IsRegistered then\n    print(stone.Namespace, stone.Path)\nend\n\nfor _, id in ipairs(Registry:GetIds(\"items\", \"myaddon\")) do\n    print(id)\nend\nfor id, entry in pairs(Registry:GetAll(\"items\", \"myaddon\")) do\n    print(id, entry.IsRegistered)\nend\n\nfor _, kind in ipairs(Registry:GetKinds()) do\n    print(kind)\nend"));
        docs.register(MLuaDocBuilder.Registration.guideStep("Registry.Containers", "Attach storage to a scripted block",
                "The block entity keeps its slots on the server and opens a vanilla chest screen when the player interacts. AllowedItems is an optional allowlist applied to inventory slot insertion.",
                "Registry.Blocks:Register(\"supply_crate\", {\n    Container = \"supply_crate\",\n    onRightClick = function(context)\n        local container = context.Block:GetContainer()\n        if container and container:IsEmpty() then\n            container:SetSlot(1, { Id = \"minecraft:torch\", Count = 1 })\n        end\n    end,\n})"));
        docs.register(MLuaDocBuilder.Registration.guideStep("Registry.Blocks", "Build a timed scripted block",
                "onStepOn runs on the server. ScheduleTick waits the requested number of game ticks, then calls onTick with the live Block instance. Schedule another tick from onTick to build a repeating behavior.",
                "Registry.Blocks:Register(\"crumbling_stone\", {\n    copy = \"minecraft:stone\",\n    copyModel = true,\n    onStepOn = function(context)\n        context.Block:ScheduleTick(40) -- two seconds\n    end,\n    onTick = function(context)\n        context.Block:Set(\"minecraft:air\")\n    end,\n})"));
        docs.register(MLuaDocBuilder.Registration.guideStep("Registry.Items", "Discover available item types",
                "GetTypes returns the registered item kind names and short descriptions, including types added by Java extensions. The shovel spelling is canonical; spade remains an accepted alias.",
                "local Registry = Minecraft:GetService(\"Registry\")\nfor name, description in pairs(Registry.Items:GetTypes()) do\n    print(name, description)\nend\n\nRegistry.Blocks:GetTypes() -- lists default and Java-registered block types"));
        docs.register(MLuaDocBuilder.Registration.guideStep("Registry.Items", "Define a reusable Lua content type",
                "RegisterType creates a reusable kind owned by your addon. It can extend a built-in or Java-registered type, and it can extend another addon's type by using its full namespace:name. Defaults merge recursively, so a definition can override one nested setting while inheriting the rest. The item uses Minecraft's native bow behavior and animation, while the default onUse callback can connect it to a projectile definition.",
                "local Registry = Minecraft:GetService(\"Registry\")\n\nRegistry.Items:RegisterType(\"hunting_bow\", {\n    Base = \"bow\",\n    Description = \"A reusable hunting bow with a custom shot.\",\n    Defaults = {\n        maxStackSize = 1,\n        durability = 420,\n        copy = \"minecraft:bow\",\n        copyModel = true,\n        description = { \"A quiet weapon for the dark.\" },\n    },\n})\n\nRegistry.Items:Register(\"watcher_bow\", {\n    kind = \"hunting_bow\",\n    onUse = function(context)\n        -- Fire an addon projectile or apply custom effects here.\n    end,\n})"));
        docs.register(MLuaDocBuilder.Registration.guideStep("Registry", "Resolve inherited type defaults",
                "ResolveType applies the same inheritance and merge rules used during registration. Use the category method when you already know the category, or the Registry method when the category comes from data.",
                "local Registry = Minecraft:GetService(\"Registry\")\n\nlocal definition = { kind = \"hunting_bow\", durability = 500 }\nlocal resolved = Registry.Items:ResolveType(definition)\n-- Equivalent when the category is dynamic:\nlocal resolvedAgain = Registry:ResolveType(\"items\", definition)\nprint(resolved.maxStackSize, resolved.durability)"));
        docs.register(MLuaDocBuilder.Registration.guideStep("Registry.Entities", "Reuse an entity archetype",
                "Entity type defaults use the same generic registry type system. The live entity still gets its own independent AI state and scripted callbacks.",
                "Registry:RegisterType(\"entities\", \"stalker_base\", {\n    Base = \"entity\",\n    Description = \"Shared physical defaults for stalker mobs.\",\n    Defaults = {\n        category = \"monster\",\n        width = 0.7,\n        height = 2.1,\n        attributes = { maxHealth = 36, movementSpeed = 0.28, attackDamage = 5 },\n    },\n})\n\nRegistry.Entities:Register(\"stalker\", {\n    RegistryType = \"stalker_base\",\n    onSpawn = function(self)\n        self:SetData({ alerted = false })\n    end,\n})"));
        docs.register(MLuaDocBuilder.Registration.guideStep("Registry.Items", "Register food and a native animated bow",
                "Food uses Minecraft's nutrition and saturation values, and onConsume runs once on the server. Set the item kind to bow to use Minecraft's native draw animation, charge timing, ammunition checks, and release behavior. Copying the vanilla bow model preserves its pull stages.",
                "Registry.Items:Register(\"haunted_ration\", {\n    food = { nutrition = 6, saturation = 0.8, alwaysEdible = true },\n    onConsume = function(context)\n        if context.Player then\n            context.Player:SendMessage(\"The ration tastes strangely warm.\")\n        end\n    end,\n})\n\nRegistry.Items:Register(\"ember_bow\", {\n    kind = \"bow\",\n    copy = \"minecraft:bow\", copyModel = true,\n    durability = 384, maxStackSize = 1,\n    description = \"A native animated bow with Minecraft arrow handling.\",\n})"));
        docs.register(MLuaDocBuilder.Registration.guideStep("Registry.Entities", "Choose the mob's type settings",
                "Start with a Lua definition for the shared entity type. Each spawned mob is a separate live ScriptedEntity instance.",
                "local stalkerDefinition = {\n    category = \"monster\",\n    attributes = { maxHealth = 30, movementSpeed = 0.28, attackDamage = 4 }\n}"));
        docs.register(MLuaDocBuilder.Registration.guideStep("Registry.Entities", "Build a mob type with chained settings",
                "EntityProperties uses the same chainable style as the item and block property classes. Attribute methods write into the entity's native attribute set; AI and lifecycle methods attach the scripted behavior used by each spawned mob.",
                "local Registry = Minecraft:GetService(\"Registry\")\n\nlocal stalker = Registry.Entities:Properties()\n    :Category(\"monster\")\n    :Size(0.7, 2.1)\n    :TrackingRange(12)\n    :MaxHealth(36)\n    :MovementSpeed(0.28)\n    :AttackDamage(5)\n    :AI({\n        initial = \"Search\",\n        states = {\n            Search = {\n                update = function(self)\n                    local player = self:FindNearestPlayer(16, true)\n                    if player then\n                        self:AttackTarget(player)\n                        return \"Chase\"\n                    end\n                end\n            },\n            Chase = {\n                update = function(self)\n                    local target = self:GetTarget()\n                    if not target then return \"Search\" end\n                    self:LookAt(target.Position)\n                    self:MoveTo(target.Position, 1.0)\n                end\n            }\n        }\n    })\n\nRegistry.Entities:Register(\"stalker\", stalker)"));
        docs.register(MLuaDocBuilder.Registration.guideStep("Registry.Entities", "Give each mob an inherited Lua class",
                "Define a class that extends ScriptedEntity, then set className on the entity definition. Every spawned mob of that type receives the registered subclass, so its methods and mixins are available to AI and lifecycle callbacks.",
                "Minecraft:RegisterClass(\"Stalker\", \"ScriptedEntity\", \"A scripted stalker mob.\")\nMinecraft:RegisterClassFunction(\"Stalker\", \"Investigate\", function(self, position)\n    return self:MoveTo(position, 1.1)\nend, \"Moves the stalker to investigate a position.\")\n\nlocal stalkerDefinition = {\n    className = \"Stalker\",\n    AI = {\n        initial = \"Search\",\n        states = {\n            Search = {\n                update = function(self)\n                    local target = self:FindNearestPlayer(16, true)\n                    if target then\n                        self:Investigate(target.Position)\n                        return \"Chase\"\n                    end\n                end\n            }\n        }\n    }\n}\n\nRegistry.Entities:Register(\"stalker\", stalkerDefinition)"));
        docs.register(MLuaDocBuilder.Registration.guideStep("Registry.Entities", "Give the live entity behavior",
                "Add AI to the definition before registering it. Callbacks receive the actual mob as self, with ScriptedEntity, LivingEntity, and Entity methods. Keep decisions in state updates; navigation continues between AI updates.",
                "stalkerDefinition.AI = {\n    initial = \"Search\",\n    states = {\n        Search = {\n            update = function(self, ai)\n                local player = self:FindNearestPlayer(16, true)\n                if player then\n                    self:AttackTarget(player)\n                    return \"Chase\"\n                end\n            end\n        },\n        Chase = {\n            update = function(self, ai)\n                local target = self:GetTarget()\n                if not target or not target:GetIsAlive() then\n                    self:AttackTarget(nil)\n                    return \"Search\"\n                end\n                self:LookAt(target.Position)\n                self:MoveTo(target.Position, 1.0)\n                if self:DistanceTo(target) <= 2.5 then\n                    self:MeleeAttack(target, 24)\n                end\n            end\n        }\n    }\n}\nRegistry.Entities:Register(\"stalker\", stalkerDefinition)"));
        docs.register(MLuaDocBuilder.Registration.guideStep("Registry.Entities", "Spawn an instance",
                "Spawn the registered type on the server through EntityService. The returned entity is managed by Minecraft and uses the callbacks registered for its type. BehaviorKey identifies the scripted definition; the inherited Type property keeps its Minecraft entity type meaning.",
                "local EntityService = Minecraft:GetService(\"EntityService\")\nlocal stalker = EntityService:Spawn(\"myaddon:stalker\", Vector3.new(12, 64, -8))\nif stalker then\n    print(stalker.BehaviorKey, stalker.Type)\n    stalker:SetData({ alerted = true })\nend"));
        docs.register(MLuaDocBuilder.Registration.guideStep("ScriptedEntity", "Handle damage and interaction",
                "Entity callbacks receive the live mob as self. onHurt runs after Minecraft accepts the damage, and onDeath receives the damage source id. Return false from onInteract to let Minecraft try its normal interaction; true or no return value consumes the interaction.",
                "stalkerDefinition.onHurt = function(self, amount, source)\n    print(self.Name, \"took\", amount, \"damage from\", source)\nend\n\nstalkerDefinition.onDeath = function(self, source)\n    print(self.Name, \"died to\", source)\nend\n\nstalkerDefinition.onInteract = function(self, player, hand)\n    self:SetAIState(\"Chase\")\n    return true\nend"));
        docs.register(MLuaDocBuilder.Registration.clazz("Block", "MLuaBlockWrapper",
                "A live block instance at a position in a Minecraft world.",
                "classes/block.html", "Instance"));
        docs.register(MLuaDocBuilder.Registration.method("Block", "Block:GetClassName(): string", "Returns the registered Lua class name of this block instance, including an addon subclass.", "", "string", "Shared", ""));
        docs.register(MLuaDocBuilder.Registration.method("Block", "Block:GetRelative(offset: Vector3): Block", "Returns the live block at an integer block offset and resolves its registered Lua subclass.", "", "Block", "Shared", ""));
        docs.register(MLuaDocBuilder.Registration.clazz("BlockEntity", "MLuaBlockWrapper",
                "A live block entity attached to a block position, with persistent state and optional inventory.",
                "classes/block-entity.html", "Instance"));
        docs.register(MLuaDocBuilder.Registration.method("BlockEntity", "BlockEntity:GetPosition(): Vector3", "Returns this block entity's world position.", "", "Vector3", "Shared", ""));
        docs.register(MLuaDocBuilder.Registration.method("BlockEntity", "BlockEntity:OnLoad(context)", "Override this lifecycle method on a registered BlockEntity subclass to run server-side whenever Minecraft loads the block entity. The context includes live Block, BlockEntity, and World instances plus a Vector3 Position.", "Lifecycle context", "void", "server:Server", ""));
        docs.register(MLuaDocBuilder.Registration.method("BlockEntity", "BlockEntity:OnUnload(context)", "Override this lifecycle method on a registered BlockEntity subclass to run server-side when Minecraft removes the block entity, including chunk unloads.", "Lifecycle context", "void", "server:Server", ""));
        docs.register(MLuaDocBuilder.Registration.method("BlockEntity", "BlockEntity:GetBlock(): Block", "Returns the live block that owns this block entity.", "", "Block", "Shared", ""));
        docs.register(MLuaDocBuilder.Registration.method("BlockEntity", "BlockEntity:GetType(): string", "Returns this block entity type's registry identifier.", "", "string", "Shared", ""));
        docs.register(MLuaDocBuilder.Registration.method("BlockEntity", "BlockEntity:GetNbt(): Nbt", "Returns this block entity's live NBT data.", "", "Nbt", "Shared", ""));
        docs.register(MLuaDocBuilder.Registration.method("BlockEntity", "BlockEntity:SetNbt(keyOrValue: string|table|Nbt, value?: any)", "On the server, writes one NBT value or replaces the full data compound.", "key and value, or a replacement table or Nbt value", "void", "server:Server", ""));
        docs.register(MLuaDocBuilder.Registration.method("BlockEntity", "BlockEntity:GetContainer(): Container|nil", "Returns the same live Container exposed by Block.Container, when available.", "", "Container|nil", "Shared", ""));
        docs.register(MLuaDocBuilder.Registration.method("BlockEntity", "BlockEntity:GetInventory(): Container|nil", "Alias for GetContainer.", "", "Container|nil", "Shared", ""));
        docs.register(MLuaDocBuilder.Registration.method("BlockEntity", "BlockEntity:HasContainer(): boolean", "Whether this block entity owns a live Container.", "", "boolean", "Shared", ""));
        docs.register(MLuaDocBuilder.Registration.method("BlockEntity", "BlockEntity:SetChanged()", "On the server, marks this block entity's saved state as changed.", "", "void", "server:Server", ""));
        docs.register(MLuaDocBuilder.Registration.property("BlockEntity", "blockEntity.Position", "Vector3", "This block entity's world position.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("BlockEntity", "blockEntity.Block", "Block", "The live block that owns this block entity.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("BlockEntity", "blockEntity.Type", "string", "This block entity type's registry identifier.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("BlockEntity", "blockEntity.Nbt", "Nbt|table", "Reads are available on both sides; writes are server-side only.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("BlockEntity", "blockEntity.Container", "Container|nil", "The same live Container exposed by Block.Container, when available.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.property("BlockEntity", "blockEntity.Inventory", "Container|nil", "Alias for blockEntity.Container.", "shared:Shared"));
        docs.register(MLuaDocBuilder.Registration.guideStep("BlockEntity", "Extend a block with saved state",
                "Register a Lua class under BlockEntity and assign it in the block definition. Define OnLoad and OnUnload on the class or provide nested callbacks. Each receives the live subclass instance and a context table on the server. The context exposes Block, BlockEntity, a live World instance, and Vector3 Position. OnLoad runs when Minecraft assigns the block entity to a level; OnUnload runs when it is removed, including chunk unloads. Retrieve the same live instance from Block:GetBlockEntity().",
                "Minecraft:RegisterClass(\"RuneChestEntity\", \"BlockEntity\", \"Stores a rune chest's state.\")\nMinecraft:RegisterClassFunction(\"RuneChestEntity\", \"SetCharge\", function(self, charge)\n    self.Nbt.charge = charge\nend, \"Saves this block's charge value.\")\nMinecraft:RegisterClassFunction(\"RuneChestEntity\", \"GetCharge\", function(self)\n    return self.Nbt.charge or 0\nend, \"Returns the saved charge value.\")\n\nRegistry.Blocks:Register(\"rune_chest\", {\n    container = { Rows = 3 },\n    blockEntity = {\n        className = \"RuneChestEntity\",\n        onLoad = function(self, context)\n            self.Nbt.charge = self.Nbt.charge or 0\n            print(context.World:GetDimension(), context.Position)\n        end,\n        onUnload = function(self, context)\n            self.Nbt.lastDimension = context.World:GetDimension()\n        end\n    }\n})\n\nlocal World = Minecraft:GetService(\"World\")\nlocal block = World:GetBlock(position)\nlocal entity = block:GetBlockEntity()\nentity:SetCharge(12)"));
        docs.register(MLuaDocBuilder.Registration.guideStep("Block", "Inspect a block from the world",
                "World:GetBlock returns a live instance of Block or the addon subclass registered for that block. Use its inherited methods and GetClassName to inspect its class.",
                "local World = Minecraft:GetService(\"World\")\nlocal block = World:GetBlock(Vector3.new(0, 64, 0))\nprint(block.Id, block:GetClassName())\nblock:Break(false)"));
        docs.register(MLuaDocBuilder.Registration.guideStep("BlockItem", "Extend the item form of a block",
                "A block item is an Item subclass. Register its class under BlockItem and pass that class to RegisterItem; callbacks receive the same live subclass.",
                "Minecraft:RegisterClass(\"SignalBlockItem\", \"BlockItem\", \"The item form of a signal block.\")\nMinecraft:RegisterClassFunction(\"SignalBlockItem\", \"IsSignalBlock\", function(self)\n    return self:GetId() == \"myaddon:signal_block\"\nend, \"Checks which registered block this item places.\")\n\nRegistry.Blocks:Register(\"signal_block\", {})\nRegistry.Blocks:RegisterItem(\"signal_block\", { className = \"SignalBlockItem\" })"));
        docs.register(MLuaDocBuilder.Registration.clazz("ScriptedEntity", "MLuaScriptedEntity",
                "A registered Lua-controlled mob with navigation, sensing, combat, and persistent AI data.",
                "classes/scripted-entity.html", "Mob"));
        docs.register(MLuaDocBuilder.Registration.method("ScriptedEntity", "ScriptedEntity:MoveTo(position: Vector3|CFrame, speed?: number): boolean",
                "On the server, finds a navigation path to the target position using a finite positive speed. The path continues after this call returns.",
                "Native target position and optional movement speed", "boolean", "server:Server", null));
        docs.register(MLuaDocBuilder.Registration.method("ScriptedEntity", "ScriptedEntity:LookAt(position: Vector3|CFrame)",
                "Turns the mob toward a native position.", "Native target position", "void", "server:Server", null));
        docs.register(MLuaDocBuilder.Registration.method("ScriptedEntity", "ScriptedEntity:FindNearestPlayer(range?: finite number, requireLineOfSight?: boolean): Player|nil",
                "Finds the nearest living player in this dimension. Range defaults to 16 and is clamped to 0 through 128 blocks.", "Optional search radius and line-of-sight requirement", "Player|nil", "server:Server", null));
        docs.register(MLuaDocBuilder.Registration.method("ScriptedEntity", "ScriptedEntity:FindNearestEntity(entityType?: string, range?: finite number): LivingEntity|nil",
                "Finds the nearest living entity in this dimension, optionally matching a registered type id. Range defaults to 16 and is clamped to 0 through 128 blocks.", "Optional registered entity type id and search radius", "LivingEntity|nil", "server:Server", null));
        docs.register(MLuaDocBuilder.Registration.method("ScriptedEntity", "ScriptedEntity:FindNearestBlock(blockId: string, radius?: whole number): Block|nil",
                "Finds the nearest block with this registered id. Radius defaults to 10 and is clamped to 0 through 16 blocks.", "Registered block id and optional integer search radius", "Block|nil", "server:Server", null));
        docs.register(MLuaDocBuilder.Registration.method("ScriptedEntity", "ScriptedEntity:SetDoorBreakProgress(position: Block|Vector3, progress: whole number -1..9): boolean",
                "Shows Minecraft's door breaking animation at progress 0 through 9, or clears it with -1.", "Door position and Minecraft destroy-progress stage", "boolean", "server:Server", null));
        docs.register(MLuaDocBuilder.Registration.method("ScriptedEntity", "ScriptedEntity:MeleeAttack(target: LivingEntity, cooldownTicks?: whole number): boolean",
                "Attacks a living target in reach. Cooldown defaults to 20 ticks and is clamped to 1 through 200 ticks.", "Live living entity and optional cooldown in ticks", "boolean", "server:Server", null));
        docs.register(MLuaDocBuilder.Registration.clazz("RegistryEntry", "MLuaRegistryEntry",
                "A live view of a namespaced key in one of MLua's supported Minecraft registries.",
                "classes/registry-entry.html", "Instance"));
        docs.register(MLuaDocBuilder.Registration.clazz("ContainerType", "MLuaContainerType",
                "A registered, reusable container layout with a namespaced id, title, row count, and item rules.",
                "classes/container-type.html", "Instance"));
        docs.register(MLuaDocBuilder.Registration.property("ContainerType", "ContainerType.AcceptsAllItems", "boolean",
            "Whether this container accepts every item.", "shared:Shared"));
    }

    private static void contributeDefinitionSchemas(MLuaDocBuilder docs) {
        schema(docs, "ItemDefinition", "Options and callbacks for a registered item.",
            "onUse?", "fun(context: ItemUseContext): boolean", "Runs when a player uses the item.",
            "onUseOnBlock?", "fun(context: ItemUseOnBlockContext): boolean", "Runs when a player uses the item on a block.",
            "onInventoryTick?", "fun(context: ItemInventoryTickContext)", "Runs while the item is in an inventory.",
            "onCrafted?", "fun(context: ItemCraftedContext)", "Runs when the item is crafted.",
            "onConsume?", "fun(context: ItemConsumeContext)", "Runs once on the server when eating finishes.",
            "onAttackEntity?", "fun(context: ItemAttackEntityContext): boolean?", "Runs from Minecraft's item hit hook after the target has been attacked, so it cannot cancel that hit. Return false to skip item hit handling; true allows native tool handling and requests durability handling for a generic scripted item.",
            "kind?", "string", "Built-in item kind such as item, sword, pickaxe, axe, shovel, hoe, or bow; registered custom kinds are also accepted.",
            "tier?", "string", "Tool material tier; use Registry.Items:GetTiers() for values exposed by the running Minecraft version.",
            "attackDamage?", "number", "Finite attack damage value for a supported tool kind.",
            "attackSpeed?", "number", "Finite attack speed value for a supported tool kind.",
            "durability?", "number", "Positive whole-number durability; requires maxStackSize to be 1 when that property is supplied.",
            "maxStackSize?", "number", "Whole number from 1 to 64; must be 1 for a durable item.",
            "fireResistant?", "boolean", "Whether the item is immune to fire and lava destruction.",
            "rarity?", "string", "Item rarity; use Registry.Items:GetRarities() for values exposed by the running Minecraft version.",
            "craftRemainder?", "string", "Item identifier left behind after crafting; it must name a registered item when creation runs.",
            "model?", "string", "Model resource identifier.", "texture?", "string", "Item texture resource identifier.",
            "copy?", "string", "Registered item whose properties are copied; unknown identifiers fail during item creation.",
            "copyModel?", "boolean", "Whether to copy the source item's model.",
            "food?", "FoodDefinition", "Food properties for edible items.",
            "className?", "string", "Registered Lua class inheriting from Item.");
        schema(docs, "FoodDefinition", "Nutrition and eating behavior for an item.",
            "nutrition", "number", "Required whole-number hunger points restored.", "saturation?", "number", "Finite non-negative saturation modifier.",
            "meat?", "boolean", "Whether this food counts as meat.",
            "alwaysEdible?", "boolean", "Whether it can be eaten when hunger is full.",
            "alwaysEat?", "boolean", "Alias for alwaysEdible.", "fast?", "boolean", "Whether eating completes quickly.");
        schema(docs, "ItemUseContext", "Context passed to an item use callback.",
            "Player", "Player|nil", "Player using the item, when available.", "World", "string", "World identifier.",
            "Position", "Vector3", "Use position.", "Hand", "string", "Hand holding the item.",
            "Item", "Item", "Live item instance.", "ItemStack", "string", "Item stack description.");
        schemaWithParent(docs, "ItemUseOnBlockContext", "ItemUseContext", "Context passed when an item is used on a block.",
            "Block", "Block", "Live block instance receiving the use.", "Face", "string", "Block face clicked.");
        schema(docs, "ItemInventoryTickContext", "Context passed while an item is in an inventory.",
            "Player", "Player|nil", "Owning player, when available.", "Entity", "Entity|nil", "Owning entity, when available.",
            "World", "string", "World identifier.", "Slot", "number", "One-based inventory slot.",
            "Selected", "boolean", "Whether this is the selected hotbar slot.", "Item", "Item", "Live item instance.",
            "ItemStack", "string", "Item stack description.");
        schema(docs, "ItemCraftedContext", "Context passed when an item is crafted.",
            "Player", "Player", "Player who crafted the item.", "World", "string", "World identifier.",
            "Position", "Vector3", "Crafting position.", "Hand", "string", "Hand value when supplied.",
            "Item", "Item", "Live crafted item.", "ItemStack", "string", "Crafted stack description.");
        schema(docs, "ItemConsumeContext", "Context passed when an item is consumed.",
            "Player", "Player|nil", "Player consuming the item.", "World", "string", "World identifier.", "Item", "Item", "Live consumed item.");
        schema(docs, "ItemAttackEntityContext", "Context passed when an item attacks an entity.",
            "Player", "Player|nil", "Player attacking with the item, when available.",
            "Attacker", "Entity|Player", "Entity performing the attack.",
            "Target", "Entity|Player", "Entity receiving the attack.",
            "Entity", "Entity|Player", "Alias for Target.",
            "Item", "Item", "Live item instance.", "ItemStack", "string", "Item stack description.");
        schema(docs, "BlockBehaviorContext", "Values supplied to scripted block callbacks.",
            "World", "string", "Dimension resource id.", "Position", "Vector3", "Center of the block position.",
            "Block", "Block", "Live block instance at Position.", "Player?", "Player", "Player associated with this callback, when available.",
            "Entity?", "Entity", "Live entity associated with this callback, when available.", "Hand?", "string", "Interaction hand when supplied.",
            "Face?", "string", "Block face involved in an interaction.", "Neighbor?", "Block", "Neighbor block that changed.",
            "Projectile?", "Entity", "Live projectile that hit the block.",
            "FallDistance?", "number", "Distance fallen before landing.");
        schema(docs, "BlockDefinition", "Properties and callbacks for a registered block.",
            "onRightClick?", "fun(context: BlockBehaviorContext): boolean", "Handles a right-click interaction; return true to consume it.",
            "onPlace?", "fun(context: BlockBehaviorContext)", "Runs when the block is placed.",
            "onBreak?", "fun(context: BlockBehaviorContext)", "Runs when the block is broken by a player.",
            "onStepOn?", "fun(context: BlockBehaviorContext)", "Runs when an entity steps on the block.",
            "onEntityInside?", "fun(context: BlockBehaviorContext)", "Runs while an entity intersects the block.",
            "onNeighborChanged?", "fun(context: BlockBehaviorContext)", "Runs when a neighboring block changes.",
            "onRemove?", "fun(context: BlockBehaviorContext)", "Runs when the block is removed or replaced.",
            "onRandomTick?", "fun(context: BlockBehaviorContext)", "Runs on a random block tick.",
            "onTick?", "fun(context: BlockBehaviorContext)", "Runs when a scheduled block tick fires.",
            "onFallOn?", "fun(context: BlockBehaviorContext)", "Handles an entity landing on the block.",
            "onProjectileHit?", "fun(context: BlockBehaviorContext)", "Runs when a projectile hits the block.",
            "copy?", "string", "Registered block whose physical properties are copied; unknown identifiers fail when the block is created.",
            "hardness?", "number", "Finite block hardness; may be -1 for an unbreakable block.",
            "strength?", "number", "Alias for hardness; provide only one of these names.",
            "resistance?", "number", "Finite non-negative explosion resistance; provide hardness as well.",
            "explosionResistance?", "number", "Alias for resistance; provide only one of these names and also provide hardness.",
            "lightLevel?", "integer", "Emitted light level from 0 to 15.",
            "friction?", "number", "Finite non-negative movement friction.",
            "speedFactor?", "number", "Finite non-negative movement speed factor.",
            "jumpFactor?", "number", "Finite non-negative jump factor.",
            "requiresCorrectTool?", "boolean", "Whether the correct tool is required for drops.",
            "collision?", "boolean", "Whether the block has collision; conflicts with noCollision=true.",
            "noCollision?", "boolean", "Whether the block has no collision.",
            "noOcclusion?", "boolean", "Whether the block does not occlude neighboring faces.",
            "randomTicks?", "boolean", "Whether the block receives random ticks.",
            "ignitedByLava?", "boolean", "Set true to make lava ignite the block.",
            "replaceable?", "boolean", "Set true to allow replacement by placement or fluid updates.",
            "pushReaction?", "string", "Piston reaction enum name; call Registry.Blocks:GetPushReactions() for values supported by the running Minecraft version.",
            "instrument?", "string", "Note block instrument enum name; call Registry.Blocks:GetInstruments() for values supported by the running Minecraft version.",
            "sound?", "string", "Minecraft block sound family; call Registry.Blocks:GetSounds() for values supported by the running version.",
            "mapColor?", "string", "Minecraft map color; call Registry.Blocks:GetMapColors() for values supported by the running version.",
            "mineableWith?", "string", "Required tool group.",
            "requiredToolTier?", "number", "Minimum tool tier.",
            "className?", "string", "Registered Lua class inheriting from Block.",
            "blockEntity?", "BlockEntityDefinition", "Lua block entity class and behavior settings.",
            "blockEntityClass?", "string", "Registered Lua class inheriting from BlockEntity.");
        schema(docs, "BlockEntityDefinition", "Lua class and load behavior for a block's live block entity.",
            "className?", "string", "Registered Lua class inheriting from BlockEntity.",
            "onLoad?", "fun(self: BlockEntity, context: table)", "Runs on the server when Minecraft assigns the block entity to a level. The context includes live Block, BlockEntity, and World instances plus a Vector3 Position.",
            "onUnload?", "fun(self: BlockEntity, context: table)", "Runs on the server when Minecraft removes the block entity. The context includes live Block, BlockEntity, and World instances plus a Vector3 Position.");
        schema(docs, "BlockItemDefinition", "Options and callbacks for a block's item form.",
            "className?", "string", "Registered Lua class inheriting from BlockItem.",
            "maxStackSize?", "whole number", "Maximum stack size from 1 to 64; durable block items must have a stack size of one.",
            "durability?", "positive whole number", "Item durability; cannot be combined with maxStackSize above one.",
            "fireResistant?", "boolean", "Whether the item form is immune to fire and lava destruction.",
            "rarity?", "string", "Item rarity: common, uncommon, rare, or epic.",
            "craftRemainder?", "string", "Item identifier left behind after crafting.",
            "copy?", "string", "Registered item whose stack, durability, rarity, food, and remainder properties are copied.",
            "food?", "FoodDefinition", "Food properties for edible block items.",
            "description?", "string|string[]", "Additional tooltip description lines.",
            "onUse?", "fun(context: ItemUseContext): boolean", "Runs when a player uses this block item.",
            "onUseOnBlock?", "fun(context: ItemUseOnBlockContext): boolean", "Runs when this item is used on a block.",
            "onInventoryTick?", "fun(context: ItemInventoryTickContext)", "Runs while the block item is in an inventory.",
            "onCrafted?", "fun(context: ItemCraftedContext)", "Runs when the block item is crafted.",
            "onConsume?", "fun(context: ItemConsumeContext)", "Runs when the edible block item is consumed.",
            "onAttackEntity?", "fun(context: ItemAttackEntityContext): boolean?", "Runs when the block item attacks an entity.");
        schema(docs, "EntityDefinition", "Properties, callbacks, and scripted AI for a registered entity.",
            "onTick?", "fun(entity: Entity)", "Runs on an entity tick.",
            "onHurt?", "fun(self: Entity, amount: number, source: string)", "Runs after damage is accepted.",
            "onDeath?", "fun(self: Entity, source: string)", "Runs when the entity dies.",
            "AI?", "AIDefinition[]", "Scripted AI behavior definitions.",
            "onSpawn?", "fun(entity: Entity)", "Runs when the entity spawns.",
            "onInteract?", "fun(self: Entity, player: Player, hand: string): boolean", "Handles player interaction.",
            "onRemove?", "fun(entity: Entity)", "Runs when the entity is removed.",
            "aiInterval?", "whole number", "Ticks between scripted AI updates, clamped to 1–120.",
            "attributes?", "table<string, number>", "Finite values using built-in camelCase aliases or registered namespaced attribute ids. maxHealth must be greater than zero and knockbackResistance must be between 0 and 1.", "width?", "number", "Finite positive width representable as a float.",
            "height?", "number", "Finite positive height representable as a float.",
            "trackingRange?", "whole number >= 0", "Network tracking range in chunks; zero disables client tracking.",
            "updateInterval?", "whole number >= 1", "Ticks between entity tracking updates.",
            "shadowRadius?", "number", "Finite non-negative rendered shadow radius.",
            "category?", "string", "Minecraft entity category.", "texture?", "string", "Entity texture resource.",
            "model?", "string", "Entity model resource.", "className?", "string", "Registered Lua class inheriting from ScriptedEntity.");
        schema(docs, "WorldGenerationFeatureDefinition", "Adds one registered placed feature to selected biomes.",
            "feature", "string", "Placed feature identifier.", "biomes", "string|string[]", "Biome identifiers or #tag selectors.",
            "step", "Enum.GenerationStep.Decoration|string", "Native Minecraft decoration step enum or its name as a string.");
        schema(docs, "ContainerDefinition", "Reusable inventory layout and optional item restrictions for a scripted block container.",
            "rows?", "whole number (1–6)", "Number of nine-slot rows in the container; defaults to 3. Rows is an accepted alias.",
            "title?", "string", "Title shown when a player opens the container; defaults to a display name based on its id. Title is an accepted alias.",
            "allowedItems?", "string|string[]", "Allowed item ids. Omit this field or pass an empty array to allow every item. AllowedItems is an accepted alias.");
        schema(docs, "SpawnEggDefinition", "Appearance and item callbacks for a registered entity spawn egg.",
            "entity?", "string", "Valid namespaced entity type identifier spawned by the egg.", "primaryColor?", "integer", "Primary egg color from 0 to 16777215.",
            "secondaryColor?", "integer", "Secondary egg color from 0 to 16777215.", "description?", "string|string[]", "Tooltip description lines.",
            "tooltip?", "string|string[]", "Additional tooltip lines.",
            "onUse?", "fun(context: ItemUseContext): boolean", "Handles use in the air.",
            "onUseOnBlock?", "fun(context: ItemUseOnBlockContext): boolean", "Handles use on a block.",
            "onInventoryTick?", "fun(context: ItemInventoryTickContext)", "Runs while the egg is in an inventory.",
            "onCrafted?", "fun(context: ItemCraftedContext)", "Runs when the egg is crafted.",
            "onConsume?", "fun(context: ItemConsumeContext)", "Runs when the egg is consumed.",
            "onAttackEntity?", "fun(context: ItemAttackEntityContext): boolean", "Handles an attack with the egg.");
        schema(docs, "AIDefinition", "One scripted AI goal for a registered entity.",
            "type", "string", "AI goal name.", "priority?", "number", "Goal priority.", "speed?", "number", "Movement speed.",
            "target?", "string", "Optional target entity type.", "radius?", "number", "Search radius.");
        schema(docs, "DamageSourceDefinition", "Damage behavior flags for a registered damage source.",
            "bypassesArmor?", "boolean", "Ignores armor.", "isFire?", "boolean", "Counts as fire damage.",
            "isExplosion?", "boolean", "Counts as explosion damage.", "isMagic?", "boolean", "Counts as magic damage.",
            "isProjectile?", "boolean", "Counts as projectile damage.", "isFall?", "boolean", "Counts as fall damage.",
            "isLightning?", "boolean", "Counts as lightning damage.");
        schema(docs, "EffectDefinition", "Visual and gameplay flags for a registered mob effect.",
            "color?", "number", "Effect particle and tint color.", "isBad?", "boolean", "Whether the effect is harmful.",
            "isBeneficial?", "boolean", "Whether the effect is beneficial.",
            "tickInterval?", "whole number >= 1", "Ticks between scripted onTick callbacks.");
        schema(docs, "PotionDefinition", "Effect and timing options for a registered potion.",
            "effect", "string", "Mob effect identifier.", "duration?", "whole number", "Effect duration in ticks (default 600).",
            "amplifier?", "whole number >= 0", "Effect amplifier (default 0).", "ambient?", "boolean", "Whether the effect is ambient.",
            "showParticles?", "boolean", "Whether effect particles are shown.");
        schema(docs, "CookingRecipeDefinition", "Ingredient, result, and smelting timing options.",
            "ingredient", "string|IngredientDefinition", "Input item or ingredient description.",
            "result", "string", "Output item identifier.", "experience?", "number >= 0", "Experience granted when the result is collected.",
            "cookingTime?", "whole number >= 1", "Cooking duration in ticks (default 200). The lowercase cookingtime alias is also accepted.");
        schema(docs, "StonecuttingRecipeDefinition", "Ingredient and output options for a stonecutting recipe.",
            "ingredient", "string|IngredientDefinition", "Input item or ingredient description.",
            "result", "string", "Output item identifier.", "count?", "whole number >= 1", "Output count (default 1).");
        schema(docs, "LootDefinition", "Loot table definition with one or more pools.",
            "pools?", "LootPoolDefinition[]", "Loot pools evaluated for this table.");
        schema(docs, "LootPoolDefinition", "One pool of weighted loot entries.",
            "rolls", "number", "Number of rolls made against the pool.", "entries", "LootEntryDefinition[]", "Weighted entries in the pool.",
            "conditions?", "LootConditionDefinition[]", "Optional conditions required before rolls.");
        schema(docs, "LootEntryDefinition", "One loot entry in a pool.",
            "type", "string", "Loot entry kind.", "item?", "string", "Item identifier for item entries.",
            "weight?", "number", "Relative selection weight.");
        schema(docs, "LootConditionDefinition", "One condition controlling whether a loot pool is active.",
            "condition", "string", "Condition kind.", "chance?", "number", "Optional probability between zero and one.");
        schema(docs, "TagDefinition", "Values and replacement behavior for a registered tag.",
            "replace?", "boolean", "Whether to replace existing tag values.", "values", "string[]", "Identifier values added to the tag.");
        schema(docs, "CreativeTabDefinition", "Icon and item contents for a registered creative tab.",
            "icon", "string", "Item identifier used as the tab icon.", "items?", "string[]", "Item identifiers shown in the tab.");
    }

    private static void contributeSignatures(MLuaDocBuilder docs) {
        docs.register(MLuaDocBuilder.Registration.signature("Registry", "Registry:Get(category: string, id: string)", "RegistryEntry|ProjectileType|table|nil"));
        docs.register(MLuaDocBuilder.Registration.signature("Registry", "Registry:GetAll(category: string, namespace?: string)", "table<string, RegistryEntry|ProjectileType|table>"));
        docs.register(MLuaDocBuilder.Registration.signature("Registry", "Registry:GetIds(category: string, namespace?: string)", "string[]"));
        docs.register(MLuaDocBuilder.Registration.signature("Registry", "Registry:Exists(category: string, id: string)", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("Registry.Items", "Registry.Items:Register(id: string, props: ItemDefinition|ItemProperties)", "RegistryEntry"));
        docs.register(MLuaDocBuilder.Registration.signature("Registry.Items", "Registry.Items:Properties()", "ItemProperties"));
        docs.register(MLuaDocBuilder.Registration.signature("Registry.Items", "Registry.Items:GetTiers()", "string[]"));
        docs.register(MLuaDocBuilder.Registration.signature("Registry.Items", "Registry.Items:GetRarities()", "string[]"));
        docs.register(MLuaDocBuilder.Registration.signature("Registry.Items", "Registry.Items:GetId(name: string)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Registry.Items", "Registry.Items:Get(name: string)", "RegistryEntry"));
        docs.register(MLuaDocBuilder.Registration.signature("Registry.Items", "Registry.Items:GetAll(namespace?: string)", "table<string, RegistryEntry>"));
        docs.register(MLuaDocBuilder.Registration.signature("Registry.Items", "Registry.Items:GetIds(namespace?: string)", "string[]"));
        docs.register(MLuaDocBuilder.Registration.signature("Registry.Items", "Registry.Items:Exists(name: string)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Registry.Blocks", "Registry.Blocks:Register(id: string, props: BlockDefinition|BlockProperties)", "RegistryEntry"));
        docs.register(MLuaDocBuilder.Registration.signature("Registry.Blocks", "Registry.Blocks:Properties()", "BlockProperties"));
        docs.register(MLuaDocBuilder.Registration.signature("Registry.Blocks", "Registry.Blocks:RegisterItem(id: string, definition?: BlockItemDefinition|ItemProperties)", "RegistryEntry"));
        docs.register(MLuaDocBuilder.Registration.signature("Registry.Blocks", "Registry.Blocks:RegisterState(id: string, blockState: string)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Registry.Blocks", "Registry.Blocks:GetPushReactions()", "string[]"));
        docs.register(MLuaDocBuilder.Registration.signature("Registry.Blocks", "Registry.Blocks:GetInstruments()", "string[]"));
        docs.register(MLuaDocBuilder.Registration.signature("Registry.Blocks", "Registry.Blocks:GetSounds()", "string[]"));
        docs.register(MLuaDocBuilder.Registration.signature("Registry.Blocks", "Registry.Blocks:GetMapColors()", "string[]"));
        docs.register(MLuaDocBuilder.Registration.signature("Registry.Blocks", "Registry.Blocks:GetId(name: string)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Registry.Blocks", "Registry.Blocks:Get(name: string)", "RegistryEntry"));
        docs.register(MLuaDocBuilder.Registration.signature("Registry.Blocks", "Registry.Blocks:GetAll(namespace?: string)", "table<string, RegistryEntry>"));
        docs.register(MLuaDocBuilder.Registration.signature("Registry.Blocks", "Registry.Blocks:GetIds(namespace?: string)", "string[]"));
        docs.register(MLuaDocBuilder.Registration.signature("Registry.Blocks", "Registry.Blocks:Exists(name: string)", null));
        docs.register(MLuaDocBuilder.Registration.signature("RegistryProperties", "RegistryProperties:Build()", "table"));
        docs.register(MLuaDocBuilder.Registration.signature("RegistryProperties", "RegistryProperties:Class(className: string)", "RegistryProperties"));
        docs.register(MLuaDocBuilder.Registration.signature("ItemProperties", "ItemProperties:Description(text: string)", "ItemProperties"));
        docs.register(MLuaDocBuilder.Registration.signature("ItemProperties", "ItemProperties:Copy(id: string)", "ItemProperties"));
        docs.register(MLuaDocBuilder.Registration.signature("ItemProperties", "ItemProperties:CopyModel(enabled: boolean)", "ItemProperties"));
        docs.register(MLuaDocBuilder.Registration.signature("BlockProperties", "BlockProperties:Description(text: string)", "BlockProperties"));
        docs.register(MLuaDocBuilder.Registration.signature("BlockProperties", "BlockProperties:Copy(id: string)", "BlockProperties"));
        docs.register(MLuaDocBuilder.Registration.signature("BlockProperties", "BlockProperties:CopyModel(enabled: boolean)", "BlockProperties"));
        String[] itemPropertyMethods = {"StacksTo(size: number)", "Durability(maxDamage: number)", "Rarity(name: string)",
            "FireResistant(enabled: boolean)", "CraftRemainder(item: string)", "Kind(name: string)", "Tier(name: string)",
            "AttackDamage(amount: number)", "AttackSpeed(amount: number)", "Food(properties: table)",
            "Nutrition(amount: number)", "Saturation(amount: number)", "Meat(enabled: boolean)",
            "AlwaysEdible(enabled: boolean)", "Fast(enabled: boolean)", "OnUse(callback: function)",
            "OnUseOnBlock(callback: function)", "OnInventoryTick(callback: function)", "OnCrafted(callback: function)",
            "OnConsume(callback: function)", "OnAttackEntity(callback: function)"};
        for (String method : itemPropertyMethods) {
            docs.register(MLuaDocBuilder.Registration.signature("ItemProperties", "ItemProperties:" + method, "ItemProperties"));
        }
        String[] blockPropertyMethods = {"Strength(hardness: number, resistance?: number)", "Resistance(amount: number)",
            "RequiresCorrectToolForDrops(enabled: boolean)", "NoCollision(enabled: boolean)", "NoOcclusion(enabled: boolean)",
            "RandomTicks(enabled: boolean)", "IgnitedByLava(enabled: boolean)", "Replaceable(enabled: boolean)",
            "LightLevel(level: number)", "Friction(multiplier: number)", "SpeedFactor(multiplier: number)",
            "JumpFactor(multiplier: number)", "Sound(name: string)", "MapColor(name: string)",
            "PushReaction(name: string)", "Instrument(name: string)", "OnRightClick(callback: function)",
            "OnPlace(callback: function)", "OnBreak(callback: function)", "OnStepOn(callback: function)",
            "OnEntityInside(callback: function)", "OnNeighborChanged(callback: function)", "OnRemove(callback: function)",
            "OnRandomTick(callback: function)", "OnTick(callback: function)", "OnFallOn(callback: function)",
            "OnProjectileHit(callback: function)"};
        for (String method : blockPropertyMethods) {
            docs.register(MLuaDocBuilder.Registration.signature("BlockProperties", "BlockProperties:" + method, "BlockProperties"));
        }
        String[] entityPropertyMethods = {"Category(name: string)", "Size(width: number, height: number)",
            "TrackingRange(chunks: number)", "UpdateInterval(ticks: number)", "ShadowRadius(radius: number)",
            "Texture(resource: string)", "Model(resource: string)", "Attributes(values: table)",
            "MaxHealth(value: number)", "MovementSpeed(value: number)", "AttackDamage(value: number)",
            "Armor(value: number)", "ArmorToughness(value: number)", "AttackSpeed(value: number)",
            "KnockbackResistance(value: number)", "FollowRange(value: number)", "AI(callback: function|table)",
            "OnTick(callback: function)", "OnHurt(callback: function)", "OnDeath(callback: function)",
            "OnSpawn(callback: function)", "OnInteract(callback: function)", "OnRemove(callback: function)"};
        for (String method : entityPropertyMethods) {
            docs.register(MLuaDocBuilder.Registration.signature("EntityProperties", "EntityProperties:" + method, "EntityProperties"));
        }
        docs.register(MLuaDocBuilder.Registration.signature("Registry.Containers", "Registry.Containers:Register(id: string, definition: ContainerDefinition)", "ContainerType"));
        docs.register(MLuaDocBuilder.Registration.signature("Registry.Containers", "Registry.Containers:GetId(name: string)", "string"));
        docs.register(MLuaDocBuilder.Registration.signature("Registry.Containers", "Registry.Containers:Get(id: string)", "ContainerType|nil"));
        docs.register(MLuaDocBuilder.Registration.signature("Registry.Containers", "Registry.Containers:GetAll(namespace?: string)", "table<string, ContainerType>"));
        docs.register(MLuaDocBuilder.Registration.signature("Registry.Containers", "Registry.Containers:GetIds(namespace?: string)", "string[]"));
        docs.register(MLuaDocBuilder.Registration.signature("Registry.Containers", "Registry.Containers:Exists(id: string)", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("ContainerType", "ContainerType:GetId()", "string"));
        docs.register(MLuaDocBuilder.Registration.signature("ContainerType", "ContainerType:GetRows()", "number"));
        docs.register(MLuaDocBuilder.Registration.signature("ContainerType", "ContainerType:GetSlotCount()", "number"));
        docs.register(MLuaDocBuilder.Registration.signature("ContainerType", "ContainerType:GetTitle()", "string"));
        docs.register(MLuaDocBuilder.Registration.signature("ContainerType", "ContainerType:GetAllowedItems()", "string[]"));
        docs.register(MLuaDocBuilder.Registration.signature("ContainerType", "ContainerType:AllowsAllItems()", "boolean"));
        docs.register(MLuaDocBuilder.Registration.method("ContainerType", "ContainerType:AllowsItem(item: string|Item): boolean",
            "Checks whether an item id or live Item instance is allowed by this container type. An unqualified id uses the container type's namespace.",
            "Namespaced item id or live Item instance", "boolean", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.signature("Registry.Entities", "Registry.Entities:Register(id: string, props: EntityDefinition)", "RegistryEntry"));
        docs.register(MLuaDocBuilder.Registration.signature("Registry.Entities", "Registry.Entities:Properties()", "EntityProperties"));
        docs.register(MLuaDocBuilder.Registration.signature("Registry.Entities", "Registry.Entities:RegisterSpawnEgg(id: string, props: SpawnEggDefinition)", "RegistryEntry"));
        docs.register(MLuaDocBuilder.Registration.signature("Registry.Entities", "Registry.Entities:Get(name: string)", "RegistryEntry"));
        docs.register(MLuaDocBuilder.Registration.signature("Registry.Entities", "Registry.Entities:GetAll(namespace?: string)", "table<string, RegistryEntry>"));
        docs.register(MLuaDocBuilder.Registration.signature("Registry.Entities", "Registry.Entities:GetIds(namespace?: string)", "string[]"));
        docs.register(MLuaDocBuilder.Registration.signature("Registry.Entities", "Registry.Entities:Exists(name: string)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Registry.Effects", "Registry.Effects:Get(name: string)", "RegistryEntry"));
        docs.register(MLuaDocBuilder.Registration.signature("Registry.Effects", "Registry.Effects:GetAll(namespace?: string)", "table<string, RegistryEntry>"));
        docs.register(MLuaDocBuilder.Registration.signature("Registry.Effects", "Registry.Effects:GetIds(namespace?: string)", "string[]"));
        docs.register(MLuaDocBuilder.Registration.signature("Registry.Potions", "Registry.Potions:Get(name: string)", "RegistryEntry"));
        docs.register(MLuaDocBuilder.Registration.signature("Registry.Potions", "Registry.Potions:GetAll(namespace?: string)", "table<string, RegistryEntry>"));
        docs.register(MLuaDocBuilder.Registration.signature("Registry.Potions", "Registry.Potions:GetIds(namespace?: string)", "string[]"));
        docs.register(MLuaDocBuilder.Registration.signature("Registry.Particles", "Registry.Particles:Get(name: string)", "RegistryEntry"));
        docs.register(MLuaDocBuilder.Registration.signature("Registry.Particles", "Registry.Particles:GetAll(namespace?: string)", "table<string, RegistryEntry>"));
        docs.register(MLuaDocBuilder.Registration.signature("Registry.Particles", "Registry.Particles:GetIds(namespace?: string)", "string[]"));
        docs.register(MLuaDocBuilder.Registration.signature("Registry.Tabs", "Registry.Tabs:Get(name: string)", "RegistryEntry"));
        docs.register(MLuaDocBuilder.Registration.signature("Registry.Tabs", "Registry.Tabs:GetAll(namespace?: string)", "table<string, RegistryEntry>"));
        docs.register(MLuaDocBuilder.Registration.signature("Registry.Tabs", "Registry.Tabs:GetIds(namespace?: string)", "string[]"));
        docs.register(MLuaDocBuilder.Registration.signature("Registry.Effects", "Registry.Effects:Register(id: string, props: EffectDefinition)", "RegistryEntry"));
        docs.register(MLuaDocBuilder.Registration.signature("Registry.Potions", "Registry.Potions:Register(id: string, props: PotionDefinition)", "RegistryEntry"));
        docs.register(MLuaDocBuilder.Registration.signature("Registry.Particles", "Registry.Particles:Register(id: string, props: ParticleDefinition)", "RegistryEntry"));
        docs.register(MLuaDocBuilder.Registration.signature("Registry.Recipes", "Registry.Recipes:Register(id: string, definition: table|string)", "Registers a recipe JSON object or JSON string."));
        docs.register(MLuaDocBuilder.Registration.signature("Registry.Recipes", "Registry.Recipes:RegisterShaped(id: string, definition: RecipeDefinition)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Registry.Recipes", "Registry.Recipes:RegisterShapeless(id: string, definition: RecipeDefinition)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Registry.Recipes", "Registry.Recipes:RegisterSmelting(id: string, definition: CookingRecipeDefinition)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Registry.Recipes", "Registry.Recipes:RegisterStonecutting(id: string, definition: StonecuttingRecipeDefinition)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Registry.Loot", "Registry.Loot:Register(definition: LootDefinition)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Registry.Loot", "Registry.Loot:RegisterEntity(id: string, props: LootDefinition)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Registry.Loot", "Registry.Loot:RegisterSimpleBlock(id: string, itemId: string)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Registry.Loot", "Registry.Loot:RegisterSelfBlock(id: string)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Registry.Loot", "Registry.Loot:RegisterSilkTouchBlock(id: string, itemId: string, count: whole number >= 1?)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Registry.Loot", "Registry.Loot:RegisterOreBlock(id: string, dropId: string, minCount: whole number >= 1?, maxCount: whole number >= minCount?)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Registry.Tags", "Registry.Tags:Register(definition: TagDefinition)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Registry.Tags", "Registry.Tags:RegisterItem(id: string, items: string[])", null));
        docs.register(MLuaDocBuilder.Registration.signature("Registry.Tags", "Registry.Tags:RegisterBlock(id: string, blocks: string[])", null));
        docs.register(MLuaDocBuilder.Registration.signature("Registry.Tags", "Registry.Tags:RegisterEntity(id: string, entities: string[])", null));
        docs.register(MLuaDocBuilder.Registration.signature("Registry.Tags", "Registry.Tags:AddToItem(id: string, items: string[])", null));
        docs.register(MLuaDocBuilder.Registration.signature("Registry.Tags", "Registry.Tags:AddToBlock(id: string, blocks: string[])", null));
        docs.register(MLuaDocBuilder.Registration.signature("Registry.Tags", "Registry.Tags:AddToEntity(id: string, entities: string[])", null));
        docs.register(MLuaDocBuilder.Registration.signature("Registry.Projectiles", "Registry.Projectiles:Register(id: string, props: ProjectileDefinition)", "ProjectileType"));
        docs.register(MLuaDocBuilder.Registration.signature("Registry.Projectiles", "Registry.Projectiles:GetId(name: string)", "string"));
        docs.register(MLuaDocBuilder.Registration.signature("Registry.Projectiles", "Registry.Projectiles:Get(id: string)", "ProjectileType|nil"));
        docs.register(MLuaDocBuilder.Registration.signature("Registry.Projectiles", "Registry.Projectiles:GetAll(namespace?: string)", "table<string, ProjectileType>"));
        docs.register(MLuaDocBuilder.Registration.signature("Registry.Projectiles", "Registry.Projectiles:GetIds(namespace?: string)", "string[]"));
        docs.register(MLuaDocBuilder.Registration.signature("Registry.Projectiles", "Registry.Projectiles:Exists(id: string)", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("Registry.Tabs", "Registry.Tabs:Register(id: string, props: CreativeTabDefinition)", "RegistryEntry"));
        docs.register(MLuaDocBuilder.Registration.signature("Registry.Tabs", "Registry.Tabs:GetId(name: string)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Registry.Config", "Registry.Config:Register(scope: string, id: string, config: ConfigDefinition)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Registry.Config", "Registry.Config:RegisterCommon(id: string, config: ConfigDefinition)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Registry.Config", "Registry.Config:RegisterClient(id: string, config: ConfigDefinition)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Registry.Config", "Registry.Config:RegisterServer(id: string, config: ConfigDefinition)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Registry.DamageSource", "Registry.DamageSource:Register(name: string, props: DamageSourceDefinition)", null));
    }

    private static void schema(MLuaDocBuilder docs, String name, String description, String... fields) {
        schemaWithParent(docs, name, null, description, fields);
    }

    private static void schemaWithParent(MLuaDocBuilder docs, String name, String parent,
                                         String description, String... fields) {
        if (fields.length % 3 != 0) throw new IllegalArgumentException("Schema fields must be name, type, description triples: " + name);
        docs.register(MLuaDocBuilder.Registration.type(name, parent, description));
        for (int index = 0; index < fields.length; index += 3) {
            docs.register(MLuaDocBuilder.Registration.field(name, fields[index], fields[index + 1], fields[index + 2]));
        }
    }
}
