package dev.mlua.minecraft.scripted;

import dev.mlua.core.MLuaRuntime;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import dev.mlua.runtime.registry.MLuaLoaderRegistry;
import dev.mlua.runtime.registry.MLuaItemFactory;
import dev.mlua.runtime.registry.MLuaItemHooks;
import dev.mlua.runtime.registry.MLuaRegistryNameHelper;
import dev.mlua.minecraft.binding.MLuaBlockWrapper;
import dev.mlua.minecraft.binding.MLuaItemWrapper;
import dev.mlua.modules.players.classes.MLuaEntityWrapper;
import dev.mlua.modules.players.classes.MLuaPlayerWrapper;
import dev.mlua.mc.impl.entity.MCServerPlayerImpl;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
//--version 26.1
import net.minecraft.world.item.component.TooltipDisplay;
//--endversion
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

public class MLuaScriptedItem extends Item {
   private final String behaviorKey;
   private final String displayName;

   public MLuaScriptedItem(Item.Properties properties, String behaviorKey) {
      super(properties);
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

//--version 1.20.1
    public void appendHoverText(ItemStack stack, @javax.annotation.Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        var id = BuiltInRegistries.ITEM.getKey(this);
        if (id != null) {
            List<String> desc = MLuaLoaderRegistry.getItemDescription(id.getNamespace(), id.getPath());
            if (desc != null) {
                for (String line : desc) {
                    tooltip.add(Component.literal(line).withStyle(style -> style.withItalic(true).withColor(0x808080)));
                }
            }
        }
        super.appendHoverText(stack, level, tooltip, flag);
    }
//--endversion
//--version 1.21.1
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
//--endversion

//--version 26.1
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                 TooltipDisplay display,
                                 java.util.function.Consumer<Component> tooltip, TooltipFlag flag) {
        var id = BuiltInRegistries.ITEM.getKey(this);
        if (id != null) {
            List<String> desc = MLuaLoaderRegistry.getItemDescription(id.getNamespace(), id.getPath());
            if (desc != null) for (String line : desc) {
                tooltip.accept(Component.literal(line).withStyle(style -> style.withItalic(true).withColor(0x808080)));
            }
        }
        super.appendHoverText(stack, context, display, tooltip, flag);
    }
//--endversion

    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity livingEntity) {
        if (!level.isClientSide) {
            MLuaBehaviorRegistry.Snapshot behaviors = MLuaBehaviorRegistry.snapshotBehaviors("item", this.behaviorKey);
            LuaValue onConsume = behaviors.callbacks().getOrDefault("onConsume", LuaValue.NIL);
            if (!onConsume.isnil()) {
                MLuaBehaviorCallbackFailureTracker.invoke(level, null, "item", this.behaviorKey,
                    "onConsume", behaviors, onConsume, () -> {
                    LuaTable ctx = new LuaTable();
                    if (livingEntity instanceof ServerPlayer sp) {
                        ctx.set("Player", MLuaPlayerWrapper.playerTable(MCServerPlayerImpl.fromReal(sp), MLuaRuntime.registrationForLevel(level)));
                    }
                    ctx.set("World", level.dimension().location().toString());
                    ctx.set("Item", MLuaItemWrapper.itemTable(stack, MLuaRuntime.registrationForLevel(level)));
                    return MLuaBehaviorRegistry.invokeCallback(behaviors, onConsume,
                        LuaValue.varargsOf(new LuaValue[]{ctx}));
                });
            }
        }
        return super.finishUsingItem(stack, level, livingEntity);
    }

    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
      MLuaBehaviorRegistry.Snapshot behaviors = MLuaBehaviorRegistry.snapshotBehaviors("item", this.behaviorKey);
      LuaValue onUse = behaviors.callbacks().getOrDefault("onUse", LuaValue.NIL);
      if (!onUse.isnil()) {
         Varargs result = MLuaBehaviorCallbackFailureTracker.invoke(level, null, "item", this.behaviorKey,
            "onUse", behaviors, onUse, () -> {
            LuaTable ctx = this.createContext(player, hand);
            return MLuaBehaviorRegistry.invokeCallback(behaviors, onUse,
               LuaValue.varargsOf(new LuaValue[]{ctx}));
         });
         if (result.arg1().isboolean() && result.arg1().toboolean()) {
            return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
         }
      }

      return super.use(level, player, hand);
   }

   public InteractionResult useOn(UseOnContext context) {
      MLuaBehaviorRegistry.Snapshot behaviors = MLuaBehaviorRegistry.snapshotBehaviors("item", this.behaviorKey);
      LuaValue onUseOnBlock = behaviors.callbacks().getOrDefault("onUseOnBlock", LuaValue.NIL);
      if (!onUseOnBlock.isnil()) {
         Varargs result = MLuaBehaviorCallbackFailureTracker.invoke(context.getLevel(), null, "item",
            this.behaviorKey, "onUseOnBlock", behaviors, onUseOnBlock, () -> {
            LuaTable ctx = this.createUseOnBlockContext(context);
            return MLuaBehaviorRegistry.invokeCallback(behaviors, onUseOnBlock,
               LuaValue.varargsOf(new LuaValue[]{ctx}));
         });
         if (result.arg1().isboolean() && result.arg1().toboolean()) {
            return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
         }
      }

      return super.useOn(context);
   }

   public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
      MLuaBehaviorRegistry.Snapshot behaviors = MLuaBehaviorRegistry.snapshotBehaviors("item", this.behaviorKey);
      LuaValue onInventoryTick = behaviors.callbacks().getOrDefault("onInventoryTick", LuaValue.NIL);
      if (!onInventoryTick.isnil()) {
         MLuaBehaviorCallbackFailureTracker.invoke(level, null, "item", this.behaviorKey,
            "onInventoryTick", behaviors, onInventoryTick, () -> {
            LuaTable ctx = this.createInventoryContext(stack, level, entity, slot, selected);
            return MLuaBehaviorRegistry.invokeCallback(behaviors, onInventoryTick,
               LuaValue.varargsOf(new LuaValue[]{ctx}));
         });
      }

      super.inventoryTick(stack, level, entity, slot, selected);
   }

    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
       LuaValue handled = MLuaItemFactory.invokeOnAttackEntityCallback(this.behaviorKey, stack, target, attacker);
       if (handled.isboolean()) return handled.toboolean();
       return super.hurtEnemy(stack, target, attacker);
    }

    public void onCraftedBy(ItemStack stack, Level level, Player player) {
      MLuaBehaviorRegistry.Snapshot behaviors = MLuaBehaviorRegistry.snapshotBehaviors("item", this.behaviorKey);
      LuaValue onCrafted = behaviors.callbacks().getOrDefault("onCrafted", LuaValue.NIL);
      if (!onCrafted.isnil()) {
         MLuaBehaviorCallbackFailureTracker.invoke(level, null, "item", this.behaviorKey,
            "onCrafted", behaviors, onCrafted, () -> {
            LuaTable ctx = this.createContext(player, player.getUsedItemHand());
            ctx.set("Item", MLuaItemWrapper.itemTable(stack, MLuaRuntime.registrationForLevel(level)));
            return MLuaBehaviorRegistry.invokeCallback(behaviors, onCrafted,
               LuaValue.varargsOf(new LuaValue[]{ctx}));
         });
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

   private LuaTable createUseOnBlockContext(UseOnContext context) {
      LuaTable ctx = new LuaTable();
      Player player = context.getPlayer();
      if (player instanceof ServerPlayer sp) {
         ctx.set("Player", MLuaPlayerWrapper.playerTable(MCServerPlayerImpl.fromReal(sp), MLuaRuntime.registrationForLevel(context.getLevel())));
      }

      BlockPos pos = context.getClickedPos();
      ctx.set("World", context.getLevel().dimension().location().toString());
      ctx.set("Position", MLuaPlayerWrapper.positionTable((double)pos.getX() + 0.5, (double)pos.getY() + 0.5, (double)pos.getZ() + 0.5));
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
