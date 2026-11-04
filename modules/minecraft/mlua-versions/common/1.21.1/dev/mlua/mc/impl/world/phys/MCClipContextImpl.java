package dev.mlua.mc.impl.world.phys;

import dev.mlua.mc.impl.core.MCVec3Impl;
import dev.mlua.mcapi.core.MCVec3;
import dev.mlua.mcapi.world.phys.MCClipContext;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.Vec3;

public class MCClipContextImpl implements MCClipContext {
    private final ClipContext inner;
    private final CollisionBlock block;
    private final Fluid fluid;

    public MCClipContextImpl(ClipContext inner, CollisionBlock block, Fluid fluid) {
        this.inner = inner;
        this.block = block;
        this.fluid = fluid;
    }

    public ClipContext inner() {
        return inner;
    }

    public static MCClipContext create(MCVec3 from, MCVec3 to, CollisionBlock block, Fluid fluid) {
        Vec3 f = new Vec3(from.x(), from.y(), from.z());
        Vec3 t = new Vec3(to.x(), to.y(), to.z());
        ClipContext.Block cb = switch (block) {
            case COLLIDER -> ClipContext.Block.COLLIDER;
            case VISUAL -> ClipContext.Block.VISUAL;
            case FALLDAMAGE -> ClipContext.Block.FALLDAMAGE_RESETTING;
        };
        ClipContext.Fluid fl = switch (fluid) {
            case NONE -> ClipContext.Fluid.NONE;
            case SOURCE_ONLY -> ClipContext.Fluid.SOURCE_ONLY;
            case ANY -> ClipContext.Fluid.ANY;
        };
        return new MCClipContextImpl(new ClipContext(f, t, cb, fl, (Entity) null), block, fluid);
    }

    @Override
    public MCVec3 getFrom() {
        return MCVec3Impl.fromReal(inner.getFrom());
    }

    @Override
    public MCVec3 getTo() {
        return MCVec3Impl.fromReal(inner.getTo());
    }

    @Override
    public CollisionBlock getCollisionBlock() {
        return block;
    }

    @Override
    public Fluid getFluid() {
        return fluid;
    }
}
