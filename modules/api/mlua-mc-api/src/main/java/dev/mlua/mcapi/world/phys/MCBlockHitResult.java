package dev.mlua.mcapi.world.phys;

import dev.mlua.mcapi.core.MCBlockPos;
import dev.mlua.mcapi.core.MCDirection;
import dev.mlua.mcapi.core.MCVec3;

public interface MCBlockHitResult extends MCHitResult {
    MCBlockPos getBlockPos();
    MCDirection getDirection();
    MCVec3 getLocation();
}
