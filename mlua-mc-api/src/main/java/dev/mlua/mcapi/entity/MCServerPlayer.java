package dev.mlua.mcapi.entity;

import dev.mlua.mcapi.world.MCServerLevel;
import dev.mlua.mcapi.network.MCConnection;

public interface MCServerPlayer extends MCPlayer {
    MCServerLevel getServerLevel();
    @Deprecated(forRemoval = false)
    default MCServerLevel serverLevel() { return getServerLevel(); }
    @Override
    MCConnection getConnection();
}
