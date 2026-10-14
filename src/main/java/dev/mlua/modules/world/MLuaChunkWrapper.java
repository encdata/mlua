package dev.mlua.modules.world;

import dev.mlua.core.classes.MLuaClassMethods;
import dev.mlua.module.MLuaRegistration;
import dev.mlua.minecraft.MLuaResourceLocations;
import dev.mlua.minecraft.binding.MLuaBlockWrapper;
import dev.mlua.modules.players.Class.MLuaEntityWrapper;
import dev.mlua.modules.players.Class.MLuaPlayerWrapper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

public final class MLuaChunkWrapper {
    private MLuaChunkWrapper() {
    }

    public static LuaTable chunkTable(Level level, ChunkPos position, MLuaRegistration registration) {
        LuaTable fields = new LuaTable();
        fields.set("__level", LuaValue.userdataOf(level));
        fields.set("__chunkX", LuaValue.valueOf(position.x));
        fields.set("__chunkZ", LuaValue.valueOf(position.z));
        LuaTable chunk = registration.createClassInstance("Chunk", fields);
        bind(chunk, "GetX", args -> LuaValue.valueOf(chunkX(args.arg1())));
        bind(chunk, "GetZ", args -> LuaValue.valueOf(chunkZ(args.arg1())));
        bind(chunk, "GetPosition", args -> {
            ChunkPos pos = chunkPosition(args.arg1());
            return MLuaPlayerWrapper.positionTable(pos.getMinBlockX(), 0, pos.getMinBlockZ());
        });
        bind(chunk, "IsLoaded", args -> {
            Level world = level(args.arg1());
            return LuaValue.valueOf(world.hasChunkAt(chunkOrigin(args.arg1(), world)));
        });
        bind(chunk, "IsForced", args -> LuaValue.valueOf(isForced(args.arg1())));
        bind(chunk, "Load", args -> {
            load(args.arg1());
            return args.arg1();
        });
        bind(chunk, "Unload", args -> {
            Level world = level(args.arg1());
            if (!(world instanceof ServerLevel server)) return LuaValue.FALSE;
            ChunkPos pos = chunkPosition(args.arg1());
            server.setChunkForced(pos.x, pos.z, false);
            return LuaValue.valueOf(!isForced(args.arg1()));
        });
        bind(chunk, "SetForced", args -> {
            Level world = level(args.arg1());
            if (!(world instanceof ServerLevel server)) return LuaValue.FALSE;
            ChunkPos pos = chunkPosition(args.arg1());
            boolean forced = args.checkboolean(2);
            server.setChunkForced(pos.x, pos.z, forced);
            return LuaValue.valueOf(isForced(args.arg1()) == forced);
        });
        bind(chunk, "ForceLoad", args -> {
            Level world = level(args.arg1());
            if (!(world instanceof ServerLevel server)) return LuaValue.FALSE;
            ChunkPos pos = chunkPosition(args.arg1());
            server.setChunkForced(pos.x, pos.z, true);
            return LuaValue.valueOf(isForced(args.arg1()));
        });
        bind(chunk, "GetBlockAt", args -> {
            Level world = level(args.arg1());
            BlockPos pos = requirePosition(args, 2, args.arg1());
            if (!ensureLoaded(world, args.arg1())) return LuaValue.NIL;
            return MLuaBlockWrapper.blockTable(world, pos, registration);
        });
        bind(chunk, "GetBlockEntityAt", args -> {
            Level world = level(args.arg1());
            BlockPos pos = requirePosition(args, 2, args.arg1());
            if (!ensureLoaded(world, args.arg1())) return LuaValue.NIL;
            return MLuaBlockWrapper.blockTable(world, pos, registration).get("BlockEntity");
        });
        bind(chunk, "SetBlockAt", args -> {
            Level world = level(args.arg1());
            if (!(world instanceof ServerLevel server)) throw new LuaError("[MLua] Chunk:SetBlockAt is server-side only.");
            BlockPos pos = requirePosition(args, 2, args.arg1());
            String id = args.checkjstring(2 + coordinateWidth(args, 2));
            Block block = BuiltInRegistries.BLOCK.getOptional(MLuaResourceLocations.parse(id)).orElse(null);
            if (block == null) throw new LuaError("[MLua] Unknown block: " + id);
            if (!ensureLoaded(server, args.arg1())) return LuaValue.FALSE;
            return LuaValue.valueOf(server.setBlock(pos, block.defaultBlockState(), 3));
        });
        bind(chunk, "GetEntities", args -> {
            Level world = level(args.arg1());
            if (!ensureLoaded(world, args.arg1())) return new LuaTable();
            ChunkPos pos = chunkPosition(args.arg1());
            AABB bounds = new AABB(pos.getMinBlockX(), world.getMinBuildHeight(), pos.getMinBlockZ(),
                pos.getMinBlockX() + 16, world.getMaxBuildHeight(), pos.getMinBlockZ() + 16);
            LuaTable entities = new LuaTable();
            int index = 1;
            for (Entity entity : world.getEntities((Entity)null, bounds)) {
                entities.set(index++, MLuaEntityWrapper.entityTable(entity, registration));
            }
            return entities;
        });
        WorldModule.chunkMethods().forEach((name, description) -> {
            LuaValue implementation = chunk.rawget(name);
            MLuaClassMethods.bindInstanceMethod(registration, "Chunk", chunk, name, implementation, description);
            chunk.rawset(name, LuaValue.NIL);
        });
        return chunk;
    }

    private static void bind(LuaTable chunk, String method, Body body) {
        chunk.set(method, new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                try {
                    return body.apply(args);
                } catch (LuaError error) {
                    throw error;
                } catch (Exception error) {
                    throw new LuaError("[MLua] " + error.getMessage());
                }
            }
        });
    }

    private static boolean isForced(LuaValue self) {
        Level world = level(self);
        if (!(world instanceof ServerLevel server)) return false;
        ChunkPos pos = chunkPosition(self);
        return server.getForcedChunks().contains(ChunkPos.asLong(pos.x, pos.z));
    }

    private static void load(LuaValue self) {
        Level world = level(self);
        if (!(world instanceof ServerLevel server)) throw new LuaError("[MLua] Chunk loading is server-side only.");
        ChunkPos pos = chunkPosition(self);
        server.getChunk(pos.x, pos.z);
    }

    private static boolean ensureLoaded(Level world, LuaValue self) {
        BlockPos origin = chunkOrigin(self, world);
        if (world.hasChunkAt(origin)) return true;
        if (!(world instanceof ServerLevel server)) return false;
        ChunkPos pos = chunkPosition(self);
        server.getChunk(pos.x, pos.z);
        return world.hasChunkAt(origin);
    }

    private static BlockPos requirePosition(Varargs args, int index, LuaValue self) {
        double[] coordinates = MLuaPlayerWrapper.positionArgs(args, index);
        ChunkPos chunk = chunkPosition(self);
        BlockPos position = BlockPos.containing(coordinates[0], coordinates[1], coordinates[2]);
        if (position.getX() >> 4 != chunk.x || position.getZ() >> 4 != chunk.z) {
            throw new LuaError("[MLua] Block position is outside this chunk.");
        }
        return position;
    }

    private static int coordinateWidth(Varargs args, int index) {
        return args.arg(index).istable() ? 1 : 3;
    }

    private static BlockPos chunkOrigin(LuaValue self, Level world) {
        ChunkPos pos = chunkPosition(self);
        return new BlockPos(pos.getMinBlockX(), world.getMinBuildHeight(), pos.getMinBlockZ());
    }

    private static ChunkPos chunkPosition(LuaValue self) {
        LuaTable table = self.checktable();
        return new ChunkPos(table.get("__chunkX").checkint(), table.get("__chunkZ").checkint());
    }

    private static int chunkX(LuaValue self) {
        return self.checktable().get("__chunkX").checkint();
    }

    private static int chunkZ(LuaValue self) {
        return self.checktable().get("__chunkZ").checkint();
    }

    private static Level level(LuaValue self) {
        LuaValue value = self.checktable().get("__level");
        if (!value.isuserdata(Level.class)) throw new LuaError("[MLua] Chunk has no live dimension.");
        return (Level)value.touserdata(Level.class);
    }

    @FunctionalInterface
    private interface Body {
        LuaValue apply(Varargs args);
    }
}
