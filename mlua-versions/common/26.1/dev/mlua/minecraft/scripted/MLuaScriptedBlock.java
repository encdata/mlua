package dev.mlua.minecraft.scripted;

import dev.mlua.core.MLuaRuntime;
import dev.mlua.runtime.registry.MLuaRenderableBlock;
import dev.mlua.runtime.client.MLuaVisualBlockEntity;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import dev.mlua.core.MLuaLogger;
import dev.mlua.minecraft.binding.MLuaBlockWrapper;
import dev.mlua.modules.players.Class.MLuaPlayerWrapper;
import dev.mlua.mc.impl.entity.MCServerPlayerImpl;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
import java.util.function.Supplier;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import dev.mlua.minecraft.scripted.MLuaBlockCallbackDispatcher;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
public class MLuaScriptedBlock extends MLuaRenderableBlock {
    private final String behaviorKey;
    public MLuaScriptedBlock(BlockBehaviour.Properties properties, String behaviorKey, Supplier<BlockEntityType<MLuaVisualBlockEntity>> blockEntityType) {
        super(properties, blockEntityType);
        this.behaviorKey = behaviorKey;
    }
    public String behaviorKey() { return behaviorKey; }
    @Override protected InteractionResult useWithoutItem(net.minecraft.world.level.block.state.BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        LuaValue callback = MLuaBehaviorRegistry.getBehavior("block", this.behaviorKey, "onRightClick");
        MLuaBehaviorRegistry.ContainerDefinition container = MLuaBehaviorRegistry.getBlockContainer(this.behaviorKey);
        if (!callback.isnil() || container != null) {
            if (level.isClientSide) return InteractionResult.sidedSuccess(true);
            try {
                if (!callback.isnil()) {
                    var registration = MLuaRuntime.registrationForLevel(level);
                    LuaTable ctx = new LuaTable();
                    ctx.set("World", level.dimension().identifier().toString());
                    ctx.set("Position", MLuaPlayerWrapper.positionTable(
                        pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
                    ctx.set("Block", MLuaBlockWrapper.blockTable(level, pos, registration));
                    if (player instanceof ServerPlayer serverPlayer) {
                        ctx.set("Player", MLuaPlayerWrapper.playerTable(
                            MCServerPlayerImpl.fromReal(serverPlayer), registration));
                    }
                    ctx.set("Face", hit.getDirection().getName());
                    Varargs result = callback.invoke(ctx);
                    if (result.arg1().isboolean() && result.arg1().toboolean()) return InteractionResult.sidedSuccess(false);
                }
            } catch (Exception exception) {
                MLuaLogger.LOGGER.error("MLua block onRightClick callback failed for '{}': {}", this.behaviorKey, exception.getMessage());
                return InteractionResult.FAIL;
            }
            if (container != null) return openContainer(level, pos, player, container);
        }
        return super.useWithoutItem(state, level, pos, player, hit);
    }
    private InteractionResult openContainer(Level level, BlockPos pos, Player player,
                                             MLuaBehaviorRegistry.ContainerDefinition definition) {
        if (!(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) return InteractionResult.PASS;
        if (!(level.getBlockEntity(pos) instanceof MLuaVisualBlockEntity container)) return InteractionResult.FAIL;
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
        return InteractionResult.SUCCESS;
    }
    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        MLuaBlockCallbackDispatcher.fireOnPlace(level, pos, null);
        super.onPlace(state, level, pos, oldState, movedByPiston);
    }
    @Override
    public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state,
                              BlockEntity blockEntity, ItemStack tool) {
        MLuaBlockCallbackDispatcher.fireOnBreak(level, pos, player);
        super.playerDestroy(level, player, pos, state, blockEntity, tool);
    }
    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
        MLuaBlockCallbackDispatcher.fireOnStepOn(level, pos, entity);
        super.stepOn(level, pos, state, entity);
    }
    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity,
                                net.minecraft.world.level.block.InsideBlockEffectApplier effectApplier,
                                boolean movedByPiston) {
        MLuaBlockCallbackDispatcher.fireCallback("onEntityInside", level, pos,
            MLuaBlockCallbackDispatcher.entityContext(level, pos, entity));
        super.entityInside(state, level, pos, entity, effectApplier, movedByPiston);
    }
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, net.minecraft.world.level.block.Block block,
                                   net.minecraft.world.redstone.Orientation orientation, boolean movedByPiston) {
        MLuaBlockCallbackDispatcher.fireCallback("onNeighborChanged", level, pos,
            MLuaBlockCallbackDispatcher.eventContext(level, pos));
        super.neighborChanged(state, level, pos, block, orientation, movedByPiston);
    }
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            MLuaBlockCallbackDispatcher.fireCallback("onRemove", level, pos,
                MLuaBlockCallbackDispatcher.eventContext(level, pos));
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        MLuaBlockCallbackDispatcher.fireCallback("onRandomTick", level, pos,
            MLuaBlockCallbackDispatcher.eventContext(level, pos));
        super.randomTick(state, level, pos, random);
    }
    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        MLuaBlockCallbackDispatcher.fireCallback("onTick", level, pos,
            MLuaBlockCallbackDispatcher.eventContext(level, pos));
        super.tick(state, level, pos, random);
    }
    @Override
    public void fallOn(Level level, BlockState state, BlockPos pos, Entity entity, float fallDistance) {
        LuaTable context = MLuaBlockCallbackDispatcher.entityContext(level, pos, entity);
        context.set("FallDistance", LuaValue.valueOf(fallDistance));
        MLuaBlockCallbackDispatcher.fireCallback("onFallOn", level, pos, context);
        super.fallOn(level, state, pos, entity, fallDistance);
    }
    @Override
    public void onProjectileHit(Level level, BlockState state, BlockHitResult hitResult, Projectile projectile) {
        LuaTable context = MLuaBlockCallbackDispatcher.eventContext(level, hitResult.getBlockPos());
        context.set("Projectile", dev.mlua.modules.players.Class.MLuaEntityWrapper.entityTable(
            projectile, MLuaRuntime.registrationForLevel(level)));
        MLuaBlockCallbackDispatcher.fireCallback("onProjectileHit", level, hitResult.getBlockPos(), context);
        super.onProjectileHit(level, state, hitResult, projectile);
    }
    public static MLuaScriptedBlock create(BlockBehaviour.Properties properties, String behaviorKey,
                                            Supplier<BlockEntityType<MLuaVisualBlockEntity>> blockEntityType) {
        return new MLuaScriptedBlock(properties, behaviorKey, blockEntityType);
    }
}
