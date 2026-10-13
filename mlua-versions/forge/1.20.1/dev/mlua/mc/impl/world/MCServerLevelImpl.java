package dev.mlua.mc.impl.world;

import dev.mlua.mcapi.chunk.MCChunk;
import dev.mlua.mcapi.server.MCMinecraftServer;
import dev.mlua.mcapi.world.MCServerLevel;
import dev.mlua.mc.impl.chunk.MCChunkImpl;
import dev.mlua.mc.impl.server.MCMinecraftServerImpl;
import java.util.List;
import net.minecraft.server.level.ServerLevel;

public class MCServerLevelImpl extends MCLevelImpl implements MCServerLevel {
    private final ServerLevel inner;

    public MCServerLevelImpl(ServerLevel inner) {
        super(inner);
        this.inner = inner;
    }

    @Override
    public MCMinecraftServer getServer() {
        return MCMinecraftServerImpl.fromReal(inner.getServer());
    }

    @Override
    public List<Object> players() {
        return List.copyOf(inner.players());
    }

    @Override
    public MCChunk getChunk(int x, int z) {
        return MCChunkImpl.fromReal(inner.getChunk(x, z));
    }
}
