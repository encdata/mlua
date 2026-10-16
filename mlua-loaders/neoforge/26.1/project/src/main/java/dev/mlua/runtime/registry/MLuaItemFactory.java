package dev.mlua.runtime.registry;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.Locale;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.core.registries.Registries;
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
import dev.mlua.modules.players.Class.MLuaEntityWrapper;
import dev.mlua.modules.players.Class.MLuaPlayerWrapper;
import dev.mlua.mc.impl.entity.MCServerPlayerImpl;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

/** 26.1 item factory. Tool behavior is configured through Item.Properties.tool APIs. */
public final class MLuaItemFactory {
    private static final MLuaRegisteredTypeCatalog<TypeProvider> REGISTERED_TYPES = new MLuaRegisteredTypeCatalog<>();

    public static java.util.List<String> tierNames() {
        return reflectedFieldNames("net.minecraft.world.item.Tiers", "net.minecraft.world.item.Tier");
    }

    public static java.util.List<String> rarityNames() {
        return reflectedEnumNames("net.minecraft.world.item.Rarity");
    }

    public static void registerType(String name, String description, TypeProvider provider) {
        registerType("mlua", name, description, provider);
    }

    public static void registerType(String owner, String name, String description, TypeProvider provider) {
        String key = MLuaRegisteredTypeCatalog.normalizeName(name, "Item");
        if (Set.of("item", "generic", "bow").contains(key)) throw new IllegalArgumentException("[MLua] Item type '" + key + "' is reserved by MLua");
        if (provider == null) throw new IllegalArgumentException("[MLua] Item type provider is required");
        REGISTERED_TYPES.register(owner, key, description, provider, "Item");
    }

    public static boolean unregisterType(String owner, String name) {
        String key = MLuaRegisteredTypeCatalog.normalizeName(name, "Item");
        if (Set.of("item", "generic", "bow").contains(key)) return false;
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
        return REGISTERED_TYPES.descriptions(types);
    }

    public void validateTypeDefinition(Map<String, Object> properties) {
        validateItemProperties(properties);
        Object raw = properties == null ? null : properties.get("kind");
        if (raw != null && !(raw instanceof String)) throw new IllegalArgumentException("[MLua] Item kind must be a string");
        if (raw instanceof String name && !Set.of("item", "generic", "bow").contains(normalizeTypeName(name))
            && !REGISTERED_TYPES.contains(normalizeTypeName(name), "Item")) {
            throw new IllegalArgumentException("[MLua] Unknown item kind: " + name);
        }
    }

    public void validateItemProperties(Map<String, Object> properties) {
        if (properties == null) return;
        int maxStack = positiveWholeNumber(properties.get("maxStackSize"), "maxStackSize", 64, 64);
        int durability = positiveWholeNumber(properties.get("durability"), "durability", Integer.MAX_VALUE, -1);
        if (durability > 0 && maxStack > 1) {
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

    private static java.util.List<String> reflectedFieldNames(String className, String expectedTypeName) {
        try {
            Class<?> owner = Class.forName(className);
            Class<?> expectedType = Class.forName(expectedTypeName);
            return java.util.Arrays.stream(owner.getFields())
                .filter(field -> java.lang.reflect.Modifier.isStatic(field.getModifiers()))
                .filter(field -> expectedType.isAssignableFrom(field.getType()))
                .map(field -> field.getName().toLowerCase(Locale.ROOT)).sorted().toList();
        } catch (ClassNotFoundException | LinkageError ignored) {
            return java.util.List.of();
        }
    }

    public Item createNamed(Map<String, Object> properties, String registryName) {
        TypeProvider custom = registeredType(properties);
        if (custom != null) return custom.createNamed(Map.copyOf(properties), registryName);
        String kind = kind(properties);
        Item.Properties registered = makeRegisteredProperties(properties, registryName);
        return "bow".equals(kind) ? new MLuaRegisteredBow(registered, registryName) : new Item(registered);
    }
    public Item createScripted(Map<String, Object> properties, String behaviorKey) {
        TypeProvider custom = registeredType(properties);
        if (custom != null) return custom.createScripted(Map.copyOf(properties), behaviorKey);
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
    private Item.Properties itemProperties(Map<String, Object> properties) {
        validateItemProperties(properties);
        Item.Properties result = new Item.Properties();
        Object maxStack = properties == null ? null : properties.get("maxStackSize");
        if (maxStack instanceof Number number) result.stacksTo(number.intValue());
        Object durability = properties == null ? null : properties.get("durability");
        if (durability instanceof Number number && number.intValue() > 0) result.durability(number.intValue());
        return result;
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
            Varargs result = callback.invoke(context);
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
            Varargs result = callback.invoke(values);
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
            Varargs result = callback.invoke(context);
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
            callback.invoke(context);
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
            callback.invoke(context);
        } catch (Exception exception) {
            dev.mlua.core.MLuaLogger.LOGGER.error("MLua item onCrafted callback failed for '{}': {}", key, exception.getMessage());
        }
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
        Item createNamed(Map<String, Object> properties, String registryName);
        Item createScripted(Map<String, Object> properties, String behaviorKey);
    }
}
