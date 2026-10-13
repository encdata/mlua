package dev.mlua.minecraft.entity;

import java.util.function.Function;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

/** Resolves numeric entity IDs without silently choosing between dimensions. */
public final class MLuaEntityLookup {
    private MLuaEntityLookup() {
    }

    public static LookupResult<Entity> findById(MinecraftServer server, int entityId) {
        if (server == null) return new LookupResult<>(null, false);
        return resolveUnique(server.getAllLevels(), level -> level.getEntity(entityId));
    }

    static <S, T> LookupResult<T> resolveUnique(Iterable<S> sources, Function<S, T> resolver) {
        T match = null;
        for (S source : sources) {
            T candidate = resolver.apply(source);
            if (candidate == null) continue;
            if (match != null) return new LookupResult<>(null, true);
            match = candidate;
        }
        return new LookupResult<>(match, false);
    }

    public record LookupResult<T>(T value, boolean ambiguous) {
        public boolean found() {
            return value != null;
        }
    }
}
