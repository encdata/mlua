package dev.mlua.mcapi.world;

import dev.mlua.mcapi.chunk.MCChunk;
import dev.mlua.mcapi.server.MCMinecraftServer;
import dev.mlua.mcapi.entity.MCServerPlayer;

import java.util.ArrayList;
import java.util.List;

public interface MCServerLevel extends MCLevel {
    MCMinecraftServer getServer();

    List<MCServerPlayer> getPlayers();
    @Deprecated(forRemoval = false)
    default List<Object> players() { return new ArrayList<Object>(getPlayers()); }

    MCChunk getChunk(int x, int z);
}
