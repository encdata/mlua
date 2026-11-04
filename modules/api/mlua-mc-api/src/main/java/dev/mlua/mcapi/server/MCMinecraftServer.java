package dev.mlua.mcapi.server;

import dev.mlua.mcapi.MCApiRuntime;

public interface MCMinecraftServer {
    String getServerVersion();
    int getPlayerCount();
    int getMaxPlayers();
    MCPlayerList getPlayerList();
    Object[] getAllLevels();
    boolean isSingleplayer();
    boolean isDedicatedServer();
    Object getWorldData();

    static MCMinecraftServer getServer() {
        return MCApiRuntime.getServer();
    }
}
