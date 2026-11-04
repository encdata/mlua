package dev.mlua.modules.world;

import dev.mlua.minecraft.binding.MLuaBiomeWrapper;
import dev.mlua.minecraft.binding.MLuaBlockStateWrapper;
import dev.mlua.minecraft.binding.MLuaBlockWrapper;
import dev.mlua.minecraft.binding.MLuaFluidStateWrapper;
import dev.mlua.module.MLuaRegistration;
import dev.mlua.modules.players.classes.MLuaEntityWrapper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/** Owns read/query bindings for a live Chunk instance. */
final class MLuaChunkContentBindings {
    private MLuaChunkContentBindings() { }

    static void bind(LuaTable chunk, MLuaRegistration registration) {
        MLuaChunkWrapper.bind(chunk, "GetBlockAt", args -> {
            Level world = MLuaChunkWrapper.level(args.arg1());
            BlockPos pos = MLuaChunkWrapper.requirePosition(args, 2, args.arg1());
            if (!MLuaChunkWrapper.ensureLoaded(world, args.arg1())) return LuaValue.NIL;
            return MLuaBlockWrapper.blockTable(world, pos, registration);
        });
        MLuaChunkWrapper.bind(chunk, "GetBlockStateAt", args -> {
            Level world = MLuaChunkWrapper.level(args.arg1());
            BlockPos pos = MLuaChunkWrapper.requirePosition(args, 2, args.arg1());
            if (!MLuaChunkWrapper.ensureLoaded(world, args.arg1())) return LuaValue.NIL;
            return MLuaBlockStateWrapper.wrap(world.getBlockState(pos), world, pos, registration);
        });
        MLuaChunkWrapper.bind(chunk, "GetFluidStateAt", args -> {
            Level world = MLuaChunkWrapper.level(args.arg1());
            BlockPos pos = MLuaChunkWrapper.requirePosition(args, 2, args.arg1());
            if (!MLuaChunkWrapper.ensureLoaded(world, args.arg1())) return LuaValue.NIL;
            return MLuaFluidStateWrapper.wrap(world.getFluidState(pos), world, pos, registration);
        });
        MLuaChunkWrapper.bind(chunk, "GetBlockEntityAt", args -> {
            Level world = MLuaChunkWrapper.level(args.arg1());
            BlockPos pos = MLuaChunkWrapper.requirePosition(args, 2, args.arg1());
            if (!MLuaChunkWrapper.ensureLoaded(world, args.arg1())) return LuaValue.NIL;
            return MLuaBlockWrapper.blockEntityAt(world, pos, registration);
        });
        MLuaChunkWrapper.bind(chunk, "GetBiomeAt", args -> {
            Level world = MLuaChunkWrapper.level(args.arg1());
            BlockPos pos = MLuaChunkWrapper.requirePosition(args, 2, args.arg1());
            if (!MLuaChunkWrapper.ensureLoaded(world, args.arg1())) return LuaValue.NIL;
            return MLuaBiomeWrapper.wrap(world.getBiome(pos), world, pos, registration);
        });
        MLuaChunkWrapper.bind(chunk, "GetFluidAt", args -> {
            Level world = MLuaChunkWrapper.level(args.arg1());
            BlockPos pos = MLuaChunkWrapper.requirePosition(args, 2, args.arg1());
            if (!MLuaChunkWrapper.ensureLoaded(world, args.arg1())) return LuaValue.NIL;
            FluidState state = world.getFluidState(pos);
            if (state == null || state.isEmpty()) return LuaValue.NIL;
            var id = BuiltInRegistries.FLUID.getKey(state.getType());
            return id == null ? LuaValue.NIL : LuaValue.valueOf(id.toString());
        });
        MLuaChunkWrapper.bind(chunk, "GetLightAt", args -> {
            Level world = MLuaChunkWrapper.level(args.arg1());
            BlockPos pos = MLuaChunkWrapper.requirePosition(args, 2, args.arg1());
            if (!MLuaChunkWrapper.ensureLoaded(world, args.arg1())) return LuaValue.NIL;
            return LuaValue.valueOf(world.getMaxLocalRawBrightness(pos));
        });
        MLuaChunkWrapper.bind(chunk, "GetHeightAt", args -> {
            Level world = MLuaChunkWrapper.level(args.arg1());
            BlockPos pos = MLuaChunkWrapper.requirePosition(args, 2, args.arg1());
            if (!MLuaChunkWrapper.ensureLoaded(world, args.arg1())) return LuaValue.NIL;
            Heightmap.Types heightmap = MLuaChunkWrapper.heightmapType(args.optjstring(3, "MOTION_BLOCKING"));
            return LuaValue.valueOf(world.getHeight(heightmap, pos.getX(), pos.getZ()));
        });
        MLuaChunkWrapper.bind(chunk, "GetTopBlockAt", args -> {
            Level world = MLuaChunkWrapper.level(args.arg1());
            BlockPos pos = MLuaChunkWrapper.requirePosition(args, 2, args.arg1());
            if (!MLuaChunkWrapper.ensureLoaded(world, args.arg1())) return LuaValue.NIL;
            Heightmap.Types heightmap = MLuaChunkWrapper.heightmapType(args.optjstring(3, "MOTION_BLOCKING"));
            int y = world.getHeight(heightmap, pos.getX(), pos.getZ()) - 1;
            if (y < MLuaChunkWrapper.minBuildHeight(world)) return LuaValue.NIL;
            return MLuaBlockWrapper.blockTable(world, new BlockPos(pos.getX(), y, pos.getZ()), registration);
        });
        MLuaChunkWrapper.bind(chunk, "GetEntities", args -> getEntities(args.arg1(), registration));
        MLuaChunkWrapper.bind(chunk, "GetBlockEntities", args -> getBlockEntities(args.arg1(), registration));
    }

    private static LuaTable getEntities(LuaValue self, MLuaRegistration registration) {
        Level world = MLuaChunkWrapper.level(self);
        if (!MLuaChunkWrapper.ensureLoaded(world, self)) return new LuaTable();
        ChunkPos pos = MLuaChunkWrapper.chunkPosition(self);
        AABB bounds = new AABB(pos.getMinBlockX(), MLuaChunkWrapper.minBuildHeight(world), pos.getMinBlockZ(),
            pos.getMinBlockX() + 16, MLuaChunkWrapper.maxBuildHeight(world), pos.getMinBlockZ() + 16);
        LuaTable entities = new LuaTable();
        int index = 1;
        for (Entity entity : world.getEntities((Entity)null, bounds)) {
            BlockPos entityPosition = entity.blockPosition();
            if ((entityPosition.getX() >> 4) != MLuaChunkWrapper.chunkX(pos)
                    || (entityPosition.getZ() >> 4) != MLuaChunkWrapper.chunkZ(pos)) continue;
            entities.set(index++, MLuaEntityWrapper.entityTable(entity, registration));
        }
        return entities;
    }

    private static LuaTable getBlockEntities(LuaValue self, MLuaRegistration registration) {
        Level world = MLuaChunkWrapper.level(self);
        if (!MLuaChunkWrapper.ensureLoaded(world, self)) return new LuaTable();
        ChunkPos pos = MLuaChunkWrapper.chunkPosition(self);
        LevelChunk loaded = world instanceof ServerLevel server
            ? server.getChunk(MLuaChunkWrapper.chunkX(pos), MLuaChunkWrapper.chunkZ(pos))
            : world.getChunk(MLuaChunkWrapper.chunkX(pos), MLuaChunkWrapper.chunkZ(pos));
        LuaTable entities = new LuaTable();
        int index = 1;
        for (BlockEntity entity : loaded.getBlockEntities().values()) {
            LuaValue blockEntity = MLuaBlockWrapper.blockEntityAt(world, entity.getBlockPos(), registration);
            if (!blockEntity.isnil()) entities.set(index++, blockEntity);
        }
        return entities;
    }
}
