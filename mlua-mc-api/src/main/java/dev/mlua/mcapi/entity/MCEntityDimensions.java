package dev.mlua.mcapi.entity;

import dev.mlua.mcapi.core.MCAABB;
import dev.mlua.mcapi.core.MCVec3;

public interface MCEntityDimensions {
    float width();
    float height();
    MCAABB makeBoundingBox(MCVec3 pos);
}
