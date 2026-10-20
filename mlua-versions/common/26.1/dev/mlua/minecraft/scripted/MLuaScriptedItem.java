package dev.mlua.minecraft.scripted;

import dev.mlua.core.MLuaRuntime;
import dev.mlua.minecraft.binding.MLuaBlockWrapper;
import dev.mlua.minecraft.binding.MLuaItemWrapper;
import dev.mlua.runtime.registry.MLuaLoaderRegistry;
import dev.mlua.runtime.registry.MLuaRegistryNameHelper;
import dev.mlua.modules.players.classes.MLuaPlayerWrapper;
import dev.mlua.modules.players.classes.MLuaEntityWrapper;
import dev.mlua.mc.impl.entity.MCServerPlayerImpl;
import java.util.List;
import java.util.function.Consumer;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;

public class MLuaScriptedItem extends Item {
    private final String behaviorKey;
    private final String displayName;

    public MLuaScriptedItem(Properties properties, String behaviorKey) {
        super(properties);
        this.behaviorKey = behaviorKey;
        this.displayName = MLuaRegistryNameHelper.displayName(behaviorKey);
    }

    public Component getName(ItemStack stack) {
        return Component.literal(this.displayName);
    }

    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                net.minecraft.world.item.component.TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        var id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(this);
        if (id != null) {
            List<String> description = MLuaLoaderRegistry.getItemDescription(id.getNamespace(), id.getPath());
            if (description != null) {
                for (String line : description) {
                    tooltip.accept(Component.literal(line).withStyle(style -> style.withItalic(true).withColor(0x808080)));
                }
            }
        }
        super.appendHoverText(stack, context, display, tooltip, flag);
    }

    @Override public InteractionResult use(Level level, Player player, InteractionHand hand) {
        LuaValue callback = MLuaBehaviorRegistry.getBehavior("item", behaviorKey, "onUse");
        if (!callback.isnil()) {
            Varargs result = MLuaBehaviorCallbackFailureTracker.invoke(level, null, "item", behaviorKey,
                "onUse", callback, () -> {
            LuaTable context = new LuaTable();
            context.set("World", level.dimension().identifier().toString());
            context.set("Hand", hand.name().toLowerCase(java.util.Locale.ROOT));
            context.set("Position", MLuaPlayerWrapper.positionTable(player.getX(), player.getY(), player.getZ()));
            context.set("Item", MLuaItemWrapper.itemTable(player.getItemInHand(hand), MLuaRuntime.registrationForLevel(level)));
            if (player instanceof ServerPlayer serverPlayer) {
                context.set("Player", MLuaPlayerWrapper.playerTable(MCServerPlayerImpl.fromReal(serverPlayer), MLuaRuntime.registrationForLevel(level)));
            }
            return MLuaBehaviorRegistry.invokeCallback("item", behaviorKey, "onUse", callback,
                LuaValue.varargsOf(new LuaValue[]{context}));
            });
            if (result.arg1().isboolean() && result.arg1().toboolean()) return InteractionResult.SUCCESS;
        }
        return super.use(level, player, hand);
    }

    @Override public InteractionResult useOn(UseOnContext context) {
        LuaValue callback = MLuaBehaviorRegistry.getBehavior("item", behaviorKey, "onUseOnBlock");
        if (!callback.isnil()) {
            Level level = context.getLevel();
            Varargs result = MLuaBehaviorCallbackFailureTracker.invoke(level, null, "item", behaviorKey,
                "onUseOnBlock", callback, () -> {
            BlockPos position = context.getClickedPos();
            var registration = MLuaRuntime.registrationForLevel(level);
            LuaTable values = new LuaTable();
            values.set("World", level.dimension().identifier().toString());
            values.set("Position", MLuaPlayerWrapper.positionTable(
                position.getX() + 0.5, position.getY() + 0.5, position.getZ() + 0.5));
            values.set("Face", context.getClickedFace().getName());
            values.set("Hand", context.getHand().name().toLowerCase(java.util.Locale.ROOT));
            values.set("Block", MLuaBlockWrapper.blockTable(level, position, registration));
            values.set("Item", MLuaItemWrapper.itemTable(context.getItemInHand(), registration));
            if (context.getPlayer() instanceof ServerPlayer serverPlayer) {
                values.set("Player", MLuaPlayerWrapper.playerTable(MCServerPlayerImpl.fromReal(serverPlayer), registration));
            }
            return MLuaBehaviorRegistry.invokeCallback("item", behaviorKey, "onUseOnBlock", callback,
                LuaValue.varargsOf(new LuaValue[]{values}));
            });
            if (result.arg1().isboolean() && result.arg1().toboolean()) return InteractionResult.SUCCESS;
        }
        return super.useOn(context);
    }

    @Override public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!level.isClientSide()) {
            LuaValue callback = MLuaBehaviorRegistry.getBehavior("item", behaviorKey, "onConsume");
            if (!callback.isnil()) {
                MLuaBehaviorCallbackFailureTracker.invoke(level, null, "item", behaviorKey,
                    "onConsume", callback, () -> {
                var registration = MLuaRuntime.registrationForLevel(level);
                LuaTable context = new LuaTable();
                context.set("World", level.dimension().identifier().toString());
                context.set("Item", MLuaItemWrapper.itemTable(stack, registration));
                context.set("Entity", MLuaEntityWrapper.entityTable(entity, registration));
                if (entity instanceof ServerPlayer player) {
                    context.set("Player", MLuaPlayerWrapper.playerTable(MCServerPlayerImpl.fromReal(player), registration));
                }
                return MLuaBehaviorRegistry.invokeCallback("item", behaviorKey, "onConsume", callback,
                    LuaValue.varargsOf(new LuaValue[]{context}));
                });
            }
        }
        return super.finishUsingItem(stack, level, entity);
    }

    @Override public void inventoryTick(ItemStack stack, net.minecraft.server.level.ServerLevel level,
                                        Entity entity, net.minecraft.world.entity.EquipmentSlot equipmentSlot) {
        LuaValue callback = MLuaBehaviorRegistry.getBehavior("item", behaviorKey, "onInventoryTick");
        if (!callback.isnil()) {
            MLuaBehaviorCallbackFailureTracker.invoke(level, null, "item", behaviorKey,
                "onInventoryTick", callback, () -> {
            var registration = MLuaRuntime.registrationForLevel(level);
            LuaTable context = new LuaTable();
            context.set("World", level.dimension().identifier().toString());
            context.set("Item", MLuaItemWrapper.itemTable(stack, registration));
            context.set("Slot", equipmentSlot.ordinal());
            context.set("Selected", LuaValue.valueOf(entity instanceof Player player && player.getMainHandItem() == stack));
            context.set("Entity", MLuaEntityWrapper.entityTable(entity, registration));
            if (entity instanceof ServerPlayer player) {
                context.set("Player", MLuaPlayerWrapper.playerTable(MCServerPlayerImpl.fromReal(player), registration));
            }
            return MLuaBehaviorRegistry.invokeCallback("item", behaviorKey, "onInventoryTick", callback,
                LuaValue.varargsOf(new LuaValue[]{context}));
            });
        }
        super.inventoryTick(stack, level, entity, equipmentSlot);
    }

    @Override public void hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        LuaValue callback = MLuaBehaviorRegistry.getBehavior("item", behaviorKey, "onAttackEntity");
        if (!callback.isnil()) {
            Varargs result = MLuaBehaviorCallbackFailureTracker.invoke(target.level(), null, "item", behaviorKey,
                "onAttackEntity", callback, () -> {
            var registration = MLuaRuntime.registrationForLevel(target.level());
            LuaTable context = new LuaTable();
            context.set("Item", MLuaItemWrapper.itemTable(stack, registration));
            LuaTable targetTable = target instanceof ServerPlayer player
                ? MLuaPlayerWrapper.playerTable(MCServerPlayerImpl.fromReal(player), registration)
                : MLuaEntityWrapper.entityTable(target, registration);
            LuaTable attackerTable = attacker instanceof ServerPlayer player
                ? MLuaPlayerWrapper.playerTable(MCServerPlayerImpl.fromReal(player), registration)
                : MLuaEntityWrapper.entityTable(attacker, registration);
            context.set("Entity", targetTable);
            context.set("Target", targetTable);
            context.set("Attacker", attackerTable);
            if (attacker instanceof ServerPlayer player) {
                context.set("Player", attackerTable);
            }
            return MLuaBehaviorRegistry.invokeCallback("item", behaviorKey, "onAttackEntity", callback,
                LuaValue.varargsOf(new LuaValue[]{context}));
            });
            if (result.arg1().isboolean() && !result.arg1().toboolean()) return;
        }
        super.hurtEnemy(stack, target, attacker);
    }

    @Override public void onCraftedBy(ItemStack stack, Player player) {
        LuaValue callback = MLuaBehaviorRegistry.getBehavior("item", behaviorKey, "onCrafted");
        if (!callback.isnil()) {
            Level level = player.level();
            MLuaBehaviorCallbackFailureTracker.invoke(level, null, "item", behaviorKey,
                "onCrafted", callback, () -> {
            var registration = MLuaRuntime.registrationForLevel(player.level());
            LuaTable context = new LuaTable();
            context.set("World", player.level().dimension().identifier().toString());
            context.set("Item", MLuaItemWrapper.itemTable(stack, registration));
            if (player instanceof ServerPlayer serverPlayer) {
                context.set("Player", MLuaPlayerWrapper.playerTable(MCServerPlayerImpl.fromReal(serverPlayer), registration));
            }
            return MLuaBehaviorRegistry.invokeCallback("item", behaviorKey, "onCrafted", callback,
                LuaValue.varargsOf(new LuaValue[]{context}));
            });
        }
        super.onCraftedBy(stack, player);
    }

}
