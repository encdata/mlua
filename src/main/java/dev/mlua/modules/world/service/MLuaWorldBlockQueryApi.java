//--version 1.20.1,1.21.1
package dev.mlua.modules.world.service;

import dev.mlua.core.service.MLuaService;
import dev.mlua.mc.impl.container.MCContainerImpl;
import dev.mlua.minecraft.binding.MLuaBiomeWrapper;
import dev.mlua.minecraft.binding.MLuaBlockStateWrapper;
import dev.mlua.minecraft.binding.MLuaBlockWrapper;
import dev.mlua.minecraft.binding.MLuaContainer;
import dev.mlua.minecraft.binding.MLuaFluidStateWrapper;
import dev.mlua.modules.world.MLuaChunkWrapper;
import dev.mlua.modules.world.MLuaPositionArguments;
import dev.mlua.runtime.client.MLuaVisualBlockEntity;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.FluidState;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

/** Binds loaded-chunk block, fluid, biome, light, weather, and container queries. */
final class MLuaWorldBlockQueryApi {
    private MLuaWorldBlockQueryApi() { }

    static void bind(LuaTable table, Supplier<Level> level, MLuaService.Context context) {
        table.rawset("GetBlock", MLuaWorldApiSupport.levelFn(level, (world, args) -> {
            BlockPos position = blockPosition(args);
            if (!MLuaChunkWrapper.ensureLoaded(world, position)) return LuaValue.NIL;
            return MLuaBlockWrapper.blockTable(world, position, context.mlua().registration());
        }));
        table.rawset("GetBlockEntity", MLuaWorldApiSupport.levelFn(level, (world, args) -> {
            BlockPos position = blockPosition(args);
            if (!MLuaChunkWrapper.ensureLoaded(world, position)) return LuaValue.NIL;
            return MLuaBlockWrapper.blockEntityAt(world, position, context.mlua().registration());
        }));
        table.rawset("GetBlockState", MLuaWorldApiSupport.levelFn(level, (world, args) -> {
            BlockPos position = blockPosition(args);
            if (!MLuaChunkWrapper.ensureLoaded(world, position)) return LuaValue.NIL;
            return MLuaBlockStateWrapper.wrap(world.getBlockState(position), world, position,
                context.mlua().registration());
        }));
        table.rawset("GetFluid", MLuaWorldApiSupport.levelFn(level, (world, args) -> {
            BlockPos position = blockPosition(args);
            if (!MLuaChunkWrapper.ensureLoaded(world, position)) return LuaValue.NIL;
            FluidState fluid = world.getFluidState(position);
            if (fluid == null || fluid.isEmpty()) return LuaValue.NIL;
            var id = BuiltInRegistries.FLUID.getKey(fluid.getType());
            return id == null ? LuaValue.NIL : LuaValue.valueOf(id.toString());
        }));
        table.rawset("GetFluidState", MLuaWorldApiSupport.levelFn(level, (world, args) -> {
            BlockPos position = blockPosition(args);
            if (!MLuaChunkWrapper.ensureLoaded(world, position)) return LuaValue.NIL;
            return MLuaFluidStateWrapper.wrap(world.getFluidState(position), world, position,
                context.mlua().registration());
        }));
        table.rawset("GetBiome", MLuaWorldApiSupport.levelFn(level, (world, args) -> {
            BlockPos position = blockPosition(args);
            if (!MLuaChunkWrapper.ensureLoaded(world, position)) return LuaValue.NIL;
            return MLuaBiomeWrapper.wrap(world.getBiome(position), world, position, context.mlua().registration());
        }));
        table.rawset("GetLightLevel", brightness(level, LightLayer.BLOCK, true));
        table.rawset("GetSkyLight", brightness(level, LightLayer.SKY, false));
        table.rawset("GetBlockLight", brightness(level, LightLayer.BLOCK, false));
        table.rawset("CanSeeSky", MLuaWorldApiSupport.levelFn(level, (world, args) -> {
            BlockPos position = blockPosition(args);
            if (!MLuaChunkWrapper.ensureLoaded(world, position)) return LuaValue.NIL;
            return LuaValue.valueOf(world.canSeeSky(position));
        }));
        table.rawset("GetSurfaceY", MLuaWorldApiSupport.levelFn(level, (world, args) -> {
            double[] coordinates = MLuaPositionArguments.vector3(args, 2);
            BlockPos column = BlockPos.containing(coordinates[0],
                MLuaWorldEnvironmentApi.minBuildHeight(world), coordinates[2]);
            if (!MLuaChunkWrapper.ensureLoaded(world, column)) return LuaValue.NIL;
            return LuaValue.valueOf(world.getHeight(Heightmap.Types.MOTION_BLOCKING,
                column.getX(), column.getZ()));
        }));
        bindContainerQuery(table, level, context);
    }

    private static LuaValue brightness(Supplier<Level> level, LightLayer layer,
                                       boolean maximumLocal) {
        return MLuaWorldApiSupport.levelFn(level, (world, args) -> {
            BlockPos position = blockPosition(args);
            if (!MLuaChunkWrapper.ensureLoaded(world, position)) return LuaValue.NIL;
            int light = maximumLocal ? world.getMaxLocalRawBrightness(position)
                : world.getBrightness(layer, position);
            return LuaValue.valueOf(light);
        });
    }

    private static void bindContainerQuery(LuaTable table, Supplier<Level> level,
                                          MLuaService.Context context) {
        table.rawset("GetBlockInventory", MLuaWorldApiSupport.serverLevelFn(context, level,
            (ServerLevel world, Varargs args) -> {
                BlockPos position = blockPosition(args);
                if (!MLuaChunkWrapper.ensureLoaded(world, position)) return LuaValue.NIL;
                BlockEntity blockEntity = world.getBlockEntity(position);
                if (blockEntity instanceof Container container
                    && (!(blockEntity instanceof MLuaVisualBlockEntity scripted) || scripted.hasContainer())) {
                    return MLuaContainer.containerTable(MCContainerImpl.fromReal(container), true,
                        context.mlua().registration());
                }
                return LuaValue.NIL;
            }));
    }

    private static BlockPos blockPosition(Varargs args) {
        return MLuaPositionArguments.blockPosition(args, 2);
    }
}
//--endversion
