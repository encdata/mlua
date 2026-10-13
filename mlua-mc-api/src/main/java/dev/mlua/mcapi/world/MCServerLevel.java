package dev.mlua.mcapi.world;

import dev.mlua.mcapi.chunk.MCChunk;
import dev.mlua.mcapi.server.MCMinecraftServer;

import java.util.List;

public interface MCServerLevel extends MCLevel {
    MCMinecraftServer getServer();

    List<Object> players();

    MCChunk getChunk(int x, int z);
}
