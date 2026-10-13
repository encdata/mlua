package dev.mlua.mcapi.world.phys;

import dev.mlua.mcapi.entity.MCEntity;

public interface MCEntityHitResult extends MCHitResult {
    MCEntity getEntity();
}
