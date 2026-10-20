package dev.mlua.minecraft.scripted;

import dev.mlua.core.MLuaRuntime;
import dev.mlua.core.MLuaLogger;
import dev.mlua.mc.impl.entity.MCServerPlayerImpl;
import dev.mlua.minecraft.binding.MLuaBlockWrapper;
import dev.mlua.minecraft.binding.MLuaItemWrapper;
import dev.mlua.modules.players.classes.MLuaEntityWrapper;
import dev.mlua.modules.players.classes.MLuaPlayerWrapper;
import dev.mlua.runtime.registry.MLuaItemHooks;
import dev.mlua.runtime.registry.MLuaLoaderRegistry;
import dev.mlua.runtime.registry.MLuaRegistryNameHelper;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

public class MLuaScriptedBlockItem extends BlockItem {
    private final String behaviorKey;
    private final String displayName;

    public MLuaScriptedBlockItem(Block block, Item.Properties properties, String behaviorKey) {
        super(block, properties);
        this.behaviorKey = behaviorKey;
        this.displayName = MLuaRegistryNameHelper.displayName(behaviorKey);
    }

    public Component getName(ItemStack stack) {
        return Component.literal(this.displayName);
    }

    @SuppressWarnings("rawtypes")
    public void initializeClient(Consumer consumer) {
        MLuaItemHooks.initializeClient(consumer);
    }

    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        var id = BuiltInRegistries.ITEM.getKey(this);
        if (id != null) {
            List<String> desc = MLuaLoaderRegistry.getItemDescription(id.getNamespace(), id.getPath());
            if (desc != null) {
                for (String line : desc) {
                    tooltip.add(Component.literal(line).withStyle(style -> style.withItalic(true).withColor(0x808080)));
                }
            }
        }
        super.appendHoverText(stack, context, tooltip, flag);
    }

    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        LuaValue onUse = MLuaBehaviorRegistry.getBehavior("item", this.behaviorKey, "onUse");
        if (!onUse.isnil()) {
            try {
                LuaTable ctx = this.createContext(player, hand);
                Varargs result = this.invokeBehavior("onUse", onUse, ctx);
                if (result.arg1().isboolean() && result.arg1().toboolean()) {
                    return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
                }
            } catch (Exception exception) {
                MLuaLogger.LOGGER.error("MLua block item onUse callback failed for '{}': {}", this.behaviorKey, exception);
            }
        }
        return super.use(level, player, hand);
    }

    public InteractionResult useOn(UseOnContext context) {
        LuaValue onUseOnBlock = MLuaBehaviorRegistry.getBehavior("item", this.behaviorKey, "onUseOnBlock");
        if (!onUseOnBlock.isnil()) {
            try {
                LuaTable ctx = this.createUseOnBlockContext(context);
                Varargs result = this.invokeBehavior("onUseOnBlock", onUseOnBlock, ctx);
                if (result.arg1().isboolean() && result.arg1().toboolean()) {
                    return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
                }
            } catch (Exception exception) {
                MLuaLogger.LOGGER.error("MLua block item onUseOnBlock callback failed for '{}': {}", this.behaviorKey, exception);
            }
        }
        return super.useOn(context);
    }

    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity livingEntity) {
        LuaValue onConsume = MLuaBehaviorRegistry.getBehavior("item", this.behaviorKey, "onConsume");
        if (!onConsume.isnil()) {
            try {
                LuaTable ctx = new LuaTable();
                if (livingEntity instanceof ServerPlayer sp) {
                    ctx.set("Player", MLuaPlayerWrapper.playerTable(MCServerPlayerImpl.fromReal(sp), MLuaRuntime.registrationForLevel(level)));
                }
                ctx.set("World", level.dimension().location().toString());
                ctx.set("Item", MLuaItemWrapper.itemTable(stack, MLuaRuntime.registrationForLevel(level)));
                this.invokeBehavior("onConsume", onConsume, ctx);
            } catch (Exception exception) {
                MLuaLogger.LOGGER.error("MLua block item onConsume callback failed for '{}': {}", this.behaviorKey, exception);
            }
        }
        return super.finishUsingItem(stack, level, livingEntity);
    }

    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        LuaValue onInventoryTick = MLuaBehaviorRegistry.getBehavior("item", this.behaviorKey, "onInventoryTick");
        if (!onInventoryTick.isnil()) {
            try {
                LuaTable ctx = this.createInventoryContext(stack, level, entity, slot, selected);
                this.invokeBehavior("onInventoryTick", onInventoryTick, ctx);
            } catch (Exception exception) {
                MLuaLogger.LOGGER.error("MLua block item onInventoryTick callback failed for '{}': {}", this.behaviorKey, exception);
            }
        }
        super.inventoryTick(stack, level, entity, slot, selected);
    }

    public void onCraftedBy(ItemStack stack, Level level, Player player) {
        LuaValue onCrafted = MLuaBehaviorRegistry.getBehavior("item", this.behaviorKey, "onCrafted");
        if (!onCrafted.isnil()) {
            try {
                LuaTable ctx = this.createContext(player, player.getUsedItemHand());
                ctx.set("Item", MLuaItemWrapper.itemTable(stack, MLuaRuntime.registrationForLevel(level)));
                this.invokeBehavior("onCrafted", onCrafted, ctx);
            } catch (Exception exception) {
                MLuaLogger.LOGGER.error("MLua block item onCrafted callback failed for '{}': {}", this.behaviorKey, exception);
            }
        }
        super.onCraftedBy(stack, level, player);
    }

    private LuaTable createContext(Player player, InteractionHand hand) {
        LuaTable ctx = new LuaTable();
        if (player instanceof ServerPlayer sp) {
           ctx.set("Player", MLuaPlayerWrapper.playerTable(MCServerPlayerImpl.fromReal(sp), MLuaRuntime.registrationForLevel(player.level())));
        }
        ctx.set("World", player.level().dimension().location().toString());
        ctx.set("Position", MLuaPlayerWrapper.positionTable(player.position().x, player.position().y, player.position().z));
        ctx.set("Hand", hand.name().toLowerCase());
        ctx.set("Item", MLuaItemWrapper.itemTable(player.getItemInHand(hand), MLuaRuntime.registrationForLevel(player.level())));
        ctx.set("ItemStack", this.behaviorKey);
        return ctx;
    }

    private Varargs invokeBehavior(String event, LuaValue callback, LuaTable context) {
        return MLuaBehaviorRegistry.invokeCallback("item", this.behaviorKey, event, callback,
            LuaValue.varargsOf(new LuaValue[]{context}));
    }

    private LuaTable createUseOnBlockContext(UseOnContext context) {
        LuaTable ctx = new LuaTable();
        Player player = context.getPlayer();
        if (player instanceof ServerPlayer sp) {
           ctx.set("Player", MLuaPlayerWrapper.playerTable(MCServerPlayerImpl.fromReal(sp), MLuaRuntime.registrationForLevel(context.getLevel())));
        }
        BlockPos pos = context.getClickedPos();
        ctx.set("World", context.getLevel().dimension().location().toString());
        ctx.set("Position", MLuaPlayerWrapper.positionTable((double) pos.getX() + 0.5, (double) pos.getY() + 0.5, (double) pos.getZ() + 0.5));
        ctx.set("Block", MLuaBlockWrapper.blockTable(context.getLevel(), pos, MLuaRuntime.registrationForLevel(context.getLevel())));
        ctx.set("Face", context.getClickedFace().getName());
        ctx.set("Hand", context.getHand().name().toLowerCase());
        ctx.set("Item", MLuaItemWrapper.itemTable(context.getItemInHand(), MLuaRuntime.registrationForLevel(context.getLevel())));
        ctx.set("ItemStack", this.behaviorKey);
        return ctx;
    }

    private LuaTable createInventoryContext(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        LuaTable ctx = new LuaTable();
        if (entity instanceof ServerPlayer sp) {
            ctx.set("Player", MLuaPlayerWrapper.playerTable(MCServerPlayerImpl.fromReal(sp), MLuaRuntime.registrationForLevel(level)));
        }
        ctx.set("World", level.dimension().location().toString());
        if (entity instanceof LivingEntity living) {
            ctx.set("Entity", MLuaEntityWrapper.entityTable(living, MLuaRuntime.registrationForLevel(level)));
        }
        ctx.set("Slot", slot);
        ctx.set("Selected", LuaValue.valueOf(selected));
        ctx.set("Item", MLuaItemWrapper.itemTable(stack, MLuaRuntime.registrationForLevel(level)));
        ctx.set("ItemStack", this.behaviorKey);
        return ctx;
    }
}
