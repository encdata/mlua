package dev.mlua.runtime.registry;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.context.UseOnContext;
import dev.mlua.minecraft.scripted.MLuaBehaviorRegistry;
import dev.mlua.core.MLuaRuntime;
import dev.mlua.minecraft.scripted.MLuaScriptedItem;
import dev.mlua.minecraft.binding.MLuaBlockWrapper;
import dev.mlua.minecraft.binding.MLuaItemWrapper;
import dev.mlua.modules.players.Class.MLuaEntityWrapper;
import dev.mlua.modules.players.Class.MLuaPlayerWrapper;
import dev.mlua.mc.impl.entity.MCServerPlayerImpl;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

/** 26.1 item factory. Tool behavior is configured through Item.Properties.tool APIs. */
public final class MLuaItemFactory {
    private static final ConcurrentMap<String, RegisteredType> REGISTERED_TYPES = new ConcurrentHashMap<>();

    public static void registerType(String name, String description, TypeProvider provider) {
        String key = normalizeTypeName(name);
        if (Set.of("item", "generic", "bow").contains(key)) throw new IllegalArgumentException("[MLua] Item type '" + key + "' is reserved by MLua");
        if (description == null || description.isBlank()) throw new IllegalArgumentException("[MLua] Item type description must be non-empty");
        if (provider == null) throw new IllegalArgumentException("[MLua] Item type provider is required");
        if (REGISTERED_TYPES.putIfAbsent(key, new RegisteredType(description.trim(), provider)) != null)
            throw new IllegalStateException("[MLua] Item type '" + key + "' is already registered");
    }

    public static Map<String, String> registeredTypes() {
        Map<String, String> types = new TreeMap<>();
        types.put("item", "A standard registered item.");
        types.put("generic", "Alias for item.");
        types.put("bow", "A native bow item with Minecraft draw and ammunition behavior.");
        REGISTERED_TYPES.forEach((name, type) -> types.put(name, type.description()));
        return Map.copyOf(types);
    }

    public void validateTypeDefinition(Map<String, Object> properties) {
        Object raw = properties == null ? null : properties.get("kind");
        if (raw != null && !(raw instanceof String)) throw new IllegalArgumentException("[MLua] Item kind must be a string");
        if (raw instanceof String name && !Set.of("item", "generic", "bow").contains(normalizeTypeName(name))
            && !REGISTERED_TYPES.containsKey(normalizeTypeName(name))) {
            throw new IllegalArgumentException("[MLua] Unknown item kind: " + name);
        }
    }

    public Item createNamed(Map<String, Object> properties, String registryName) {
        RegisteredType custom = registeredType(properties);
        if (custom != null) return custom.provider().createNamed(Map.copyOf(properties), registryName);
        String kind = kind(properties);
        return "bow".equals(kind) ? new MLuaRegisteredBow(itemProperties(properties), registryName) : create(properties);
    }
    public Item createScripted(Map<String, Object> properties, String behaviorKey) {
        RegisteredType custom = registeredType(properties);
        if (custom != null) return custom.provider().createScripted(Map.copyOf(properties), behaviorKey);
        return new MLuaScriptedItem(itemProperties(properties), behaviorKey);
    }
    private Item create(Map<String, Object> properties) { return new Item(itemProperties(properties)); }
    public Item.Properties makeProperties(Map<String, Object> properties) {
        return itemProperties(properties);
    }
    private Item.Properties itemProperties(Map<String, Object> properties) {
        Item.Properties result = new Item.Properties();
        Object maxStack = properties == null ? null : properties.get("maxStackSize");
        if (maxStack instanceof Number number) result.stacksTo(Math.max(1, number.intValue()));
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

    private static RegisteredType registeredType(Map<String, Object> properties) {
        RegisteredType type = REGISTERED_TYPES.get(kind(properties));
        return type;
    }

    private static String normalizeTypeName(String name) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("[MLua] Item kind must be non-empty");
        String normalized = name.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z][a-z0-9_]*")) throw new IllegalArgumentException("[MLua] Invalid item kind: " + name);
        return normalized;
    }

    private record RegisteredType(String description, TypeProvider provider) { }

    public interface TypeProvider {
        Item createNamed(Map<String, Object> properties, String registryName);
        Item createScripted(Map<String, Object> properties, String behaviorKey);
    }
}
