package dev.mlua.mc.impl.server;

import dev.mlua.mcapi.entity.MCServerPlayer;
import dev.mlua.mcapi.server.MCPlayerList;
import dev.mlua.mc.impl.entity.MCServerPlayerImpl;
import net.minecraft.server.players.PlayerList;

import java.util.ArrayList;
import java.util.List;

public class MCPlayerListImpl implements MCPlayerList {
    private final PlayerList inner;

    public MCPlayerListImpl(PlayerList inner) {
        this.inner = inner;
    }

    public static MCPlayerListImpl fromReal(PlayerList inner) {
        return inner == null ? null : new MCPlayerListImpl(inner);
    }

    public PlayerList inner() {
        return inner;
    }

    @Override
    public List<MCServerPlayer> getPlayers() {
        List<MCServerPlayer> result = new ArrayList<>();
        for (net.minecraft.server.level.ServerPlayer player : inner.getPlayers()) {
            result.add(MCServerPlayerImpl.fromReal(player));
        }
        return result;
    }

    @Override
    public boolean isOp(Object gameProfile) {
        if (gameProfile instanceof com.mojang.authlib.GameProfile profile) {
            return inner.isOp(profile);
        }
        return false;
    }

    @Override
    public int getPlayerCount() {
        return inner.getPlayerCount();
    }

    @Override
    public int getMaxPlayers() {
        return inner.getMaxPlayers();
    }
}
