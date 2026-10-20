package dev.mlua.mcapi.world.phys;

import dev.mlua.mcapi.core.MCVec3;

public interface MCClipContext {
    MCVec3 getFrom();
    MCVec3 getTo();
    CollisionBlock getCollisionBlock();
    Fluid getFluid();

    static MCClipContext create(MCVec3 from, MCVec3 to, CollisionBlock block, Fluid fluid) {
        return new MCClipContext() {
            @Override
            public MCVec3 getFrom() {
                return from;
            }

            @Override
            public MCVec3 getTo() {
                return to;
            }

            @Override
            public CollisionBlock getCollisionBlock() {
                return block;
            }

            @Override
            public Fluid getFluid() {
                return fluid;
            }
        };
    }

    enum CollisionBlock {
        COLLIDER,
        VISUAL,
        FALLDAMAGE
    }

    enum Fluid {
        NONE,
        SOURCE_ONLY,
        ANY
    }
}
