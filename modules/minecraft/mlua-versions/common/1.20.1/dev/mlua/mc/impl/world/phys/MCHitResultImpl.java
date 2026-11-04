package dev.mlua.mc.impl.world.phys;

import dev.mlua.mcapi.world.phys.MCHitResult;
import net.minecraft.world.phys.HitResult;

public class MCHitResultImpl implements MCHitResult {
    private final HitResult inner;

    public MCHitResultImpl(HitResult inner) {
        this.inner = inner;
    }

    public HitResult inner() {
        return inner;
    }

    public static MCHitResult fromReal(HitResult hit) {
        if (hit == null) return null;
        if (hit.getType() == HitResult.Type.MISS) {
            return new MCHitResultImpl(hit);
        }
        if (hit instanceof net.minecraft.world.phys.BlockHitResult bhr) {
            return new MCBlockHitResultImpl(bhr);
        }
        if (hit instanceof net.minecraft.world.phys.EntityHitResult ehr) {
            return new MCEntityHitResultImpl(ehr);
        }
        return new MCHitResultImpl(hit);
    }

    @Override
    public Type getType() {
        return switch (inner.getType()) {
            case MISS -> Type.MISS;
            case BLOCK -> Type.BLOCK;
            case ENTITY -> Type.ENTITY;
        };
    }
}
