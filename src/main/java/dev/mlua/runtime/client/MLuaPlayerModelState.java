package dev.mlua.runtime.client;

import dev.mlua.minecraft.animation.MLuaEntityScope;

public interface MLuaPlayerModelState {
    MLuaEntityScope mlua$getEntityScope();

    void mlua$setEntityScope(MLuaEntityScope scope);
}
