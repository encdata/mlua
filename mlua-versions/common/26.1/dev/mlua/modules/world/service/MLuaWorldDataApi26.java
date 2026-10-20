package dev.mlua.modules.world.service;

import dev.mlua.core.service.MLuaService;
import dev.mlua.mc.impl.container.MCContainerImpl;
import dev.mlua.minecraft.binding.MLuaContainer;
import dev.mlua.modules.world.MLuaChunkWrapper;
import dev.mlua.modules.world.MLuaPositionArguments;
import dev.mlua.modules.world.MLuaWorldGameRules26;
import dev.mlua.runtime.client.MLuaVisualBlockEntity;
import dev.mlua.runtime.data.MLuaDataManager;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/** Binds persistent data, gamerules, and block inventory access for 26.x Worlds. */
final class MLuaWorldDataApi26 {
    private MLuaWorldDataApi26() { }

    static void bind(MLuaWorldService owner, MLuaService.Context context, LuaTable worldTable,
                     Map<String, LuaValue> implementations, Level level) {
        owner.bindWorldMethod(worldTable, implementations, "GetBlockInventory", MLuaWorldService.levelFunction(level, (world, args) -> {
            ServerLevel serverLevel = MLuaWorldService.requireServerWorld(world, "World:GetBlockInventory");
            BlockPos position = MLuaPositionArguments.blockPosition(args, 2);
            if (!MLuaChunkWrapper.ensureLoaded(serverLevel, position)) return LuaValue.NIL;
            BlockEntity blockEntity = serverLevel.getBlockEntity(position);
            if (!(blockEntity instanceof Container container)
                || (blockEntity instanceof MLuaVisualBlockEntity scripted && !scripted.hasContainer())) {
                return LuaValue.NIL;
            }
            return MLuaContainer.containerTable(MCContainerImpl.fromReal(container), true,
                context.mlua().registration());
        }));
        owner.bindWorldMethod(worldTable, implementations, "GetPersistentData", MLuaWorldService.levelFunction(level,
            (world, args) -> MLuaDataManager.worldPersistentData(world)));
        owner.bindWorldMethod(worldTable, implementations, "GetSyncedData", MLuaWorldService.levelFunction(level,
            (world, args) -> MLuaDataManager.worldSyncedData(world)));
        owner.bindWorldMethod(worldTable, implementations, "GetData", MLuaWorldService.levelFunction(level, (world, args) -> {
            LuaTable data = MLuaDataManager.worldPersistentData(world);
            return data.get("Get").call(data, LuaValue.valueOf(args.checkjstring(2)));
        }));
        owner.bindWorldMethod(worldTable, implementations, "SetData", MLuaWorldService.levelFunction(level, (world, args) -> {
            MLuaWorldService.requireServerWorld(world, "SetData");
            LuaTable data = MLuaDataManager.worldPersistentData(world);
            data.get("Set").call(data, LuaValue.valueOf(args.checkjstring(2)), args.arg(3));
            return LuaValue.NONE;
        }));
        owner.bindWorldMethod(worldTable, implementations, "RemoveData", MLuaWorldService.levelFunction(level, (world, args) -> {
            MLuaWorldService.requireServerWorld(world, "RemoveData");
            LuaTable data = MLuaDataManager.worldPersistentData(world);
            data.get("Remove").call(data, LuaValue.valueOf(args.checkjstring(2)));
            return LuaValue.NONE;
        }));
        owner.bindWorldMethod(worldTable, implementations, "ListGameRules", MLuaWorldService.levelFunction(level,
            (world, args) -> MLuaWorldGameRules26.list(world, context.mlua().registration(), () -> context.mlua().server())));
        owner.bindWorldMethod(worldTable, implementations, "GetGameRule", MLuaWorldService.levelFunction(level,
            (world, args) -> MLuaWorldGameRules26.get(world, args.checkjstring(2))));
        owner.bindWorldMethod(worldTable, implementations, "GetGameRuleEntry", MLuaWorldService.levelFunction(level,
            (world, args) -> MLuaWorldGameRules26.entry(world, args.checkjstring(2),
                context.mlua().registration(), () -> context.mlua().server())));
        owner.bindWorldMethod(worldTable, implementations, "SetGameRule", MLuaWorldService.levelFunction(level, (world, args) -> {
            ServerLevel serverLevel = MLuaWorldService.requireServerWorld(world, "SetGameRule");
            MLuaWorldGameRules26.set(serverLevel, args.checkjstring(2), args.arg(3), context.mlua().server());
            return LuaValue.NONE;
        }));
    }
}
