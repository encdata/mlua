package dev.mlua.minecraft.scripted;

import dev.mlua.runtime.registry.MLuaItemFactory;
import dev.mlua.runtime.registry.MLuaItemHooks;
import dev.mlua.runtime.registry.MLuaRegistryNameHelper;
import dev.mlua.minecraft.binding.MLuaItemTooltip;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
//--version 26.1
import net.minecraft.world.item.component.TooltipDisplay;
//--endversion
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/** Legacy-target block item; callback context and failure handling belong to MLuaItemFactory. */
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

    @SuppressWarnings("rawtypes")
    public void initializeClient(Consumer consumer) {
        MLuaItemHooks.initializeClient(consumer);
    }

//--version 1.20.1
    @Override
    public void appendHoverText(ItemStack stack, @javax.annotation.Nullable Level level,
            List<Component> tooltip, TooltipFlag flag) {
        MLuaItemTooltip.append(this, tooltip::add);
        super.appendHoverText(stack, level, tooltip, flag);
    }
//--endversion
//--version 1.21.1
    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
            List<Component> tooltip, TooltipFlag flag) {
        MLuaItemTooltip.append(this, tooltip::add);
        super.appendHoverText(stack, context, tooltip, flag);
    }
//--endversion
//--version 26.1
    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
            Consumer<Component> tooltip, TooltipFlag flag) {
        MLuaItemTooltip.append(this, tooltip);
        super.appendHoverText(stack, context, display, tooltip, flag);
    }
//--endversion

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        InteractionResultHolder<ItemStack> result = MLuaItemFactory.invokeOnUse(this.behaviorKey, level, player, hand);
        return result != null ? result : super.use(level, player, hand);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        InteractionResult result = MLuaItemFactory.invokeOnUseOnBlock(this.behaviorKey, context);
        return result != null ? result : super.useOn(context);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity livingEntity) {
        MLuaItemFactory.invokeOnConsume(this.behaviorKey, stack, level, livingEntity);
        return super.finishUsingItem(stack, level, livingEntity);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        MLuaItemFactory.invokeOnInventoryTick(this.behaviorKey, stack, level, entity, slot, selected);
        super.inventoryTick(stack, level, entity, slot, selected);
    }

    @Override
    public void onCraftedBy(ItemStack stack, Level level, Player player) {
        MLuaItemFactory.invokeOnCrafted(this.behaviorKey, stack, level, player);
        super.onCraftedBy(stack, level, player);
    }
}
