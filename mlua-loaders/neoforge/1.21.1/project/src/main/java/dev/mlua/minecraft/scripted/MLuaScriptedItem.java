package dev.mlua.minecraft.scripted;

import dev.mlua.core.MLuaLogger;
import dev.mlua.mc.impl.entity.MCServerPlayerImpl;
import dev.mlua.minecraft.binding.MLuaBlockWrapper;
import dev.mlua.minecraft.binding.MLuaItemWrapper;
import dev.mlua.modules.players.Class.MLuaEntityWrapper;
import dev.mlua.modules.players.Class.MLuaPlayerWrapper;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

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

   public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity livingEntity) {
      LuaValue onConsume = MLuaBehaviorRegistry.getItemOnConsume(this.behaviorKey);
      if (!onConsume.isnil()) {
         try {
            LuaTable ctx = new LuaTable();
            if (livingEntity instanceof ServerPlayer sp) {
               ctx.set("Player", MLuaPlayerWrapper.playerTable(MCServerPlayerImpl.fromReal(sp)));
            }
            ctx.set("World", level.dimension().location().toString());
            ctx.set("Item", MLuaItemWrapper.itemTable(stack));
            onConsume.invoke(ctx);
         } catch (Exception exception) {
            MLuaLogger.LOGGER.error("MLua item onConsume callback failed for '{}': {}", this.behaviorKey, exception.getMessage());
         }
      }
      return super.finishUsingItem(stack, level, livingEntity);
   }

   public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
      LuaValue onUse = MLuaBehaviorRegistry.getItemOnUse(this.behaviorKey);
      if (!onUse.isnil()) {
         try {
            LuaTable ctx = this.createContext(player, hand);
            Varargs result = onUse.invoke(ctx);
            if (result.arg1().isboolean() && result.arg1().toboolean()) {
               return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
            }
         } catch (Exception exception) {
            MLuaLogger.LOGGER.error("MLua item onUse callback failed for '{}': {}", this.behaviorKey, exception.getMessage());
         }
      }

      return super.use(level, player, hand);
   }

   public InteractionResult useOn(UseOnContext context) {
      LuaValue onUseOnBlock = MLuaBehaviorRegistry.getItemOnUseOnBlock(this.behaviorKey);
      if (!onUseOnBlock.isnil()) {
         try {
            LuaTable ctx = this.createUseOnBlockContext(context);
            Varargs result = onUseOnBlock.invoke(ctx);
            if (result.arg1().isboolean() && result.arg1().toboolean()) {
               return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
            }
         } catch (Exception exception) {
            MLuaLogger.LOGGER.error("MLua item onUseOnBlock callback failed for '{}': {}", this.behaviorKey, exception.getMessage());
         }
      }

      return super.useOn(context);
   }

   public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
      LuaValue onInventoryTick = MLuaBehaviorRegistry.getItemOnInventoryTick(this.behaviorKey);
      if (!onInventoryTick.isnil()) {
         try {
            LuaTable ctx = this.createInventoryContext(stack, level, entity, slot, selected);
            onInventoryTick.invoke(ctx);
         } catch (Exception exception) {
            MLuaLogger.LOGGER.error("MLua item onInventoryTick callback failed for '{}': {}", this.behaviorKey, exception.getMessage());
         }
      }

      super.inventoryTick(stack, level, entity, slot, selected);
   }

   public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
      LuaValue onAttack = MLuaBehaviorRegistry.getItemOnAttackEntity(this.behaviorKey);
      if (!onAttack.isnil()) {
         try {
            LuaTable ctx = new LuaTable();
            ctx.set("Item", MLuaItemWrapper.itemTable(stack));
            ctx.set("Entity", MLuaEntityWrapper.entityTable(target));
            if (attacker instanceof ServerPlayer sp) {
               ctx.set("Player", MLuaPlayerWrapper.playerTable(MCServerPlayerImpl.fromReal(sp)));
            }
            ctx.set("ItemStack", this.behaviorKey);
            Varargs result = onAttack.invoke(ctx);
            if (result.arg1().isboolean() && !result.arg1().toboolean()) {
               return false;
            }
         } catch (Exception exception) {
            MLuaLogger.LOGGER.error("MLua item onAttackEntity callback failed for '{}': {}", this.behaviorKey, exception.getMessage());
         }
      }
      return super.hurtEnemy(stack, target, attacker);
   }

   public void onCraftedBy(ItemStack stack, Level level, Player player) {
      LuaValue onCrafted = MLuaBehaviorRegistry.getItemOnCrafted(this.behaviorKey);
      if (!onCrafted.isnil()) {
         try {
            LuaTable ctx = this.createContext(player, player.getUsedItemHand());
            ctx.set("Item", MLuaItemWrapper.itemTable(stack));
            onCrafted.invoke(ctx);
         } catch (Exception exception) {
            MLuaLogger.LOGGER.error("MLua item onCrafted callback failed for '{}': {}", this.behaviorKey, exception.getMessage());
         }
      }

      super.onCraftedBy(stack, level, player);
   }

   private LuaTable createContext(Player player, InteractionHand hand) {
      LuaTable ctx = new LuaTable();
      if (player instanceof ServerPlayer sp) {
         ctx.set("Player", MLuaPlayerWrapper.playerTable(MCServerPlayerImpl.fromReal(sp)));
      }

      ctx.set("World", player.level().dimension().location().toString());
      ctx.set("Position", MLuaPlayerWrapper.positionTable(player.position().x, player.position().y, player.position().z));
      ctx.set("Hand", hand.name().toLowerCase());
      ctx.set("Item", MLuaItemWrapper.itemTable(player.getItemInHand(hand)));
      ctx.set("ItemStack", this.behaviorKey);
      return ctx;
   }

   private LuaTable createUseOnBlockContext(UseOnContext context) {
      LuaTable ctx = new LuaTable();
      Player player = context.getPlayer();
      if (player instanceof ServerPlayer sp) {
         ctx.set("Player", MLuaPlayerWrapper.playerTable(MCServerPlayerImpl.fromReal(sp)));
      }

      BlockPos pos = context.getClickedPos();
      ctx.set("World", context.getLevel().dimension().location().toString());
      ctx.set("Position", MLuaPlayerWrapper.positionTable((double) pos.getX() + 0.5, (double) pos.getY() + 0.5, (double) pos.getZ() + 0.5));
      ctx.set("Block", MLuaBlockWrapper.blockTable(context.getLevel(), pos));
      ctx.set("Face", context.getClickedFace().getName());
      ctx.set("Hand", context.getHand().name().toLowerCase());
      ctx.set("Item", MLuaItemWrapper.itemTable(context.getItemInHand()));
      ctx.set("ItemStack", this.behaviorKey);
      return ctx;
   }

   private LuaTable createInventoryContext(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
      LuaTable ctx = new LuaTable();
      if (entity instanceof ServerPlayer sp) {
         ctx.set("Player", MLuaPlayerWrapper.playerTable(MCServerPlayerImpl.fromReal(sp)));
      }

      ctx.set("World", level.dimension().location().toString());
      if (entity instanceof LivingEntity living) {
         ctx.set("Entity", MLuaEntityWrapper.entityTable(living));
      }
      ctx.set("Slot", slot);
      ctx.set("Selected", LuaValue.valueOf(selected));
      ctx.set("Item", MLuaItemWrapper.itemTable(stack));
      ctx.set("ItemStack", this.behaviorKey);
      return ctx;
   }
}
