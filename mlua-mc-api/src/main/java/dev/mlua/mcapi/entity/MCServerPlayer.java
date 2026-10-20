package dev.mlua.mcapi.entity;

import dev.mlua.mcapi.world.MCServerLevel;
import dev.mlua.mcapi.network.MCConnection;

public interface MCServerPlayer extends MCPlayer {
    MCServerLevel getServerLevel();
    @Override
    MCConnection getConnection();
}
