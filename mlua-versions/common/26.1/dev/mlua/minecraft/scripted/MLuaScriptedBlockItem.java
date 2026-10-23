package dev.mlua.minecraft.scripted;

import dev.mlua.runtime.registry.MLuaItemBehaviorDispatcher;
import dev.mlua.runtime.registry.MLuaLoaderRegistry;
import dev.mlua.runtime.registry.MLuaRegistryNameHelper;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.item.component.TooltipDisplay;

/** 26.1 block items share the same scripted lifecycle and callback dispatcher as scripted items. */
public class MLuaScriptedBlockItem extends BlockItem {
    private final String behaviorKey;
    private final String displayName;

    public MLuaScriptedBlockItem(Block block, Item.Properties properties, String behaviorKey) {
        super(block, properties);
        this.behaviorKey = behaviorKey;
        this.displayName = MLuaRegistryNameHelper.displayName(behaviorKey);
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.literal(this.displayName);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        var id = BuiltInRegistries.ITEM.getKey(this);
        if (id != null) {
            List<String> description = MLuaLoaderRegistry.getItemDescription(id.getNamespace(), id.getPath());
            if (description != null) {
                for (String line : description) {
                    tooltip.accept(Component.literal(line).withStyle(style ->
                        style.withItalic(true).withColor(0x808080)));
                }
            }
        }
        super.appendHoverText(stack, context, display, tooltip, flag);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        InteractionResult result = MLuaItemBehaviorDispatcher.onUse(this.behaviorKey, level, player, hand);
        return result == null ? super.use(level, player, hand) : result;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        InteractionResult result = MLuaItemBehaviorDispatcher.onUseOnBlock(this.behaviorKey, context);
        return result == null ? super.useOn(context) : result;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        MLuaItemBehaviorDispatcher.onConsume(this.behaviorKey, stack, level, entity);
        return super.finishUsingItem(stack, level, entity);
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, EquipmentSlot equipmentSlot) {
        MLuaItemBehaviorDispatcher.onInventoryTick(this.behaviorKey, stack, level, entity, equipmentSlot);
        super.inventoryTick(stack, level, entity, equipmentSlot);
    }

    @Override
    public void hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (MLuaItemBehaviorDispatcher.onAttackEntity(this.behaviorKey, stack, target, attacker)) {
            super.hurtEnemy(stack, target, attacker);
        }
    }

    @Override
    public void onCraftedBy(ItemStack stack, Player player) {
        MLuaItemBehaviorDispatcher.onCrafted(this.behaviorKey, stack, player);
        super.onCraftedBy(stack, player);
    }
}
