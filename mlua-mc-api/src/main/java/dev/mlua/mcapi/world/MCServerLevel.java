package dev.mlua.mcapi.world;

import dev.mlua.mcapi.chunk.MCChunk;
import dev.mlua.mcapi.server.MCMinecraftServer;
import dev.mlua.mcapi.entity.MCServerPlayer;

import java.util.List;

public interface MCServerLevel extends MCLevel {
    MCMinecraftServer getServer();

    List<MCServerPlayer> getPlayers();

    MCChunk getChunk(int x, int z);
}
