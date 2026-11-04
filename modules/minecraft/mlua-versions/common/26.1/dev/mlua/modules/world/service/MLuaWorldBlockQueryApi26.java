package dev.mlua.modules.world.service;

import dev.mlua.core.service.MLuaService;
import dev.mlua.modules.world.MLuaChunkWrapper;
import dev.mlua.modules.world.MLuaPositionArguments;
import dev.mlua.minecraft.binding.MLuaBiomeWrapper;
import dev.mlua.minecraft.binding.MLuaBlockStateWrapper;
import dev.mlua.minecraft.binding.MLuaBlockWrapper;
import dev.mlua.minecraft.binding.MLuaFluidStateWrapper;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/** Owns the typed block and environment queries on 26.x World instances. */
final class MLuaWorldBlockQueryApi26 {
    private MLuaWorldBlockQueryApi26() { }

    static void bind(MLuaWorldService owner, MLuaService.Context context, LuaTable worldTable,
                     Map<String, LuaValue> implementations, Level level) {
        owner.bindWorldMethod(worldTable, implementations, "GetBlock", MLuaWorldService.levelFunction(level, (world, args) -> {
            BlockPos position = MLuaPositionArguments.blockPosition(args, 2);
            if (!MLuaChunkWrapper.ensureLoaded(world, position)) return LuaValue.NIL;
            return MLuaBlockWrapper.blockTable(world, position, context.mlua().registration());
        }));
        owner.bindWorldMethod(worldTable, implementations, "GetBlockState", MLuaWorldService.levelFunction(level, (world, args) -> {
            BlockPos position = MLuaPositionArguments.blockPosition(args, 2);
            if (!MLuaChunkWrapper.ensureLoaded(world, position)) return LuaValue.NIL;
            return MLuaBlockStateWrapper.wrap(world.getBlockState(position), world, position,
                context.mlua().registration());
        }));
        owner.bindWorldMethod(worldTable, implementations, "GetBlockEntity", MLuaWorldService.levelFunction(level, (world, args) -> {
            BlockPos position = MLuaPositionArguments.blockPosition(args, 2);
            if (!MLuaChunkWrapper.ensureLoaded(world, position)) return LuaValue.NIL;
            return MLuaBlockWrapper.blockEntityAt(world, position, context.mlua().registration());
        }));
        owner.bindWorldMethod(worldTable, implementations, "GetFluid", MLuaWorldService.levelFunction(level, (world, args) -> {
            BlockPos position = MLuaPositionArguments.blockPosition(args, 2);
            if (!MLuaChunkWrapper.ensureLoaded(world, position)) return LuaValue.NIL;
            FluidState state = world.getFluidState(position);
            if (state.isEmpty()) return LuaValue.NIL;
            var id = BuiltInRegistries.FLUID.getKey(state.getType());
            return id == null ? LuaValue.NIL : LuaValue.valueOf(id.toString());
        }));
        owner.bindWorldMethod(worldTable, implementations, "GetFluidState", MLuaWorldService.levelFunction(level, (world, args) -> {
            BlockPos position = MLuaPositionArguments.blockPosition(args, 2);
            if (!MLuaChunkWrapper.ensureLoaded(world, position)) return LuaValue.NIL;
            return MLuaFluidStateWrapper.wrap(world.getFluidState(position), world, position,
                context.mlua().registration());
        }));
        owner.bindWorldMethod(worldTable, implementations, "GetBiome", MLuaWorldService.levelFunction(level, (world, args) -> {
            BlockPos position = MLuaPositionArguments.blockPosition(args, 2);
            if (!MLuaChunkWrapper.ensureLoaded(world, position)) return LuaValue.NIL;
            return MLuaBiomeWrapper.wrap(world.getBiome(position), world, position,
                context.mlua().registration());
        }));
        owner.bindWorldMethod(worldTable, implementations, "GetLightLevel", MLuaWorldService.levelFunction(level, (world, args) -> {
            BlockPos position = MLuaPositionArguments.blockPosition(args, 2);
            if (!MLuaChunkWrapper.ensureLoaded(world, position)) return LuaValue.NIL;
            return LuaValue.valueOf(world.getMaxLocalRawBrightness(position));
        }));
        owner.bindWorldMethod(worldTable, implementations, "GetSkyLight", MLuaWorldService.levelFunction(level, (world, args) -> {
            BlockPos position = MLuaPositionArguments.blockPosition(args, 2);
            if (!MLuaChunkWrapper.ensureLoaded(world, position)) return LuaValue.NIL;
            return LuaValue.valueOf(world.getBrightness(LightLayer.SKY, position));
        }));
        owner.bindWorldMethod(worldTable, implementations, "GetBlockLight", MLuaWorldService.levelFunction(level, (world, args) -> {
            BlockPos position = MLuaPositionArguments.blockPosition(args, 2);
            if (!MLuaChunkWrapper.ensureLoaded(world, position)) return LuaValue.NIL;
            return LuaValue.valueOf(world.getBrightness(LightLayer.BLOCK, position));
        }));
        owner.bindWorldMethod(worldTable, implementations, "CanSeeSky", MLuaWorldService.levelFunction(level, (world, args) -> {
            BlockPos position = MLuaPositionArguments.blockPosition(args, 2);
            if (!MLuaChunkWrapper.ensureLoaded(world, position)) return LuaValue.NIL;
            return LuaValue.valueOf(world.canSeeSky(position));
        }));
        owner.bindWorldMethod(worldTable, implementations, "IsRaining", MLuaWorldService.levelFunction(level,
            (world, args) -> LuaValue.valueOf(world.isRaining())));
        owner.bindWorldMethod(worldTable, implementations, "IsThundering", MLuaWorldService.levelFunction(level,
            (world, args) -> LuaValue.valueOf(world.isThundering())));
        owner.bindWorldMethod(worldTable, implementations, "IsRainingAt", MLuaWorldService.levelFunction(level, (world, args) -> {
            BlockPos position = MLuaPositionArguments.blockPosition(args, 2);
            if (!MLuaChunkWrapper.ensureLoaded(world, position)) return LuaValue.NIL;
            return LuaValue.valueOf(world.isRainingAt(position));
        }));
        owner.bindWorldMethod(worldTable, implementations, "GetSurfaceY", MLuaWorldService.levelFunction(level, (world, args) -> {
            double[] position = MLuaPositionArguments.vector3(args, 2);
            BlockPos column = BlockPos.containing(position[0], world.getMinY(), position[2]);
            if (!MLuaChunkWrapper.ensureLoaded(world, column)) return LuaValue.NIL;
            return LuaValue.valueOf(world.getHeight(Heightmap.Types.MOTION_BLOCKING,
                column.getX(), column.getZ()));
        }));
    }
}
