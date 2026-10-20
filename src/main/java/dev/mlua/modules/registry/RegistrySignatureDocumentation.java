package dev.mlua.modules.registry;

import dev.mlua.module.MLuaDocBuilder;

/** Documents Registry service and category signatures. */
final class RegistrySignatureDocumentation {
    private RegistrySignatureDocumentation() {}

    static void contribute(MLuaDocBuilder docs) {
        docs.register(MLuaDocBuilder.Registration.signature("Item", "Item.Properties()", "ItemProperties"));
        docs.register(MLuaDocBuilder.Registration.signature("Block", "Block.Properties()", "BlockProperties"));
        docs.register(MLuaDocBuilder.Registration.signature("Registry", "Registry:GetScriptLimits()", "ScriptDefinitionLimits"));
        docs.register(MLuaDocBuilder.Registration.type("ScriptDefinitionLimits", null,
            "Current usage and capacity for scripted behavior, Java content-class mappings, entity AI settings, containers, content types, and addon-defined registry entries."));
        docs.register(MLuaDocBuilder.Registration.field("ScriptDefinitionLimits", "ActiveBehaviors", "number",
            "Scripted behavior definitions currently registered across MLua."));
        docs.register(MLuaDocBuilder.Registration.field("ScriptDefinitionLimits", "AddonBehaviors", "number",
            "Scripted behavior definitions owned by the current addon."));
        docs.register(MLuaDocBuilder.Registration.field("ScriptDefinitionLimits", "MaxBehaviors", "number",
            "Maximum scripted behavior definitions across MLua."));
        docs.register(MLuaDocBuilder.Registration.field("ScriptDefinitionLimits", "MaxBehaviorsPerAddon", "number",
            "Maximum scripted behavior definitions owned by one addon."));
        docs.register(MLuaDocBuilder.Registration.field("ScriptDefinitionLimits", "MaxCallbacksPerDefinition", "number",
            "Maximum callbacks in one scripted content definition."));
        docs.register(MLuaDocBuilder.Registration.field("ScriptDefinitionLimits", "ActiveClassMappings", "number",
            "Java content-class mappings currently registered across MLua."));
        docs.register(MLuaDocBuilder.Registration.field("ScriptDefinitionLimits", "AddonClassMappings", "number",
            "Java content-class mappings owned by the current addon."));
        docs.register(MLuaDocBuilder.Registration.field("ScriptDefinitionLimits", "MaxClassMappings", "number",
            "Maximum Java content-class mappings across MLua."));
        docs.register(MLuaDocBuilder.Registration.field("ScriptDefinitionLimits", "MaxClassMappingsPerAddon", "number",
            "Maximum Java content-class mappings owned by one addon."));
        docs.register(MLuaDocBuilder.Registration.field("ScriptDefinitionLimits", "ActiveBlockContainers", "number",
            "Scriptable block containers currently registered across MLua."));
        docs.register(MLuaDocBuilder.Registration.field("ScriptDefinitionLimits", "AddonBlockContainers", "number",
            "Scriptable block containers owned by the current addon."));
        docs.register(MLuaDocBuilder.Registration.field("ScriptDefinitionLimits", "MaxBlockContainers", "number",
            "Maximum scriptable block containers across MLua."));
        docs.register(MLuaDocBuilder.Registration.field("ScriptDefinitionLimits", "ActiveContainerTypes", "number",
            "Reusable container types currently registered across MLua."));
        docs.register(MLuaDocBuilder.Registration.field("ScriptDefinitionLimits", "AddonContainerTypes", "number",
            "Reusable container types owned by the current addon."));
        docs.register(MLuaDocBuilder.Registration.field("ScriptDefinitionLimits", "MaxContainerTypes", "number",
            "Maximum reusable container types across MLua."));
        docs.register(MLuaDocBuilder.Registration.field("ScriptDefinitionLimits", "MaxContainersPerAddon", "number",
            "Maximum block containers or reusable container types in each collection owned by one addon."));
        docs.register(MLuaDocBuilder.Registration.field("ScriptDefinitionLimits", "MaxAllowedItemsPerRule", "number",
            "Maximum item identifiers in a container-wide or per-slot rule."));
        docs.register(MLuaDocBuilder.Registration.field("ScriptDefinitionLimits", "MaxAllowedItemReferencesPerContainer", "number",
            "Maximum combined item identifier references across all rules for one container."));
        docs.register(MLuaDocBuilder.Registration.field("ScriptDefinitionLimits", "MaxContainerTitleLength", "number",
            "Maximum character count for a scripted container title."));
        docs.register(MLuaDocBuilder.Registration.field("ScriptDefinitionLimits", "ActiveEntityAIConfigs", "number",
            "Scripted entity AI update intervals currently registered across MLua."));
        docs.register(MLuaDocBuilder.Registration.field("ScriptDefinitionLimits", "AddonEntityAIConfigs", "number",
            "Scripted entity AI update intervals owned by the current addon."));
        docs.register(MLuaDocBuilder.Registration.field("ScriptDefinitionLimits", "MaxEntityAIConfigs", "number",
            "Maximum scripted entity AI update intervals across MLua."));
        docs.register(MLuaDocBuilder.Registration.field("ScriptDefinitionLimits", "MaxEntityAIConfigsPerAddon", "number",
            "Maximum scripted entity AI update intervals owned by one addon."));
        docs.register(MLuaDocBuilder.Registration.field("ScriptDefinitionLimits", "MaxEntityAIStatesPerDefinition", "number",
            "Maximum named states in one scripted entity AI definition."));
        docs.register(MLuaDocBuilder.Registration.field("ScriptDefinitionLimits", "MaxEntityAITableEntries", "number",
            "Maximum total entries in the nested Lua tables of one scripted entity AI definition."));
        docs.register(MLuaDocBuilder.Registration.field("ScriptDefinitionLimits", "MaxEntityAITableDepth", "number",
            "Maximum nested table depth in one scripted entity AI definition."));
        docs.register(MLuaDocBuilder.Registration.field("ScriptDefinitionLimits", "MaxIdentifierLength", "number",
            "Maximum identifier length for scripted content definitions."));
        docs.register(MLuaDocBuilder.Registration.field("ScriptDefinitionLimits", "MaxBehaviorNameLength", "number",
            "Maximum length for a behavior callback name or scripted entity AI state name."));
        docs.register(MLuaDocBuilder.Registration.field("ScriptDefinitionLimits", "ActiveContentTypes", "number",
            "Reusable content types currently registered in this runtime."));
        docs.register(MLuaDocBuilder.Registration.field("ScriptDefinitionLimits", "AddonContentTypes", "number",
            "Reusable content types owned by the current addon."));
        docs.register(MLuaDocBuilder.Registration.field("ScriptDefinitionLimits", "MaxContentTypes", "number",
            "Maximum reusable content types in this runtime."));
        docs.register(MLuaDocBuilder.Registration.field("ScriptDefinitionLimits", "MaxContentTypesPerDomain", "number",
            "Maximum reusable content types in one content domain."));
        docs.register(MLuaDocBuilder.Registration.field("ScriptDefinitionLimits", "MaxContentTypeNameLength", "number",
            "Maximum qualified length for a reusable content type name or reference."));
        docs.register(MLuaDocBuilder.Registration.field("ScriptDefinitionLimits", "MaxContentTypeDescriptionLength", "number",
            "Maximum description length for a reusable content type."));
        docs.register(MLuaDocBuilder.Registration.field("ScriptDefinitionLimits", "MaxContentTypeBaseLength", "number",
            "Maximum base reference length for a reusable content type."));
        docs.register(MLuaDocBuilder.Registration.field("ScriptDefinitionLimits", "MaxContentTypeDefaultEntries", "number",
            "Maximum nested table entries stored in one reusable content type's defaults."));
        docs.register(MLuaDocBuilder.Registration.field("ScriptDefinitionLimits", "ActiveContentTypeDefaultTableEntries", "number",
            "Nested default table entries currently stored by reusable content types in this runtime."));
        docs.register(MLuaDocBuilder.Registration.field("ScriptDefinitionLimits", "AddonContentTypeDefaultTableEntries", "number",
            "Nested default table entries stored by reusable content types owned by the current addon."));
        docs.register(MLuaDocBuilder.Registration.field("ScriptDefinitionLimits", "MaxContentTypeDefaultTableEntriesPerAddon", "number",
            "Maximum nested default table entries stored by one addon across all its reusable content types."));
        docs.register(MLuaDocBuilder.Registration.field("ScriptDefinitionLimits", "MaxContentTypeDefaultTableEntriesTotal", "number",
            "Maximum nested default table entries stored by all reusable content types in this runtime."));
        docs.register(MLuaDocBuilder.Registration.field("ScriptDefinitionLimits", "MaxContentTypeTableDepth", "number",
            "Maximum nested table depth for reusable content type defaults."));
        docs.register(MLuaDocBuilder.Registration.field("ScriptDefinitionLimits", "MaxContentTypeOwnerLength", "number",
            "Maximum owner namespace length for reusable content types."));
        docs.register(MLuaDocBuilder.Registration.field("ScriptDefinitionLimits", "MaxContentTypeDomainLength", "number",
            "Maximum category domain length used to group reusable content types."));
        docs.register(MLuaDocBuilder.Registration.field("ScriptDefinitionLimits", "ActiveRegistryCategories", "number",
            "Addon-defined registry categories currently registered across MLua."));
        docs.register(MLuaDocBuilder.Registration.field("ScriptDefinitionLimits", "AddonRegistryCategories", "number",
            "Addon-defined registry categories owned by the current addon."));
        docs.register(MLuaDocBuilder.Registration.field("ScriptDefinitionLimits", "MaxRegistryCategories", "number",
            "Maximum addon-defined registry categories across MLua."));
        docs.register(MLuaDocBuilder.Registration.field("ScriptDefinitionLimits", "MaxRegistryCategoriesPerAddon", "number",
            "Maximum addon-defined registry categories owned by one addon."));
        docs.register(MLuaDocBuilder.Registration.field("ScriptDefinitionLimits", "ActiveRegistryDefinitions", "number",
            "Definitions currently stored in addon-defined registry categories."));
        docs.register(MLuaDocBuilder.Registration.field("ScriptDefinitionLimits", "AddonRegistryDefinitions", "number",
            "Definitions in addon-defined categories owned by the current addon."));
        docs.register(MLuaDocBuilder.Registration.field("ScriptDefinitionLimits", "ActiveRegistryDefinitionTableEntries", "number",
            "Unique nested table entries currently retained by addon-defined registry definitions."));
        docs.register(MLuaDocBuilder.Registration.field("ScriptDefinitionLimits", "AddonRegistryDefinitionTableEntries", "number",
            "Unique nested table entries retained by the current addon's registry definitions."));
        docs.register(MLuaDocBuilder.Registration.field("ScriptDefinitionLimits", "PendingRegistryDefinitions", "number",
            "Registry definitions currently being processed by registration callbacks."));
        docs.register(MLuaDocBuilder.Registration.field("ScriptDefinitionLimits", "AddonPendingRegistryDefinitions", "number",
            "Pending registry definitions owned by the current addon."));
        docs.register(MLuaDocBuilder.Registration.field("ScriptDefinitionLimits", "MaxRegistryDefinitions", "number",
            "Maximum stored definitions across addon-defined registry categories."));
        docs.register(MLuaDocBuilder.Registration.field("ScriptDefinitionLimits", "MaxRegistryDefinitionsPerCategory", "number",
            "Maximum stored and pending definitions in one addon-defined registry category."));
        docs.register(MLuaDocBuilder.Registration.field("ScriptDefinitionLimits", "MaxRegistryDefinitionsPerAddon", "number",
            "Maximum stored and pending definitions owned by one addon across all addon-defined registry categories."));
        docs.register(MLuaDocBuilder.Registration.field("ScriptDefinitionLimits", "MaxPendingRegistryDefinitionsPerCategory", "number",
            "Maximum simultaneous pending registration callbacks for one category."));
        docs.register(MLuaDocBuilder.Registration.field("ScriptDefinitionLimits", "MaxRegistryDefinitionIdLength", "number",
            "Maximum identifier length for a definition in an addon-defined category."));
        docs.register(MLuaDocBuilder.Registration.field("ScriptDefinitionLimits", "MaxRegistryDefinitionTableEntries", "number",
            "Maximum nested table entries stored in one addon-defined registry definition."));
        docs.register(MLuaDocBuilder.Registration.field("ScriptDefinitionLimits", "MaxRegistryDefinitionTableEntriesPerAddon", "number",
            "Maximum unique nested table entries retained by one addon's registry definitions."));
        docs.register(MLuaDocBuilder.Registration.field("ScriptDefinitionLimits", "MaxRegistryDefinitionTableEntriesTotal", "number",
            "Maximum unique nested table entries retained across addon-defined registry definitions."));
        docs.register(MLuaDocBuilder.Registration.field("ScriptDefinitionLimits", "MaxRegistryDefinitionTableDepth", "number",
            "Maximum nested table depth in an addon-defined registry definition."));
        docs.register(MLuaDocBuilder.Registration.signature("Registry", "Registry:Get(category: string, id: string)", "RegistryEntry|RegistryDefinition|GeneratedDataResource|GeneratedAssetResource|ContainerType|ProjectileType|table|nil"));
        docs.register(MLuaDocBuilder.Registration.signature("Registry", "Registry:GetAll(category: string, namespace?: string)", "table<string, RegistryEntry|RegistryDefinition|GeneratedDataResource|GeneratedAssetResource|ContainerType|ProjectileType|table>"));
        docs.register(MLuaDocBuilder.Registration.signature("Registry", "Registry:GetEntry(category: string, id: string)", "RegistryEntry|RegistryDefinition|GeneratedDataResource|GeneratedAssetResource|ContainerType|ProjectileType|nil"));
        docs.register(MLuaDocBuilder.Registration.signature("Registry", "Registry:GetEntries(category: string, namespace?: string)", "(RegistryEntry|RegistryDefinition|GeneratedDataResource|GeneratedAssetResource|ContainerType|ProjectileType)[]"));
        docs.register(MLuaDocBuilder.Registration.signature("Registry", "Registry:GetEntry(\"datapack\", resourceType: string, id: string): GeneratedDataResource?", null));
        docs.register(MLuaDocBuilder.Registration.signature("Registry", "Registry:GetEntries(\"datapack\", resourceType: string, namespace?: string): GeneratedDataResource[]", null));
        docs.register(MLuaDocBuilder.Registration.signature("Registry", "Registry:GetAll(\"datapack\", resourceType: string, namespace?: string): table<string, GeneratedDataResource>", null));
        docs.register(MLuaDocBuilder.Registration.signature("Registry", "Registry:Get(\"worldgen\", resourceType: string, id: string): GeneratedDataResource?", null));
        docs.register(MLuaDocBuilder.Registration.signature("Registry", "Registry:GetEntry(\"worldgen\", resourceType: string, id: string): GeneratedDataResource?", null));
        docs.register(MLuaDocBuilder.Registration.signature("Registry", "Registry:GetEntries(\"worldgen\", resourceType: string, namespace?: string): GeneratedDataResource[]", null));
        docs.register(MLuaDocBuilder.Registration.signature("Registry", "Registry:GetAll(\"worldgen\", resourceType: string, namespace?: string): table<string, GeneratedDataResource>", null));
        docs.register(MLuaDocBuilder.Registration.signature("Registry", "Registry:Get(\"recipes\", id: string): GeneratedAssetResource?", null));
        docs.register(MLuaDocBuilder.Registration.signature("Registry", "Registry:GetEntry(\"recipes\", id: string): GeneratedAssetResource?", null));
        docs.register(MLuaDocBuilder.Registration.signature("Registry", "Registry:GetIds(\"recipes\", namespace?: string): string[]", null));
        docs.register(MLuaDocBuilder.Registration.signature("Registry", "Registry:GetEntries(\"recipes\", namespace?: string): GeneratedAssetResource[]", null));
        docs.register(MLuaDocBuilder.Registration.signature("Registry", "Registry:GetAll(\"recipes\", namespace?: string): table<string, GeneratedAssetResource>", null));
        docs.register(MLuaDocBuilder.Registration.signature("Registry", "Registry:Get(\"loot\", id: string): GeneratedAssetResource?", null));
        docs.register(MLuaDocBuilder.Registration.signature("Registry", "Registry:GetEntry(\"loot\", id: string): GeneratedAssetResource?", null));
        docs.register(MLuaDocBuilder.Registration.signature("Registry", "Registry:GetIds(\"loot\", namespace?: string): string[]", null));
        docs.register(MLuaDocBuilder.Registration.signature("Registry", "Registry:GetEntries(\"loot\", namespace?: string): GeneratedAssetResource[]", null));
        docs.register(MLuaDocBuilder.Registration.signature("Registry", "Registry:GetAll(\"loot\", namespace?: string): table<string, GeneratedAssetResource>", null));
        docs.register(MLuaDocBuilder.Registration.signature("Registry", "Registry:GetIds(\"worldgen\", resourceType: string, namespace?: string): string[]", null));
        docs.register(MLuaDocBuilder.Registration.signature("Registry", "Registry:Exists(\"worldgen\", resourceType: string, id: string): boolean", null));
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
        docs.register(MLuaDocBuilder.Registration.signature("Registry.Blocks", "Registry.Blocks:RegisterState(id: string, blockState: table|string)", "string"));
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
        docs.register(MLuaDocBuilder.Registration.signature("ItemProperties", "ItemProperties:Description(text: string|string[])", "ItemProperties"));
        docs.register(MLuaDocBuilder.Registration.signature("ItemProperties", "ItemProperties:Copy(id: string)", "ItemProperties"));
        docs.register(MLuaDocBuilder.Registration.signature("ItemProperties", "ItemProperties:CopyModel(enabled: boolean)", "ItemProperties"));
        docs.register(MLuaDocBuilder.Registration.signature("ItemProperties", "ItemProperties:Texture(resource: string|table)", "ItemProperties"));
        docs.register(MLuaDocBuilder.Registration.signature("ItemProperties", "ItemProperties:Model(resource: string)", "ItemProperties"));
        docs.register(MLuaDocBuilder.Registration.signature("BlockProperties", "BlockProperties:Description(text: string|string[])", "BlockProperties"));
        docs.register(MLuaDocBuilder.Registration.signature("BlockProperties", "BlockProperties:Copy(id: string)", "BlockProperties"));
        docs.register(MLuaDocBuilder.Registration.signature("BlockProperties", "BlockProperties:CopyModel(enabled: boolean)", "BlockProperties"));
        docs.register(MLuaDocBuilder.Registration.signature("BlockProperties", "BlockProperties:Texture(resource: string|table)", "BlockProperties"));
        docs.register(MLuaDocBuilder.Registration.signature("BlockProperties", "BlockProperties:Model(resource: string)", "BlockProperties"));
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
        String[] blockPropertyMethods = {"Strength(hardness: number, resistance?: number)",
            "Container(definition: ContainerType|string|table)", "BlockEntity(definition: table)",
            "Resistance(amount: number)",
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
            "TrackingRange(chunks: number)", "UpdateInterval(ticks: number)", "AIInterval(ticks: number)", "ShadowRadius(radius: number)",
            "Texture(resource: string|table)", "Model(resource: string)", "Attributes(values: table)",
            "MaxHealth(value: number)", "MovementSpeed(value: number)", "AttackDamage(value: number)",
            "Armor(value: number)", "ArmorToughness(value: number)", "AttackSpeed(value: number)",
            "KnockbackResistance(value: number)", "FollowRange(value: number)", "AI(callback: function|table)",
            "OnTick(callback: function)", "OnHurt(callback: function)", "OnDeath(callback: function)",
            "OnSpawn(callback: function)", "OnInteract(callback: function)",
            "OnTargetChanged(callback: function)", "OnRemove(callback: function)"};
        for (String method : entityPropertyMethods) {
            docs.register(MLuaDocBuilder.Registration.signature("EntityProperties", "EntityProperties:" + method, "EntityProperties"));
        }
        docs.register(MLuaDocBuilder.Registration.signature("Registry.Containers", "Registry.Containers:Register(id: string, definition: ContainerDefinition)", "ContainerType"));
        docs.register(MLuaDocBuilder.Registration.signature("Registry.Containers", "Registry.Containers:GetId(name: string)", "string"));
        docs.register(MLuaDocBuilder.Registration.signature("Registry.Containers", "Registry.Containers:Get(id: string)", "ContainerType|nil"));
        docs.register(MLuaDocBuilder.Registration.signature("Registry.Containers", "Registry.Containers:GetEntry(id: string)", "ContainerType|nil"));
        docs.register(MLuaDocBuilder.Registration.signature("Registry.Containers", "Registry.Containers:GetAll(namespace?: string)", "table<string, ContainerType>"));
        docs.register(MLuaDocBuilder.Registration.signature("Registry.Containers", "Registry.Containers:GetEntries(namespace?: string)", "ContainerType[]"));
        docs.register(MLuaDocBuilder.Registration.signature("Registry.Containers", "Registry.Containers:GetIds(namespace?: string)", "string[]"));
        docs.register(MLuaDocBuilder.Registration.signature("Registry.Containers", "Registry.Containers:Exists(id: string)", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("ContainerType", "ContainerType:GetId()", "string"));
        docs.register(MLuaDocBuilder.Registration.signature("ContainerType", "ContainerType:GetRows()", "number"));
        docs.register(MLuaDocBuilder.Registration.signature("ContainerType", "ContainerType:GetSlotCount()", "number"));
        docs.register(MLuaDocBuilder.Registration.signature("ContainerType", "ContainerType:GetTitle()", "string"));
        docs.register(MLuaDocBuilder.Registration.signature("ContainerType", "ContainerType:GetAllowedItems()", "string[]"));
        docs.register(MLuaDocBuilder.Registration.signature("ContainerType", "ContainerType:GetAllowedItemsForSlot(slot: number)", "string[]"));
        docs.register(MLuaDocBuilder.Registration.signature("ContainerType", "ContainerType:AllowsAllItems()", "boolean"));
        docs.register(MLuaDocBuilder.Registration.method("ContainerType", "ContainerType:AllowsItem(item: string|Item): boolean",
            "Checks the container-wide item rule. An unqualified id uses the container type's namespace; use AllowsItemInSlot to include slot-specific rules.",
            "Namespaced item id or live Item instance", "boolean", "shared:Shared", null));
        docs.register(MLuaDocBuilder.Registration.method("ContainerType", "ContainerType:AllowsItemInSlot(slot: number, item: string|Item): boolean",
            "Checks the slot-specific allowlist, falling back to the container-wide allowlist. An unqualified item id uses the container type's namespace.",
            "One-based slot index and item id or live Item instance", "boolean", "shared:Shared", null));
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
        docs.register(MLuaDocBuilder.Registration.signature("Registry.Recipes", "Registry.Recipes:Register(id: string, definition: table|string)", "string"));
        docs.register(MLuaDocBuilder.Registration.signature("Registry.Recipes", "Registry.Recipes:RegisterShaped(id: string, definition: ShapedRecipeDefinition)", "string"));
        docs.register(MLuaDocBuilder.Registration.signature("Registry.Recipes", "Registry.Recipes:RegisterShapeless(id: string, definition: ShapelessRecipeDefinition)", "string"));
        docs.register(MLuaDocBuilder.Registration.signature("Registry.Recipes", "Registry.Recipes:RegisterSmelting(id: string, definition: CookingRecipeDefinition)", "string"));
        docs.register(MLuaDocBuilder.Registration.signature("Registry.Recipes", "Registry.Recipes:RegisterCooking(id: string, type: CookingRecipeKind, definition: CookingRecipeDefinition)", "string"));
        docs.register(MLuaDocBuilder.Registration.signature("Registry.Recipes", "Registry.Recipes:RegisterStonecutting(id: string, definition: StonecuttingRecipeDefinition)", "string"));
        docs.register(MLuaDocBuilder.Registration.signature("Registry.Recipes", "Registry.Recipes:RegisterSmithingTransform(id: string, definition: SmithingTransformRecipeDefinition)", "string"));
        docs.register(MLuaDocBuilder.Registration.signature("Registry.Recipes", "Registry.Recipes:RegisterSmithingTrim(id: string, definition: SmithingTrimRecipeDefinition)", "string"));
        docs.register(MLuaDocBuilder.Registration.signature("Registry.Loot", "Registry.Loot:Register(id: string, definition: LootDefinition|string)", "string"));
        docs.register(MLuaDocBuilder.Registration.signature("Registry.Loot", "Registry.Loot:RegisterEntity(entityId: string, definition: LootDefinition|string)", "string"));
        docs.register(MLuaDocBuilder.Registration.signature("Registry.Loot", "Registry.Loot:RegisterSimpleBlock(id: string, itemId?: string)", "string"));
        docs.register(MLuaDocBuilder.Registration.signature("Registry.Loot", "Registry.Loot:RegisterSelfBlock(id: string)", "string"));
        docs.register(MLuaDocBuilder.Registration.signature("Registry.Loot", "Registry.Loot:RegisterSilkTouchBlock(id: string, itemId: string, count: whole number >= 1?)", "string"));
        docs.register(MLuaDocBuilder.Registration.signature("Registry.Loot", "Registry.Loot:RegisterOreBlock(id: string, dropId: string, minCount: whole number >= 1?, maxCount: whole number >= minCount?)", "string"));
        docs.register(MLuaDocBuilder.Registration.signature("Registry.Tags", "Registry.Tags:Register(kind: string, id: string, definition: TagDefinition)", "string"));
        docs.register(MLuaDocBuilder.Registration.signature("Registry.Tags", "Registry.Tags:RegisterItem(id: string, items: string[]|TagDefinition)", "string"));
        docs.register(MLuaDocBuilder.Registration.signature("Registry.Tags", "Registry.Tags:RegisterBlock(id: string, blocks: string[]|TagDefinition)", "string"));
        docs.register(MLuaDocBuilder.Registration.signature("Registry.Tags", "Registry.Tags:RegisterEntity(id: string, entities: string[]|TagDefinition)", "string"));
        docs.register(MLuaDocBuilder.Registration.signature("Registry.Tags", "Registry.Tags:AddToItem(id: string, items: string[])", "string"));
        docs.register(MLuaDocBuilder.Registration.signature("Registry.Tags", "Registry.Tags:AddToBlock(id: string, blocks: string[])", "string"));
        docs.register(MLuaDocBuilder.Registration.signature("Registry.Tags", "Registry.Tags:AddToEntity(id: string, entities: string[])", "string"));
        docs.register(MLuaDocBuilder.Registration.signature("Registry.Projectiles", "Registry.Projectiles:Register(id: string, props: ProjectileDefinition)", "ProjectileType"));
        docs.register(MLuaDocBuilder.Registration.signature("Registry.Projectiles", "Registry.Projectiles:GetId(name: string)", "string"));
        docs.register(MLuaDocBuilder.Registration.signature("Registry.Projectiles", "Registry.Projectiles:Get(id: string)", "ProjectileType|nil"));
        docs.register(MLuaDocBuilder.Registration.signature("Registry.Projectiles", "Registry.Projectiles:GetEntry(id: string)", "ProjectileType|nil"));
        docs.register(MLuaDocBuilder.Registration.signature("Registry.Projectiles", "Registry.Projectiles:GetAll(namespace?: string)", "table<string, ProjectileType>"));
        docs.register(MLuaDocBuilder.Registration.signature("Registry.Projectiles", "Registry.Projectiles:GetEntries(namespace?: string)", "ProjectileType[]"));
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


}
