package dev.mlua.mc.impl.world.phys;

import dev.mlua.mcapi.entity.MCEntity;
import dev.mlua.mcapi.world.phys.MCEntityHitResult;
import dev.mlua.mc.impl.entity.MCEntityImpl;
import net.minecraft.world.phys.EntityHitResult;

public class MCEntityHitResultImpl extends MCHitResultImpl implements MCEntityHitResult {
    private final EntityHitResult inner;

    public MCEntityHitResultImpl(EntityHitResult inner) {
        super(inner);
        this.inner = inner;
    }

    @Override
    public MCEntity getEntity() {
        return new MCEntityImpl(inner.getEntity());
    }
}
