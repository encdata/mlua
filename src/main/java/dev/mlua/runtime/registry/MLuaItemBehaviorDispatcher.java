package dev.mlua.runtime.registry;

import dev.mlua.core.MLuaLogger;
import dev.mlua.core.MLuaRuntime;
import dev.mlua.mc.impl.entity.MCServerPlayerImpl;
import dev.mlua.minecraft.binding.MLuaBlockWrapper;
import dev.mlua.minecraft.binding.MLuaItemWrapper;
import dev.mlua.minecraft.scripted.MLuaBehaviorRegistry;
import dev.mlua.modules.players.classes.MLuaEntityWrapper;
import dev.mlua.modules.players.classes.MLuaPlayerWrapper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

/** Builds item callback contexts and applies the shared scripted item interaction rules. */
final class MLuaItemBehaviorDispatcher {
    private MLuaItemBehaviorDispatcher() { }

    static LuaValue onAttackEntityCallback(String behaviorKey, ItemStack stack,
                                           LivingEntity target, LivingEntity attacker) {
        MLuaBehaviorRegistry.Snapshot behaviors = MLuaBehaviorRegistry.snapshotBehaviors("item", behaviorKey);
        LuaValue callback = behaviors.callbacks().getOrDefault("onAttackEntity", LuaValue.NIL);
        if (callback.isnil()) return LuaValue.NIL;
        try {
            var registration = MLuaRuntime.registrationForLevel(target.level());
            LuaTable targetTable = target instanceof ServerPlayer player
                ? MLuaPlayerWrapper.playerTable(MCServerPlayerImpl.fromReal(player), registration)
                : MLuaEntityWrapper.entityTable(target, registration);
            LuaTable attackerTable = attacker instanceof ServerPlayer player
                ? MLuaPlayerWrapper.playerTable(MCServerPlayerImpl.fromReal(player), registration)
                : MLuaEntityWrapper.entityTable(attacker, registration);
            LuaTable context = new LuaTable();
            context.set("Item", MLuaItemWrapper.itemTable(stack, registration));
            context.set("Entity", targetTable);
            context.set("Target", targetTable);
            context.set("Attacker", attackerTable);
            if (attacker instanceof ServerPlayer) context.set("Player", attackerTable);
            context.set("ItemStack", behaviorKey);
            Varargs result = MLuaBehaviorRegistry.invokeGameplayCallback(target.level(), null,
                "item", behaviorKey, "onAttackEntity", behaviors, callback,
                LuaValue.varargsOf(new LuaValue[]{context}));
            return result.arg1();
        } catch (Exception exception) {
            MLuaLogger.LOGGER.error("MLua item onAttackEntity callback failed for '{}': {}",
                behaviorKey, exception.getMessage());
            return LuaValue.NIL;
        }
    }

    static boolean onAttackEntity(String behaviorKey, ItemStack stack,
                                  LivingEntity target, LivingEntity attacker) {
        LuaValue result = onAttackEntityCallback(behaviorKey, stack, target, attacker);
        return !result.isboolean() || result.toboolean();
    }

    static void onConsume(String behaviorKey, ItemStack stack, Level level, LivingEntity livingEntity) {
        if (level.isClientSide) return;
        MLuaBehaviorRegistry.Snapshot behaviors = MLuaBehaviorRegistry.snapshotBehaviors("item", behaviorKey);
        LuaValue callback = behaviors.callbacks().getOrDefault("onConsume", LuaValue.NIL);
        if (callback.isnil()) return;
        try {
            var registration = MLuaRuntime.registrationForLevel(level);
            LuaTable context = new LuaTable();
            if (livingEntity instanceof ServerPlayer player) {
                context.set("Player", MLuaPlayerWrapper.playerTable(MCServerPlayerImpl.fromReal(player), registration));
            }
            context.set("World", level.dimension().location().toString());
            context.set("Item", MLuaItemWrapper.itemTable(stack, registration));
            context.set("ItemStack", behaviorKey);
            context.set("Entity", MLuaEntityWrapper.entityTable(livingEntity, registration));
            invoke(level, null, behaviorKey, "onConsume", behaviors, callback, context);
        } catch (Exception failure) {
            MLuaLogger.LOGGER.error("MLua item onConsume callback failed for '{}': {}",
                behaviorKey, failure.getMessage());
        }
    }

    static InteractionResultHolder<ItemStack> onUse(String behaviorKey, Level level,
                                                      Player player, InteractionHand hand) {
        MLuaBehaviorRegistry.Snapshot behaviors = MLuaBehaviorRegistry.snapshotBehaviors("item", behaviorKey);
        LuaValue callback = behaviors.callbacks().getOrDefault("onUse", LuaValue.NIL);
        if (callback.isnil()) return null;
        try {
            var registration = MLuaRuntime.registrationForLevel(level);
            LuaTable context = new LuaTable();
            if (player instanceof ServerPlayer serverPlayer) {
                context.set("Player", MLuaPlayerWrapper.playerTable(
                    MCServerPlayerImpl.fromReal(serverPlayer), registration));
            }
            context.set("World", player.level().dimension().location().toString());
            context.set("Position", MLuaPlayerWrapper.positionTable(
                player.position().x, player.position().y, player.position().z));
            context.set("Hand", hand.name().toLowerCase());
            context.set("Item", MLuaItemWrapper.itemTable(player.getItemInHand(hand), registration));
            context.set("ItemStack", behaviorKey);
            Varargs result = invoke(level, null, behaviorKey, "onUse", behaviors, callback, context);
            if (result.arg1().isboolean() && result.arg1().toboolean()) {
                return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
            }
        } catch (Exception failure) {
            MLuaLogger.LOGGER.error("MLua item onUse callback failed for '{}'", behaviorKey, failure);
        }
        return null;
    }

    static InteractionResult onUseOnBlock(String behaviorKey, UseOnContext context) {
        MLuaBehaviorRegistry.Snapshot behaviors = MLuaBehaviorRegistry.snapshotBehaviors("item", behaviorKey);
        LuaValue callback = behaviors.callbacks().getOrDefault("onUseOnBlock", LuaValue.NIL);
        if (callback.isnil()) return null;
        try {
            Level level = context.getLevel();
            var registration = MLuaRuntime.registrationForLevel(level);
            LuaTable luaContext = new LuaTable();
            Player player = context.getPlayer();
            if (player instanceof ServerPlayer serverPlayer) {
                luaContext.set("Player", MLuaPlayerWrapper.playerTable(
                    MCServerPlayerImpl.fromReal(serverPlayer), registration));
            }
            var position = context.getClickedPos();
            luaContext.set("World", level.dimension().location().toString());
            luaContext.set("Position", MLuaPlayerWrapper.positionTable(
                (double) position.getX() + 0.5, (double) position.getY() + 0.5,
                (double) position.getZ() + 0.5));
            luaContext.set("Block", MLuaBlockWrapper.blockTable(level, position, registration));
            luaContext.set("Face", context.getClickedFace().getName());
            luaContext.set("Hand", context.getHand().name().toLowerCase());
            luaContext.set("Item", MLuaItemWrapper.itemTable(context.getItemInHand(), registration));
            luaContext.set("ItemStack", behaviorKey);
            Varargs result = invoke(level, position, behaviorKey, "onUseOnBlock", behaviors, callback, luaContext);
            if (result.arg1().isboolean() && result.arg1().toboolean()) {
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
        } catch (Exception failure) {
            MLuaLogger.LOGGER.error("MLua item onUseOnBlock callback failed for '{}': {}",
                behaviorKey, failure.getMessage());
        }
        return null;
    }

    static void onInventoryTick(String behaviorKey, ItemStack stack, Level level,
                                Entity entity, int slot, boolean selected) {
        MLuaBehaviorRegistry.Snapshot behaviors = MLuaBehaviorRegistry.snapshotBehaviors("item", behaviorKey);
        LuaValue callback = behaviors.callbacks().getOrDefault("onInventoryTick", LuaValue.NIL);
        if (callback.isnil()) return;
        try {
            var registration = MLuaRuntime.registrationForLevel(level);
            LuaTable context = new LuaTable();
            if (entity instanceof ServerPlayer player) {
                context.set("Player", MLuaPlayerWrapper.playerTable(MCServerPlayerImpl.fromReal(player), registration));
            }
            context.set("World", level.dimension().location().toString());
            if (entity instanceof LivingEntity living) {
                context.set("Entity", MLuaEntityWrapper.entityTable(living, registration));
            }
            context.set("Slot", slot);
            context.set("SlotIndex", slot + 1);
            String equipmentSlot = equipmentSlot(entity, stack, selected);
            if (equipmentSlot != null) context.set("EquipmentSlot", equipmentSlot);
            context.set("Selected", LuaValue.valueOf(selected));
            context.set("Item", MLuaItemWrapper.itemTable(stack, registration));
            context.set("ItemStack", behaviorKey);
            invoke(level, null, behaviorKey, "onInventoryTick", behaviors, callback, context);
        } catch (Exception failure) {
            MLuaLogger.LOGGER.error("MLua item onInventoryTick callback failed for '{}': {}",
                behaviorKey, failure.getMessage());
        }
    }

    static void onCrafted(String behaviorKey, ItemStack stack, Level level, Player player) {
        MLuaBehaviorRegistry.Snapshot behaviors = MLuaBehaviorRegistry.snapshotBehaviors("item", behaviorKey);
        LuaValue callback = behaviors.callbacks().getOrDefault("onCrafted", LuaValue.NIL);
        if (callback.isnil()) return;
        try {
            var registration = MLuaRuntime.registrationForLevel(level);
            LuaTable context = new LuaTable();
            if (player instanceof ServerPlayer serverPlayer) {
                context.set("Player", MLuaPlayerWrapper.playerTable(
                    MCServerPlayerImpl.fromReal(serverPlayer), registration));
            }
            context.set("World", player.level().dimension().location().toString());
            context.set("Position", MLuaPlayerWrapper.positionTable(
                player.position().x, player.position().y, player.position().z));
            context.set("Hand", player.getUsedItemHand() == null ? "main_hand"
                : player.getUsedItemHand().name().toLowerCase());
            context.set("ItemStack", behaviorKey);
            context.set("Item", MLuaItemWrapper.itemTable(stack, registration));
            invoke(level, null, behaviorKey, "onCrafted", behaviors, callback, context);
        } catch (Exception failure) {
            MLuaLogger.LOGGER.error("MLua item onCrafted callback failed for '{}': {}",
                behaviorKey, failure.getMessage());
        }
    }

    private static String equipmentSlot(Entity entity, ItemStack stack, boolean selected) {
        if (selected) return net.minecraft.world.entity.EquipmentSlot.MAINHAND.name();
        if (!(entity instanceof LivingEntity living)) return null;
        for (net.minecraft.world.entity.EquipmentSlot slot : net.minecraft.world.entity.EquipmentSlot.values()) {
            if (living.getItemBySlot(slot) == stack) return slot.name();
        }
        return null;
    }

    private static Varargs invoke(Level level, BlockPos position, String behaviorKey, String event,
                                  MLuaBehaviorRegistry.Snapshot behaviors, LuaValue callback,
                                  LuaTable context) {
        return MLuaBehaviorRegistry.invokeGameplayCallback(level, position, "item", behaviorKey, event,
            behaviors, callback, LuaValue.varargsOf(new LuaValue[]{context}));
    }
}
