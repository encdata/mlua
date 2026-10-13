package dev.mlua.mcapi.item;

import dev.mlua.mcapi.core.MCBlockPos;
import dev.mlua.mcapi.core.MCDirection;
import dev.mlua.mcapi.core.MCVec3;
import dev.mlua.mcapi.entity.MCPlayer;
import dev.mlua.mcapi.world.MCInteractionHand;
import dev.mlua.mcapi.world.MCLevel;
import dev.mlua.mcapi.world.phys.MCBlockHitResult;

public interface MCUseOnContext {
    MCLevel getLevel();
    MCPlayer getPlayer();
    MCInteractionHand getHand();
    MCBlockHitResult getHitResult();
    MCBlockPos getClickedPos();
    MCDirection getClickedFace();
    MCVec3 getClickLocation();
    MCItemStack getItemInHand();
}
