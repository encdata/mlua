package dev.mlua.modules.world;

import net.minecraft.world.level.block.entity.BlockEntity;

import dev.mlua.api.MLuaLuaErrors;
import dev.mlua.core.classes.MLuaClassMethods;
import dev.mlua.module.MLuaRegistration;
import dev.mlua.minecraft.MLuaResourceLocations;
import dev.mlua.minecraft.binding.MLuaBlockWrapper;
import dev.mlua.minecraft.binding.MLuaBiomeWrapper;
import dev.mlua.minecraft.binding.MLuaBlockStateWrapper;
import dev.mlua.minecraft.binding.MLuaBlockStateProperties;
import dev.mlua.minecraft.binding.MLuaFluidStateWrapper;
import dev.mlua.math.MLuaVector;
import dev.mlua.modules.players.classes.MLuaEntityWrapper;
import dev.mlua.modules.players.classes.MLuaPlayerWrapper;
import dev.mlua.runtime.MLuaGameThreadAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

public final class MLuaChunkWrapper {
    private static final Map<MLuaRegistration, Map<LuaTable, ChunkInstances>> CHUNK_INSTANCES = new WeakHashMap<>();

    private MLuaChunkWrapper() {
    }

    public static LuaTable chunkTable(Level level, ChunkPos position, MLuaRegistration registration,
                                      LuaTable worldInstance) {
        long key = ((long)chunkX(position) << 32) | (chunkZ(position) & 0xffffffffL);
        synchronized (CHUNK_INSTANCES) {
            Map<LuaTable, ChunkInstances> worlds = CHUNK_INSTANCES.computeIfAbsent(
                registration, ignored -> new WeakHashMap<>());
            ChunkInstances chunks = worlds.computeIfAbsent(worldInstance, ignored -> new ChunkInstances());
            chunks.reapCollectedInstances();
            ChunkTableReference reference = chunks.byPosition.get(key);
            LuaTable cached = reference == null ? null : reference.get();
            if (cached != null && registration.isInstanceOf(cached, "Chunk")) return cached;
            LuaTable chunk = createChunkTable(level, position, registration, worldInstance);
            chunks.byPosition.put(key, new ChunkTableReference(chunk, key, chunks.collectedInstances));
            return chunk;
        }
    }

    private static final class ChunkInstances {
        private final ReferenceQueue<LuaTable> collectedInstances = new ReferenceQueue<>();
        private final Map<Long, ChunkTableReference> byPosition = new HashMap<>();

        private void reapCollectedInstances() {
            ChunkTableReference collected;
            while ((collected = (ChunkTableReference)this.collectedInstances.poll()) != null) {
                this.byPosition.remove(collected.positionKey, collected);
            }
        }
    }

    private static final class ChunkTableReference extends WeakReference<LuaTable> {
        private final long positionKey;

        private ChunkTableReference(LuaTable instance, long positionKey, ReferenceQueue<LuaTable> queue) {
            super(instance, queue);
            this.positionKey = positionKey;
        }
    }

    private static LuaTable createChunkTable(Level level, ChunkPos position, MLuaRegistration registration,
                                             LuaTable worldInstance) {
        LuaTable fields = new LuaTable();
        fields.set("__level", LuaValue.userdataOf(level));
        fields.set("__world_instance", worldInstance);
        fields.set("__chunkX", LuaValue.valueOf(chunkX(position)));
        fields.set("__chunkZ", LuaValue.valueOf(chunkZ(position)));
        LuaTable chunk = registration.createClassInstance("Chunk", fields);
        bind(chunk, "GetX", args -> LuaValue.valueOf(chunkX(args.arg1())));
        bind(chunk, "GetZ", args -> LuaValue.valueOf(chunkZ(args.arg1())));
        Body getWorld = args -> args.arg1().checktable().get("__world_instance");
        bind(chunk, "GetWorld", getWorld);
        bind(chunk, "GetLevel", getWorld);
        bind(chunk, "GetDimension", args -> LuaValue.valueOf(level(args.arg1()).dimension().location().toString()));
        bind(chunk, "GetPosition", args -> {
            ChunkPos pos = chunkPosition(args.arg1());
            return MLuaVector.vector2(chunkX(pos), chunkZ(pos));
        });
        bind(chunk, "GetBlockOrigin", args -> {
            ChunkPos pos = chunkPosition(args.arg1());
            Level world = level(args.arg1());
            return MLuaPlayerWrapper.positionTable(pos.getMinBlockX(), minBuildHeight(world), pos.getMinBlockZ());
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
            ChunkPos pos = chunkPosition(args.arg1());
            return LuaValue.valueOf(setForced(world, pos, false, "Chunk:Unload"));
        });
        bind(chunk, "SetForced", args -> {
            Level world = level(args.arg1());
            ChunkPos pos = chunkPosition(args.arg1());
            boolean forced = args.checkboolean(2);
            return LuaValue.valueOf(setForced(world, pos, forced, "Chunk:SetForced"));
        });
        bind(chunk, "ForceLoad", args -> {
            Level world = level(args.arg1());
            ChunkPos pos = chunkPosition(args.arg1());
            return LuaValue.valueOf(setForced(world, pos, true, "Chunk:ForceLoad"));
        });
        bind(chunk, "GetBlockAt", args -> {
            Level world = level(args.arg1());
            BlockPos pos = requirePosition(args, 2, args.arg1());
            if (!ensureLoaded(world, args.arg1())) return LuaValue.NIL;
            return MLuaBlockWrapper.blockTable(world, pos, registration);
        });
        bind(chunk, "GetBlockStateAt", args -> {
            Level world = level(args.arg1());
            BlockPos pos = requirePosition(args, 2, args.arg1());
            if (!ensureLoaded(world, args.arg1())) return LuaValue.NIL;
            return MLuaBlockStateWrapper.wrap(world.getBlockState(pos), world, pos, registration);
        });
        bind(chunk, "GetFluidStateAt", args -> {
            Level world = level(args.arg1());
            BlockPos pos = requirePosition(args, 2, args.arg1());
            if (!ensureLoaded(world, args.arg1())) return LuaValue.NIL;
            return MLuaFluidStateWrapper.wrap(world.getFluidState(pos), world, pos, registration);
        });
        bind(chunk, "GetBlockEntityAt", args -> {
            Level world = level(args.arg1());
            BlockPos pos = requirePosition(args, 2, args.arg1());
            if (!ensureLoaded(world, args.arg1())) return LuaValue.NIL;
            return MLuaBlockWrapper.blockEntityAt(world, pos, registration);
        });
        bind(chunk, "GetBiomeAt", args -> {
            Level world = level(args.arg1());
            BlockPos pos = requirePosition(args, 2, args.arg1());
            if (!ensureLoaded(world, args.arg1())) return LuaValue.NIL;
            return MLuaBiomeWrapper.wrap(world.getBiome(pos), world, pos, registration);
        });
        bind(chunk, "GetFluidAt", args -> {
            Level world = level(args.arg1());
            BlockPos pos = requirePosition(args, 2, args.arg1());
            if (!ensureLoaded(world, args.arg1())) return LuaValue.NIL;
            FluidState state = world.getFluidState(pos);
            if (state == null || state.isEmpty()) return LuaValue.NIL;
            ResourceLocation id = BuiltInRegistries.FLUID.getKey(state.getType());
            return id == null ? LuaValue.NIL : LuaValue.valueOf(id.toString());
        });
        bind(chunk, "GetLightAt", args -> {
            Level world = level(args.arg1());
            BlockPos pos = requirePosition(args, 2, args.arg1());
            if (!ensureLoaded(world, args.arg1())) return LuaValue.NIL;
            return LuaValue.valueOf(world.getMaxLocalRawBrightness(pos));
        });
        bind(chunk, "GetHeightAt", args -> {
            Level world = level(args.arg1());
            BlockPos pos = requirePosition(args, 2, args.arg1());
            if (!ensureLoaded(world, args.arg1())) return LuaValue.NIL;
            Heightmap.Types heightmap = heightmapType(args.optjstring(3, "MOTION_BLOCKING"));
            return LuaValue.valueOf(world.getHeight(heightmap, pos.getX(), pos.getZ()));
        });
        bind(chunk, "GetTopBlockAt", args -> {
            Level world = level(args.arg1());
            BlockPos pos = requirePosition(args, 2, args.arg1());
            if (!ensureLoaded(world, args.arg1())) return LuaValue.NIL;
            Heightmap.Types heightmap = heightmapType(args.optjstring(3, "MOTION_BLOCKING"));
            int y = world.getHeight(heightmap, pos.getX(), pos.getZ()) - 1;
            if (y < minBuildHeight(world)) return LuaValue.NIL;
            return MLuaBlockWrapper.blockTable(world, new BlockPos(pos.getX(), y, pos.getZ()), registration);
        });
        bind(chunk, "SetBlockAt", args -> {
            Level world = level(args.arg1());
            if (!(world instanceof ServerLevel server)) throw new LuaError("[MLua] Chunk:SetBlockAt is server-side only.");
            BlockPos pos = requirePosition(args, 2, args.arg1());
            int valueIndex = 2 + coordinateWidth(args, 2);
            LuaValue blockValue = args.arg(valueIndex);
            BlockState state;
            int updateFlagsIndex;
            if (registration.isInstanceOf(blockValue, "BlockState")) {
                state = MLuaBlockStateWrapper.blockStateFromLua(blockValue, registration);
                updateFlagsIndex = valueIndex + 1;
            } else {
                String id = blockValue.checkjstring();
                Block block = BuiltInRegistries.BLOCK.getOptional(MLuaResourceLocations.parse(id)).orElse(null);
                if (block == null) throw new LuaError("[MLua] Unknown block: " + id);
                LuaValue properties = args.arg(valueIndex + 1);
                if (!properties.isnil() && !properties.istable() && !properties.isnumber()) {
                    throw new LuaError("[MLua] Chunk:SetBlockAt expects a property table followed by optional update flags.");
                }
                state = MLuaBlockStateProperties.apply(block.defaultBlockState(), properties.istable() ? properties : LuaValue.NIL);
                updateFlagsIndex = properties.istable() ? valueIndex + 2 : valueIndex + 1;
            }
            int updateFlags = MLuaBlockUpdateFlags.read(args.arg(updateFlagsIndex), "Chunk:SetBlockAt");
            if (!ensureLoaded(server, args.arg1())) return LuaValue.FALSE;
            return LuaValue.valueOf(server.setBlock(pos, state, updateFlags));
        });
        bind(chunk, "GetEntities", args -> {
            Level world = level(args.arg1());
            if (!ensureLoaded(world, args.arg1())) return new LuaTable();
            ChunkPos pos = chunkPosition(args.arg1());
            AABB bounds = new AABB(pos.getMinBlockX(), minBuildHeight(world), pos.getMinBlockZ(),
                pos.getMinBlockX() + 16, maxBuildHeight(world), pos.getMinBlockZ() + 16);
            LuaTable entities = new LuaTable();
            int index = 1;
            for (Entity entity : world.getEntities((Entity)null, bounds)) {
                BlockPos entityPosition = entity.blockPosition();
                if ((entityPosition.getX() >> 4) != chunkX(pos)
                    || (entityPosition.getZ() >> 4) != chunkZ(pos)) {
                    continue;
                }
                entities.set(index++, MLuaEntityWrapper.entityTable(entity, registration));
            }
            return entities;
        });
        bind(chunk, "GetBlockEntities", args -> {
            Level world = level(args.arg1());
            if (!ensureLoaded(world, args.arg1())) return new LuaTable();
            ChunkPos pos = chunkPosition(args.arg1());
            LevelChunk loaded = world instanceof ServerLevel server
                ? MLuaServerThread.call(server.getServer(), () -> server.getChunk(chunkX(pos), chunkZ(pos)))
                : world.getChunk(chunkX(pos), chunkZ(pos));
            LuaTable entities = new LuaTable();
            int index = 1;
            for (BlockEntity entity : loaded.getBlockEntities().values()) {
                LuaTable block = MLuaBlockWrapper.blockTable(world, entity.getBlockPos(), registration);
                LuaValue blockEntity = block.get("BlockEntity");
                if (!blockEntity.isnil()) entities.set(index++, blockEntity);
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

    public static ChunkPos chunkPosition(Varargs args, int index) {
        LuaValue value = args.arg(index);
        if (MLuaVector.isVector2(value)) {
            LuaTable vector = value.checktable();
            return new ChunkPos(
                chunkCoordinate(vector.get("X"), "X"),
                chunkCoordinate(vector.get("Y"), "Y"));
        }
        double[] position = MLuaPositionArguments.vector3(value, "Chunk position");
        BlockPos blockPosition = BlockPos.containing(position[0], position[1], position[2]);
        return new ChunkPos(blockPosition.getX() >> 4, blockPosition.getZ() >> 4);
    }

    private static int chunkCoordinate(LuaValue value, String axis) {
        double coordinate = value.checkdouble();
        if (!Double.isFinite(coordinate) || coordinate != Math.rint(coordinate)
            || coordinate < Integer.MIN_VALUE || coordinate > Integer.MAX_VALUE) {
            throw new LuaError("[MLua] Chunk Vector2 " + axis
                + " coordinate must be a whole number within the 32-bit integer range");
        }
        return (int)coordinate;
    }

    public static int chunkX(ChunkPos position) {
        //--version 26.1
        return position.x();
        //--endversion
        //--version 1.20.1,1.21.1
        return position.x;
        //--endversion
    }

    public static int chunkZ(ChunkPos position) {
        //--version 26.1
        return position.z();
        //--endversion
        //--version 1.20.1,1.21.1
        return position.z;
        //--endversion
    }

    public static boolean isForced(ServerLevel level, ChunkPos position) {
        //--version 26.1
        return level.getForceLoadedChunks().contains(position.pack());
        //--endversion
        //--version 1.20.1,1.21.1
        return level.getForcedChunks().contains(ChunkPos.asLong(chunkX(position), chunkZ(position)));
        //--endversion
    }

    public static void bindWorldMethods(LuaTable world, MLuaRegistration registration) {
        MLuaWorldChunkApi.bind(world, registration);
    }

    private static ServerLevel requireServerLevel(Level level, String method) {
        if (level instanceof ServerLevel server) return server;
        throw new LuaError("[MLua] " + method + " is server-side only.");
    }

    static boolean setForced(Level level, ChunkPos position, boolean forced, String method) {
        ServerLevel server = requireServerLevel(level, method);
        server.setChunkForced(chunkX(position), chunkZ(position), forced);
        return isForced(server, position) == forced;
    }

    private static void bind(LuaTable chunk, String method, Body body) {
        chunk.set(method, new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                try {
                    Level world = level(args.arg1());
                    return MLuaGameThreadAccess.call(world, () -> body.apply(args));
                } catch (LuaError error) {
                    throw error;
                } catch (Exception error) {
                    throw MLuaLuaErrors.javaFailure("Chunk method", error);
                }
            }
        });
    }

    private static boolean isForced(LuaValue self) {
        Level world = level(self);
        if (!(world instanceof ServerLevel server)) return false;
        ChunkPos pos = chunkPosition(self);
        //--version 26.1
        return server.getForceLoadedChunks().contains(pos.pack());
        //--endversion
        //--version 1.20.1,1.21.1
        return server.getForcedChunks().contains(ChunkPos.asLong(chunkX(pos), chunkZ(pos)));
        //--endversion
    }

    private static void load(LuaValue self) {
        Level world = level(self);
        if (!(world instanceof ServerLevel server)) throw new LuaError("[MLua] Chunk loading is server-side only.");
        ChunkPos pos = chunkPosition(self);
        server.getChunk(chunkX(pos), chunkZ(pos));
    }

    private static boolean ensureLoaded(Level world, LuaValue self) {
        BlockPos origin = chunkOrigin(self, world);
        ChunkPos pos = chunkPosition(self);
        return ensureLoaded(world, pos, origin);
    }

    public static boolean ensureLoaded(Level world, BlockPos position) {
        ChunkPos pos = new ChunkPos(position.getX() >> 4, position.getZ() >> 4);
        return ensureLoaded(world, pos, position);
    }

    private static boolean ensureLoaded(Level world, ChunkPos position, BlockPos probe) {
        if (world.hasChunkAt(probe)) return true;
        if (!(world instanceof ServerLevel server)) return false;
        server.getChunk(chunkX(position), chunkZ(position));
        return world.hasChunkAt(probe);
    }

    private static BlockPos requirePosition(Varargs args, int index, LuaValue self) {
        double[] coordinates = MLuaPositionArguments.vector3(args.arg(index), "Chunk block position");
        ChunkPos chunk = chunkPosition(self);
        BlockPos position = BlockPos.containing(coordinates[0], coordinates[1], coordinates[2]);
        if (position.getX() >> 4 != chunkX(chunk) || position.getZ() >> 4 != chunkZ(chunk)) {
            throw new LuaError("[MLua] Block position is outside this chunk.");
        }
        return position;
    }

    private static int coordinateWidth(Varargs args, int index) {
        return args.arg(index).istable() ? 1 : 3;
    }

    private static BlockPos chunkOrigin(LuaValue self, Level world) {
        ChunkPos pos = chunkPosition(self);
        return new BlockPos(pos.getMinBlockX(), minBuildHeight(world), pos.getMinBlockZ());
    }

    private static ChunkPos chunkPosition(LuaValue self) {
        LuaTable table = self.checktable();
        return new ChunkPos(table.get("__chunkX").checkint(), table.get("__chunkZ").checkint());
    }

    static int minBuildHeight(Level world) {
        //--version 26.1
        return world.getMinY();
        //--endversion
        //--version 1.20.1,1.21.1
        return world.getMinBuildHeight();
        //--endversion
    }

    private static int maxBuildHeight(Level world) {
        //--version 26.1
        return world.getMinY() + world.getHeight();
        //--endversion
        //--version 1.20.1,1.21.1
        return world.getMaxBuildHeight();
        //--endversion
    }

    private static Heightmap.Types heightmapType(String value) {
        try {
            return Heightmap.Types.valueOf(value.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException error) {
            String available = java.util.Arrays.stream(Heightmap.Types.values())
                .map(Enum::name)
                .collect(java.util.stream.Collectors.joining(", "));
            throw new LuaError("[MLua] Unknown heightmap '" + value + "'. Available types: " + available);
        }
    }

    private static ResourceLocation biomeId(Level world, BlockPos pos) {
        //--version 26.1
        return world.registryAccess().lookupOrThrow(Registries.BIOME).getKey(world.getBiome(pos).value());
        //--endversion
        //--version 1.20.1,1.21.1
        return world.registryAccess().registryOrThrow(Registries.BIOME).getKey(world.getBiome(pos).value());
        //--endversion
    }

    private static int chunkX(LuaValue self) {
        return self.checktable().get("__chunkX").checkint();
    }

    private static int chunkZ(LuaValue self) {
        return self.checktable().get("__chunkZ").checkint();
    }

    static Level level(LuaValue self) {
        LuaValue value = self.checktable().get("__level");
        if (!value.isuserdata(Level.class)) throw new LuaError("[MLua] Chunk has no live dimension.");
        return (Level)value.touserdata(Level.class);
    }

    @FunctionalInterface
    private interface Body {
        LuaValue apply(Varargs args);
    }
}
