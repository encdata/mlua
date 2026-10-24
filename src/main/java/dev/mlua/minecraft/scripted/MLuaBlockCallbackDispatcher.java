package dev.mlua.minecraft.scripted;

import dev.mlua.core.MLuaRuntime;
import dev.mlua.core.MLuaLogger;
//--version 1.20.1,1.21.1
import dev.mlua.mc.impl.entity.MCLocalPlayerImpl;
//--endversion
import dev.mlua.mc.impl.entity.MCServerPlayerImpl;
import dev.mlua.minecraft.binding.MLuaBlockWrapper;
import dev.mlua.modules.players.classes.MLuaEntityWrapper;
import dev.mlua.modules.players.classes.MLuaPlayerWrapper;
import java.util.function.Supplier;
//--version 1.20.1,1.21.1
import net.minecraft.client.player.LocalPlayer;
//--endversion
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

public final class MLuaBlockCallbackDispatcher {
    private MLuaBlockCallbackDispatcher() {
    }

    public static void fireOnPlace(Level level, BlockPos pos, Player player) {
        if (isClientSide(level)) {
            return;
        }
        String behaviorKey = behaviorKey(level, pos);
        if (behaviorKey == null || isDuplicate("place", level, pos, player)) {
            return;
        }
        invokeLifecycleCallback(level, pos, behaviorKey, "onPlace", player, null);
    }

    public static void fireOnBreak(Level level, BlockPos pos, Player player) {
        if (isClientSide(level)) {
            return;
        }
        String behaviorKey = behaviorKey(level, pos);
        if (behaviorKey == null || isDuplicate("break", level, pos, player)) {
            return;
        }
        invokeLifecycleCallback(level, pos, behaviorKey, "onBreak", player, null);
    }

    public static void fireOnStepOn(Level level, BlockPos pos, Entity entity) {
        if (isClientSide(level)) {
            return;
        }
        String behaviorKey = behaviorKey(level, pos);
        if (behaviorKey == null || isDuplicate("step", level, pos, entity)) {
            return;
        }
        invokeLifecycleCallback(level, pos, behaviorKey, "onStepOn",
            entity instanceof Player player ? player : null, entity);
    }

    public static void fireCallback(String callbackName, Level level, BlockPos pos, LuaTable context) {
        fireCallback(callbackName, level, pos, () -> context);
    }

    /** Resolves the callback before building context, avoiding wrapper work for unhandled events. */
    public static void fireCallback(String callbackName, Level level, BlockPos pos,
                                    Supplier<LuaTable> contextFactory) {
        if (isClientSide(level)) return;
        String behaviorKey = behaviorKey(level, pos);
        if (behaviorKey == null) return;
        MLuaBehaviorRegistry.Snapshot behaviors = MLuaBehaviorRegistry.snapshotBehaviors("block", behaviorKey);
        LuaValue callback = behaviors.callbacks().getOrDefault(callbackName, LuaValue.NIL);
        if (callback.isnil()) return;
        LuaTable context;
        try {
            context = contextFactory.get();
        } catch (Exception failure) {
            MLuaLogger.LOGGER.error("MLua block {} context failed for '{}' at {}",
                callbackName, behaviorKey, pos, failure);
            return;
        }
        MLuaBlockCallbackFailureTracker.invoke(level, pos, behaviorKey, callbackName, behaviors, callback,
            LuaValue.varargsOf(new LuaValue[]{context}));
    }

    public static LuaTable eventContext(Level level, BlockPos pos) {
        return baseContext(level, pos, null);
    }

    /** Builds the shared neighbor-change context used by every supported Minecraft version. */
    public static LuaTable neighborChangedContext(Level level, BlockPos pos, BlockPos neighborPos) {
        LuaTable context = eventContext(level, pos);
        var registration = MLuaRuntime.registrationForLevel(level);
        context.set("Neighbor", neighborPos == null ? LuaValue.NIL
            : MLuaBlockWrapper.blockTable(level, neighborPos, registration));
        return context;
    }

    public static void fireNeighborChanged(Level level, BlockPos pos, BlockPos neighborPos) {
        if (isClientSide(level)) return;
        fireCallback("onNeighborChanged", level, pos,
            () -> neighborChangedContext(level, pos, neighborPos));
    }

    public static LuaTable entityContext(Level level, BlockPos pos, Entity entity) {
        LuaTable context = baseContext(level, pos, entity instanceof Player player ? player : null);
        context.set("Entity", MLuaEntityWrapper.entityTable(entity, MLuaRuntime.registrationForLevel(level)));
        return context;
    }

    private static void invokeLifecycleCallback(Level level, BlockPos pos, String behaviorKey,
            String event, Player player, Entity entity) {
        MLuaBehaviorRegistry.Snapshot behaviors = MLuaBehaviorRegistry.snapshotBehaviors("block", behaviorKey);
        LuaValue callback = behaviors.callbacks().getOrDefault(event, LuaValue.NIL);
        if (callback.isnil()) return;
        try {
            LuaTable context = baseContext(level, pos, player);
            if (entity != null) {
                context.set("Entity", MLuaEntityWrapper.entityTable(entity, MLuaRuntime.registrationForLevel(level)));
            }
            MLuaBlockCallbackFailureTracker.invoke(level, pos, behaviorKey, event, behaviors, callback,
                LuaValue.varargsOf(new LuaValue[]{context}));
        } catch (Exception failure) {
            MLuaLogger.LOGGER.error("MLua block {} callback context failed for '{}' at {}",
                event, behaviorKey, pos, failure);
        }
    }

    private static LuaTable baseContext(Level level, BlockPos pos, Player player) {
        LuaTable ctx = new LuaTable();
        ctx.set("World", dimensionId(level));
        ctx.set("Position", MLuaPlayerWrapper.positionTable(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
        var registration = MLuaRuntime.registrationForLevel(level);
        ctx.set("Block", MLuaBlockWrapper.blockTable(level, pos, registration));
        if (player instanceof ServerPlayer serverPlayer) {
            ctx.set("Player", MLuaPlayerWrapper.playerTable(MCServerPlayerImpl.fromReal(serverPlayer), registration));
        }
        //--version 1.20.1,1.21.1
        if (player instanceof LocalPlayer localPlayer) {
            ctx.set("Player", MLuaPlayerWrapper.localPlayerTable(MCLocalPlayerImpl.fromReal(localPlayer), registration));
        }
        //--endversion
        return ctx;
    }

    private static String behaviorKey(Level level, BlockPos pos) {
        Block block = level.getBlockState(pos).getBlock();
        return BuiltInRegistries.BLOCK.getKey(block) != null ? BuiltInRegistries.BLOCK.getKey(block).toString() : null;
    }

    private static boolean isDuplicate(String type, Level level, BlockPos pos, Entity entity) {
        String entityKey = entity != null ? entity.getStringUUID() : "none";
        return MLuaBlockEventDeduplicator.isDuplicate(type, level, dimensionId(level),
            pos.asLong(), entityKey, level.getGameTime());
    }

    private static boolean isClientSide(Level level) {
        //--version 26.1
        return level.isClientSide();
        //--endversion
        //--version 1.20.1,1.21.1
        return level.isClientSide;
        //--endversion
    }

    private static String dimensionId(Level level) {
        //--version 26.1
        return level.dimension().identifier().toString();
        //--endversion
        //--version 1.20.1,1.21.1
        return level.dimension().location().toString();
        //--endversion
    }
}
