package dev.mlua.minecraft.scripted;

import dev.mlua.core.MLuaRuntime;
import dev.mlua.core.MLuaLogger;
import dev.mlua.mc.impl.entity.MCLocalPlayerImpl;
import dev.mlua.mc.impl.entity.MCServerPlayerImpl;
import dev.mlua.minecraft.binding.MLuaBlockWrapper;
import dev.mlua.minecraft.client.MLuaMinecraftClient;
import dev.mlua.modules.players.classes.MLuaEntityWrapper;
import dev.mlua.modules.players.classes.MLuaPlayerWrapper;
import dev.mlua.runtime.client.MLuaVisualBlockEntity;
import dev.mlua.runtime.registry.MLuaRegistryNameHelper;
import dev.mlua.runtime.registry.MLuaRenderableBlock;
import java.util.function.Supplier;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.Containers;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

public class MLuaScriptedBlock extends MLuaRenderableBlock {
   private final String behaviorKey;
   private final String displayName;

   public MLuaScriptedBlock(BlockBehaviour.Properties properties, String behaviorKey, Supplier<BlockEntityType<MLuaVisualBlockEntity>> blockEntityType) {
      super(properties, blockEntityType);
      this.behaviorKey = behaviorKey;
      this.displayName = MLuaRegistryNameHelper.displayName(behaviorKey);
   }

   public MutableComponent getName() {
      return Component.literal(this.displayName);
   }

   protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
      LuaValue onRightClick = MLuaBehaviorRegistry.getBehavior("block", this.behaviorKey, "onRightClick");
      MLuaBehaviorRegistry.ContainerDefinition container = MLuaBehaviorRegistry.getBlockContainer(this.behaviorKey);
      if (!onRightClick.isnil() || container != null) {
         if (level.isClientSide) {
            return InteractionResult.sidedSuccess(true);
         }

         try {
            LuaTable ctx = this.createContext(level, pos, player, null);
            Varargs result = this.invokeBehavior("onRightClick", onRightClick, ctx);
            if (result.arg1().isboolean() && result.arg1().toboolean()) {
               return InteractionResult.sidedSuccess(false);
            }
         } catch (Exception exception) {
            MLuaLogger.LOGGER.error("MLua block onRightClick callback failed for '{}': {}", this.behaviorKey, exception);
            return InteractionResult.FAIL;
         }
         if (container != null) return this.openContainer(level, pos, player, container);
      }

      return super.useWithoutItem(state, level, pos, player, hit);
   }

   @Override
   public ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
         Player player, InteractionHand hand, BlockHitResult hit) {
      LuaValue onRightClick = MLuaBehaviorRegistry.getBehavior("block", this.behaviorKey, "onRightClick");
      MLuaBehaviorRegistry.ContainerDefinition container = MLuaBehaviorRegistry.getBlockContainer(this.behaviorKey);
      if (onRightClick.isnil() && container == null) {
         return super.useItemOn(stack, state, level, pos, player, hand, hit);
      }
      if (level.isClientSide) return ItemInteractionResult.sidedSuccess(true);
      try {
         if (!onRightClick.isnil()) {
            LuaTable context = this.createContext(level, pos, player, hand);
            context.set("Face", hit.getDirection().getName());
            Varargs result = this.invokeBehavior("onRightClick", onRightClick, context);
            if (result.arg1().isboolean() && result.arg1().toboolean()) {
               return ItemInteractionResult.sidedSuccess(false);
            }
         }
      } catch (Exception exception) {
         MLuaLogger.LOGGER.error("MLua block onRightClick callback failed for '{}'", this.behaviorKey, exception);
         return ItemInteractionResult.FAIL;
      }
      if (container != null) {
         InteractionResult opened = this.openContainer(level, pos, player, container);
         return opened == InteractionResult.FAIL ? ItemInteractionResult.FAIL : ItemInteractionResult.sidedSuccess(false);
      }
      return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
   }

   private InteractionResult openContainer(Level level, BlockPos pos, Player player,
         MLuaBehaviorRegistry.ContainerDefinition definition) {
      if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.PASS;
      if (!(level.getBlockEntity(pos) instanceof MLuaVisualBlockEntity blockEntity)) return InteractionResult.FAIL;
      try {
         int rows = definition.rows();
         blockEntity.configureContainer(rows * 9);
         MenuType<?> menuType = switch (rows) {
            case 1 -> MenuType.GENERIC_9x1;
            case 2 -> MenuType.GENERIC_9x2;
            case 3 -> MenuType.GENERIC_9x3;
            case 4 -> MenuType.GENERIC_9x4;
            case 5 -> MenuType.GENERIC_9x5;
            case 6 -> MenuType.GENERIC_9x6;
            default -> throw new IllegalStateException("Unregistered scripted container row count: " + rows);
         };
         serverPlayer.openMenu(new SimpleMenuProvider((id, inventory, viewer) ->
            new ChestMenu(menuType, id, inventory, blockEntity, rows),
            Component.literal(definition.title())));
         return InteractionResult.sidedSuccess(false);
      } catch (RuntimeException exception) {
         MLuaLogger.LOGGER.error("Failed to open scripted container '{}' at {}", this.behaviorKey, pos, exception);
         return InteractionResult.FAIL;
      }
   }

   public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
      MLuaBlockCallbackDispatcher.fireOnPlace(level, pos, (Player) null);
      super.onPlace(state, level, pos, oldState, movedByPiston);
   }

   public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state, BlockEntity blockEntity, ItemStack tool) {
      MLuaBlockCallbackDispatcher.fireOnBreak(level, pos, player);
      super.playerDestroy(level, player, pos, state, blockEntity, tool);
   }

   public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
      MLuaBlockCallbackDispatcher.fireOnStepOn(level, pos, entity);
      super.stepOn(level, pos, state, entity);
   }

   public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
      LuaValue onEntityInside = MLuaBehaviorRegistry.getBehavior("block", this.behaviorKey, "onEntityInside");
      if (!onEntityInside.isnil() && !level.isClientSide) {
         try {
            LuaTable ctx = this.entityContext(level, pos, entity);
            if (entity instanceof ServerPlayer sp) {
               ctx.set("Player", MLuaPlayerWrapper.playerTable(MCServerPlayerImpl.fromReal(sp), MLuaRuntime.registrationForLevel(level)));
            } else if (entity instanceof LocalPlayer lp) {
               ctx.set("Player", MLuaPlayerWrapper.localPlayerTable(MCLocalPlayerImpl.fromReal(lp), MLuaRuntime.registrationForLevel(level)));
            }

            this.invokeBehavior("onEntityInside", onEntityInside, ctx);
         } catch (Exception exception) {
            MLuaLogger.LOGGER.error("MLua block onEntityInside callback failed for '{}': {}", this.behaviorKey, exception);
         }
      }

      super.entityInside(state, level, pos, entity);
   }

   public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean movedByPiston) {
      MLuaBlockCallbackDispatcher.fireNeighborChanged(level, pos, fromPos);
      super.neighborChanged(state, level, pos, block, fromPos, movedByPiston);
   }

   public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
      LuaValue onRemove = MLuaBehaviorRegistry.getBehavior("block", this.behaviorKey, "onRemove");
      if (!onRemove.isnil() && !level.isClientSide) {
         try {
            LuaTable ctx = new LuaTable();
            ctx.set("World", level.dimension().location().toString());
            ctx.set("Position", MLuaPlayerWrapper.positionTable((double) pos.getX() + 0.5, (double) pos.getY() + 0.5, (double) pos.getZ() + 0.5));
            ctx.set("Block", MLuaBlockWrapper.blockTable(level, pos, MLuaRuntime.registrationForLevel(level)));
            this.invokeBehavior("onRemove", onRemove, ctx);
         } catch (Exception exception) {
            MLuaLogger.LOGGER.error("MLua block onRemove callback failed for '{}': {}", this.behaviorKey, exception);
         }
      }

      if (state.getBlock() != newState.getBlock()) {
         this.dropContainerContents(level, pos);
      }
      super.onRemove(state, level, pos, newState, movedByPiston);
   }

   private void dropContainerContents(Level level, BlockPos pos) {
      if (level.isClientSide) return;
      BlockEntity blockEntity = level.getBlockEntity(pos);
      if (blockEntity instanceof MLuaVisualBlockEntity container && container.hasContainer()) {
         Containers.dropContents(level, pos, container);
      }
   }

   public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
      MLuaBlockCallbackDispatcher.fireCallback("onRandomTick", level, pos,
         () -> MLuaBlockCallbackDispatcher.eventContext(level, pos));
      super.randomTick(state, level, pos, random);
   }

   public void fallOn(Level level, BlockState state, BlockPos pos, Entity entity, float fallDistance) {
      LuaValue onFallOn = MLuaBehaviorRegistry.getBehavior("block", this.behaviorKey, "onFallOn");
      if (!onFallOn.isnil() && !level.isClientSide) {
         try {
            LuaTable ctx = this.entityContext(level, pos, entity);
            ctx.set("FallDistance", LuaValue.valueOf(fallDistance));
            this.invokeBehavior("onFallOn", onFallOn, ctx);
         } catch (Exception exception) {
            MLuaLogger.LOGGER.error("MLua block onFallOn callback failed for '{}': {}", this.behaviorKey, exception);
         }
      }
      super.fallOn(level, state, pos, entity, fallDistance);
   }

   public void onProjectileHit(Level level, BlockState state, BlockHitResult hitResult, Projectile projectile) {
      BlockPos pos = hitResult.getBlockPos();
      MLuaBlockCallbackDispatcher.fireCallback("onProjectileHit", level, pos, () -> {
         LuaTable context = MLuaBlockCallbackDispatcher.eventContext(level, pos);
         context.set("Projectile", MLuaEntityWrapper.entityTable(projectile, MLuaRuntime.registrationForLevel(level)));
         return context;
      });
      super.onProjectileHit(level, state, hitResult, projectile);
   }

   private LuaTable createContext(Level level, BlockPos pos, Player player, InteractionHand hand) {
      LuaTable ctx = new LuaTable();
      if (player instanceof ServerPlayer sp) {
         ctx.set("Player", MLuaPlayerWrapper.playerTable(MCServerPlayerImpl.fromReal(sp), MLuaRuntime.registrationForLevel(level)));
      } else if (player instanceof LocalPlayer lp) {
         ctx.set("Player", MLuaPlayerWrapper.localPlayerTable(MCLocalPlayerImpl.fromReal(lp), MLuaRuntime.registrationForLevel(level)));
      } else if (level.isClientSide) {
         LocalPlayer local = MLuaMinecraftClient.localPlayer();
         if (local != null) {
            ctx.set("Player", MLuaPlayerWrapper.localPlayerTable(MCLocalPlayerImpl.fromReal(local), MLuaRuntime.registrationForLevel(level)));
         }
      }

      ctx.set("World", level.dimension().location().toString());
      ctx.set("Position", MLuaPlayerWrapper.positionTable((double) pos.getX() + 0.5, (double) pos.getY() + 0.5, (double) pos.getZ() + 0.5));
      ctx.set("Block", MLuaBlockWrapper.blockTable(level, pos, MLuaRuntime.registrationForLevel(level)));
      if (hand != null) {
         ctx.set("Hand", hand.name().toLowerCase(java.util.Locale.ROOT));
         ctx.set("HeldItem", dev.mlua.minecraft.binding.MLuaItemWrapper.itemTable(
            player.getItemInHand(hand), MLuaRuntime.registrationForLevel(level)));
      }

      return ctx;
   }

   private LuaTable entityContext(Level level, BlockPos pos, Entity entity) {
      LuaTable ctx = new LuaTable();
      ctx.set("World", level.dimension().location().toString());
      ctx.set("Position", MLuaPlayerWrapper.positionTable((double) pos.getX() + 0.5, (double) pos.getY() + 0.5, (double) pos.getZ() + 0.5));
      ctx.set("Block", MLuaBlockWrapper.blockTable(level, pos, MLuaRuntime.registrationForLevel(level)));
      ctx.set("Entity", MLuaEntityWrapper.entityTable(entity, MLuaRuntime.registrationForLevel(level)));
      return ctx;
   }

   public static MLuaScriptedBlock create(BlockBehaviour.Properties properties, String behaviorKey, Supplier<BlockEntityType<MLuaVisualBlockEntity>> blockEntityType) {
      return new MLuaScriptedBlock(properties, behaviorKey, blockEntityType);
   }

   private Varargs invokeBehavior(String event, LuaValue callback, LuaTable context) {
      return MLuaBehaviorRegistry.invokeCallback("block", this.behaviorKey, event, callback,
         LuaValue.varargsOf(new LuaValue[]{context}));
   }
}
