package dev.mlua.minecraft.scripted;

import dev.mlua.core.MLuaRuntime;
import dev.mlua.core.MLuaLogger;
import dev.mlua.mc.impl.entity.MCLocalPlayerImpl;
import dev.mlua.mc.impl.entity.MCServerPlayerImpl;
import dev.mlua.minecraft.binding.MLuaBlockWrapper;
import dev.mlua.minecraft.client.MLuaMinecraftClient;
import dev.mlua.modules.players.Class.MLuaEntityWrapper;
import dev.mlua.modules.players.Class.MLuaPlayerWrapper;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
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
      LuaValue onRightClick = MLuaBehaviorRegistry.getBlockOnRightClick(this.behaviorKey);
      if (!onRightClick.isnil()) {
         if (level.isClientSide) {
            return InteractionResult.sidedSuccess(true);
         }

         try {
            LuaTable ctx = this.createContext(level, pos, player, null);
            Varargs result = onRightClick.invoke(ctx);
            if (result.arg1().isboolean() && result.arg1().toboolean()) {
               return InteractionResult.sidedSuccess(false);
            }
         } catch (Exception exception) {
            MLuaLogger.LOGGER.error("MLua block onRightClick callback failed for '{}': {}", this.behaviorKey, exception.getMessage());
         }
      }

      return super.useWithoutItem(state, level, pos, player, hit);
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
      LuaValue onEntityInside = MLuaBehaviorRegistry.getBlockOnEntityInside(this.behaviorKey);
      if (!onEntityInside.isnil() && !level.isClientSide) {
         try {
            LuaTable ctx = this.entityContext(level, pos, entity);
            if (entity instanceof ServerPlayer sp) {
               ctx.set("Player", MLuaPlayerWrapper.playerTable(MCServerPlayerImpl.fromReal(sp), MLuaRuntime.registrationForLevel(level)));
            } else if (entity instanceof LocalPlayer lp) {
               ctx.set("Player", MLuaPlayerWrapper.localPlayerTable(MCLocalPlayerImpl.fromReal(lp), MLuaRuntime.registrationForLevel(level)));
            }

            onEntityInside.invoke(ctx);
         } catch (Exception exception) {
            MLuaLogger.LOGGER.error("MLua block onEntityInside callback failed for '{}': {}", this.behaviorKey, exception.getMessage());
         }
      }

      super.entityInside(state, level, pos, entity);
   }

   public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean movedByPiston) {
      LuaValue onNeighborChanged = MLuaBehaviorRegistry.getBlockOnNeighborChanged(this.behaviorKey);
      if (!onNeighborChanged.isnil() && !level.isClientSide) {
         try {
            LuaTable ctx = new LuaTable();
            ctx.set("World", level.dimension().location().toString());
            ctx.set("Position", MLuaPlayerWrapper.positionTable((double) pos.getX() + 0.5, (double) pos.getY() + 0.5, (double) pos.getZ() + 0.5));
            ctx.set("Block", MLuaBlockWrapper.blockTable(level, pos, MLuaRuntime.registrationForLevel(level)));
            ctx.set("Neighbor", fromPos == null ? LuaValue.NIL : MLuaBlockWrapper.blockTable(level, fromPos, MLuaRuntime.registrationForLevel(level)));
            onNeighborChanged.invoke(ctx);
         } catch (Exception exception) {
            MLuaLogger.LOGGER.error("MLua block onNeighborChanged callback failed for '{}': {}", this.behaviorKey, exception.getMessage());
         }
      }

      super.neighborChanged(state, level, pos, block, fromPos, movedByPiston);
   }

   public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
      LuaValue onRemove = MLuaBehaviorRegistry.getBlockOnRemove(this.behaviorKey);
      if (!onRemove.isnil() && !level.isClientSide) {
         try {
            LuaTable ctx = new LuaTable();
            ctx.set("World", level.dimension().location().toString());
            ctx.set("Position", MLuaPlayerWrapper.positionTable((double) pos.getX() + 0.5, (double) pos.getY() + 0.5, (double) pos.getZ() + 0.5));
            ctx.set("Block", MLuaBlockWrapper.blockTable(level, pos, MLuaRuntime.registrationForLevel(level)));
            onRemove.invoke(ctx);
         } catch (Exception exception) {
            MLuaLogger.LOGGER.error("MLua block onRemove callback failed for '{}': {}", this.behaviorKey, exception.getMessage());
         }
      }

      super.onRemove(state, level, pos, newState, movedByPiston);
   }

   public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
      LuaValue onRandomTick = MLuaBehaviorRegistry.getBlockOnRandomTick(this.behaviorKey);
      if (!onRandomTick.isnil()) {
         try {
            LuaTable ctx = this.createContext(level, pos, null, null);
            onRandomTick.invoke(ctx);
         } catch (Exception exception) {
            MLuaLogger.LOGGER.error("MLua block onRandomTick callback failed for '{}': {}", this.behaviorKey, exception.getMessage());
         }
      }

      super.randomTick(state, level, pos, random);
   }

   public void fallOn(Level level, BlockState state, BlockPos pos, Entity entity, float fallDistance) {
      LuaValue onFallOn = MLuaBehaviorRegistry.getBlockOnFallOn(this.behaviorKey);
      if (!onFallOn.isnil() && !level.isClientSide) {
         try {
            LuaTable ctx = this.entityContext(level, pos, entity);
            ctx.set("FallDistance", LuaValue.valueOf(fallDistance));
            onFallOn.invoke(ctx);
         } catch (Exception exception) {
            MLuaLogger.LOGGER.error("MLua block onFallOn callback failed for '{}': {}", this.behaviorKey, exception.getMessage());
         }
      }
      super.fallOn(level, state, pos, entity, fallDistance);
   }

   public void onProjectileHit(Level level, BlockState state, BlockHitResult hitResult, Projectile projectile) {
      LuaValue onProjectileHit = MLuaBehaviorRegistry.getBlockOnProjectileHit(this.behaviorKey);
      if (!onProjectileHit.isnil() && !level.isClientSide) {
         try {
            LuaTable ctx = new LuaTable();
            ctx.set("World", level.dimension().location().toString());
            ctx.set("Position", MLuaPlayerWrapper.positionTable((double) hitResult.getBlockPos().getX() + 0.5, (double) hitResult.getBlockPos().getY() + 0.5, (double) hitResult.getBlockPos().getZ() + 0.5));
            ctx.set("Block", MLuaBlockWrapper.blockTable(level, hitResult.getBlockPos(), MLuaRuntime.registrationForLevel(level)));
            ctx.set("Projectile", projectile.getStringUUID());
            onProjectileHit.invoke(ctx);
         } catch (Exception exception) {
            MLuaLogger.LOGGER.error("MLua block onProjectileHit callback failed for '{}': {}", this.behaviorKey, exception.getMessage());
         }
      }
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
         ctx.set("Hand", hand.name().toLowerCase());
      }

      return ctx;
   }

   private LuaTable entityContext(Level level, BlockPos pos, Entity entity) {
      LuaTable ctx = new LuaTable();
      ctx.set("World", level.dimension().location().toString());
      ctx.set("Position", MLuaPlayerWrapper.positionTable((double) pos.getX() + 0.5, (double) pos.getY() + 0.5, (double) pos.getZ() + 0.5));
      ctx.set("Block", MLuaBlockWrapper.blockTable(level, pos, MLuaRuntime.registrationForLevel(level)));
      ctx.set("Entity", entity instanceof LivingEntity living ? MLuaEntityWrapper.entityTable(living, MLuaRuntime.registrationForLevel(level)) : LuaValue.valueOf(entity.getStringUUID()));
      return ctx;
   }

   public static MLuaScriptedBlock create(BlockBehaviour.Properties properties, String behaviorKey, Supplier<BlockEntityType<MLuaVisualBlockEntity>> blockEntityType) {
      return new MLuaScriptedBlock(properties, behaviorKey, blockEntityType);
   }
}
