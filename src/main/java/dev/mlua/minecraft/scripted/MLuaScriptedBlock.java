package dev.mlua.minecraft.scripted;

import dev.mlua.core.MLuaRuntime;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import dev.mlua.core.MLuaLogger;
import dev.mlua.runtime.client.MLuaVisualBlockEntity;
import dev.mlua.runtime.registry.MLuaRegistryNameHelper;
import dev.mlua.runtime.registry.MLuaRenderableBlock;
import dev.mlua.minecraft.binding.MLuaBlockWrapper;
import dev.mlua.minecraft.client.MLuaMinecraftClient;
import dev.mlua.modules.players.classes.MLuaEntityWrapper;
import dev.mlua.modules.players.classes.MLuaPlayerWrapper;
import dev.mlua.mc.impl.entity.MCServerPlayerImpl;
import dev.mlua.mc.impl.entity.MCLocalPlayerImpl;
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
//--version 1.21.1
import net.minecraft.world.ItemInteractionResult;
//--endversion
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.Containers;
//--version 26.1
import net.minecraft.world.level.block.InsideBlockEffectApplier;
import net.minecraft.world.level.redstone.Orientation;
//--endversion
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

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

   public String behaviorKey() { return this.behaviorKey; }

   @Override
   public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
      BlockEntity blockEntity = super.newBlockEntity(pos, state);
      if (blockEntity instanceof MLuaVisualBlockEntity visual) {
         MLuaBehaviorRegistry.ContainerDefinition container =
            MLuaBehaviorRegistry.getBlockContainer(this.behaviorKey);
         if (container != null) visual.configureContainer(container.rows() * 9);
      }
      return blockEntity;
   }

//--version 1.20.1
   public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
      MLuaBehaviorRegistry.Snapshot behaviors = MLuaBehaviorRegistry.snapshotBehaviors("block", this.behaviorKey);
      LuaValue onRightClick = behaviors.callbacks().getOrDefault("onRightClick", LuaValue.NIL);
      MLuaBehaviorRegistry.ContainerDefinition container = MLuaBehaviorRegistry.getBlockContainer(this.behaviorKey);
      if (!onRightClick.isnil() || container != null) {
         if (level.isClientSide) {
            return InteractionResult.sidedSuccess(true);
         }

         try {
            if (!onRightClick.isnil()) {
               LuaTable ctx = this.createContext(level, pos, player, hand, hit);
               Varargs result = this.invokeBehavior(behaviors, onRightClick, ctx);
               if (result.arg1().isboolean() && result.arg1().toboolean()) {
                  return InteractionResult.sidedSuccess(false);
               }
            }
         } catch (Exception var10) {
            MLuaLogger.LOGGER.error("MLua block onRightClick callback failed for '{}'", this.behaviorKey, var10);
            return InteractionResult.FAIL;
         }
         if (container != null) return this.openContainer(level, pos, player, container);
      }

      return super.use(state, level, pos, player, hand, hit);
   }
//--endversion
//--version 1.21.1
   @Override
   public ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
         Player player, InteractionHand hand, BlockHitResult hit) {
      MLuaBehaviorRegistry.Snapshot behaviors = MLuaBehaviorRegistry.snapshotBehaviors("block", this.behaviorKey);
      LuaValue onRightClick = behaviors.callbacks().getOrDefault("onRightClick", LuaValue.NIL);
      MLuaBehaviorRegistry.ContainerDefinition container = MLuaBehaviorRegistry.getBlockContainer(this.behaviorKey);
      if (onRightClick.isnil() && container == null) {
         return super.useItemOn(stack, state, level, pos, player, hand, hit);
      }
      if (level.isClientSide) return ItemInteractionResult.sidedSuccess(true);
      try {
         if (!onRightClick.isnil()) {
            LuaTable context = this.createContext(level, pos, player, hand, hit);
            Varargs result = this.invokeBehavior(behaviors, onRightClick, context);
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

   protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
      MLuaBehaviorRegistry.Snapshot behaviors = MLuaBehaviorRegistry.snapshotBehaviors("block", this.behaviorKey);
      LuaValue onRightClick = behaviors.callbacks().getOrDefault("onRightClick", LuaValue.NIL);
      MLuaBehaviorRegistry.ContainerDefinition container = MLuaBehaviorRegistry.getBlockContainer(this.behaviorKey);
      if (!onRightClick.isnil() || container != null) {
         if (level.isClientSide) {
            return InteractionResult.sidedSuccess(true);
         }

         try {
            if (!onRightClick.isnil()) {
               LuaTable ctx = this.createContext(level, pos, player, null, hit);
               Varargs result = this.invokeBehavior(behaviors, onRightClick, ctx);
               if (result.arg1().isboolean() && result.arg1().toboolean()) {
                  return InteractionResult.sidedSuccess(false);
               }
            }
         } catch (Exception exception) {
            MLuaLogger.LOGGER.error("MLua block onRightClick callback failed for '{}'", this.behaviorKey, exception);
            return InteractionResult.FAIL;
         }
         if (container != null) return this.openContainer(level, pos, player, container);
      }

      return super.useWithoutItem(state, level, pos, player, hit);
   }
//--endversion
//--version 26.1
   protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
      MLuaBehaviorRegistry.Snapshot behaviors = MLuaBehaviorRegistry.snapshotBehaviors("block", this.behaviorKey);
      LuaValue onRightClick = behaviors.callbacks().getOrDefault("onRightClick", LuaValue.NIL);
      MLuaBehaviorRegistry.ContainerDefinition container = MLuaBehaviorRegistry.getBlockContainer(this.behaviorKey);
      if (!onRightClick.isnil() || container != null) {
         if (level.isClientSide) return InteractionResult.sidedSuccess(true);
         try {
            if (!onRightClick.isnil()) {
               Varargs result = this.invokeBehavior(behaviors, onRightClick, this.createContext(level, pos, player, null, hit));
               if (result.arg1().isboolean() && result.arg1().toboolean()) return InteractionResult.sidedSuccess(false);
            }
         } catch (Exception exception) {
            MLuaLogger.LOGGER.error("MLua block onRightClick callback failed for '{}'", this.behaviorKey, exception);
            return InteractionResult.FAIL;
         }
         if (container != null) return this.openContainer(level, pos, player, container);
      }
      return super.useWithoutItem(state, level, pos, player, hit);
   }
//--endversion

   private InteractionResult openContainer(Level level, BlockPos pos, Player player,
         MLuaBehaviorRegistry.ContainerDefinition definition) {
      if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.PASS;
      BlockEntity blockEntity = level.getBlockEntity(pos);
      if (!(blockEntity instanceof MLuaVisualBlockEntity container)) {
         MLuaLogger.LOGGER.error("Scripted block '{}' has a container definition but no MLua block entity at {}", this.behaviorKey, pos);
         return InteractionResult.FAIL;
      }
      try {
         int rows = definition.rows();
         container.configureContainer(rows * 9);
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
            new ChestMenu(menuType, id, inventory, container, rows), Component.literal(definition.title())));
         return InteractionResult.sidedSuccess(false);
      } catch (RuntimeException exception) {
         MLuaLogger.LOGGER.error("Failed to open scripted container '{}' at {}", this.behaviorKey, pos, exception);
         return InteractionResult.FAIL;
      }
   }

   public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
      MLuaBlockCallbackDispatcher.fireOnPlace(level, pos, (Player)null);
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
      MLuaBehaviorRegistry.Snapshot behaviors = MLuaBehaviorRegistry.snapshotBehaviors("block", this.behaviorKey);
      LuaValue onEntityInside = behaviors.callbacks().getOrDefault("onEntityInside", LuaValue.NIL);
      if (!onEntityInside.isnil() && !level.isClientSide) {
         try {
            LuaTable ctx = this.entityContext(level, pos, entity);
            if (entity instanceof ServerPlayer) {
                ServerPlayer sp = (ServerPlayer)entity;
                ctx.set("Player", MLuaPlayerWrapper.playerTable(MCServerPlayerImpl.fromReal(sp), MLuaRuntime.registrationForLevel(level)));
            } else if (entity instanceof LocalPlayer) {
                LocalPlayer lp = (LocalPlayer)entity;
                ctx.set("Player", MLuaPlayerWrapper.localPlayerTable(MCLocalPlayerImpl.fromReal(lp), MLuaRuntime.registrationForLevel(level)));
            }

            this.invokeBehavior(behaviors, onEntityInside, ctx);
         } catch (Exception var9) {
            MLuaLogger.LOGGER.error("MLua block onEntityInside callback failed for '{}'", this.behaviorKey, var9);
         }
      }

      super.entityInside(state, level, pos, entity);
   }
//--version 26.1
   protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity,
                               InsideBlockEffectApplier effectApplier, boolean movedByPiston) {
      MLuaBehaviorRegistry.Snapshot behaviors = MLuaBehaviorRegistry.snapshotBehaviors("block", this.behaviorKey);
      LuaValue onEntityInside = behaviors.callbacks().getOrDefault("onEntityInside", LuaValue.NIL);
      if (!onEntityInside.isnil() && !level.isClientSide) {
         try {
            LuaTable ctx = this.entityContext(level, pos, entity);
            if (entity instanceof ServerPlayer sp) ctx.set("Player", MLuaPlayerWrapper.playerTable(MCServerPlayerImpl.fromReal(sp), MLuaRuntime.registrationForLevel(level)));
            else if (entity instanceof LocalPlayer lp) ctx.set("Player", MLuaPlayerWrapper.localPlayerTable(MCLocalPlayerImpl.fromReal(lp), MLuaRuntime.registrationForLevel(level)));
            this.invokeBehavior(behaviors, onEntityInside, ctx);
         } catch (Exception exception) {
            MLuaLogger.LOGGER.error("MLua block onEntityInside callback failed for '{}'", this.behaviorKey, exception);
         }
      }
      super.entityInside(state, level, pos, entity, effectApplier, movedByPiston);
   }
//--endversion

   public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean movedByPiston) {
      MLuaBlockCallbackDispatcher.fireNeighborChanged(level, pos, fromPos);
      super.neighborChanged(state, level, pos, block, fromPos, movedByPiston);
   }
//--version 26.1
   protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block,
                                  Orientation orientation, boolean movedByPiston) {
      BlockPos neighborPos = orientation == null ? null
         : pos.relative(orientation.getFront().getOpposite());
      MLuaBlockCallbackDispatcher.fireNeighborChanged(level, pos, neighborPos);
      super.neighborChanged(state, level, pos, block, orientation, movedByPiston);
   }
//--endversion

   public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
      MLuaBehaviorRegistry.Snapshot behaviors = MLuaBehaviorRegistry.snapshotBehaviors("block", this.behaviorKey);
      LuaValue onRemove = behaviors.callbacks().getOrDefault("onRemove", LuaValue.NIL);
      if (!onRemove.isnil() && !level.isClientSide) {
         try {
            LuaTable ctx = new LuaTable();
            ctx.set("World", level.dimension().location().toString());
            ctx.set("Position", MLuaPlayerWrapper.positionTable((double)pos.getX() + 0.5, (double)pos.getY() + 0.5, (double)pos.getZ() + 0.5));
            ctx.set("Block", MLuaBlockWrapper.blockTable(level, pos, MLuaRuntime.registrationForLevel(level)));
            this.invokeBehavior(behaviors, onRemove, ctx);
         } catch (Exception var8) {
            MLuaLogger.LOGGER.error("MLua block onRemove callback failed for '{}'", this.behaviorKey, var8);
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

   @Override
   public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
      MLuaBlockCallbackDispatcher.fireCallback("onTick", level, pos,
         () -> MLuaBlockCallbackDispatcher.eventContext(level, pos));
      super.tick(state, level, pos, random);
   }

   public void fallOn(Level level, BlockState state, BlockPos pos, Entity entity, float fallDistance) {
      MLuaBehaviorRegistry.Snapshot behaviors = MLuaBehaviorRegistry.snapshotBehaviors("block", this.behaviorKey);
      LuaValue onFallOn = behaviors.callbacks().getOrDefault("onFallOn", LuaValue.NIL);
      if (!onFallOn.isnil() && !level.isClientSide) {
         try {
            LuaTable ctx = this.entityContext(level, pos, entity);
            ctx.set("FallDistance", LuaValue.valueOf(fallDistance));
            this.invokeBehavior(behaviors, onFallOn, ctx);
         } catch (Exception var9) {
            MLuaLogger.LOGGER.error("MLua block onFallOn callback failed for '{}'", this.behaviorKey, var9);
         }
      }
      super.fallOn(level, state, pos, entity, fallDistance);
   }

   public void onProjectileHit(Level level, BlockState state, BlockHitResult hitResult, Projectile projectile) {
      MLuaBehaviorRegistry.Snapshot behaviors = MLuaBehaviorRegistry.snapshotBehaviors("block", this.behaviorKey);
      LuaValue onProjectileHit = behaviors.callbacks().getOrDefault("onProjectileHit", LuaValue.NIL);
      if (!onProjectileHit.isnil() && !level.isClientSide) {
         try {
            LuaTable ctx = new LuaTable();
            ctx.set("World", level.dimension().location().toString());
            ctx.set("Position", MLuaPlayerWrapper.positionTable((double)hitResult.getBlockPos().getX() + 0.5, (double)hitResult.getBlockPos().getY() + 0.5, (double)hitResult.getBlockPos().getZ() + 0.5));
            ctx.set("Block", MLuaBlockWrapper.blockTable(level, hitResult.getBlockPos(), MLuaRuntime.registrationForLevel(level)));
            ctx.set("Projectile", MLuaEntityWrapper.entityTable(projectile, MLuaRuntime.registrationForLevel(level)));
            this.invokeBehavior(behaviors, onProjectileHit, ctx);
         } catch (Exception var8) {
            MLuaLogger.LOGGER.error("MLua block onProjectileHit callback failed for '{}'", this.behaviorKey, var8);
         }
      }
      super.onProjectileHit(level, state, hitResult, projectile);
   }

   private Varargs invokeBehavior(MLuaBehaviorRegistry.Snapshot behaviors, LuaValue callback, LuaTable context) {
      return MLuaBehaviorRegistry.invokeCallback(behaviors, callback,
         LuaValue.varargsOf(new LuaValue[]{context}));
   }

   private LuaTable createContext(Level level, BlockPos pos, Player player, InteractionHand hand) {
      return createContext(level, pos, player, hand, null);
   }

   private LuaTable createContext(Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
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
      ctx.set("Position", MLuaPlayerWrapper.positionTable((double)pos.getX() + 0.5, (double)pos.getY() + 0.5, (double)pos.getZ() + 0.5));
      ctx.set("Block", MLuaBlockWrapper.blockTable(level, pos, MLuaRuntime.registrationForLevel(level)));
      if (hand != null) {
         ctx.set("Hand", hand.name().toLowerCase(java.util.Locale.ROOT));
         ctx.set("HeldItem", dev.mlua.minecraft.binding.MLuaItemWrapper.itemTable(
            player.getItemInHand(hand), MLuaRuntime.registrationForLevel(level)));
      }
      if (hit != null) ctx.set("Face", hit.getDirection().getName());

      return ctx;
   }

   private LuaTable entityContext(Level level, BlockPos pos, Entity entity) {
      LuaTable ctx = new LuaTable();
      ctx.set("World", level.dimension().location().toString());
      ctx.set("Position", MLuaPlayerWrapper.positionTable((double)pos.getX() + 0.5, (double)pos.getY() + 0.5, (double)pos.getZ() + 0.5));
      ctx.set("Block", MLuaBlockWrapper.blockTable(level, pos, MLuaRuntime.registrationForLevel(level)));
      Object var10002;
      var10002 = MLuaEntityWrapper.entityTable(entity, MLuaRuntime.registrationForLevel(level));

      ctx.set("Entity", (LuaValue)var10002);
      return ctx;
   }

   public static MLuaScriptedBlock create(BlockBehaviour.Properties properties, String behaviorKey, Supplier<BlockEntityType<MLuaVisualBlockEntity>> blockEntityType) {
      return new MLuaScriptedBlock(properties, behaviorKey, blockEntityType);
   }
}
