package dev.mlua.minecraft.scripted;

import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import dev.mlua.core.MLuaLogger;
import dev.mlua.runtime.client.MLuaVisualBlockEntity;
import dev.mlua.runtime.registry.MLuaRegistryNameHelper;
import dev.mlua.runtime.registry.MLuaRenderableBlock;
import dev.mlua.minecraft.binding.MLuaBlockWrapper;
import dev.mlua.minecraft.client.MLuaMinecraftClient;
import dev.mlua.modules.players.Class.MLuaEntityWrapper;
import dev.mlua.modules.players.Class.MLuaPlayerWrapper;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
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

//--version 1.20.1
   public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
      LuaValue onRightClick = MLuaBehaviorRegistry.getCallback("block", this.behaviorKey, "onRightClick");
      MLuaBehaviorRegistry.ContainerDefinition container = MLuaBehaviorRegistry.getBlockContainer(this.behaviorKey);
      if (!onRightClick.isnil() || container != null) {
         if (level.isClientSide) {
            return InteractionResult.sidedSuccess(true);
         }

         try {
            if (!onRightClick.isnil()) {
               LuaTable ctx = this.createContext(level, pos, player, hand);
               Varargs result = onRightClick.invoke(ctx);
               if (result.arg1().isboolean() && result.arg1().toboolean()) {
                  return InteractionResult.sidedSuccess(false);
               }
            }
         } catch (Exception var10) {
            MLuaLogger.LOGGER.error("MLua block onRightClick callback failed for '{}': {}", this.behaviorKey, var10.getMessage());
            return InteractionResult.FAIL;
         }
         if (container != null) return this.openContainer(level, pos, player, container);
      }

      return super.use(state, level, pos, player, hand, hit);
   }
//--endversion
//--version 1.21.1
   protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
      LuaValue onRightClick = MLuaBehaviorRegistry.getCallback("block", this.behaviorKey, "onRightClick");
      MLuaBehaviorRegistry.ContainerDefinition container = MLuaBehaviorRegistry.getBlockContainer(this.behaviorKey);
      if (!onRightClick.isnil() || container != null) {
         if (level.isClientSide) {
            return InteractionResult.sidedSuccess(true);
         }

         try {
            if (!onRightClick.isnil()) {
               LuaTable ctx = this.createContext(level, pos, player, null);
               Varargs result = onRightClick.invoke(ctx);
               if (result.arg1().isboolean() && result.arg1().toboolean()) {
                  return InteractionResult.sidedSuccess(false);
               }
            }
         } catch (Exception exception) {
            MLuaLogger.LOGGER.error("MLua block onRightClick callback failed for '{}': {}", this.behaviorKey, exception.getMessage());
            return InteractionResult.FAIL;
         }
         if (container != null) return this.openContainer(level, pos, player, container);
      }

      return super.useWithoutItem(state, level, pos, player, hit);
   }
//--endversion
//--version 26.1
   protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
      LuaValue onRightClick = MLuaBehaviorRegistry.getCallback("block", this.behaviorKey, "onRightClick");
      MLuaBehaviorRegistry.ContainerDefinition container = MLuaBehaviorRegistry.getBlockContainer(this.behaviorKey);
      if (!onRightClick.isnil() || container != null) {
         if (level.isClientSide) return InteractionResult.sidedSuccess(true);
         try {
            if (!onRightClick.isnil()) {
               Varargs result = onRightClick.invoke(this.createContext(level, pos, player, null));
               if (result.arg1().isboolean() && result.arg1().toboolean()) return InteractionResult.sidedSuccess(false);
            }
         } catch (Exception exception) {
            MLuaLogger.LOGGER.error("MLua block onRightClick callback failed for '{}': {}", this.behaviorKey, exception.getMessage());
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
      LuaValue onEntityInside = MLuaBehaviorRegistry.getCallback("block", this.behaviorKey, "onEntityInside");
      if (!onEntityInside.isnil() && !level.isClientSide) {
         try {
            LuaTable ctx = this.entityContext(level, pos, entity);
            if (entity instanceof ServerPlayer) {
                ServerPlayer sp = (ServerPlayer)entity;
                ctx.set("Player", MLuaPlayerWrapper.playerTable(MCServerPlayerImpl.fromReal(sp), MLuaScriptRuntimeRegistration.forLevel(level)));
            } else if (entity instanceof LocalPlayer) {
                LocalPlayer lp = (LocalPlayer)entity;
                ctx.set("Player", MLuaPlayerWrapper.localPlayerTable(MCLocalPlayerImpl.fromReal(lp), MLuaScriptRuntimeRegistration.forLevel(level)));
            }

            onEntityInside.invoke(ctx);
         } catch (Exception var9) {
            MLuaLogger.LOGGER.error("MLua block onEntityInside callback failed for '{}': {}", this.behaviorKey, var9.getMessage());
         }
      }

      super.entityInside(state, level, pos, entity);
   }
//--version 26.1
   protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity,
                               net.minecraft.world.level.block.InsideBlockEffectApplier effectApplier, boolean movedByPiston) {
      LuaValue onEntityInside = MLuaBehaviorRegistry.getCallback("block", this.behaviorKey, "onEntityInside");
      if (!onEntityInside.isnil() && !level.isClientSide) {
         try {
            LuaTable ctx = this.entityContext(level, pos, entity);
            if (entity instanceof ServerPlayer sp) ctx.set("Player", MLuaPlayerWrapper.playerTable(MCServerPlayerImpl.fromReal(sp), MLuaScriptRuntimeRegistration.forLevel(level)));
            else if (entity instanceof LocalPlayer lp) ctx.set("Player", MLuaPlayerWrapper.localPlayerTable(MCLocalPlayerImpl.fromReal(lp), MLuaScriptRuntimeRegistration.forLevel(level)));
            onEntityInside.invoke(ctx);
         } catch (Exception exception) {
            MLuaLogger.LOGGER.error("MLua block onEntityInside callback failed for '{}': {}", this.behaviorKey, exception.getMessage());
         }
      }
      super.entityInside(state, level, pos, entity, effectApplier, movedByPiston);
   }
//--endversion

   public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean movedByPiston) {
      LuaValue onNeighborChanged = MLuaBehaviorRegistry.getCallback("block", this.behaviorKey, "onNeighborChanged");
      if (!onNeighborChanged.isnil() && !level.isClientSide) {
         try {
            LuaTable ctx = new LuaTable();
            ctx.set("World", level.dimension().location().toString());
            ctx.set("Position", MLuaPlayerWrapper.positionTable((double)pos.getX() + 0.5, (double)pos.getY() + 0.5, (double)pos.getZ() + 0.5));
            ctx.set("Block", MLuaBlockWrapper.blockTable(level, pos, MLuaScriptRuntimeRegistration.forLevel(level)));
            ctx.set("Neighbor", (LuaValue)(fromPos == null ? LuaValue.NIL : MLuaBlockWrapper.blockTable(level, fromPos, MLuaScriptRuntimeRegistration.forLevel(level))));
            onNeighborChanged.invoke(ctx);
         } catch (Exception var9) {
            MLuaLogger.LOGGER.error("MLua block onNeighborChanged callback failed for '{}': {}", this.behaviorKey, var9.getMessage());
         }
      }

      super.neighborChanged(state, level, pos, block, fromPos, movedByPiston);
   }
//--version 26.1
   protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block,
                                  net.minecraft.world.redstone.Orientation orientation, boolean movedByPiston) {
      LuaValue callback = MLuaBehaviorRegistry.getCallback("block", this.behaviorKey, "onNeighborChanged");
      if (!callback.isnil() && !level.isClientSide) {
         try {
            LuaTable ctx = new LuaTable();
            ctx.set("World", level.dimension().location().toString());
            ctx.set("Position", MLuaPlayerWrapper.positionTable(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
            ctx.set("Block", MLuaBlockWrapper.blockTable(level, pos, MLuaScriptRuntimeRegistration.forLevel(level)));
            callback.invoke(ctx);
         } catch (Exception exception) {
            MLuaLogger.LOGGER.error("MLua block onNeighborChanged callback failed for '{}': {}", this.behaviorKey, exception.getMessage());
         }
      }
      super.neighborChanged(state, level, pos, block, orientation, movedByPiston);
   }
//--endversion

   public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
      LuaValue onRemove = MLuaBehaviorRegistry.getCallback("block", this.behaviorKey, "onRemove");
      if (!onRemove.isnil() && !level.isClientSide) {
         try {
            LuaTable ctx = new LuaTable();
            ctx.set("World", level.dimension().location().toString());
            ctx.set("Position", MLuaPlayerWrapper.positionTable((double)pos.getX() + 0.5, (double)pos.getY() + 0.5, (double)pos.getZ() + 0.5));
            ctx.set("Block", MLuaBlockWrapper.blockTable(level, pos, MLuaScriptRuntimeRegistration.forLevel(level)));
            onRemove.invoke(ctx);
         } catch (Exception var8) {
            MLuaLogger.LOGGER.error("MLua block onRemove callback failed for '{}': {}", this.behaviorKey, var8.getMessage());
         }
      }

      super.onRemove(state, level, pos, newState, movedByPiston);
   }

   public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
      LuaValue onRandomTick = MLuaBehaviorRegistry.getCallback("block", this.behaviorKey, "onRandomTick");
      if (!onRandomTick.isnil()) {
         try {
            LuaTable ctx = this.createContext(level, pos, (Player)null, (InteractionHand)null);
            onRandomTick.invoke(ctx);
         } catch (Exception var7) {
            MLuaLogger.LOGGER.error("MLua block onRandomTick callback failed for '{}': {}", this.behaviorKey, var7.getMessage());
         }
      }

      super.randomTick(state, level, pos, random);
   }

   @Override
   public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
      LuaValue onTick = MLuaBehaviorRegistry.getCallback("block", this.behaviorKey, "onTick");
      if (!onTick.isnil()) {
         try {
            onTick.invoke(this.createContext(level, pos, null, null));
         } catch (Exception exception) {
            MLuaLogger.LOGGER.error("MLua block onTick callback failed for '{}': {}", this.behaviorKey, exception.getMessage());
         }
      }
      super.tick(state, level, pos, random);
   }

   public void fallOn(Level level, BlockState state, BlockPos pos, Entity entity, float fallDistance) {
      LuaValue onFallOn = MLuaBehaviorRegistry.getCallback("block", this.behaviorKey, "onFallOn");
      if (!onFallOn.isnil() && !level.isClientSide) {
         try {
            LuaTable ctx = this.entityContext(level, pos, entity);
            ctx.set("FallDistance", LuaValue.valueOf(fallDistance));
            onFallOn.invoke(ctx);
         } catch (Exception var9) {
            MLuaLogger.LOGGER.error("MLua block onFallOn callback failed for '{}': {}", this.behaviorKey, var9.getMessage());
         }
      }
      super.fallOn(level, state, pos, entity, fallDistance);
   }

   public void onProjectileHit(Level level, BlockState state, BlockHitResult hitResult, Projectile projectile) {
      LuaValue onProjectileHit = MLuaBehaviorRegistry.getCallback("block", this.behaviorKey, "onProjectileHit");
      if (!onProjectileHit.isnil() && !level.isClientSide) {
         try {
            LuaTable ctx = new LuaTable();
            ctx.set("World", level.dimension().location().toString());
            ctx.set("Position", MLuaPlayerWrapper.positionTable((double)hitResult.getBlockPos().getX() + 0.5, (double)hitResult.getBlockPos().getY() + 0.5, (double)hitResult.getBlockPos().getZ() + 0.5));
            ctx.set("Block", MLuaBlockWrapper.blockTable(level, hitResult.getBlockPos(), MLuaScriptRuntimeRegistration.forLevel(level)));
            ctx.set("Projectile", projectile.getStringUUID());
            onProjectileHit.invoke(ctx);
         } catch (Exception var8) {
            MLuaLogger.LOGGER.error("MLua block onProjectileHit callback failed for '{}': {}", this.behaviorKey, var8.getMessage());
         }
      }
      super.onProjectileHit(level, state, hitResult, projectile);
   }

   private LuaTable createContext(Level level, BlockPos pos, Player player, InteractionHand hand) {
      LuaTable ctx = new LuaTable();
      if (player instanceof ServerPlayer sp) {
         ctx.set("Player", MLuaPlayerWrapper.playerTable(MCServerPlayerImpl.fromReal(sp), MLuaScriptRuntimeRegistration.forLevel(level)));
      } else if (player instanceof LocalPlayer lp) {
         ctx.set("Player", MLuaPlayerWrapper.localPlayerTable(MCLocalPlayerImpl.fromReal(lp), MLuaScriptRuntimeRegistration.forLevel(level)));
      } else if (level.isClientSide) {
         LocalPlayer local = MLuaMinecraftClient.localPlayer();
         if (local != null) {
            ctx.set("Player", MLuaPlayerWrapper.localPlayerTable(MCLocalPlayerImpl.fromReal(local), MLuaScriptRuntimeRegistration.forLevel(level)));
         }
      }

      ctx.set("World", level.dimension().location().toString());
      ctx.set("Position", MLuaPlayerWrapper.positionTable((double)pos.getX() + 0.5, (double)pos.getY() + 0.5, (double)pos.getZ() + 0.5));
      ctx.set("Block", MLuaBlockWrapper.blockTable(level, pos, MLuaScriptRuntimeRegistration.forLevel(level)));
      if (hand != null) {
         ctx.set("Hand", hand.name().toLowerCase());
      }

      return ctx;
   }

   private LuaTable entityContext(Level level, BlockPos pos, Entity entity) {
      LuaTable ctx = new LuaTable();
      ctx.set("World", level.dimension().location().toString());
      ctx.set("Position", MLuaPlayerWrapper.positionTable((double)pos.getX() + 0.5, (double)pos.getY() + 0.5, (double)pos.getZ() + 0.5));
      ctx.set("Block", MLuaBlockWrapper.blockTable(level, pos, MLuaScriptRuntimeRegistration.forLevel(level)));
      Object var10002;
      if (entity instanceof LivingEntity living) {
         var10002 = MLuaEntityWrapper.entityTable(living, MLuaScriptRuntimeRegistration.forLevel(level));
      } else {
         var10002 = LuaValue.valueOf(entity.getStringUUID());
      }

      ctx.set("Entity", (LuaValue)var10002);
      return ctx;
   }

   public static MLuaScriptedBlock create(BlockBehaviour.Properties properties, String behaviorKey, Supplier<BlockEntityType<MLuaVisualBlockEntity>> blockEntityType) {
      return new MLuaScriptedBlock(properties, behaviorKey, blockEntityType);
   }
}
