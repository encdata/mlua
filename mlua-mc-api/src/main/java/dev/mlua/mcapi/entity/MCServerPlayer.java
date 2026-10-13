package dev.mlua.mcapi.entity;

import dev.mlua.mcapi.world.MCServerLevel;

public interface MCServerPlayer extends MCPlayer {
    MCServerLevel serverLevel();
    Object getConnection();
}
