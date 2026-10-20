package dev.mlua.mc.impl.entity;

import dev.mlua.mcapi.entity.MCServerPlayer;
import dev.mlua.mcapi.network.MCConnection;
import dev.mlua.mc.impl.network.MCConnectionImpl;
import dev.mlua.mcapi.world.MCServerLevel;
import dev.mlua.mc.impl.world.MCServerLevelImpl;
import net.minecraft.server.level.ServerPlayer;

public class MCServerPlayerImpl extends MCPlayerImpl implements MCServerPlayer {

    public MCServerPlayerImpl(ServerPlayer inner) {
        super(inner);
    }

    private ServerPlayer serverPlayer() {
        return (ServerPlayer) inner;
    }

    public static MCServerPlayerImpl fromReal(ServerPlayer inner) {
        return inner == null ? null : new MCServerPlayerImpl(inner);
    }

    @Override
    public MCServerLevel getServerLevel() {
        return new MCServerLevelImpl(serverPlayer().serverLevel());
    }

    @Override
    public MCConnection getConnection() {
        return MCConnectionImpl.fromPlayerListener(serverPlayer().connection);
    }
}
