package dev.mlua.mc.impl.server;

import dev.mlua.mcapi.server.MCMinecraftServer;
import dev.mlua.mcapi.server.MCPlayerList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import java.util.ArrayList;

public class MCMinecraftServerImpl implements MCMinecraftServer {
    private final MinecraftServer inner;

    public MCMinecraftServerImpl(MinecraftServer inner) {
        this.inner = inner;
    }

    public static MCMinecraftServerImpl fromReal(MinecraftServer inner) {
        return inner == null ? null : new MCMinecraftServerImpl(inner);
    }

    public MinecraftServer inner() {
        return inner;
    }

    @Override
    public String getServerVersion() {
        return inner.getServerVersion();
    }

    @Override
    public int getPlayerCount() {
        return inner.getPlayerCount();
    }

    @Override
    public int getMaxPlayers() {
        return inner.getMaxPlayers();
    }

    @Override
    public MCPlayerList getPlayerList() {
        return MCPlayerListImpl.fromReal(inner.getPlayerList());
    }

    @Override
    public Object[] getAllLevels() {
        java.util.List<Object> levels = new ArrayList<>();
        for (ServerLevel level : inner.getAllLevels()) {
            levels.add(level);
        }
        return levels.toArray();
    }

    @Override
    public boolean isSingleplayer() {
        return inner.isSingleplayer();
    }

    @Override
    public boolean isDedicatedServer() {
        return inner.isDedicatedServer();
    }

    @Override
    public Object getWorldData() {
        return inner.getWorldData();
    }
}
