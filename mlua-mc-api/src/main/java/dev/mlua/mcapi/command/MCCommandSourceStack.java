package dev.mlua.mcapi.command;

import dev.mlua.mcapi.core.MCVec3;
import dev.mlua.mcapi.entity.MCEntity;
import dev.mlua.mcapi.entity.MCPlayer;
import dev.mlua.mcapi.world.MCLevel;

public interface MCCommandSourceStack {
    MCLevel getLevel();
    MCPlayer getPlayer();
    MCEntity getEntity();
    MCVec3 getPosition();
    boolean hasPermission(int level);
    Object getDisplayName();
}
