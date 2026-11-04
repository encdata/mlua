package dev.mlua.modules.world;

import dev.mlua.api.MLuaLuaErrors;
import dev.mlua.core.classes.MLuaClassMethods;
import dev.mlua.minecraft.binding.MLuaLuaArguments;
import dev.mlua.module.MLuaRegistration;
import dev.mlua.runtime.MLuaGameThreadAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/** World-level chunk operations; Chunk instance behavior stays in {@link MLuaChunkWrapper}. */
public final class MLuaWorldChunkApi {
    private MLuaWorldChunkApi() { }

    public static void bind(LuaTable world, MLuaRegistration registration) {
        bindWorldMethod(world, registration, "IsChunkLoaded", args -> {
            Level level = MLuaChunkWrapper.level(args.arg1());
            ChunkPos position = MLuaChunkWrapper.chunkPosition(args, 2);
            BlockPos origin = new BlockPos(position.getMinBlockX(),
                MLuaChunkWrapper.minBuildHeight(level), position.getMinBlockZ());
            return LuaValue.valueOf(level.hasChunkAt(origin));
        });
        bindWorldMethod(world, registration, "IsBlockLoaded", args -> {
            Level level = MLuaChunkWrapper.level(args.arg1());
            double[] coordinates = MLuaPositionArguments.vector3(args, 2);
            BlockPos position = BlockPos.containing(coordinates[0], coordinates[1], coordinates[2]);
            return LuaValue.valueOf(level.hasChunkAt(position));
        });
        LuaValue getChunk = worldMethod("GetChunk", args -> {
            Level level = MLuaChunkWrapper.level(args.arg1());
            return MLuaChunkWrapper.chunkTable(level, MLuaChunkWrapper.chunkPosition(args, 2),
                registration, args.arg1().checktable());
        });
        bindWorldMethod(world, registration, "GetChunk", getChunk);
        bindWorldMethod(world, registration, "GetChunkAt", getChunk);
        bindWorldMethod(world, registration, "LoadChunk", args -> {
            Level level = MLuaChunkWrapper.level(args.arg1());
            ServerLevel server = requireServerLevel(level, "World:LoadChunk");
            ChunkPos position = MLuaChunkWrapper.chunkPosition(args, 2);
            server.getChunk(MLuaChunkWrapper.chunkX(position), MLuaChunkWrapper.chunkZ(position));
            return MLuaChunkWrapper.chunkTable(level, position, registration, args.arg1().checktable());
        });
        bindWorldMethod(world, registration, "UnloadChunk", args -> {
            Level level = MLuaChunkWrapper.level(args.arg1());
            ChunkPos position = MLuaChunkWrapper.chunkPosition(args, 2);
            return LuaValue.valueOf(MLuaChunkWrapper.setForced(level, position, false, "World:UnloadChunk"));
        });
        bindWorldMethod(world, registration, "SetChunkForced", args -> {
            Level level = MLuaChunkWrapper.level(args.arg1());
            ChunkPos position = MLuaChunkWrapper.chunkPosition(args, 2);
            boolean forced = args.checkboolean(3);
            return LuaValue.valueOf(MLuaChunkWrapper.setForced(level, position, forced, "World:SetChunkForced"));
        });
        bindWorldMethod(world, registration, "ForceLoadChunk", args -> {
            Level level = MLuaChunkWrapper.level(args.arg1());
            ChunkPos position = MLuaChunkWrapper.chunkPosition(args, 2);
            return LuaValue.valueOf(MLuaChunkWrapper.setForced(level, position, true, "World:ForceLoadChunk"));
        });
        bindWorldMethod(world, registration, "IsChunkForced", args -> {
            Level level = MLuaChunkWrapper.level(args.arg1());
            ChunkPos position = MLuaChunkWrapper.chunkPosition(args, 2);
            return level instanceof ServerLevel server
                ? LuaValue.valueOf(MLuaChunkWrapper.isForced(server, position)) : LuaValue.FALSE;
        });
        bindWorldMethod(world, registration, "GetEntitiesInChunk", args -> {
            Level level = MLuaChunkWrapper.level(args.arg1());
            ChunkPos position = MLuaChunkWrapper.chunkPosition(args, 2);
            BlockPos origin = new BlockPos(position.getMinBlockX(),
                MLuaChunkWrapper.minBuildHeight(level), position.getMinBlockZ());
            boolean load = MLuaLuaArguments.optionalBoolean(
                args, 3, true, "World:GetEntitiesInChunk", "loadChunk");
            if (!level.hasChunkAt(origin)) {
                if (!load || !(level instanceof ServerLevel server)) return new LuaTable();
                server.getChunk(MLuaChunkWrapper.chunkX(position), MLuaChunkWrapper.chunkZ(position));
            }
            LuaTable chunk = MLuaChunkWrapper.chunkTable(level, position, registration, world);
            return chunk.get("GetEntities").invoke(LuaValue.varargsOf(new LuaValue[]{chunk})).arg1();
        });
    }

    private static void bindWorldMethod(LuaTable world, MLuaRegistration registration, String name, Body body) {
        bindWorldMethod(world, registration, name, worldMethod(name, body));
    }

    private static void bindWorldMethod(LuaTable world, MLuaRegistration registration, String name,
                                        LuaValue method) {
        String description = WorldModule.worldMethods().get(name);
        if (description == null) throw new IllegalStateException("World method has no description: " + name);
        MLuaClassMethods.bindInstanceMethod(registration, "World", world, name, method, description);
    }

    private static LuaValue worldMethod(String name, Body body) {
        return new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                try {
                    Level level = MLuaChunkWrapper.level(args.arg1());
                    return MLuaGameThreadAccess.call(level, () -> body.apply(args));
                } catch (LuaError error) {
                    throw error;
                } catch (Exception error) {
                    throw MLuaLuaErrors.javaFailure("World:" + name, error);
                }
            }
        };
    }

    private static ServerLevel requireServerLevel(Level level, String method) {
        if (level instanceof ServerLevel server) return server;
        throw new LuaError("[MLua] " + method + " is server-side only.");
    }

    @FunctionalInterface
    private interface Body { LuaValue apply(Varargs args) throws Exception; }
}
