package dev.mlua.mcapi.world.phys;

public interface MCHitResult {
    Type getType();

    enum Type {
        MISS,
        BLOCK,
        ENTITY
    }
}
