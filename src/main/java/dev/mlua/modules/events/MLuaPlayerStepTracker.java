package dev.mlua.modules.events;

import dev.mlua.core.MLuaRuntime;
import dev.mlua.minecraft.scripted.MLuaBlockCallbackDispatcher;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Owns per-runtime player positions used to dispatch block step callbacks. */
final class MLuaPlayerStepTracker {
    private static final ConcurrentMap<OwnerKey, RuntimePositions> RUNTIMES = new ConcurrentHashMap<>();

    private MLuaPlayerStepTracker() { }

    static void clearRuntime(Object runtimeOwner) {
        if (runtimeOwner != null) RUNTIMES.remove(new OwnerKey(runtimeOwner));
    }

    static void forgetPlayer(UUID playerId) {
        if (playerId == null) return;
        for (RuntimePositions runtime : RUNTIMES.values()) runtime.forgetPlayer(playerId);
    }

    static void forgetPlayer(UUID playerId, Object runtimeOwner) {
        if (playerId == null) return;
        RuntimePositions runtime = RUNTIMES.get(new OwnerKey(runtimeOwner));
        if (runtime != null) runtime.forgetPlayer(playerId);
    }

    static void forgetWorld(ResourceLocation dimension, Object runtimeOwner) {
        if (dimension == null) return;
        RuntimePositions runtime = RUNTIMES.get(new OwnerKey(runtimeOwner));
        if (runtime != null) runtime.levels.remove(dimension);
    }

    static void track(Object levelObject) {
        if (!(levelObject instanceof ServerLevel level)) return;
        Object runtimeOwner = MLuaRuntime.sessionForLevel(level);
        RuntimePositions runtime = RUNTIMES.computeIfAbsent(new OwnerKey(runtimeOwner), ignored -> new RuntimePositions());
        ResourceLocation dimension = level.dimension().location();
        ConcurrentMap<UUID, Long> positions = runtime.levels.computeIfAbsent(dimension, ignored -> new ConcurrentHashMap<>());
        for (ServerPlayer player : level.players()) {
            BlockPos position = player.blockPosition().below();
            long currentPosition = position.asLong();
            Long previousPosition = positions.put(player.getUUID(), currentPosition);
            if (previousPosition == null || previousPosition.longValue() != currentPosition) {
                MLuaBlockCallbackDispatcher.fireOnStepOn(level, position, player);
            }
        }
    }

    private static final class RuntimePositions {
        private final ConcurrentMap<ResourceLocation, ConcurrentMap<UUID, Long>> levels = new ConcurrentHashMap<>();

        private void forgetPlayer(UUID playerId) {
            levels.values().forEach(positions -> positions.remove(playerId));
        }
    }

    private static final class OwnerKey {
        private final Object value;
        private final int hashCode;

        private OwnerKey(Object value) {
            this.value = value;
            this.hashCode = System.identityHashCode(value);
        }

        @Override public int hashCode() { return hashCode; }

        @Override public boolean equals(Object other) {
            return other instanceof OwnerKey key && key.value == this.value;
        }
    }
}
