package dev.mlua.modules.registry;

import dev.mlua.core.MLuaContextView;
import dev.mlua.core.classes.MLuaClassMethods;
import dev.mlua.module.MLuaRegistration;
import dev.mlua.minecraft.binding.MLuaItemWrapper;
import dev.mlua.minecraft.binding.MLuaBlockWrapper;
import dev.mlua.minecraft.binding.MLuaRegistryCatalogs;
import dev.mlua.minecraft.binding.MLuaRegistryEntry;
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
        registration.registerClass("Nbt", registration.requireInstanceBaseClassName(), "A live view of a Minecraft NBT compound that reads and writes its backing data.");
        MLuaClassMethods.registerInstanceMethod(registration, "Nbt", "Get", "Reads a value at the given NBT key.");
        MLuaClassMethods.registerInstanceMethod(registration, "Nbt", "Set", "Writes a value at the given NBT key.");
        MLuaClassMethods.registerInstanceMethod(registration, "Nbt", "Remove", "Removes the value at the given NBT key.");
        registration.registerClass("ScriptedEntity", "LivingEntity",
            "A registered Lua-controlled mob with navigation, sensing, combat, and persistent AI data.");
        String[][] aiMethods = {
            {"MoveTo", "Find a path to coordinates at the given movement speed."},
            {"LookAt", "Turns the mob toward coordinates."},
            {"AttackTarget", "Sets or clears the mob's current living target."},
            {"GetTarget", "Returns the current living target, or nil."},
            {"GetBehaviorKey", "Returns the registered content key that defines this mob's behavior."},
            {"GetAIState", "Returns the mob's active scripted AI state, or nil."},
            {"GetAIStateTicks", "Returns the number of ticks spent in the active AI state."},
            {"SetAIState", "Changes the active scripted AI state, runs its exit and enter callbacks, and resumes AI disabled after an error."},
            {"FindNearestPlayer", "Finds the nearest living player in range, optionally requiring line of sight."},
            {"FindNearestEntity", "Finds the nearest living entity in range, optionally filtering by entity type."},
            {"FindNearestBlock", "Finds a matching block within the search radius."},
            {"NavigateTo", "Alias for MoveTo."},
            {"OpenDoor", "Opens or toggles a door at the given position."},
            {"CloseDoor", "Closes a door at the given position."},
            {"IsDoorOpen", "Returns whether the door at the given position is open."},
            {"SetDoorBreakProgress", "Shows or clears the mob's door breaking progress."},
            {"BreakDoor", "Breaks a wooden door at the given position."},
            {"SetCanOpenDoors", "Enables or disables opening doors while navigating."},
            {"SetCanPassDoors", "Enables or disables pathfinding through doors."},
            {"GetData", "Returns persistent Lua data saved with this mob."},
            {"SetData", "Replaces persistent Lua data saved with this mob."},
            {"RandomStroll", "Chooses a nearby safe position and walks there."},
            {"Panic", "Moves away from the last attacker or chooses a random escape direction."},
            {"DistanceTo", "Returns the distance to another entity instance."},
            {"CanSee", "Returns whether the mob has line of sight to another living entity."},
            {"MeleeAttack", "Attacks a living target in reach, with a configurable hit cooldown."}
        };
        for (String[] method : aiMethods) {
            MLuaClassMethods.registerInstanceMethod(registration, "ScriptedEntity", method[0], method[1]);
        }
        MLuaClassMethods.registerInstanceProperty(registration, "ScriptedEntity", "BehaviorKey",
            "GetBehaviorKey", null, "string", "shared:Shared",
            "The registered content key that selects this scripted mob's behavior definition.");
        MLuaBlockWrapper.classTable(registration);
        MLuaRegistryEntry.registerClass(registration);
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
        MLuaItemWrapper.attachCatalogs(itemGlobal, items);
    }

    @Override
    public void contributeDocs(MLuaDocBuilder docs) {
        docs.signature("Item", "Item:SetDisplayName(name: string)", null);
        docs.signature("Item", "Item:SetLore(lore: string[])", null);
        docs.signature("Item", "Item:SetCount(count: number)", null);
        docs.signature("Item", "Item:SetDamage(damage: number)", null);
        docs.signature("Item", "Item:SetNbt(nbt: string)", null);
        docs.signature("Item", "Item:SetTag(tag: string)", null);
        docs.signature("Registry.DataPack", "Registry.DataPack:Register(resourceType: string, id: string, definition: table)", null);
        docs.signature("Registry.Assets", "Registry.Assets:Register(path: string, definition: table)", null);
        docs.signature("Registry.Assets", "Registry.Assets:register(path: string, definition: table)", "Alias of Register.");
        docs.signature("Registry.WorldGeneration", "Registry.WorldGeneration:Register(resourceType: string, id: string, definition: table)", null);
        docs.signature("Registry.WorldGeneration", "Registry.WorldGeneration:AddFeature(name: string, definition: WorldGenerationFeatureDefinition)", null);
        docs.signature("Registry.WorldGeneration", "Registry.WorldGeneration:register(resourceType: string, id: string, definition: table)", "Alias of Register.");
        docs.signature("Registry.WorldGeneration", "Registry.WorldGeneration:addFeature(name: string, definition: WorldGenerationFeatureDefinition)", "Alias of AddFeature.");

        contributeDefinitionSchemas(docs);
        contributeSignatures(docs);
        docs.signature("Block", "Block:GetInventory()", "Container");
        docs.signature("Block", "Block:GetClassName()", "string");
        docs.signature("BlockEntity", "BlockEntity:GetLevel()", "Level");
        docs.signature("BlockEntity", "BlockEntity:GetDimension()", "string");
        docs.signature("BlockEntity", "BlockEntity:GetIsRemoved()", "boolean");
        docs.signature("Block", "Block:GetStateProperties()", "BlockStateProperties");
        docs.signature("Block", "Block:SetStateProperties(properties: BlockStateProperties)", "boolean");
        docs.signature("Block", "Block:GetFluid()", "string?");
        docs.signature("Block", "Block:GetLightLevel()", "number");
        docs.signature("Block", "Block:GetSkyLight()", "number");
        docs.signature("Block", "Block:GetBlockLight()", "number");
        docs.signature("Block", "Block:GetCanSeeSky()", "boolean");
        docs.signature("Block", "Block:GetRedstonePower()", "number");
        docs.signature("Block", "Block:GetIsPowered()", "boolean");
        docs.signature("Block", "Block:GetDestroySpeed()", "number");
        docs.signature("Block", "Block:GetCanSurvive()", "boolean");
        docs.signature("Block", "Block:GetCanBeReplaced()", "boolean");
        docs.signature("Block", "Block:GetHasBlockEntity()", "boolean");
        docs.signature("Block", "Block:GetIsRandomlyTicking()", "boolean");
        docs.signature("Block", "Block:Set(blockId: string, properties: BlockStateProperties?)", "boolean");
        docs.signature("Block", "Block:Break(dropItems: boolean?)", "boolean");
        docs.service("Registry", "MLuaRegistryService",
                "Registers addon content, generated data pack resources, Minecraft registry entries, and reusable inherited content types.",
                "services/registry.html");
        docs.guideStep("Registry.Containers", "Register a reusable container type",
                "Set chest rows and a title once, then reference the type from any scripted block. AllowedItems accepts item ids; an empty or omitted list accepts every item.",
                "local Registry = Minecraft:GetService(\"Registry\")\n\nRegistry.Containers:Register(\"supply_crate\", {\n    Rows = 3,\n    Title = \"Supply Crate\",\n    AllowedItems = { \"minecraft:torch\", \"minecraft:lantern\" },\n})");
        docs.guideStep("Registry", "Use the shared registration entry point",
                "Registry:Register routes definitions through the typed registries for items, blocks, entities, effects, potions, particles, containers, projectiles, world generation, tabs, recipes, loot, tags, common config, and damage sources. Use block_item after registering its block; register a tag as Registry:Register(\"tag\", kind, name, definition). RegisterType defines reusable addon-scoped defaults for each category. Items select them with kind, blocks with type, and other categories with RegistryType. Use Registry.ContentTypes for reusable defaults in your own data domains. GetRegistered returns IDs visible for a Minecraft registry kind.",
                "local Registry = Minecraft:GetService(\"Registry\")\n\nRegistry:Register(\"item\", \"flare_gun\", {\n    kind = \"firearm\",\n    maxStackSize = 1,\n    onUse = function(context)\n        -- Use the live player and projectile services here.\n    end,\n})\n\nfor _, id in ipairs(Registry:GetRegistered(\"items\")) do\n    print(id)\nend");
        docs.guideStep("Registry", "Add an addon-owned registry category",
                "RegisterCategory adds a category to Registry. Its handler receives each namespaced id and resolved definition during the registry phase. RegisterType and ResolveType use the same inherited defaults as built-in categories. Get and GetAll return copies of the registered definitions.",
                "local Registry = Minecraft:GetService(\"Registry\")\n\nRegistry:RegisterCategory(\"horror:weapons\", \"Weapon tuning data owned by this addon.\", function(id, definition)\n    if definition.Damage <= 0 then\n        error(\"Weapon damage must be positive\")\n    end\nend)\n\nRegistry:RegisterType(\"horror:weapons\", \"sidearm\", {\n    Base = \"default\",\n    Defaults = { Damage = 8, MagazineSize = 6 },\n})\n\nRegistry:Register(\"horror:weapons\", \"revolver\", {\n    RegistryType = \"sidearm\",\n    Damage = 12,\n})\n\nlocal revolver = Registry:Get(\"horror:weapons\", \"horror:revolver\")\nfor id, weapon in pairs(Registry:GetAll(\"horror:weapons\")) do\n    print(id, weapon.Damage)\nend");
        docs.guideStep("Registry.ContentTypes", "Define types for addon data",
                "ContentTypes lets an addon reuse inherited defaults and descriptions outside Minecraft's built-in categories. Give each domain a stable name, register its types during the registry phase, then resolve definitions with RegistryType. A definition without a selector passes through unchanged; a named type that is missing from that domain raises an error.",
                "local Registry = Minecraft:GetService(\"Registry\")\n\nRegistry.ContentTypes:Register(\"horror.weapon\", \"sidearm\", {\n    Base = \"default\",\n    Description = \"Shared defaults for compact horror weapons.\",\n    Defaults = { Damage = 6, Magazine = { Size = 8, ReloadTicks = 30 } },\n})\n\nRegistry.ContentTypes:Register(\"horror.weapon\", \"revolver\", {\n    Base = \"sidearm\",\n    Description = \"A slow, powerful revolver.\",\n    Defaults = { Damage = 12, Magazine = { Size = 6 } },\n})\n\nlocal weapon = Registry.ContentTypes:Resolve(\"horror.weapon\", {\n    RegistryType = \"revolver\",\n    Damage = 14,\n})\nprint(weapon.Damage, weapon.Magazine.Size, weapon.Magazine.ReloadTicks)");
        docs.guideStep("Registry", "Attach Lua classes to items and blocks",
                "Register a class under Item or Block, then set className in the content definition. Callback contexts receive live instances of that class, including inherited methods and applied mixins.",
                "Minecraft:RegisterClass(\"HuntingBow\", \"Item\", \"A reusable hunting bow.\")\nMinecraft:RegisterClassFunction(\"HuntingBow\", \"IsReady\", function(self)\n    return self:GetDamage() < self:GetMaxDamage()\nend, \"Checks whether the bow can still be used.\")\n\nMinecraft:RegisterClass(\"HauntedSoil\", \"Block\", \"Soil that reacts to nearby players.\")\nMinecraft:RegisterClassFunction(\"HauntedSoil\", \"Collapse\", function(self)\n    self:Set(\"minecraft:air\")\nend, \"Removes this soil block.\")\n\nRegistry.Items:Register(\"hunting_bow\", { className = \"HuntingBow\" })\nRegistry.Blocks:Register(\"haunted_soil\", { className = \"HauntedSoil\" })");
        docs.guideStep("Registry.DataPack", "Register data-driven world generation",
                "WorldGeneration writes vanilla dimension, dimension_type, and worldgen resources into the addon's namespace. Biomes use the worldgen/biome resource path. Use the exact JSON fields supported by the Minecraft version you target. Register configured and placed features for terrain, plants, and trees, then use AddFeature to place a feature in matching biomes. Biome ids and tags share one selector shape across loaders. Definitions can inherit reusable defaults through RegistryType.",
                "local Registry = Minecraft:GetService(\"Registry\")\n\nRegistry.DataPack:RegisterType(\"tree_feature\", {\n    Base = \"default\",\n    Description = \"Shared placement settings for this addon's trees.\",\n    Defaults = { type = \"minecraft:tree\" },\n})\n\nRegistry.DataPack:Register(\"worldgen/configured_feature\", \"dead_oak\", {\n    RegistryType = \"tree_feature\",\n    config = {\n        dirt_provider = { type = \"minecraft:simple_state_provider\", state = { Name = \"minecraft:dirt\" } },\n        trunk_provider = { type = \"minecraft:simple_state_provider\", state = { Name = \"minecraft:oak_log\" } },\n        foliage_provider = { type = \"minecraft:simple_state_provider\", state = { Name = \"minecraft:oak_leaves\", Properties = { distance = \"7\", persistent = \"false\" } } },\n        trunk_placer = { type = \"minecraft:straight_trunk_placer\", base_height = 4, height_rand_a = 2, height_rand_b = 0 },\n        foliage_placer = { type = \"minecraft:blob_foliage_placer\", radius = 2, offset = 0, height = 3 },\n        minimum_size = { type = \"minecraft:two_layers_feature_size\", limit = 1, lower_size = 0, upper_size = 1 },\n        decorators = {},\n    },\n})\n\nRegistry.DataPack:Register(\"worldgen/placed_feature\", \"dead_oak\", {\n    feature = \"myaddon:dead_oak\",\n    placement = {\n        { type = \"minecraft:count\", count = 1 },\n        { type = \"minecraft:in_square\" },\n        { type = \"minecraft:heightmap\", heightmap = \"WORLD_SURFACE_WG\" },\n        { type = \"minecraft:biome\" },\n    },\n})");
        docs.guideStep("Registry.WorldGeneration", "Place an ore, tree, or plant in selected biomes",
                "Use a biome id or tag selector with the same API on Fabric, Forge, and NeoForge. The placed feature must already be registered as a data pack resource.",
                "Registry.WorldGeneration:AddFeature(\"dead_oak_forest\", {\n    feature = \"myaddon:dead_oak\",\n    biomes = { \"#minecraft:is_forest\" },\n    step = \"vegetal_decoration\",\n})");
        docs.guideStep("Registry.WorldGeneration", "Define a biome, dimension, or terrain resource",
                "register writes a canonical Minecraft data resource to the generated server pack. resourceType is an open path: use dimension, dimension_type, or any worldgen/* registry path such as worldgen/biome, worldgen/noise_settings, and worldgen/density_function. IDs are owned by the addon namespace. These definitions are version-specific Minecraft JSON and are loaded when the generated data pack is enabled.",
                "local WorldGeneration = Minecraft:GetService(\"Registry\").WorldGeneration\n\nWorldGeneration:Register(\"worldgen/biome\", \"ashen_marsh\", {\n    has_precipitation = true,\n    temperature = 0.7,\n    downfall = 0.9,\n    effects = {\n        fog_color = 12638463,\n        water_color = 4159204,\n        water_fog_color = 329011,\n        sky_color = 7907327,\n    },\n    spawners = {},\n    spawn_costs = {},\n    carvers = {},\n    features = {},\n})\n\nWorldGeneration:Register(\"dimension_type\", \"ashen_depths\", {\n    ultrawarm = false,\n    natural = false,\n    coordinate_scale = 1.0,\n    has_skylight = false,\n    has_ceiling = true,\n    ambient_light = 0.1,\n    logical_height = 256,\n    infiniburn = \"minecraft:infiniburn_overworld\",\n    effects = \"minecraft:the_nether\",\n    min_y = -64,\n    height = 384,\n    monster_spawn_block_light_limit = 0,\n    monster_spawn_light_level = { type = \"minecraft:uniform\", value = { min_inclusive = 0, max_inclusive = 7 } },\n})\n\nWorldGeneration:Register(\"dimension\", \"ashen_depths\", {\n    type = \"myaddon:ashen_depths\",\n    generator = {\n        type = \"minecraft:noise\",\n        settings = \"myaddon:ashen_depths\",\n        biome_source = {\n            type = \"minecraft:fixed\",\n            biome = \"myaddon:ashen_marsh\",\n        },\n    },\n})\n\nWorldGeneration:Register(\"worldgen/noise_settings\", \"ashen_depths\", {\n    sea_level = 32,\n    disable_mob_generation = false,\n    aquifers_enabled = true,\n    ore_veins_enabled = false,\n    legacy_random_source = false,\n    default_block = { Name = \"minecraft:deepslate\" },\n    default_fluid = { Name = \"minecraft:lava\" },\n    noise = { min_y = -64, height = 384, size_horizontal = 1, size_vertical = 2 },\n    noise_router = {\n        barrier = \"minecraft:overworld/barrier\",\n        fluid_level_floodedness = \"minecraft:overworld/fluid_level_floodedness\",\n        fluid_level_spread = \"minecraft:overworld/fluid_level_spread\",\n        lava = \"minecraft:overworld/lava\",\n        temperature = \"minecraft:overworld/temperature\",\n        vegetation = \"minecraft:overworld/vegetation\",\n        continents = \"minecraft:overworld/continents\",\n        erosion = \"minecraft:overworld/erosion\",\n        depth = \"minecraft:overworld/depth\",\n        ridges = \"minecraft:overworld/ridges\",\n        initial_density_without_jaggedness = \"minecraft:overworld/initial_density_without_jaggedness\",\n        final_density = \"minecraft:overworld/final_density\",\n        vein_toggle = \"minecraft:overworld/vein_toggle\",\n        vein_ridged = \"minecraft:overworld/vein_ridged\",\n        vein_gap = \"minecraft:overworld/vein_gap\",\n    },\n    spawn_target = {},\n    surface_rule = { type = \"minecraft:block\", result_state = { Name = \"minecraft:deepslate\" } },\n})");
        docs.guideStep("Registry.Recipes", "Register a vanilla recipe type directly",
                "Registry.Recipes:Register accepts a Lua table or a JSON string. Use it for vanilla recipe types that do not have a dedicated Recipe builder; keep the fields aligned with the Minecraft version you target.",
                "local Recipes = Minecraft:GetService(\"Registry\").Recipes\n\nRecipes:Register(\"armor_trim\", {\n    type = \"minecraft:smithing_trim\",\n    template = { item = \"minecraft:coast_armor_trim_smithing_template\" },\n    base = { tag = \"minecraft:trimmable_armor\" },\n    addition = { tag = \"minecraft:trim_materials\" },\n})");
        docs.guideStep("Registry", "Work with registry entries generically",
                "Native registry tables share GetId, GetEntry, and Exists for items, blocks, entities, effects, potions, particles, and creative tabs. GetEntry returns the same live RegistryEntry class as Registry:GetEntry and RegistryEntry.new, keeping the normalized kind and namespaced ID together.",
                "local Registry = Minecraft:GetService(\"Registry\")\nlocal bow = Registry.Items:GetEntry(\"minecraft:bow\")\nlocal stone = Registry.Blocks:GetEntry(\"minecraft:stone\")\nlocal sameShape = RegistryEntry.new(\"items\", \"minecraft:bow\")\n\nprint(Registry.Items:GetId(\"bow\"))\nprint(bow.Kind, bow.Id)\nif stone.IsRegistered then\n    print(stone.Namespace, stone.Path)\nend\n\nfor _, kind in ipairs(Registry:GetKinds()) do\n    print(kind)\nend");
        docs.guideStep("Registry.Containers", "Attach storage to a scripted block",
                "The block entity keeps its slots on the server and opens a vanilla chest screen when the player interacts. AllowedItems is an optional allowlist applied to inventory slot insertion.",
                "Registry.Blocks:Register(\"supply_crate\", {\n    Container = \"supply_crate\",\n    onRightClick = function(context)\n        local container = context.Block:GetContainer()\n        if container and container:IsEmpty() then\n            container:SetSlot(1, { Id = \"minecraft:torch\", Count = 1 })\n        end\n    end,\n})");
        docs.guideStep("Registry.Blocks", "Build a timed scripted block",
                "onStepOn runs on the server. ScheduleTick waits the requested number of game ticks, then calls onTick with the live Block instance. Schedule another tick from onTick to build a repeating behavior.",
                "Registry.Blocks:Register(\"crumbling_stone\", {\n    copy = \"minecraft:stone\",\n    copyModel = true,\n    onStepOn = function(context)\n        context.Block:ScheduleTick(40) -- two seconds\n    end,\n    onTick = function(context)\n        context.Block:Set(\"minecraft:air\")\n    end,\n})");
        docs.guideStep("Registry.Items", "Discover available item types",
                "GetTypes returns the registered item kind names and short descriptions, including types added by Java extensions. The shovel spelling is canonical; spade remains an accepted alias.",
                "local Registry = Minecraft:GetService(\"Registry\")\nfor name, description in pairs(Registry.Items:GetTypes()) do\n    print(name, description)\nend\n\nRegistry.Blocks:GetTypes() -- lists default and Java-registered block types");
        docs.guideStep("Registry.Items", "Define a reusable Lua content type",
                "RegisterType creates a reusable kind owned by your addon. It can extend a built-in or Java-registered type, and it can extend another addon's type by using its full namespace:name. Defaults merge recursively, so a definition can override one nested setting while inheriting the rest. The item uses Minecraft's native bow behavior and animation, while the default onUse callback can connect it to a projectile definition.",
                "local Registry = Minecraft:GetService(\"Registry\")\n\nRegistry.Items:RegisterType(\"hunting_bow\", {\n    Base = \"bow\",\n    Description = \"A reusable hunting bow with a custom shot.\",\n    Defaults = {\n        maxStackSize = 1,\n        durability = 420,\n        copy = \"minecraft:bow\",\n        copyModel = true,\n        description = { \"A quiet weapon for the dark.\" },\n    },\n})\n\nRegistry.Items:Register(\"watcher_bow\", {\n    kind = \"hunting_bow\",\n    onUse = function(context)\n        -- Fire an addon projectile or apply custom effects here.\n    end,\n})");
        docs.guideStep("Registry", "Resolve inherited type defaults",
                "ResolveType applies the same inheritance and merge rules used during registration. Use the category method when you already know the category, or the Registry method when the category comes from data.",
                "local Registry = Minecraft:GetService(\"Registry\")\n\nlocal definition = { kind = \"hunting_bow\", durability = 500 }\nlocal resolved = Registry.Items:ResolveType(definition)\n-- Equivalent when the category is dynamic:\nlocal resolvedAgain = Registry:ResolveType(\"items\", definition)\nprint(resolved.maxStackSize, resolved.durability)");
        docs.guideStep("Registry.Entities", "Reuse an entity archetype",
                "Entity type defaults use the same generic registry type system. The live entity still gets its own independent AI state and scripted callbacks.",
                "Registry:RegisterType(\"entities\", \"stalker_base\", {\n    Base = \"entity\",\n    Description = \"Shared physical defaults for stalker mobs.\",\n    Defaults = {\n        category = \"monster\",\n        width = 0.7,\n        height = 2.1,\n        attributes = { maxHealth = 36, movementSpeed = 0.28, attackDamage = 5 },\n    },\n})\n\nRegistry.Entities:Register(\"stalker\", {\n    RegistryType = \"stalker_base\",\n    onSpawn = function(self)\n        self:SetData({ alerted = false })\n    end,\n})");
        docs.guideStep("Registry.Items", "Register food and a native animated bow",
                "Food uses Minecraft's nutrition and saturation values, and onConsume runs once on the server. Set the item kind to bow to use Minecraft's native draw animation, charge timing, ammunition checks, and release behavior. Copying the vanilla bow model preserves its pull stages.",
                "Registry.Items:Register(\"haunted_ration\", {\n    food = { nutrition = 6, saturation = 0.8, alwaysEdible = true },\n    onConsume = function(context)\n        if context.Player then\n            context.Player:SendMessage(\"The ration tastes strangely warm.\")\n        end\n    end,\n})\n\nRegistry.Items:Register(\"ember_bow\", {\n    kind = \"bow\",\n    copy = \"minecraft:bow\", copyModel = true,\n    durability = 384, maxStackSize = 1,\n    description = \"A native animated bow with Minecraft arrow handling.\",\n})");
        docs.guideStep("Registry.Entities", "Choose the mob's type settings",
                "Start with a Lua definition for the shared entity type. Each spawned mob is a separate live ScriptedEntity instance.",
                "local stalkerDefinition = {\n    category = \"monster\",\n    attributes = { maxHealth = 30, movementSpeed = 0.28, attackDamage = 4 }\n}");
        docs.guideStep("Registry.Entities", "Give each mob an inherited Lua class",
                "Define a class that extends ScriptedEntity, then set className on the entity definition. Every spawned mob of that type receives the registered subclass, so its methods and mixins are available to AI and lifecycle callbacks.",
                "Minecraft:RegisterClass(\"Stalker\", \"ScriptedEntity\", \"A scripted stalker mob.\")\nMinecraft:RegisterClassFunction(\"Stalker\", \"Investigate\", function(self, x, y, z)\n    return self:MoveTo(x, y, z, 1.1)\nend, \"Moves the stalker to investigate a position.\")\n\nlocal stalkerDefinition = {\n    className = \"Stalker\",\n    AI = {\n        initial = \"Search\",\n        states = {\n            Search = {\n                update = function(self)\n                    local target = self:FindNearestPlayer(16, true)\n                    if target then\n                        self:Investigate(target:GetPositionX(), target:GetPositionY(), target:GetPositionZ())\n                        return \"Chase\"\n                    end\n                end\n            }\n        }\n    }\n}\n\nRegistry.Entities:Register(\"stalker\", stalkerDefinition)");
        docs.guideStep("Registry.Entities", "Give the live entity behavior",
                "Add AI to the definition before registering it. Callbacks receive the actual mob as self, with ScriptedEntity, LivingEntity, and Entity methods. Keep decisions in state updates; navigation continues between AI updates.",
                "stalkerDefinition.AI = {\n    initial = \"Search\",\n    states = {\n        Search = {\n            update = function(self, ai)\n                local player = self:FindNearestPlayer(16, true)\n                if player then\n                    self:AttackTarget(player)\n                    return \"Chase\"\n                end\n            end\n        },\n        Chase = {\n            update = function(self, ai)\n                local target = self:GetTarget()\n                if not target or not target:GetIsAlive() then\n                    self:AttackTarget(nil)\n                    return \"Search\"\n                end\n                self:LookAt(target.PositionX, target.PositionY, target.PositionZ)\n                self:MoveTo(target.PositionX, target.PositionY, target.PositionZ, 1.0)\n                if self:DistanceTo(target) <= 2.5 then\n                    self:MeleeAttack(target, 24)\n                end\n            end\n        }\n    }\n}\nRegistry.Entities:Register(\"stalker\", stalkerDefinition)");
        docs.guideStep("Registry.Entities", "Spawn an instance",
                "Spawn the registered type on the server through EntityService. The returned entity is managed by Minecraft and uses the callbacks registered for its type. BehaviorKey identifies the scripted definition; the inherited Type property keeps its Minecraft entity type meaning.",
                "local EntityService = Minecraft:GetService(\"EntityService\")\nlocal stalker = EntityService:Spawn(\"myaddon:stalker\", Vector3.new(12, 64, -8))\nif stalker then\n    print(stalker.BehaviorKey, stalker.Type)\n    stalker:SetData({ alerted = true })\nend");
        docs.guideStep("ScriptedEntity", "Handle damage and interaction",
                "Entity callbacks receive the live mob as self. onHurt runs after Minecraft accepts the damage, and onDeath receives the damage source id. Return false from onInteract to let Minecraft try its normal interaction; true or no return value consumes the interaction.",
                "stalkerDefinition.onHurt = function(self, amount, source)\n    print(self.Name, \"took\", amount, \"damage from\", source)\nend\n\nstalkerDefinition.onDeath = function(self, source)\n    print(self.Name, \"died to\", source)\nend\n\nstalkerDefinition.onInteract = function(self, player, hand)\n    self:SetAIState(\"Chase\")\n    return true\nend");
        docs.clazz("Block", "MLuaBlockWrapper",
                "A live block instance at a position in a Minecraft world.",
                "classes/block.html", "Instance");
        docs.method("Block", "Block:GetClassName(): string", "Returns the registered Lua class name of this block instance, including an addon subclass.", "", "string", "Shared", "");
        docs.clazz("BlockEntity", "MLuaBlockWrapper",
                "A live block entity attached to a block position, with persistent state and optional inventory.",
                "classes/block-entity.html", "Instance");
        docs.method("BlockEntity", "BlockEntity:GetPosition(): Vector3", "Returns this block entity's world position.", "", "Vector3", "Shared", "");
        docs.method("BlockEntity", "BlockEntity:GetBlock(): Block", "Returns the live block that owns this block entity.", "", "Block", "Shared", "");
        docs.method("BlockEntity", "BlockEntity:GetType(): string", "Returns this block entity type's registry identifier.", "", "string", "Shared", "");
        docs.method("BlockEntity", "BlockEntity:GetNbt(): Nbt", "Returns this block entity's live NBT data.", "", "Nbt", "Shared", "");
        docs.method("BlockEntity", "BlockEntity:SetNbt(keyOrValue: string|table|Nbt, value?: any)", "Writes one NBT value or replaces the full data compound.", "key and value, or a replacement table or Nbt value", "void", "Shared", "");
        docs.method("BlockEntity", "BlockEntity:GetContainer(): Container|nil", "Returns the same live Container exposed by Block.Container, when available.", "", "Container|nil", "Shared", "");
        docs.method("BlockEntity", "BlockEntity:GetInventory(): Container|nil", "Alias for GetContainer.", "", "Container|nil", "Shared", "");
        docs.method("BlockEntity", "BlockEntity:HasContainer(): boolean", "Whether this block entity owns a live Container.", "", "boolean", "Shared", "");
        docs.method("BlockEntity", "BlockEntity:SetChanged()", "Marks this block entity's saved state as changed.", "", "void", "Shared", "");
        docs.property("BlockEntity", "blockEntity.Position", "Vector3", "This block entity's world position.", "shared:Shared");
        docs.property("BlockEntity", "blockEntity.Block", "Block", "The live block that owns this block entity.", "shared:Shared");
        docs.property("BlockEntity", "blockEntity.Type", "string", "This block entity type's registry identifier.", "shared:Shared");
        docs.property("BlockEntity", "blockEntity.Nbt", "Nbt|table", "The live NBT view; assign a table or Nbt value to replace its data.", "shared:Shared");
        docs.property("BlockEntity", "blockEntity.Container", "Container|nil", "The same live Container exposed by Block.Container, when available.", "shared:Shared");
        docs.property("BlockEntity", "blockEntity.Inventory", "Container|nil", "Alias for blockEntity.Container.", "shared:Shared");
        docs.property("BlockEntity", "blockEntity.HasContainer", "boolean", "Whether this block entity owns a live Container.", "shared:Shared");
        docs.guideStep("BlockEntity", "Extend a block with saved state",
                "Register a Lua class under BlockEntity and assign it in the block definition. The nested onLoad callback receives the live subclass instance and a context table when Minecraft loads the block entity on the server. Retrieve the same live instance from Block:GetBlockEntity().",
                "Minecraft:RegisterClass(\"RuneChestEntity\", \"BlockEntity\", \"Stores a rune chest's state.\")\nMinecraft:RegisterClassFunction(\"RuneChestEntity\", \"SetCharge\", function(self, charge)\n    self.Nbt.charge = charge\nend, \"Saves this block's charge value.\")\nMinecraft:RegisterClassFunction(\"RuneChestEntity\", \"GetCharge\", function(self)\n    return self.Nbt.charge or 0\nend, \"Returns the saved charge value.\")\n\nRegistry.Blocks:Register(\"rune_chest\", {\n    container = { Rows = 3 },\n    blockEntity = {\n        className = \"RuneChestEntity\",\n        onLoad = function(self, context)\n            self.Nbt.charge = self.Nbt.charge or 0\n            print(context.World, context.Position)\n        end\n    }\n})\n\nlocal World = Minecraft:GetService(\"World\")\nlocal block = World:GetBlock(position)\nlocal entity = block:GetBlockEntity()\nentity:SetCharge(12)");
        docs.guideStep("Block", "Inspect a block from the world",
                "World:GetBlock returns a live instance of Block or the addon subclass registered for that block. Use its inherited methods and GetClassName to inspect its class.",
                "local World = Minecraft:GetService(\"World\")\nlocal block = World:GetBlock(Vector3.new(0, 64, 0))\nprint(block.Id, block:GetClassName())\nblock:Break(false)");
        docs.guideStep("BlockItem", "Extend the item form of a block",
                "A block item is an Item subclass. Register its class under BlockItem and pass that class to RegisterItem; callbacks receive the same live subclass.",
                "Minecraft:RegisterClass(\"SignalBlockItem\", \"BlockItem\", \"The item form of a signal block.\")\nMinecraft:RegisterClassFunction(\"SignalBlockItem\", \"IsSignalBlock\", function(self)\n    return self:GetId() == \"myaddon:signal_block\"\nend, \"Checks which registered block this item places.\")\n\nRegistry.Blocks:Register(\"signal_block\", {})\nRegistry.Blocks:RegisterItem(\"signal_block\", { className = \"SignalBlockItem\" })");
        docs.clazz("ScriptedEntity", "MLuaScriptedEntity",
                "A registered Lua-controlled mob with navigation, sensing, combat, and persistent AI data.",
                "classes/scripted-entity.html", "LivingEntity");
        docs.clazz("RegistryEntry", "MLuaRegistryEntry",
                "A live view of a namespaced key in one of MLua's supported Minecraft registries.",
                "classes/registry-entry.html", "Instance");
    }

    private static void contributeDefinitionSchemas(MLuaDocBuilder docs) {
        schema(docs, "ItemDefinition", "Options and callbacks for a registered item.",
            "onUse?", "fun(context: ItemUseContext): boolean", "Runs when a player uses the item.",
            "onUseOnBlock?", "fun(context: ItemUseOnBlockContext): boolean", "Runs when a player uses the item on a block.",
            "onInventoryTick?", "fun(context: ItemInventoryTickContext)", "Runs while the item is in an inventory.",
            "onCrafted?", "fun(context: ItemCraftedContext)", "Runs when the item is crafted.",
            "onConsume?", "fun(context: ItemConsumeContext)", "Runs once on the server when eating finishes.",
            "onAttackEntity?", "fun(context: ItemAttackEntityContext): boolean", "Runs when the item attacks an entity.",
            "model?", "string", "Model resource identifier.", "texture?", "string", "Item texture resource identifier.",
            "copy?", "string", "Vanilla item whose behavior or properties are copied.",
            "copyModel?", "string", "Whether to copy the source item's model.",
            "food?", "FoodDefinition", "Food properties for edible items.",
            "className?", "string", "Registered Lua class inheriting from Item.");
        schema(docs, "FoodDefinition", "Nutrition and eating behavior for an item.",
            "nutrition?", "number", "Hunger points restored.", "saturation?", "number", "Saturation modifier.",
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
        schema(docs, "BlockDefinition", "Properties and callbacks for a registered block.",
            "onRightClick?", "fun(player: Player, x: number, y: number, z: number): boolean", "Handles a right-click interaction.",
            "onPlace?", "fun(player: Player, x: number, y: number, z: number)", "Runs when the block is placed.",
            "onBreak?", "fun(player: Player, x: number, y: number, z: number)", "Runs when the block is broken.",
            "onStepOn?", "fun(entity: Entity, x: number, y: number, z: number)", "Runs when an entity steps on the block.",
            "onEntityInside?", "fun(entity: Entity, x: number, y: number, z: number)", "Runs while an entity intersects the block.",
            "onNeighborChanged?", "fun(x: number, y: number, z: number, neighborId: string)", "Runs when a neighboring block changes.",
            "onRemove?", "fun(x: number, y: number, z: number)", "Runs when the block is removed.",
            "onRandomTick?", "fun(x: number, y: number, z: number)", "Runs on a random block tick.",
            "onFallOn?", "fun(entity: Entity, x: number, y: number, z: number, distance: number)", "Handles an entity landing on the block.",
            "onProjectileHit?", "fun(projectile: Entity, x: number, y: number, z: number)", "Runs when a projectile hits the block.",
            "hardness?", "number", "Block mining hardness.", "resistance?", "number", "Explosion resistance.",
            "lightLevel?", "number", "Emitted light level.", "mineableWith?", "string", "Required tool group.",
            "requiredToolTier?", "number", "Minimum tool tier.",
            "className?", "string", "Registered Lua class inheriting from Block.",
            "blockEntity?", "BlockEntityDefinition", "Lua block entity class and behavior settings.",
            "blockEntityClass?", "string", "Registered Lua class inheriting from BlockEntity.");
        schema(docs, "BlockEntityDefinition", "Lua class and load behavior for a block's live block entity.",
            "className?", "string", "Registered Lua class inheriting from BlockEntity.",
            "onLoad?", "fun(self: BlockEntity, context: table)", "Runs server-side when Minecraft loads the block entity, including when its chunk loads.");
        schema(docs, "BlockItemDefinition", "Options and callbacks for a block's item form.",
            "className?", "string", "Registered Lua class inheriting from BlockItem.",
            "onUse?", "fun(context: ItemUseContext): boolean", "Runs when a player uses this block item.",
            "onUseOnBlock?", "fun(context: ItemUseOnBlockContext): boolean", "Runs when this item is used on a block.");
        schema(docs, "EntityDefinition", "Properties, callbacks, and scripted AI for a registered entity.",
            "onTick?", "fun(entity: Entity)", "Runs on an entity tick.",
            "onHurt?", "fun(self: Entity, amount: number, source: string)", "Runs after damage is accepted.",
            "onDeath?", "fun(self: Entity, source: string)", "Runs when the entity dies.",
            "AI?", "AIDefinition[]", "Scripted AI behavior definitions.",
            "onSpawn?", "fun(entity: Entity)", "Runs when the entity spawns.",
            "onInteract?", "fun(self: Entity, player: Player, hand: string): boolean", "Handles player interaction.",
            "onRemove?", "fun(entity: Entity)", "Runs when the entity is removed.",
            "attributes?", "table<string, number>", "Entity attribute values.", "width?", "number", "Entity width.",
            "height?", "number", "Entity height.", "trackingRange?", "number", "Network tracking range.",
            "updateInterval?", "number", "Network update interval.", "shadowRadius?", "number", "Rendered shadow radius.",
            "category?", "string", "Minecraft entity category.", "texture?", "string", "Entity texture resource.",
            "model?", "string", "Entity model resource.", "className?", "string", "Registered Lua class inheriting from ScriptedEntity.");
        schema(docs, "WorldGenerationFeatureDefinition", "Adds one registered placed feature to selected biomes.",
            "feature", "string", "Placed feature identifier.", "biomes", "string|string[]", "Biome identifiers or #tag selectors.",
            "step", "string", "Minecraft generation decoration step.");
        schema(docs, "SpawnEggDefinition", "Appearance and item callbacks for a registered entity spawn egg.",
            "entity?", "string", "Entity type identifier spawned by the egg.", "primaryColor?", "number", "Primary egg color.",
            "secondaryColor?", "number", "Secondary egg color.", "description?", "string|string[]", "Tooltip description lines.",
            "tooltip?", "string|string[]", "Additional tooltip lines.",
            "onUse?", "fun(player: Player, item: Item, hand: string): boolean", "Handles use in the air.",
            "onUseOnBlock?", "fun(player: Player, item: Item, x: number, y: number, z: number): boolean", "Handles use on a block.",
            "onInventoryTick?", "fun(player: Player, item: Item, slot: number, selected: boolean)", "Runs while the egg is in an inventory.",
            "onCrafted?", "fun(player: Player, item: Item)", "Runs when the egg is crafted.",
            "onConsume?", "fun(player: Player, item: Item)", "Runs when the egg is consumed.",
            "onAttackEntity?", "fun(player: Player, item: Item, target: Entity): boolean", "Handles an attack with the egg.");
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
            "isBeneficial?", "boolean", "Whether the effect is beneficial.");
        schema(docs, "PotionDefinition", "Effect and timing options for a registered potion.",
            "effect", "string", "Mob effect identifier.", "duration?", "number", "Effect duration in ticks.",
            "amplifier?", "number", "Effect amplifier.", "ambient?", "boolean", "Whether the effect is ambient.",
            "showParticles?", "boolean", "Whether effect particles are shown.");
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
        docs.signature("Registry.Items", "Registry.Items:Register(id: string, props: ItemDefinition)", null);
        docs.signature("Registry.Items", "Registry.Items:GetId(name: string)", null);
        docs.signature("Registry.Items", "Registry.Items:Exists(name: string)", null);
        docs.signature("Registry.Blocks", "Registry.Blocks:Register(id: string, props: BlockDefinition)", null);
        docs.signature("Registry.Blocks", "Registry.Blocks:RegisterItem(id: string, definition?: BlockItemDefinition)", null);
        docs.signature("Registry.Blocks", "Registry.Blocks:RegisterState(id: string, blockState: string)", null);
        docs.signature("Registry.Blocks", "Registry.Blocks:GetId(name: string)", null);
        docs.signature("Registry.Blocks", "Registry.Blocks:Exists(name: string)", null);
        docs.signature("Registry.Entities", "Registry.Entities:Register(id: string, props: EntityDefinition)", null);
        docs.signature("Registry.Entities", "Registry.Entities:RegisterSpawnEgg(id: string, props: SpawnEggDefinition)", null);
        docs.signature("Registry.Entities", "Registry.Entities:Exists(name: string)", null);
        docs.signature("Registry.Effects", "Registry.Effects:Register(id: string, props: EffectDefinition)", null);
        docs.signature("Registry.Potions", "Registry.Potions:Register(id: string, props: PotionDefinition)", null);
        docs.signature("Registry.Particles", "Registry.Particles:Register(id: string, props: ParticleDefinition)", null);
        docs.signature("Registry.Recipes", "Registry.Recipes:Register(id: string, definition: table|string)", "Registers a recipe JSON object or JSON string.");
        docs.signature("Registry.Recipes", "Registry.Recipes:RegisterShaped(id: string, definition: RecipeDefinition)", null);
        docs.signature("Registry.Recipes", "Registry.Recipes:RegisterShapeless(id: string, definition: RecipeDefinition)", null);
        docs.signature("Registry.Recipes", "Registry.Recipes:RegisterSmelting(id: string, definition: RecipeDefinition)", null);
        docs.signature("Registry.Recipes", "Registry.Recipes:RegisterStonecutting(id: string, definition: RecipeDefinition)", null);
        docs.signature("Registry.Loot", "Registry.Loot:Register(definition: LootDefinition)", null);
        docs.signature("Registry.Loot", "Registry.Loot:RegisterEntity(id: string, props: LootDefinition)", null);
        docs.signature("Registry.Loot", "Registry.Loot:RegisterSimpleBlock(id: string, itemId: string)", null);
        docs.signature("Registry.Loot", "Registry.Loot:RegisterSelfBlock(id: string)", null);
        docs.signature("Registry.Loot", "Registry.Loot:RegisterSilkTouchBlock(id: string, itemId: string)", null);
        docs.signature("Registry.Loot", "Registry.Loot:RegisterOreBlock(id: string, dropId: string, minCount: number, maxCount: number)", null);
        docs.signature("Registry.Tags", "Registry.Tags:Register(definition: TagDefinition)", null);
        docs.signature("Registry.Tags", "Registry.Tags:RegisterItem(id: string, items: string[])", null);
        docs.signature("Registry.Tags", "Registry.Tags:RegisterBlock(id: string, blocks: string[])", null);
        docs.signature("Registry.Tags", "Registry.Tags:RegisterEntity(id: string, entities: string[])", null);
        docs.signature("Registry.Tags", "Registry.Tags:AddToItem(id: string, items: string[])", null);
        docs.signature("Registry.Tags", "Registry.Tags:AddToBlock(id: string, blocks: string[])", null);
        docs.signature("Registry.Tags", "Registry.Tags:AddToEntity(id: string, entities: string[])", null);
        docs.signature("Registry.Projectiles", "Registry.Projectiles:Register(id: string, props: ProjectileDefinition)", null);
        docs.signature("Registry.Tabs", "Registry.Tabs:Register(id: string, props: CreativeTabDefinition)", null);
        docs.signature("Registry.Tabs", "Registry.Tabs:GetId(name: string)", null);
        docs.signature("Registry.Config", "Registry.Config:Register(scope: string, id: string, config: ConfigDefinition)", null);
        docs.signature("Registry.Config", "Registry.Config:RegisterCommon(id: string, config: ConfigDefinition)", null);
        docs.signature("Registry.Config", "Registry.Config:RegisterClient(id: string, config: ConfigDefinition)", null);
        docs.signature("Registry.Config", "Registry.Config:RegisterServer(id: string, config: ConfigDefinition)", null);
        docs.signature("Registry.DamageSource", "Registry.DamageSource:Register(name: string, props: DamageSourceDefinition)", null);
    }

    private static void schema(MLuaDocBuilder docs, String name, String description, String... fields) {
        schemaWithParent(docs, name, null, description, fields);
    }

    private static void schemaWithParent(MLuaDocBuilder docs, String name, String parent,
                                         String description, String... fields) {
        if (fields.length % 3 != 0) throw new IllegalArgumentException("Schema fields must be name, type, description triples: " + name);
        docs.type(name, parent, description);
        for (int index = 0; index < fields.length; index += 3) {
            docs.field(name, fields[index], fields[index + 1], fields[index + 2]);
        }
    }
}
