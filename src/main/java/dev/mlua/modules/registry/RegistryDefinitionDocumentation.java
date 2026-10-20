package dev.mlua.modules.registry;

import dev.mlua.module.MLuaDocBuilder;

/** Documents the definition table schemas used by Registry registration. */
final class RegistryDefinitionDocumentation {
    private RegistryDefinitionDocumentation() {}

    static void contribute(MLuaDocBuilder docs) {
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
            "HeldItem?", "Item", "Item stack held for an item-on-block interaction.",
            "Face?", "string", "Block face involved in an interaction.", "Neighbor?", "Block", "Neighbor block that triggered the update, when Minecraft provides its position; otherwise nil.",
            "Projectile?", "Entity", "Live projectile that hit the block.",
            "FallDistance?", "number", "Distance fallen before landing.");
        schema(docs, "BlockDefinition", "Properties and callbacks for a registered block.",
            "onRightClick?", "fun(context: BlockBehaviorContext): boolean", "Handles a right-click interaction. Return true to consume it. Returning false or nil lets the configured container open, if present; otherwise Minecraft can continue the held-item interaction.",
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
            "onUnload?", "fun(self: BlockEntity, context: table)", "Runs on the server when Minecraft removes the block entity. The context includes live Block, BlockEntity, and World instances plus a Vector3 Position.",
            "onOpen?", "fun(self: BlockEntity, context: table)", "Runs on the server for each entity opening the block's container. Context.User is the live viewing entity; Context.Player is set when it is a player. Context.Container is the inventory.",
            "onClose?", "fun(self: BlockEntity, context: table)", "Runs on the server for each entity closing the block's container. Context.User is the live viewing entity; Context.Player is set when it is a player. Context.Container is the inventory.",
            "onContainerChanged?", "fun(self: BlockEntity, context: table)", "Runs on the server after the container contents change. Context.Container is the live Container when one exists; Context.Slot is the changed one-based slot when known. Nested changes made by this callback do not recursively invoke it; an error disables this callback for this block entity until its definition changes.");
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
            "onSpawn?", "fun(entity: Entity)", "Runs once when the entity is first added after creation. MLua saves this state, so chunk reloads and dimension transfers do not repeat the callback.",
            "onInteract?", "fun(self: Entity, player: Player, hand: string): boolean", "Handles player interaction.",
            "onTargetChanged?", "fun(self: Entity, target: Entity|nil, previousTarget: Entity|nil)", "Runs on the server after the mob's target changes. Either target may be nil. Changing the target inside this callback updates the mob but does not recursively dispatch the callback.",
            "onRemove?", "fun(entity: Entity, reason: string)", "Runs when the entity leaves the active level. The reason is Minecraft's removal reason, such as KILLED, DISCARDED, UNLOADED_TO_CHUNK, UNLOADED_WITH_PLAYER, or CHANGED_DIMENSION.",
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
            "allowedItems?", "string|string[]", "Container-wide allowed item ids. Omit this field or pass an empty array to allow every item unless a slot overrides it. AllowedItems is an accepted alias.",
            "slots?", "ContainerSlotDefinition[]", "Optional per-slot rules in one-based order. Omitted slots inherit the container-wide allowlist.");
        schema(docs, "ContainerSlotDefinition", "Restrictions for one one-based slot in a scripted container.",
            "allowedItems?", "string|string[]", "Allowed item ids for this slot. Omit this field to inherit the container-wide allowlist; pass an empty array to allow every item in this slot. AllowedItems is an accepted alias.");
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
        docs.register(MLuaDocBuilder.Registration.type("IngredientDefinition", null,
            "A recipe ingredient written as an item id (\"minecraft:iron_ingot\"), a tag selector (\"#minecraft:planks\"), or a one-field table ({ item = \"minecraft:iron_ingot\" }, { tag = \"minecraft:planks\" }, or { items = { \"minecraft:iron_ingot\", \"minecraft:gold_ingot\" } }). The items array accepts 1-256 distinct item ids and cannot contain minecraft:air. Typed recipe builders validate identifiers and convert this stable Lua form to the format required by the active Minecraft version."));
        schema(docs, "CraftingResultDefinition", "Output stack for a shaped or shapeless crafting recipe.",
            "item", "string", "Namespaced output item id. The id alias is also accepted and converted for the active Minecraft version.",
            "count?", "whole number >= 1", "Output count (default 1). Additional Minecraft result fields are preserved as supplied.");
        schema(docs, "ShapedRecipeDefinition", "A vanilla 3-by-3 crafting recipe with a character pattern.",
            "pattern", "string[1..3]", "One to three equal-width rows, each one to three characters. A space marks an empty slot.",
            "key", "table<string, IngredientDefinition>", "Maps each used one-character pattern symbol to one item, tag, or item list.",
            "result", "string|CraftingResultDefinition", "Output item id or output stack table.");
        schema(docs, "ShapelessRecipeDefinition", "A vanilla crafting recipe matched without a shaped grid.",
            "ingredients", "IngredientDefinition[1..9]", "One to nine item, tag, or item-list ingredient definitions.",
            "result", "string|CraftingResultDefinition", "Output item id or output stack table.");
        schema(docs, "CookingRecipeDefinition", "Ingredient, result, and cooking timing options for smelting, blasting, smoking, and campfire recipes.",
            "ingredient", "string|IngredientDefinition", "Input item or ingredient description.",
            "result", "string", "Output item identifier.", "count?", "whole number >= 1", "Output count (default 1). Minecraft 1.20.1 only supports a count of 1.",
            "experience?", "number >= 0", "Experience granted when the result is collected.",
            "cookingTime?", "whole number >= 1", "Cooking duration in ticks (default 200). The lowercase cookingtime alias is also accepted.");
        docs.register(MLuaDocBuilder.Registration.type("CookingRecipeKind", null,
            "Vanilla cooking recipe type accepted by Registry.Recipes:RegisterCooking: smelting, blasting, smoking, or campfire_cooking."));
        schema(docs, "StonecuttingRecipeDefinition", "Ingredient and output options for a stonecutting recipe.",
            "ingredient", "string|IngredientDefinition", "Input item or ingredient description.",
            "result", "string", "Output item identifier.", "count?", "whole number >= 1", "Output count (default 1).");
        schema(docs, "SmithingTransformRecipeDefinition", "Inputs and output for a smithing-table item upgrade.",
            "template", "IngredientDefinition", "Smithing template ingredient.",
            "base", "IngredientDefinition", "Item being upgraded.",
            "addition", "IngredientDefinition", "Upgrade material ingredient.",
            "result", "string", "Output item identifier.",
            "count?", "whole number >= 1", "Output count (default 1).");
        schema(docs, "SmithingTrimRecipeDefinition", "Inputs for a smithing-table armor trim recipe.",
            "template", "IngredientDefinition", "Trim pattern template ingredient.",
            "base", "IngredientDefinition", "Trimmable armor ingredient.",
            "addition", "IngredientDefinition", "Trim material ingredient.");
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
