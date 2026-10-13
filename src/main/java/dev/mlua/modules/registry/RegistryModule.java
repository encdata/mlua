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
        MLuaContentTypeRegistry.clearOwner(runtime, ownerModId);
        MLuaBehaviorRegistry.clearOwner(ownerModId);
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
            .description("Registers and queries addon content across Minecraft registries, including items, blocks, entities, effects, recipes, loot, tags, projectiles, and reusable content types.");
        registry.function("Register", "Routes a named content definition through its typed registry.")
            .function("RegisterType", "Registers an addon-scoped reusable content type with inherited defaults.")
            .function("ResolveType", "Returns a definition with its reusable type defaults merged in.")
            .function("GetKinds", "Lists the registry kinds supported by this loader.")
            .function("GetTypes", "Lists reusable implementation and addon-defined content types for a category.")
            .function("GetRegistered", "Returns identifiers registered in a supported Minecraft registry.")
            .function("GetEntry", "Creates a live RegistryEntry view for a kind and namespaced identifier.")
            .function("Exists", "Checks whether an identifier currently resolves in its registry.");
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

        contributeDefinitionSchemas(docs);
        contributeSignatures(docs);
        docs.signature("Block", "Block:GetInventory()", "Container");
        docs.service("Registry", "MLuaRegistryService",
                "Registers and queries addon content across Minecraft registries, including items, blocks, entities, effects, recipes, loot, tags, projectiles, and reusable content types.",
                "services/registry.html");
        docs.guideStep("Registry.Containers", "Register a reusable container type",
                "Set the number of chest rows and the title once, then reference the type from any scripted block.",
                "local Registry = Minecraft:GetService(\"Registry\")\n\nRegistry.Containers:Register(\"supply_crate\", {\n    Rows = 3,\n    Title = \"Supply Crate\",\n})");
        docs.guideStep("Registry", "Use the shared registration entry point",
                "Registry:Register routes definitions through the typed registries for items, blocks, entities, effects, potions, particles, containers, projectiles, tabs, recipes, loot, tags, common config, and damage sources. Use block_item after registering its block; register a tag as Registry:Register(\"tag\", kind, name, definition). RegisterType defines reusable addon-scoped defaults for each category. Items select them with kind, blocks with type, and other categories with RegistryType. Use Registry.ContentTypes for reusable defaults in your own data domains. GetRegistered returns IDs visible for a Minecraft registry kind.",
                "local Registry = Minecraft:GetService(\"Registry\")\n\nRegistry:Register(\"item\", \"flare_gun\", {\n    kind = \"firearm\",\n    maxStackSize = 1,\n    onUse = function(context)\n        -- Use the live player and projectile services here.\n    end,\n})\n\nfor _, id in ipairs(Registry:GetRegistered(\"items\")) do\n    print(id)\nend");
        docs.guideStep("Registry.ContentTypes", "Define types for addon data",
                "ContentTypes lets an addon reuse inherited defaults and descriptions outside Minecraft's built-in categories. Give each domain a stable name, register its types during the registry phase, then resolve definitions with RegistryType. A definition without a selector passes through unchanged; a named type that is missing from that domain raises an error.",
                "local Registry = Minecraft:GetService(\"Registry\")\n\nRegistry.ContentTypes:Register(\"horror.weapon\", \"sidearm\", {\n    Base = \"default\",\n    Description = \"Shared defaults for compact horror weapons.\",\n    Defaults = { Damage = 6, Magazine = { Size = 8, ReloadTicks = 30 } },\n})\n\nRegistry.ContentTypes:Register(\"horror.weapon\", \"revolver\", {\n    Base = \"sidearm\",\n    Description = \"A slow, powerful revolver.\",\n    Defaults = { Damage = 12, Magazine = { Size = 6 } },\n})\n\nlocal weapon = Registry.ContentTypes:Resolve(\"horror.weapon\", {\n    RegistryType = \"revolver\",\n    Damage = 14,\n})\nprint(weapon.Damage, weapon.Magazine.Size, weapon.Magazine.ReloadTicks)");
        docs.guideStep("Registry", "Work with registry entries generically",
                "Native registry tables share GetId, GetEntry, and Exists for items, blocks, entities, effects, potions, particles, and creative tabs. GetEntry returns the same live RegistryEntry class as Registry:GetEntry and RegistryEntry.new, keeping the normalized kind and namespaced ID together.",
                "local Registry = Minecraft:GetService(\"Registry\")\nlocal bow = Registry.Items:GetEntry(\"minecraft:bow\")\nlocal stone = Registry.Blocks:GetEntry(\"minecraft:stone\")\nlocal sameShape = RegistryEntry.new(\"items\", \"minecraft:bow\")\n\nprint(Registry.Items:GetId(\"bow\")) -- resolves to the addon's namespace\nprint(bow:GetKind(), bow:GetId())\nif stone:Exists() then\n    print(stone:GetNamespace(), stone:GetPath())\nend\n\nfor _, kind in ipairs(Registry:GetKinds()) do\n    print(kind)\nend");
        docs.guideStep("Registry.Containers", "Attach storage to a scripted block",
                "The block entity keeps its slots on the server and opens a vanilla chest screen when the player interacts with the block.",
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
        docs.guideStep("Registry.Entities", "Give the live entity behavior",
                "Add AI to the definition before registering it. Callbacks receive the actual mob as self, with ScriptedEntity, LivingEntity, and Entity methods. Keep decisions in state updates; navigation continues between AI updates.",
                "stalkerDefinition.AI = {\n    initial = \"Search\",\n    states = {\n        Search = {\n            update = function(self, ai)\n                local player = self:FindNearestPlayer(16, true)\n                if player then\n                    self:AttackTarget(player)\n                    return \"Chase\"\n                end\n            end\n        },\n        Chase = {\n            update = function(self, ai)\n                local target = self:GetTarget()\n                if not target or not target:GetIsAlive() then\n                    self:AttackTarget(nil)\n                    return \"Search\"\n                end\n                self:LookAt(target.PositionX, target.PositionY, target.PositionZ)\n                self:MoveTo(target.PositionX, target.PositionY, target.PositionZ, 1.0)\n                if self:DistanceTo(target) <= 2.5 then\n                    self:MeleeAttack(target, 24)\n                end\n            end\n        }\n    }\n}\nRegistry.Entities:Register(\"stalker\", stalkerDefinition)");
        docs.guideStep("Registry.Entities", "Spawn an instance",
                "Spawn the registered type on the server through EntityService. The returned entity is managed by Minecraft and uses the callbacks registered for its type. BehaviorKey identifies the scripted definition; the inherited Type property keeps its Minecraft entity type meaning.",
                "local EntityService = Minecraft:GetService(\"EntityService\")\nlocal stalker = EntityService:Spawn(\"myaddon:stalker\", { X = 12, Y = 64, Z = -8 })\nif stalker then\n    print(stalker.BehaviorKey, stalker.Type)\n    stalker:SetData({ alerted = true })\nend");
        docs.guideStep("ScriptedEntity", "Handle damage and interaction",
                "Entity callbacks receive the live mob as self. onHurt runs after Minecraft accepts the damage, and onDeath receives the damage source id. Return false from onInteract to let Minecraft try its normal interaction; true or no return value consumes the interaction.",
                "stalkerDefinition.onHurt = function(self, amount, source)\n    print(self.Name, \"took\", amount, \"damage from\", source)\nend\n\nstalkerDefinition.onDeath = function(self, source)\n    print(self.Name, \"died to\", source)\nend\n\nstalkerDefinition.onInteract = function(self, player, hand)\n    self:SetAIState(\"Chase\")\n    return true\nend");
        docs.clazz("Block", "MLuaBlockWrapper",
                "A live block instance at a position in a Minecraft world.",
                "classes/block.html", "Instance");
        docs.guideStep("Block", "Inspect a block from the world",
                "World:GetBlock returns a live Block wrapper at the requested position. Read its registered id and use its instance methods to change or break it.",
                "local World = Minecraft:GetService(\"World\")\nlocal block = World:GetBlock(Vector3.new(0, 64, 0))\nprint(block.Id, block.Name)\nblock:Break(false)");
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
            "food?", "FoodDefinition", "Food properties for edible items.");
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
            "Player", "Player|nil", "Player using the item.", "Entity", "Entity", "Target entity.",
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
            "requiredToolTier?", "number", "Minimum tool tier.");
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
            "model?", "string", "Entity model resource.");
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
        docs.signature("Registry.Blocks", "Registry.Blocks:RegisterItem(id: string)", null);
        docs.signature("Registry.Blocks", "Registry.Blocks:RegisterState(id: string, blockState: string)", null);
        docs.signature("Registry.Blocks", "Registry.Blocks:GetId(name: string)", null);
        docs.signature("Registry.Blocks", "Registry.Blocks:Exists(name: string)", null);
        docs.signature("Registry.Entities", "Registry.Entities:Register(id: string, props: EntityDefinition)", null);
        docs.signature("Registry.Entities", "Registry.Entities:RegisterSpawnEgg(id: string, props: SpawnEggDefinition)", null);
        docs.signature("Registry.Entities", "Registry.Entities:Exists(name: string)", null);
        docs.signature("Registry.Effects", "Registry.Effects:Register(id: string, props: EffectDefinition)", null);
        docs.signature("Registry.Potions", "Registry.Potions:Register(id: string, props: PotionDefinition)", null);
        docs.signature("Registry.Particles", "Registry.Particles:Register(id: string, props: ParticleDefinition)", null);
        docs.signature("Registry.Recipes", "Registry.Recipes:Register(definition: RecipeDefinition)", null);
        docs.signature("Registry.Recipes", "Registry.Recipes:RegisterShaped(id: string, props: RecipeDefinition)", null);
        docs.signature("Registry.Recipes", "Registry.Recipes:RegisterShapeless(id: string, props: RecipeDefinition)", null);
        docs.signature("Registry.Recipes", "Registry.Recipes:RegisterSmelting(id: string, props: RecipeDefinition)", null);
        docs.signature("Registry.Recipes", "Registry.Recipes:RegisterStonecutting(id: string, props: RecipeDefinition)", null);
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
