package dev.mlua.runtime.registry;

import dev.mlua.core.MLuaRuntime;
import dev.mlua.mc.impl.entity.MCServerPlayerImpl;
import dev.mlua.minecraft.binding.MLuaBlockWrapper;
import dev.mlua.minecraft.binding.MLuaItemWrapper;
import dev.mlua.minecraft.scripted.MLuaBehaviorRegistry;
import dev.mlua.modules.players.classes.MLuaEntityWrapper;
import dev.mlua.modules.players.classes.MLuaPlayerWrapper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

/** Builds Lua item callback contexts for Minecraft's 26.1 item hooks. */
public final class MLuaItemBehaviorDispatcher {
    private MLuaItemBehaviorDispatcher() { }

    public static InteractionResult onUse(String key, Level level, Player player, InteractionHand hand) {
        LuaTable context = new LuaTable();
        context.set("World", level.dimension().identifier().toString());
        context.set("Position", MLuaPlayerWrapper.positionTable(player.getX(), player.getY(), player.getZ()));
        context.set("Hand", hand.name().toLowerCase(java.util.Locale.ROOT));
        context.set("Item", MLuaItemWrapper.itemTable(player.getItemInHand(hand), registration(level)));
        context.set("ItemStack", key);
        addPlayer(context, player, registration(level));
        Varargs result = invoke(level, key, "onUse", context);
        return result.arg1().isboolean() && result.arg1().toboolean() ? InteractionResult.SUCCESS : null;
    }

    public static InteractionResult onUseOnBlock(String key, UseOnContext use) {
        Level level = use.getLevel();
        var registration = registration(level);
        var position = use.getClickedPos();
        LuaTable context = new LuaTable();
        context.set("World", level.dimension().identifier().toString());
        context.set("Position", MLuaPlayerWrapper.positionTable(
            position.getX() + 0.5, position.getY() + 0.5, position.getZ() + 0.5));
        context.set("Face", use.getClickedFace().getName());
        context.set("Hand", use.getHand().name().toLowerCase(java.util.Locale.ROOT));
        context.set("Block", MLuaBlockWrapper.blockTable(level, use.getClickedPos(), registration));
        context.set("Item", MLuaItemWrapper.itemTable(use.getItemInHand(), registration));
        context.set("ItemStack", key);
        addPlayer(context, use.getPlayer(), registration);
        Varargs result = invoke(level, key, "onUseOnBlock", context);
        return result.arg1().isboolean() && result.arg1().toboolean() ? InteractionResult.SUCCESS : null;
    }

    public static void onConsume(String key, ItemStack stack, Level level, LivingEntity entity) {
        if (level.isClientSide()) return;
        var registration = registration(level);
        LuaTable context = new LuaTable();
        context.set("World", level.dimension().identifier().toString());
        context.set("Item", MLuaItemWrapper.itemTable(stack, registration));
        context.set("ItemStack", key);
        context.set("Entity", MLuaEntityWrapper.entityTable(entity, registration));
        addPlayer(context, entity instanceof Player player ? player : null, registration);
        invoke(level, key, "onConsume", context);
    }

    public static void onInventoryTick(String key, ItemStack stack, ServerLevel level,
                                       Entity entity, EquipmentSlot slot) {
        var registration = registration(level);
        LuaTable context = new LuaTable();
        context.set("World", level.dimension().identifier().toString());
        context.set("Item", MLuaItemWrapper.itemTable(stack, registration));
        context.set("ItemStack", key);
        context.set("Slot", slot.ordinal());
        context.set("EquipmentSlot", slot.name());
        context.set("Selected", LuaValue.valueOf(entity instanceof Player player
            && player.getMainHandItem() == stack));
        context.set("Entity", MLuaEntityWrapper.entityTable(entity, registration));
        addPlayer(context, entity instanceof Player player ? player : null, registration);
        invoke(level, key, "onInventoryTick", context);
    }

    public static boolean onAttackEntity(String key, ItemStack stack, LivingEntity target, LivingEntity attacker) {
        var registration = registration(target.level());
        LuaTable targetTable = entity(target, registration);
        LuaTable context = new LuaTable();
        context.set("Item", MLuaItemWrapper.itemTable(stack, registration));
        context.set("ItemStack", key);
        context.set("Entity", targetTable);
        context.set("Target", targetTable);
        context.set("Attacker", entity(attacker, registration));
        addPlayer(context, attacker instanceof Player player ? player : null, registration);
        Varargs result = invoke(target.level(), key, "onAttackEntity", context);
        return !result.arg1().isboolean() || result.arg1().toboolean();
    }

    public static void onCrafted(String key, ItemStack stack, Player player) {
        Level level = player.level();
        var registration = registration(level);
        LuaTable context = new LuaTable();
        context.set("World", level.dimension().identifier().toString());
        context.set("Item", MLuaItemWrapper.itemTable(stack, registration));
        context.set("ItemStack", key);
        context.set("Position", MLuaPlayerWrapper.positionTable(player.getX(), player.getY(), player.getZ()));
        context.set("Hand", player.getUsedItemHand() == null ? "main_hand"
            : player.getUsedItemHand().name().toLowerCase(java.util.Locale.ROOT));
        addPlayer(context, player, registration);
        invoke(level, key, "onCrafted", context);
    }

    private static Varargs invoke(Level level, String key, String event, LuaTable context) {
        MLuaBehaviorRegistry.Snapshot behaviors = MLuaBehaviorRegistry.snapshotBehaviors("item", key);
        LuaValue callback = behaviors.callbacks().getOrDefault(event, LuaValue.NIL);
        return MLuaBehaviorRegistry.invokeGameplayCallback(level, null, "item", key, event,
            behaviors, callback, LuaValue.varargsOf(new LuaValue[]{context}));
    }

    private static void addPlayer(LuaTable context, Player player,
                                  dev.mlua.module.MLuaRegistration registration) {
        if (player instanceof ServerPlayer serverPlayer) {
            context.set("Player", MLuaPlayerWrapper.playerTable(MCServerPlayerImpl.fromReal(serverPlayer), registration));
        }
    }

    private static LuaTable entity(LivingEntity entity, dev.mlua.module.MLuaRegistration registration) {
        return entity instanceof ServerPlayer player
            ? MLuaPlayerWrapper.playerTable(MCServerPlayerImpl.fromReal(player), registration)
            : MLuaEntityWrapper.entityTable(entity, registration);
    }

    private static dev.mlua.module.MLuaRegistration registration(Level level) {
        return MLuaRuntime.registrationForLevel(level);
    }

}
