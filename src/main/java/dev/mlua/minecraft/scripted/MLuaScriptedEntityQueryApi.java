package dev.mlua.minecraft.scripted;

import dev.mlua.core.MLuaRuntime;
import dev.mlua.minecraft.MLuaResourceLocations;
import dev.mlua.minecraft.binding.MLuaBlockWrapper;
import dev.mlua.minecraft.binding.MLuaLuaArguments;
import dev.mlua.modules.entity.MLuaEntityQuery;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/** Binds spatial queries exposed by scripted entities. */
final class MLuaScriptedEntityQueryApi {
    private MLuaScriptedEntityQueryApi() { }

    static void bind(LuaTable methods, MLuaScriptedEntity entity) {
        methods.set("FindNearestPlayer", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                double distance = boundedSearchDistance(MLuaLuaArguments.optionalFiniteNumber(args, 2, 16.0,
                    "ScriptedEntity:FindNearestPlayer", "range"));
                boolean requireLineOfSight = MLuaLuaArguments.optionalBoolean(args, 3, false,
                    "ScriptedEntity:FindNearestPlayer", "requireLineOfSight");
                Level level = entity.level();
                Player[] nearest = new Player[1];
                double[] nearestDistance = {distance * distance};
                MLuaEntityQuery.forEachWithinRadius(level, entity.getX(), entity.getY(), entity.getZ(), distance,
                    entity, MLuaEntityQuery.alivePlayersFilter(), candidate -> {
                        if (candidate instanceof Player target
                                && (!requireLineOfSight || entity.hasLineOfSight(target))) {
                            double candidateDistance = entity.distanceToSqr(target);
                            if (candidateDistance <= nearestDistance[0]) {
                                nearestDistance[0] = candidateDistance;
                                nearest[0] = target;
                            }
                        }
                    });
                return nearest[0] == null ? LuaValue.NIL : entity.entityToLua(nearest[0]);
            }
        });
        methods.set("FindNearestEntity", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                LuaValue typeValue = args.arg(2);
                if (!typeValue.isnil() && typeValue.type() != LuaValue.TSTRING) {
                    throw new LuaError("[MLua] ScriptedEntity:FindNearestEntity type must be an entity type id or nil.");
                }
                String typeId = typeValue.isnil() ? null : typeValue.tojstring();
                double distance = boundedSearchDistance(MLuaLuaArguments.optionalFiniteNumber(args, 3, 16.0,
                    "ScriptedEntity:FindNearestEntity", "range"));
                Level level = entity.level();
                LivingEntity[] nearest = new LivingEntity[1];
                double[] bestDistance = {distance * distance};
                MLuaEntityQuery.forEachWithinRadius(level, entity.getX(), entity.getY(), entity.getZ(), distance,
                    entity, MLuaEntityQuery.aliveEntitiesFilter(
                        typeId == null || typeId.isBlank() ? null : typeId), candidate -> {
                        if (!(candidate instanceof LivingEntity target)) return;
                        double candidateDistance = entity.distanceToSqr(target);
                        if (candidateDistance < bestDistance[0]) {
                            bestDistance[0] = candidateDistance;
                            nearest[0] = target;
                        }
                    });
                return nearest[0] == null ? LuaValue.NIL : entity.entityToLua(nearest[0]);
            }
        });
        methods.set("FindNearestBlock", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                LuaValue blockValue = args.arg(2);
                if (blockValue.type() != LuaValue.TSTRING || blockValue.tojstring().isBlank()) {
                    throw new LuaError("[MLua] ScriptedEntity:FindNearestBlock expects a non-empty block id string.");
                }
                var blockId = MLuaResourceLocations.tryParseOrNull(blockValue.tojstring());
                if (blockId == null) {
                    throw new LuaError("[MLua] ScriptedEntity:FindNearestBlock received an invalid block id: "
                        + blockValue.tojstring());
                }
                int requestedRadius = MLuaLuaArguments.optionalWholeInt(args, 3, 10,
                    "ScriptedEntity:FindNearestBlock", "radius");
                int radius = Math.max(0, Math.min(16, requestedRadius));
                BlockPos origin = entity.blockPosition();
                BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
                int minChunkX = (origin.getX() - radius) >> 4;
                int minChunkZ = (origin.getZ() - radius) >> 4;
                int chunkWidth = ((origin.getX() + radius) >> 4) - minChunkX + 1;
                int chunkDepth = ((origin.getZ() + radius) >> 4) - minChunkZ + 1;
                Level level = entity.level();
                boolean[][] checkedChunks = new boolean[chunkWidth][chunkDepth];
                boolean[][] loadedChunks = new boolean[chunkWidth][chunkDepth];
                BlockPos nearest = null;
                int radiusSquared = radius * radius;
                int nearestDistanceSquared = Integer.MAX_VALUE;
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dy = -radius; dy <= radius; dy++) {
                        for (int dz = -radius; dz <= radius; dz++) {
                            int distanceSquared = dx * dx + dy * dy + dz * dz;
                            if (distanceSquared > radiusSquared || distanceSquared >= nearestDistanceSquared) continue;
                            mutable.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                            int chunkX = (mutable.getX() >> 4) - minChunkX;
                            int chunkZ = (mutable.getZ() >> 4) - minChunkZ;
                            if (!checkedChunks[chunkX][chunkZ]) {
                                checkedChunks[chunkX][chunkZ] = true;
                                BlockPos chunkProbe = new BlockPos(mutable.getX(), origin.getY(), mutable.getZ());
                                loadedChunks[chunkX][chunkZ] = level.hasChunkAt(chunkProbe);
                            }
                            if (!loadedChunks[chunkX][chunkZ]) continue;
                            BlockState state = level.getBlockState(mutable);
                            if (blockId.equals(BuiltInRegistries.BLOCK.getKey(state.getBlock()))) {
                                nearestDistanceSquared = distanceSquared;
                                nearest = mutable.immutable();
                            }
                        }
                    }
                }
                return nearest == null ? LuaValue.NIL : MLuaBlockWrapper.blockTable(
                    level, nearest, MLuaRuntime.registrationForLevel(level));
            }
        });
        methods.set("DistanceTo", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                Entity other = entity.resolveEntity(args.arg(2));
                if (other == null) {
                    throw new LuaError("[MLua] DistanceTo expects a live Entity or Player instance.");
                }
                return LuaValue.valueOf(Math.sqrt(entity.distanceToSqr(other)));
            }
        });
    }

    private static double boundedSearchDistance(double distance) {
        if (!Double.isFinite(distance)) return 16.0;
        return Math.max(0.0, Math.min(128.0, distance));
    }
}
