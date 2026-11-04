package dev.mlua.minecraft.scripted;

import dev.mlua.runtime.registry.MLuaItemFactory;
import dev.mlua.runtime.registry.MLuaRegistryNameHelper;
import dev.mlua.minecraft.binding.MLuaItemTooltip;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/** Minecraft 26.1 item hooks delegate Lua context construction to the item behavior dispatcher. */
public class MLuaScriptedItem extends Item {
    private final String behaviorKey;
    private final String displayName;

    public MLuaScriptedItem(Properties properties, String behaviorKey) {
        super(properties);
        this.behaviorKey = behaviorKey;
        this.displayName = MLuaRegistryNameHelper.displayName(behaviorKey);
    }

    @Override public Component getName(ItemStack stack) {
        return Component.literal(this.displayName);
    }

    @Override public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                          net.minecraft.world.item.component.TooltipDisplay display,
                                          Consumer<Component> tooltip, TooltipFlag flag) {
        MLuaItemTooltip.append(this, tooltip);
        super.appendHoverText(stack, context, display, tooltip, flag);
    }

    @Override public InteractionResult use(Level level, Player player, InteractionHand hand) {
        InteractionResult result = MLuaItemFactory.invokeOnUse(this.behaviorKey, level, player, hand);
        return result == null ? super.use(level, player, hand) : result;
    }

    @Override public InteractionResult useOn(UseOnContext context) {
        InteractionResult result = MLuaItemFactory.invokeOnUseOnBlock(this.behaviorKey, context);
        return result == null ? super.useOn(context) : result;
    }

    @Override public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        MLuaItemFactory.invokeOnConsume(this.behaviorKey, stack, level, entity);
        return super.finishUsingItem(stack, level, entity);
    }

    @Override public void inventoryTick(ItemStack stack, ServerLevel level,
                                        Entity entity, EquipmentSlot equipmentSlot) {
        MLuaItemFactory.invokeOnInventoryTick(this.behaviorKey, stack, level, entity, equipmentSlot);
        super.inventoryTick(stack, level, entity, equipmentSlot);
    }

    @Override public void hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (MLuaItemFactory.invokeOnAttackEntity(this.behaviorKey, stack, target, attacker)) {
            super.hurtEnemy(stack, target, attacker);
        }
    }

    @Override public void onCraftedBy(ItemStack stack, Player player) {
        MLuaItemFactory.invokeOnCrafted(this.behaviorKey, stack, player);
        super.onCraftedBy(stack, player);
    }
}
