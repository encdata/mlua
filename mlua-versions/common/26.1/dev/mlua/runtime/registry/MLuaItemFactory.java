package dev.mlua.runtime.registry;

import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.TreeMap;
import java.util.Locale;
import java.lang.reflect.Field;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.component.DataComponentInitializers;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.context.UseOnContext;
import dev.mlua.minecraft.scripted.MLuaBehaviorRegistry;
import dev.mlua.core.MLuaRuntime;
import dev.mlua.minecraft.scripted.MLuaScriptedItem;
import dev.mlua.minecraft.binding.MLuaBlockWrapper;
import dev.mlua.minecraft.binding.MLuaItemWrapper;
import dev.mlua.minecraft.MLuaResourceLocations;
import dev.mlua.modules.players.classes.MLuaEntityWrapper;
import dev.mlua.modules.players.classes.MLuaPlayerWrapper;
import dev.mlua.mc.impl.entity.MCServerPlayerImpl;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

/** 26.1 item factory. Tool behavior is configured through Item.Properties.tool APIs. */
public final class MLuaItemFactory {
    private static final MLuaRegisteredTypeCatalog<TypeProvider> REGISTERED_TYPES = new MLuaRegisteredTypeCatalog<>();
    private static final Map<String, ToolMaterial> TOOL_MATERIALS = toolMaterials();

    public static java.util.List<String> tierNames() {
        java.util.TreeSet<String> names = new java.util.TreeSet<>(TOOL_MATERIALS.keySet());
        if (names.contains("wood")) names.add("wooden");
        if (names.contains("gold")) names.add("golden");
        return java.util.List.copyOf(names);
    }

    public static java.util.List<String> rarityNames() {
        return reflectedEnumNames("net.minecraft.world.item.Rarity");
    }

    public static void registerType(String name, String description, TypeProvider provider) {
        registerType("mlua", name, description, provider);
    }

    public static void registerType(String owner, String name, String description, TypeProvider provider) {
        String key = MLuaRegisteredTypeCatalog.normalizeName(name, "Item");
        if (builtInKind(key)) throw new IllegalArgumentException("[MLua] Item type '" + key + "' is reserved by MLua");
        if (provider == null) throw new IllegalArgumentException("[MLua] Item type provider is required");
        REGISTERED_TYPES.register(owner, key, description, provider, "Item");
    }

    public static boolean unregisterType(String owner, String name) {
        String key = MLuaRegisteredTypeCatalog.normalizeName(name, "Item");
        if (builtInKind(key)) return false;
        return REGISTERED_TYPES.remove(owner, key, "Item");
    }

    public static int unregisterTypes(String owner) {
        return REGISTERED_TYPES.removeOwner(owner);
    }

    public static Map<String, String> registeredTypes() {
        Map<String, String> types = new TreeMap<>();
        types.put("item", "A standard registered item.");
        types.put("generic", "Alias for item.");
        types.put("bow", "A native bow item with Minecraft draw and ammunition behavior.");
        types.put("sword", "A tiered melee weapon with Minecraft attack and durability components.");
        types.put("pickaxe", "A tiered mining tool with Minecraft mining rules.");
        types.put("pick", "Alias for pickaxe.");
        types.put("axe", "A tiered axe with Minecraft mining and weapon behavior.");
        types.put("shovel", "A tiered shovel with Minecraft mining rules.");
        types.put("spade", "Alias for shovel.");
        types.put("hoe", "A tiered hoe with Minecraft mining rules.");
        return REGISTERED_TYPES.descriptions(types);
    }

    public void validateTypeDefinition(Map<String, Object> properties) {
        validateItemProperties(properties);
        Object raw = properties == null ? null : properties.get("kind");
        if (raw != null && !(raw instanceof String)) throw new IllegalArgumentException("[MLua] Item kind must be a string");
        String kind = kind(properties);
        if (!builtInKind(kind) && !REGISTERED_TYPES.contains(kind, "Item")) {
            throw new IllegalArgumentException("[MLua] Unknown item kind: " + kind);
        }
        if (builtInKind(kind)) validateKindProperties(properties, kind);
        TypeProvider provider = registeredType(properties);
        if (provider != null) provider.validateDefinition(MLuaDefinitionSnapshot.freeze(properties));
    }

    public void validateItemProperties(Map<String, Object> properties) {
        if (properties == null) return;
        validateCopyDefinition(properties.get("copy"));
        int maxStack = positiveWholeNumber(properties.get("maxStackSize"), "maxStackSize", 64, 64);
        int durability = positiveWholeNumber(properties.get("durability"), "durability", Integer.MAX_VALUE, -1);
        if (durability > 0 && properties.get("maxStackSize") != null && maxStack > 1) {
            throw new IllegalArgumentException("[MLua] Item property 'durability' cannot be combined with maxStackSize > 1");
        }
        Object fireResistant = properties.get("fireResistant");
        if (fireResistant != null && !(fireResistant instanceof Boolean)) {
            throw new IllegalArgumentException("[MLua] Item property 'fireResistant' must be a boolean");
        }
        Object rarity = properties.get("rarity");
        if (rarity != null && (!(rarity instanceof String value) || value.isBlank())) {
            throw new IllegalArgumentException("[MLua] Item property 'rarity' must be a non-empty string");
        }
        if (rarity instanceof String value) rarity(value);
        validateCraftRemainder(properties.get("craftRemainder"));
        validateFood(properties.get("food"));
        String kind = kind(properties);
        if (builtInKind(kind)) validateKindProperties(properties, kind);
    }

    private static int positiveWholeNumber(Object raw, String name, int maximum, int missing) {
        if (raw == null) return missing;
        if (!(raw instanceof Number number)) throw new IllegalArgumentException("[MLua] Item property '" + name + "' must be a whole number from 1 to " + maximum);
        double value = number.doubleValue();
        if (!Double.isFinite(value) || value != Math.rint(value) || value < 1 || value > maximum) {
            throw new IllegalArgumentException("[MLua] Item property '" + name + "' must be a whole number from 1 to " + maximum);
        }
        return (int)value;
    }

    private static java.util.List<String> reflectedEnumNames(String className) {
        try {
            Class<?> type = Class.forName(className);
            if (!type.isEnum()) return java.util.List.of();
            Object[] constants = type.getEnumConstants();
            return java.util.Arrays.stream(constants).map(value -> ((Enum<?>)value).name().toLowerCase(Locale.ROOT)).sorted().toList();
        } catch (ClassNotFoundException | LinkageError ignored) {
            return java.util.List.of();
        }
    }

    public Item createNamed(Map<String, Object> properties, String registryName) {
        TypeProvider custom = registeredType(properties);
        if (custom != null) {
            validateItemProperties(properties);
            Map<String, Object> definition = MLuaDefinitionSnapshot.validateAndFreeze(
                properties, custom::validateDefinition);
            return custom.createNamed(definition, registryName);
        }
        validateTypeDefinition(properties);
        if ("bow".equals(kind(properties))) {
            return new MLuaRegisteredBow(makeRegisteredBowProperties(properties, registryName), registryName);
        }
        return new Item(makeRegisteredProperties(properties, registryName));
    }
    public Item createScripted(Map<String, Object> properties, String behaviorKey) {
        TypeProvider custom = registeredType(properties);
        if (custom != null) {
            validateItemProperties(properties);
            Map<String, Object> definition = MLuaDefinitionSnapshot.validateAndFreeze(
                properties, custom::validateDefinition);
            return custom.createScripted(definition, behaviorKey);
        }
        validateTypeDefinition(properties);
        if ("bow".equals(kind(properties))) {
            return new MLuaRegisteredBow(makeRegisteredBowProperties(properties, behaviorKey), behaviorKey);
        }
        return new MLuaScriptedItem(makeRegisteredProperties(properties, behaviorKey), behaviorKey);
    }
    public Item.Properties makeProperties(Map<String, Object> properties) {
        return itemProperties(properties);
    }
    public Item.Properties makeRegisteredProperties(Map<String, Object> properties, String registryName) {
        Item.Properties result = itemProperties(properties);
        result.setId(ResourceKey.create(Registries.ITEM, MLuaResourceLocations.parse(registryName)));
        return result;
    }

    private Item.Properties makeRegisteredBowProperties(Map<String, Object> properties, String registryName) {
        Item.Properties result = makeBowProperties(properties);
        result.setId(ResourceKey.create(Registries.ITEM, MLuaResourceLocations.parse(registryName)));
        return result;
    }

    private Item.Properties makeBowProperties(Map<String, Object> properties) {
        if (properties != null && (properties.containsKey("durability") || properties.containsKey("copy"))) {
            return makeProperties(properties);
        }
        Map<String, Object> bowProperties = properties == null
            ? new java.util.HashMap<>() : new java.util.HashMap<>(properties);
        bowProperties.put("durability", 384);
        return makeProperties(bowProperties);
    }

    private Item.Properties itemProperties(Map<String, Object> properties) {
        validateItemProperties(properties);
        Item.Properties result = copyProperties(properties);
        applyToolProperties(result, properties);
        Object maxStack = properties == null ? null : properties.get("maxStackSize");
        if (maxStack instanceof Number number) result.stacksTo(number.intValue());
        Object durability = properties == null ? null : properties.get("durability");
        if (durability instanceof Number number && number.intValue() > 0) result.durability(number.intValue());
        if (Boolean.TRUE.equals(properties == null ? null : properties.get("fireResistant"))) {
            result.fireResistant();
        }
        Object rarity = properties == null ? null : properties.get("rarity");
        if (rarity instanceof String name) result.rarity(rarity(name));
        Object craftRemainder = properties == null ? null : properties.get("craftRemainder");
        if (craftRemainder instanceof String id) {
            Item remainder = net.minecraft.core.registries.BuiltInRegistries.ITEM
                .getOptional(MLuaResourceLocations.parse(id)).orElse(null);
            if (remainder == null) {
                throw new IllegalArgumentException("[MLua] Unknown craftRemainder item: " + id);
            }
            result.craftRemainder(remainder);
        }
        Object food = properties == null ? null : properties.get("food");
        if (food instanceof Map<?, ?> definition) {
            FoodProperties.Builder builder = new FoodProperties.Builder()
                .nutrition(((Number) definition.get("nutrition")).intValue())
                .saturationModifier(definition.get("saturation") instanceof Number saturation
                    ? saturation.floatValue() : 0.0F);
            if (Boolean.TRUE.equals(definition.get("alwaysEat"))
                || Boolean.TRUE.equals(definition.get("alwaysEdible"))) {
                builder.alwaysEdible();
            }
            FoodProperties foodProperties = builder.build();
            if (Boolean.TRUE.equals(definition.get("fast"))) {
                result.food(foodProperties, Consumables.defaultFood()
                    .consumeSeconds(Consumables.DRIED_KELP.consumeSeconds()).build());
            } else {
                result.food(foodProperties);
            }
        }
        return result;
    }

    private static Item.Properties copyProperties(Map<String, Object> properties) {
        Object rawCopy = properties == null ? null : properties.get("copy");
        if (!(rawCopy instanceof String id)) return new Item.Properties();
        Item source = net.minecraft.core.registries.BuiltInRegistries.ITEM
            .getOptional(MLuaResourceLocations.parse(id)).orElse(null);
        if (source == null) {
            throw new IllegalArgumentException("[MLua] Unknown item in copy property: " + id);
        }
        Item.Properties result = new Item.Properties();
        ResourceKey<Item> sourceKey = ResourceKey.create(Registries.ITEM, MLuaResourceLocations.parse(id));
        copyComponentInitializers(result, sourceKey);
        return result;
    }

    @SuppressWarnings("unchecked")
    private static void copyComponentInitializers(Item.Properties target, ResourceKey<Item> sourceKey) {
        try {
            Field initializersField = DataComponentInitializers.class.getDeclaredField("initializers");
            initializersField.setAccessible(true);
            List<?> entries = (List<?>) initializersField.get(
                net.minecraft.core.registries.BuiltInRegistries.DATA_COMPONENT_INITIALIZERS);

            DataComponentInitializers.Initializer<Item> copied = null;
            for (Object entry : entries) {
                Class<?> entryType = entry.getClass();
                var keyAccessor = entryType.getDeclaredMethod("key");
                var initializerAccessor = entryType.getDeclaredMethod("initializer");
                keyAccessor.setAccessible(true);
                initializerAccessor.setAccessible(true);
                if (!sourceKey.equals(keyAccessor.invoke(entry))) continue;

                DataComponentInitializers.Initializer<Item> initializer =
                    (DataComponentInitializers.Initializer<Item>) initializerAccessor.invoke(entry);
                copied = copied == null ? initializer : copied.andThen(initializer);
            }
            if (copied == null) {
                throw new IllegalArgumentException("[MLua] No item component defaults are registered for copy source "
                    + sourceKey);
            }
            DataComponentInitializers.Initializer<Item> copiedInitializer = copied;

            Field targetInitializerField = Item.Properties.class.getDeclaredField("componentInitializer");
            targetInitializerField.setAccessible(true);
            DataComponentInitializers.Initializer<Item> targetInitializer =
                (DataComponentInitializers.Initializer<Item>) targetInitializerField.get(target);
            DataComponentInitializers.Initializer<Item> combined = (components, registries, targetKey) -> {
                if (targetInitializer != null) targetInitializer.run(components, registries, targetKey);
                copiedInitializer.run(components, registries, sourceKey);
            };
            targetInitializerField.set(target, combined);
        } catch (ReflectiveOperationException | SecurityException error) {
            throw new IllegalStateException("[MLua] Could not copy deferred item component defaults from "
                + sourceKey, error);
        }
    }

    private static void validateCopyDefinition(Object raw) {
        if (raw == null) return;
        if (!(raw instanceof String id) || id.isBlank()) {
            throw new IllegalArgumentException("[MLua] Item copy must be a non-empty item identifier");
        }
        if (MLuaResourceLocations.tryParseOrNull(id) == null) {
            throw new IllegalArgumentException("[MLua] Invalid item copy identifier '" + id + "'");
        }
    }

    private static void validateCraftRemainder(Object raw) {
        if (raw == null) return;
        if (!(raw instanceof String id) || id.isBlank()) {
            throw new IllegalArgumentException("[MLua] Item property 'craftRemainder' must be a non-empty item identifier");
        }
        if (MLuaResourceLocations.tryParseOrNull(id) == null) {
            throw new IllegalArgumentException("[MLua] Invalid craftRemainder item identifier '" + id + "'");
        }
    }

    private static void validateFood(Object raw) {
        if (raw == null) return;
        if (!(raw instanceof Map<?, ?> food)) {
            throw new IllegalArgumentException("[MLua] Item property 'food' must be a table");
        }
        Object rawNutrition = food.get("nutrition");
        if (!(rawNutrition instanceof Number nutrition)) {
            throw new IllegalArgumentException("[MLua] food.nutrition is required and must be a whole number");
        }
        double nutritionValue = nutrition.doubleValue();
        if (!Double.isFinite(nutritionValue) || nutritionValue != Math.rint(nutritionValue)
            || nutritionValue < 0 || nutritionValue > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("[MLua] food.nutrition must be a whole number from 0 to " + Integer.MAX_VALUE);
        }
        Object rawSaturation = food.get("saturation");
        if (rawSaturation != null) {
            if (!(rawSaturation instanceof Number saturation)) {
                throw new IllegalArgumentException("[MLua] food.saturation must be a non-negative number");
            }
            double value = saturation.doubleValue();
            if (!Double.isFinite(value) || value < 0 || value > Float.MAX_VALUE) {
                throw new IllegalArgumentException("[MLua] food.saturation must be finite and within the non-negative float range");
            }
        }
        for (String name : java.util.List.of("meat", "alwaysEat", "alwaysEdible", "fast")) {
            Object value = food.get(name);
            if (value != null && !(value instanceof Boolean)) {
                throw new IllegalArgumentException("[MLua] food." + name + " must be a boolean");
            }
        }
    }

    private static boolean builtInKind(String kind) {
        return Set.of("item", "generic", "bow", "sword", "pickaxe", "pick", "axe",
            "shovel", "spade", "hoe").contains(kind);
    }

    private static boolean tieredKind(String kind) {
        return Set.of("sword", "pickaxe", "pick", "axe", "shovel", "spade", "hoe").contains(kind);
    }

    private static void validateKindProperties(Map<String, Object> properties, String kind) {
        boolean tiered = tieredKind(kind);
        if (!tiered && properties != null && properties.get("tier") != null) {
            throw new IllegalArgumentException("[MLua] Item property 'tier' is only valid for tiered tool kinds");
        }
        if (!tiered && properties != null
            && (properties.get("attackDamage") != null || properties.get("attackSpeed") != null)) {
            throw new IllegalArgumentException("[MLua] Item attackDamage and attackSpeed are only valid for tiered tool kinds");
        }
        if (tiered) {
            Object maxStack = properties == null ? null : properties.get("maxStackSize");
            if (maxStack != null && positiveWholeNumber(maxStack, "maxStackSize", 64, 64) > 1) {
                throw new IllegalArgumentException("[MLua] Tiered tool kinds require maxStackSize = 1");
            }
            toolMaterial(properties);
            finiteFloat(properties, "attackDamage", defaultAttackDamage(kind));
            finiteFloat(properties, "attackSpeed", defaultAttackSpeed(kind));
        }
    }

    private static void applyToolProperties(Item.Properties properties, Map<String, Object> definition) {
        String kind = kind(definition);
        if (!tieredKind(kind)) return;
        ToolMaterial material = toolMaterial(definition);
        float damage = finiteFloat(definition, "attackDamage", defaultAttackDamage(kind));
        float speed = finiteFloat(definition, "attackSpeed", defaultAttackSpeed(kind));
        switch (kind) {
            case "sword" -> properties.sword(material, damage, speed);
            case "pick", "pickaxe" -> properties.pickaxe(material, damage, speed);
            case "axe" -> properties.axe(material, damage, speed);
            case "shovel", "spade" -> properties.shovel(material, damage, speed);
            case "hoe" -> properties.hoe(material, damage, speed);
            default -> throw new IllegalArgumentException("[MLua] Unsupported built-in item kind: " + kind);
        }
    }

    private static ToolMaterial toolMaterial(Map<String, Object> properties) {
        Object raw = properties == null ? null : properties.get("tier");
        if (raw == null) return TOOL_MATERIALS.get("wood");
        if (!(raw instanceof String name) || name.isBlank()) {
            throw new IllegalArgumentException("[MLua] Item property 'tier' must be a non-empty string");
        }
        String normalized = name.trim().toLowerCase(Locale.ROOT);
        ToolMaterial material = TOOL_MATERIALS.get(normalized);
        if (material == null && normalized.equals("wooden")) material = TOOL_MATERIALS.get("wood");
        if (material == null && normalized.equals("golden")) material = TOOL_MATERIALS.get("gold");
        if (material == null) material = TOOL_MATERIALS.get(normalized.replace("_", ""));
        if (material == null) {
            throw new IllegalArgumentException("[MLua] Unknown item tier: " + name
                + ". Use Registry.Items:GetTiers() to list values available in this Minecraft version.");
        }
        return material;
    }

    private static Map<String, ToolMaterial> toolMaterials() {
        Map<String, ToolMaterial> materials = new TreeMap<>();
        try {
            for (var field : ToolMaterial.class.getFields()) {
                if (!java.lang.reflect.Modifier.isStatic(field.getModifiers())
                    || !ToolMaterial.class.isAssignableFrom(field.getType())) continue;
                Object value = field.get(null);
                if (value instanceof ToolMaterial material) {
                    materials.put(field.getName().toLowerCase(Locale.ROOT), material);
                }
            }
        } catch (IllegalAccessException ignored) {
            // Omit inaccessible tool materials from the Lua-visible choices.
        }
        return java.util.Collections.unmodifiableMap(materials);
    }

    private static float finiteFloat(Map<String, Object> properties, String name, float defaultValue) {
        Object raw = properties == null ? null : properties.get(name);
        if (raw == null) return defaultValue;
        if (!(raw instanceof Number number)) {
            throw new IllegalArgumentException("[MLua] Item property '" + name + "' must be numeric");
        }
        double value = number.doubleValue();
        if (!Double.isFinite(value) || value < -Float.MAX_VALUE || value > Float.MAX_VALUE) {
            throw new IllegalArgumentException("[MLua] Item property '" + name + "' must be a finite float");
        }
        return (float) value;
    }

    private static float defaultAttackDamage(String kind) {
        return switch (kind) {
            case "sword" -> 3.0F;
            case "pick", "pickaxe", "shovel", "spade" -> 1.0F;
            case "axe" -> 6.0F;
            case "hoe" -> -2.0F;
            default -> 0.0F;
        };
    }

    private static float defaultAttackSpeed(String kind) {
        return switch (kind) {
            case "sword" -> -2.4F;
            case "pick", "pickaxe" -> -2.8F;
            case "axe" -> -3.1F;
            case "shovel", "spade" -> -3.0F;
            case "hoe" -> -1.0F;
            default -> 0.0F;
        };
    }

    private static net.minecraft.world.item.Rarity rarity(String name) {
        try {
            return net.minecraft.world.item.Rarity.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("[MLua] Unknown item rarity: " + name
                + ". Use Registry.Items:GetRarities() to list values available in this Minecraft version.", invalid);
        }
    }

    static InteractionResult invokeOnUse(String key, Level level, Player player, InteractionHand hand) {
        LuaValue callback = MLuaBehaviorRegistry.getBehavior("item", key, "onUse");
        if (callback.isnil()) return null;
        try {
            LuaTable context = new LuaTable();
            var registration = MLuaRuntime.registrationForLevel(level);
            context.set("World", level.dimension().identifier().toString());
            context.set("Position", MLuaPlayerWrapper.positionTable(player.getX(), player.getY(), player.getZ()));
            context.set("Hand", hand.name().toLowerCase(Locale.ROOT));
            context.set("Item", MLuaItemWrapper.itemTable(player.getItemInHand(hand), registration));
            context.set("ItemStack", key);
            if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                context.set("Player", MLuaPlayerWrapper.playerTable(MCServerPlayerImpl.fromReal(serverPlayer), registration));
            }
            Varargs result = invokeBehavior(key, "onUse", callback, context);
            return result.arg1().isboolean() && result.arg1().toboolean() ? InteractionResult.SUCCESS : null;
        } catch (Exception exception) {
            dev.mlua.core.MLuaLogger.LOGGER.error("MLua item onUse callback failed for '{}': {}", key, exception.getMessage());
            return null;
        }
    }

    static InteractionResult invokeOnUseOnBlock(String key, UseOnContext context) {
        LuaValue callback = MLuaBehaviorRegistry.getBehavior("item", key, "onUseOnBlock");
        if (callback.isnil()) return null;
        try {
            LuaTable values = new LuaTable();
            var level = context.getLevel();
            var position = context.getClickedPos();
            var registration = MLuaRuntime.registrationForLevel(level);
            values.set("World", level.dimension().identifier().toString());
            values.set("Position", MLuaPlayerWrapper.positionTable(position.getX(), position.getY(), position.getZ()));
            values.set("Block", MLuaBlockWrapper.blockTable(level, position, registration));
            values.set("Face", context.getClickedFace().getName());
            values.set("Hand", context.getHand().name().toLowerCase(Locale.ROOT));
            values.set("Item", MLuaItemWrapper.itemTable(context.getItemInHand(), registration));
            values.set("ItemStack", key);
            if (context.getPlayer() instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                values.set("Player", MLuaPlayerWrapper.playerTable(MCServerPlayerImpl.fromReal(serverPlayer), registration));
            }
            Varargs result = invokeBehavior(key, "onUseOnBlock", callback, values);
            return result.arg1().isboolean() && result.arg1().toboolean() ? InteractionResult.SUCCESS : null;
        } catch (Exception exception) {
            dev.mlua.core.MLuaLogger.LOGGER.error("MLua item onUseOnBlock callback failed for '{}': {}", key, exception.getMessage());
            return null;
        }
    }

    static boolean invokeOnAttackEntity(String key, ItemStack stack, net.minecraft.world.entity.LivingEntity target,
                                        net.minecraft.world.entity.LivingEntity attacker) {
        LuaValue callback = MLuaBehaviorRegistry.getBehavior("item", key, "onAttackEntity");
        if (callback.isnil()) return true;
        try {
            var registration = MLuaRuntime.registrationForLevel(target.level());
            LuaTable targetTable = target instanceof net.minecraft.server.level.ServerPlayer player
                ? MLuaPlayerWrapper.playerTable(MCServerPlayerImpl.fromReal(player), registration)
                : MLuaEntityWrapper.entityTable(target, registration);
            LuaTable attackerTable = attacker instanceof net.minecraft.server.level.ServerPlayer player
                ? MLuaPlayerWrapper.playerTable(MCServerPlayerImpl.fromReal(player), registration)
                : MLuaEntityWrapper.entityTable(attacker, registration);
            LuaTable context = new LuaTable();
            context.set("Item", MLuaItemWrapper.itemTable(stack, registration));
            context.set("Entity", targetTable);
            context.set("Target", targetTable);
            context.set("Attacker", attackerTable);
            if (attacker instanceof net.minecraft.server.level.ServerPlayer) context.set("Player", attackerTable);
            context.set("ItemStack", key);
            Varargs result = invokeBehavior(key, "onAttackEntity", callback, context);
            return !result.arg1().isboolean() || result.arg1().toboolean();
        } catch (Exception exception) {
            dev.mlua.core.MLuaLogger.LOGGER.error("MLua item onAttackEntity callback failed for '{}': {}", key, exception.getMessage());
            return true;
        }
    }

    static void invokeOnInventoryTick(String key, ItemStack stack, ServerLevel level, Entity entity, EquipmentSlot slot) {
        LuaValue callback = MLuaBehaviorRegistry.getBehavior("item", key, "onInventoryTick");
        if (callback.isnil()) return;
        try {
            LuaTable context = new LuaTable();
            var registration = MLuaRuntime.registrationForLevel(level);
            context.set("World", level.dimension().identifier().toString());
            context.set("Item", MLuaItemWrapper.itemTable(stack, registration));
            context.set("Slot", slot.ordinal());
            context.set("Selected", LuaValue.valueOf(entity instanceof Player player && player.getMainHandItem() == stack));
            context.set("Entity", MLuaEntityWrapper.entityTable(entity, registration));
            context.set("ItemStack", key);
            if (entity instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                context.set("Player", MLuaPlayerWrapper.playerTable(MCServerPlayerImpl.fromReal(serverPlayer), registration));
            }
            invokeBehavior(key, "onInventoryTick", callback, context);
        } catch (Exception exception) {
            dev.mlua.core.MLuaLogger.LOGGER.error("MLua item onInventoryTick callback failed for '{}': {}", key, exception.getMessage());
        }
    }

    static void invokeOnCrafted(String key, ItemStack stack, Player player) {
        LuaValue callback = MLuaBehaviorRegistry.getBehavior("item", key, "onCrafted");
        if (callback.isnil()) return;
        try {
            LuaTable context = new LuaTable();
            var registration = MLuaRuntime.registrationForLevel(player.level());
            context.set("World", player.level().dimension().identifier().toString());
            context.set("Item", MLuaItemWrapper.itemTable(stack, registration));
            context.set("ItemStack", key);
            context.set("Position", MLuaPlayerWrapper.positionTable(player.getX(), player.getY(), player.getZ()));
            context.set("Hand", player.getUsedItemHand() == null ? "main_hand" : player.getUsedItemHand().name().toLowerCase(Locale.ROOT));
            if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                context.set("Player", MLuaPlayerWrapper.playerTable(MCServerPlayerImpl.fromReal(serverPlayer), registration));
            }
            invokeBehavior(key, "onCrafted", callback, context);
        } catch (Exception exception) {
            dev.mlua.core.MLuaLogger.LOGGER.error("MLua item onCrafted callback failed for '{}': {}", key, exception.getMessage());
        }
    }

    private static Varargs invokeBehavior(String key, String event, LuaValue callback, LuaTable context) {
        return MLuaBehaviorRegistry.invokeCallback("item", key, event, callback,
            LuaValue.varargsOf(new LuaValue[]{context}));
    }

    private static String kind(Map<String, Object> properties) {
        Object value = properties == null ? null : properties.get("kind");
        return value instanceof String name ? normalizeTypeName(name) : "item";
    }

    private static TypeProvider registeredType(Map<String, Object> properties) {
        return REGISTERED_TYPES.get(kind(properties), "Item");
    }

    private static String normalizeTypeName(String name) {
        return MLuaRegisteredTypeCatalog.normalizeName(name, "Item");
    }


    public interface TypeProvider {
        default void validateDefinition(Map<String, Object> definition) { }
        Item createNamed(Map<String, Object> properties, String registryName);
        Item createScripted(Map<String, Object> properties, String behaviorKey);
    }
}
