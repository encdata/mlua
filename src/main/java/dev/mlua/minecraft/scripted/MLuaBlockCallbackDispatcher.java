package dev.mlua.minecraft.scripted;

import dev.mlua.core.MLuaRuntime;
import dev.mlua.core.MLuaLogger;
import dev.mlua.mc.impl.entity.MCLocalPlayerImpl;
import dev.mlua.mc.impl.entity.MCServerPlayerImpl;
import dev.mlua.minecraft.binding.MLuaBlockWrapper;
import dev.mlua.modules.players.Class.MLuaEntityWrapper;
import dev.mlua.modules.players.Class.MLuaPlayerWrapper;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.player.LocalPlayer;
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
    private static final Map<String, Long> RECENT_CALLBACKS = new ConcurrentHashMap<>();

    private MLuaBlockCallbackDispatcher() {
    }

    public static void fireOnPlace(Level level, BlockPos pos, Player player) {
        if (level.isClientSide) {
            return;
        }
        String behaviorKey = behaviorKey(level, pos);
        if (behaviorKey == null || isDuplicate("place", level, pos, player)) {
            return;
        }
        try {
            MLuaBehaviorRegistry.invokeBehavior("block", behaviorKey, "onPlace",
                LuaValue.varargsOf(new LuaValue[]{baseContext(level, pos, player)}));
        } catch (Exception ex) {
            MLuaLogger.LOGGER.error("MLua block onPlace callback failed for '{}': {}", behaviorKey, ex.getMessage());
        }
    }

    public static void fireOnBreak(Level level, BlockPos pos, Player player) {
        if (level.isClientSide) {
            return;
        }
        String behaviorKey = behaviorKey(level, pos);
        if (behaviorKey == null || isDuplicate("break", level, pos, player)) {
            return;
        }
        try {
            MLuaBehaviorRegistry.invokeBehavior("block", behaviorKey, "onBreak",
                LuaValue.varargsOf(new LuaValue[]{baseContext(level, pos, player)}));
        } catch (Exception ex) {
            MLuaLogger.LOGGER.error("MLua block onBreak callback failed for '{}': {}", behaviorKey, ex.getMessage());
        }
    }

    public static void fireOnStepOn(Level level, BlockPos pos, Entity entity) {
        if (level.isClientSide) {
            return;
        }
        String behaviorKey = behaviorKey(level, pos);
        if (behaviorKey == null || isDuplicate("step", level, pos, entity)) {
            return;
        }
        try {
            LuaTable ctx = baseContext(level, pos, entity instanceof Player player ? player : null);
            ctx.set("Entity", MLuaEntityWrapper.entityTable(entity, MLuaRuntime.registrationForLevel(level)));
            MLuaBehaviorRegistry.invokeBehavior("block", behaviorKey, "onStepOn",
                LuaValue.varargsOf(new LuaValue[]{ctx}));
        } catch (Exception ex) {
            MLuaLogger.LOGGER.error("MLua block onStepOn callback failed for '{}': {}", behaviorKey, ex.getMessage());
        }
    }

    public static void fireCallback(String callbackName, Level level, BlockPos pos, LuaTable context) {
        if (level.isClientSide) return;
        String behaviorKey = behaviorKey(level, pos);
        if (behaviorKey == null) return;
        try {
            MLuaBehaviorRegistry.invokeBehavior("block", behaviorKey, callbackName,
                LuaValue.varargsOf(new LuaValue[]{context}));
        } catch (Exception exception) {
            MLuaLogger.LOGGER.error("MLua block {} callback failed for '{}': {}", callbackName, behaviorKey, exception.getMessage());
        }
    }

    public static LuaTable eventContext(Level level, BlockPos pos) {
        return baseContext(level, pos, null);
    }

    public static LuaTable entityContext(Level level, BlockPos pos, Entity entity) {
        LuaTable context = baseContext(level, pos, entity instanceof Player player ? player : null);
        context.set("Entity", MLuaEntityWrapper.entityTable(entity, MLuaRuntime.registrationForLevel(level)));
        return context;
    }

    private static LuaTable baseContext(Level level, BlockPos pos, Player player) {
        LuaTable ctx = new LuaTable();
        ctx.set("World", level.dimension().location().toString());
        ctx.set("Position", MLuaPlayerWrapper.positionTable(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
        var registration = MLuaRuntime.registrationForLevel(level);
        ctx.set("Block", MLuaBlockWrapper.blockTable(level, pos, registration));
        if (player instanceof ServerPlayer serverPlayer) {
            ctx.set("Player", MLuaPlayerWrapper.playerTable(MCServerPlayerImpl.fromReal(serverPlayer), registration));
        } else if (player instanceof LocalPlayer localPlayer) {
            ctx.set("Player", MLuaPlayerWrapper.localPlayerTable(MCLocalPlayerImpl.fromReal(localPlayer), registration));
        }
        return ctx;
    }

    private static String behaviorKey(Level level, BlockPos pos) {
        Block block = level.getBlockState(pos).getBlock();
        return BuiltInRegistries.BLOCK.getKey(block) != null ? BuiltInRegistries.BLOCK.getKey(block).toString() : null;
    }

    private static boolean isDuplicate(String type, Level level, BlockPos pos, Entity entity) {
        long gameTime = level.getGameTime();
        String entityKey = entity != null ? entity.getStringUUID() : "none";
        String key = type + "|" + level.dimension().location() + "|" + pos.asLong() + "|" + entityKey;
        Long previous = RECENT_CALLBACKS.put(key, gameTime);
        return previous != null && previous == gameTime;
    }
}
